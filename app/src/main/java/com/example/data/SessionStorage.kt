package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.model.AgentWorkMode
import com.example.model.ChatAttachment
import com.example.model.ChatMessageItem
import com.example.model.ChatRole
import com.example.model.ProjectType
import com.example.model.ReasoningEffort
import com.example.model.SessionItem
import com.example.model.ToolExecutionItem
import com.example.model.ToolKind
import com.example.model.ToolStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

data class SessionsHistorySnapshot(
    val sessions: List<SessionItem> = emptyList(),
    val messagesBySession: Map<String, List<ChatMessageItem>> = emptyMap()
)

class SessionStorage(private val context: Context) {

    private val historyFile: File
        get() = File(context.filesDir, HISTORY_FILE_NAME)

    private val attachmentsDir: File
        get() = File(context.filesDir, "chat_attachments").apply { mkdirs() }

    suspend fun load(): SessionsHistorySnapshot = withContext(Dispatchers.IO) {
        val file = historyFile
        if (!file.exists()) return@withContext SessionsHistorySnapshot()
        return@withContext runCatching {
            SessionHistoryCodec.decode(file.readText(StandardCharsets.UTF_8))
        }.getOrDefault(SessionsHistorySnapshot())
    }

    suspend fun save(snapshot: SessionsHistorySnapshot) = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = SessionHistoryCodec.encode(snapshot)
            historyFile.writeText(encoded, StandardCharsets.UTF_8)
        }
    }

    suspend fun prepareImageAttachment(uri: Uri): Result<ChatAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read selected image")
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Selected file is not a supported image")

            val scaled = scaleBitmapDown(decoded, MAX_IMAGE_DIM)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val jpegBytes = baos.toByteArray()

            val id = UUID.randomUUID().toString()
            val previewFile = File(attachmentsDir, "$id.jpg")
            previewFile.writeBytes(jpegBytes)

            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            val displayName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "image.jpg" } ?: "image.jpg"

            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()

            ChatAttachment(
                id = id,
                displayName = displayName,
                mimeType = "image/jpeg",
                base64Data = base64,
                localFilePath = previewFile.absolutePath
            )
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    companion object {
        private const val HISTORY_FILE_NAME = "sessions_history.json"
        private const val MAX_IMAGE_DIM = 1024
    }
}

object SessionHistoryCodec {

    fun encode(snapshot: SessionsHistorySnapshot): String {
        val root = JSONObject()
        val sessionsArr = JSONArray()
        snapshot.sessions.forEach { session ->
            sessionsArr.put(JSONObject().apply {
                put("id", session.id)
                put("projectName", session.projectName)
                put("projectFolder", session.projectFolder)
                put("folderUri", session.folderUri ?: JSONObject.NULL)
                put("mode", session.mode.name)
                put("model", session.model)
                put("createdAtLabel", session.createdAtLabel)
                put("lastActivityAtMs", session.lastActivityAtMs)
                put("projectType", session.projectType.name)
                put("hasIndexHtml", session.hasIndexHtml)
                put("reasoningEffortOverride", session.reasoningEffortOverride?.name ?: JSONObject.NULL)
            })
        }
        root.put("sessions", sessionsArr)

        val messagesObj = JSONObject()
        snapshot.messagesBySession.forEach { (sessionId, messages) ->
            val msgArr = JSONArray()
            messages.forEach { msg ->
                msgArr.put(JSONObject().apply {
                    put("id", msg.id)
                    put("role", msg.role.name)
                    put("text", msg.text)
                    put("timestampMs", msg.timestampMs)
                    put("modelPromptText", msg.modelPromptText ?: JSONObject.NULL)

                    if (msg.attachment != null) {
                        put("attachment", JSONObject().apply {
                            put("id", msg.attachment.id)
                            put("displayName", msg.attachment.displayName)
                            put("mimeType", msg.attachment.mimeType)
                            put("localFilePath", msg.attachment.localFilePath ?: JSONObject.NULL)
                        })
                    }

                    val toolsArr = JSONArray()
                    msg.toolCalls.forEach { tool ->
                        toolsArr.put(JSONObject().apply {
                            put("id", tool.id)
                            put("kind", tool.kind.name)
                            put("target", tool.target)
                            put("summary", tool.summary)
                            put("fullOutput", tool.fullOutput)
                            put("status", tool.status.name)
                            put("canUndo", tool.canUndo)
                            put("backupRelativePath", tool.backupRelativePath ?: JSONObject.NULL)
                            put("previousContent", tool.previousContent ?: JSONObject.NULL)
                            put("existedBefore", tool.existedBefore)
                            put("titleOverride", tool.titleOverride ?: JSONObject.NULL)
                        })
                    }
                    put("toolCalls", toolsArr)
                })
            }
            messagesObj.put(sessionId, msgArr)
        }
        root.put("messagesBySession", messagesObj)
        return root.toString()
    }

    fun decode(rawJson: String): SessionsHistorySnapshot {
        val root = JSONObject(rawJson)
        val sessionsArr = root.optJSONArray("sessions") ?: JSONArray()
        val sessions = mutableListOf<SessionItem>()
        for (i in 0 until sessionsArr.length()) {
            val obj = sessionsArr.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val mode = runCatching { AgentWorkMode.valueOf(obj.optString("mode")) }
                .getOrDefault(AgentWorkMode.BUILD)
            val projectType = runCatching { ProjectType.valueOf(obj.optString("projectType")) }
                .getOrDefault(ProjectType.GENERAL)
            val reasoningOverride = if (obj.isNull("reasoningEffortOverride")) {
                null
            } else {
                runCatching { ReasoningEffort.valueOf(obj.optString("reasoningEffortOverride")) }.getOrNull()
            }
            sessions.add(
                SessionItem(
                    id = id,
                    projectName = obj.optString("projectName", "Workspace"),
                    projectFolder = obj.optString("projectFolder", ""),
                    folderUri = if (obj.isNull("folderUri")) null else obj.optString("folderUri"),
                    mode = mode,
                    model = obj.optString("model", ""),
                    createdAtLabel = obj.optString("createdAtLabel", "Just now"),
                    lastActivityAtMs = obj.optLong("lastActivityAtMs", System.currentTimeMillis()),
                    projectType = projectType,
                    hasIndexHtml = obj.optBoolean("hasIndexHtml", false),
                    reasoningEffortOverride = reasoningOverride
                )
            )
        }

        val messagesMap = mutableMapOf<String, List<ChatMessageItem>>()
        val messagesObj = root.optJSONObject("messagesBySession")
        if (messagesObj != null) {
            val keys = messagesObj.keys()
            while (keys.hasNext()) {
                val sessionId = keys.next()
                val msgArr = messagesObj.optJSONArray(sessionId) ?: continue
                val list = mutableListOf<ChatMessageItem>()
                for (i in 0 until msgArr.length()) {
                    val mObj = msgArr.optJSONObject(i) ?: continue
                    val role = runCatching { ChatRole.valueOf(mObj.optString("role")) }
                        .getOrDefault(ChatRole.ASSISTANT)
                    val attObj = mObj.optJSONObject("attachment")
                    val attachment = if (attObj != null) {
                        val localPath = if (attObj.isNull("localFilePath")) null else attObj.optString("localFilePath")
                        val base64 = localPath?.let { path ->
                            runCatching {
                                val file = File(path)
                                if (file.exists()) Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) else ""
                            }.getOrDefault("")
                        }.orEmpty()
                        ChatAttachment(
                            id = attObj.optString("id", UUID.randomUUID().toString()),
                            displayName = attObj.optString("displayName", "image.jpg"),
                            mimeType = attObj.optString("mimeType", "image/jpeg"),
                            base64Data = base64,
                            localFilePath = localPath
                        )
                    } else {
                        null
                    }

                    val toolsArr = mObj.optJSONArray("toolCalls")
                    val tools = mutableListOf<ToolExecutionItem>()
                    if (toolsArr != null) {
                        for (t in 0 until toolsArr.length()) {
                            val tObj = toolsArr.optJSONObject(t) ?: continue
                            val kind = runCatching { ToolKind.valueOf(tObj.optString("kind")) }
                                .getOrDefault(ToolKind.LIST_FILES)
                            val status = runCatching { ToolStatus.valueOf(tObj.optString("status")) }
                                .getOrDefault(ToolStatus.DONE)
                            tools.add(
                                ToolExecutionItem(
                                    id = tObj.optString("id", UUID.randomUUID().toString()),
                                    kind = kind,
                                    target = tObj.optString("target", ""),
                                    summary = tObj.optString("summary", ""),
                                    fullOutput = tObj.optString("fullOutput", ""),
                                    status = if (status == ToolStatus.RUNNING) ToolStatus.FAILED else status,
                                    canUndo = tObj.optBoolean("canUndo", false),
                                    backupRelativePath = if (tObj.isNull("backupRelativePath")) null else tObj.optString("backupRelativePath"),
                                    previousContent = if (tObj.isNull("previousContent")) null else tObj.optString("previousContent"),
                                    existedBefore = tObj.optBoolean("existedBefore", true),
                                    titleOverride = if (tObj.isNull("titleOverride")) null else tObj.optString("titleOverride")
                                )
                            )
                        }
                    }

                    list.add(
                        ChatMessageItem(
                            id = mObj.optString("id", UUID.randomUUID().toString()),
                            role = role,
                            text = mObj.optString("text", ""),
                            timestampMs = mObj.optLong("timestampMs", System.currentTimeMillis()),
                            attachment = attachment,
                            toolCalls = tools,
                            isStreaming = false,
                            modelPromptText = if (mObj.isNull("modelPromptText")) null else mObj.optString("modelPromptText")
                        )
                    )
                }
                messagesMap[sessionId] = list
            }
        }

        return SessionsHistorySnapshot(
            sessions = sessions,
            messagesBySession = messagesMap
        )
    }
}
