package com.example.data

import com.example.model.AgentWorkMode
import com.example.model.ChatMessageItem
import com.example.model.ChatRole
import com.example.model.ReasoningEffort
import com.example.model.SessionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface RemoteConnectOutcome {
    data class Success(
        val normalizedUrl: String,
        val serverInfo: String,
        val remoteSessions: List<SessionItem> = emptyList()
    ) : RemoteConnectOutcome

    data class Failure(val errorMessage: String) : RemoteConnectOutcome
}

sealed interface RemoteChatOutcome {
    data class Success(val fullText: String) : RemoteChatOutcome
    data object Cancelled : RemoteChatOutcome
    data class Error(val errorMessage: String) : RemoteChatOutcome
}

class RemoteServerClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    suspend fun connect(
        rawUrl: String,
        password: String
    ): RemoteConnectOutcome = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(rawUrl)
            ?: return@withContext RemoteConnectOutcome.Failure("Enter a valid http:// or https:// URL")

        val probePaths = listOf("/health", "/global/health", "/session", "/v1/models", "/")
        var lastHttpFailure: String? = null

        for (path in probePaths) {
            val targetUrl = if (path == "/") normalizedUrl else "$normalizedUrl$path"
            try {
                executeWithAuthRetry(
                    password = password,
                    buildRequest = { authHeader ->
                        Request.Builder()
                            .url(targetUrl)
                            .get()
                            .header("Accept", "application/json, text/plain, */*")
                            .apply {
                                if (!authHeader.isNullOrBlank()) {
                                    header("Authorization", authHeader)
                                }
                            }
                            .build()
                    }
                ).use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val sessions = if (path == "/session") {
                            parseSessionsJson(body)
                        } else {
                            when (val sessionResult = tryFetchRemoteSessionsStrict(normalizedUrl, password)) {
                                is SessionFetchResult.AuthFailed -> {
                                    return@withContext RemoteConnectOutcome.Failure(sessionResult.errorMessage)
                                }
                                is SessionFetchResult.Loaded -> sessionResult.sessions
                            }
                        }
                        return@withContext RemoteConnectOutcome.Success(
                            normalizedUrl = normalizedUrl,
                            serverInfo = "Connected (HTTP ${response.code})",
                            remoteSessions = sessions
                        )
                    }

                    // Auth errors or server errors must fail immediately and show the real error
                    if (response.code == 401 || response.code == 403 || response.code >= 500) {
                        return@withContext RemoteConnectOutcome.Failure(
                            formatHttpError(response.code, response.message, body)
                        )
                    }

                    lastHttpFailure = formatHttpError(response.code, response.message, body)
                    if (response.code != 404 && response.code != 405) {
                        return@withContext RemoteConnectOutcome.Failure(lastHttpFailure!!)
                    }
                }
            } catch (e: Exception) {
                return@withContext RemoteConnectOutcome.Failure(formatException(e))
            }
        }

        RemoteConnectOutcome.Failure(
            lastHttpFailure ?: "Server did not respond on any expected endpoint"
        )
    }

    suspend fun fetchSessions(
        serverUrl: String,
        password: String
    ): List<SessionItem> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(serverUrl) ?: return@withContext emptyList()
        tryFetchRemoteSessions(normalizedUrl, password)
    }

    suspend fun sendChatTurn(
        serverUrl: String,
        password: String,
        sessionId: String,
        model: String,
        mode: AgentWorkMode,
        reasoningEffort: ReasoningEffort,
        historyMessages: List<ChatMessageItem>,
        onCallCreated: (Call) -> Unit,
        onTextDelta: suspend (String) -> Unit
    ): RemoteChatOutcome = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(serverUrl)
            ?: return@withContext RemoteChatOutcome.Error("Invalid server URL: $serverUrl")

        val latestUserPrompt = historyMessages.lastOrNull { it.role == ChatRole.USER }?.let {
            it.modelPromptText ?: it.text
        }.orEmpty()

        val messageArray = JSONArray().apply {
            historyMessages.forEach { msg ->
                val roleStr = when (msg.role) {
                    ChatRole.USER -> "user"
                    ChatRole.ASSISTANT -> "assistant"
                    ChatRole.ERROR, ChatRole.NOTICE -> null
                } ?: return@forEach
                val contentText = if (msg.role == ChatRole.USER) {
                    msg.modelPromptText ?: msg.text
                } else {
                    msg.text
                }
                if (contentText.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", roleStr)
                        put("content", contentText)
                    })
                }
            }
        }

        val endpoints = listOf(
            "$normalizedUrl/chat" to JSONObject().apply {
                put("sessionId", sessionId)
                put("message", latestUserPrompt)
                put("messages", messageArray)
                put("mode", mode.label)
                if (model.isNotBlank()) put("model", model)
                reasoningEffort.apiValue?.let { put("reasoning_effort", it) }
                put("stream", true)
            },
            "$normalizedUrl/v1/chat/completions" to JSONObject().apply {
                put("model", model.ifBlank { "default" })
                put("stream", true)
                put("messages", messageArray)
                reasoningEffort.apiValue?.let { put("reasoning_effort", it) }
            }
        )

        var lastFailure = "Remote server returned no usable chat endpoint"

        for ((index, pair) in endpoints.withIndex()) {
            val (endpointUrl, payload) = pair
            try {
                executeWithAuthRetry(
                    password = password,
                    onCallCreated = onCallCreated,
                    buildRequest = { authHeader ->
                        Request.Builder()
                            .url(endpointUrl)
                            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                            .header("Accept", "text/event-stream, application/json")
                            .apply {
                                if (!authHeader.isNullOrBlank()) {
                                    header("Authorization", authHeader)
                                }
                            }
                            .build()
                    }
                ).use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string().orEmpty()
                        lastFailure = formatHttpError(response.code, response.message, errBody)
                        val canFallback = (response.code == 404 || response.code == 405) &&
                            index < endpoints.lastIndex
                        if (canFallback) {
                            return@use
                        }
                        return@withContext RemoteChatOutcome.Error(lastFailure)
                    }

                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    val body = response.body
                        ?: return@withContext RemoteChatOutcome.Error("Empty response from remote server")

                    if (contentType.contains("text/event-stream")) {
                        val accumulated = StringBuilder()
                        val source = body.source()
                        while (!source.exhausted()) {
                            currentCoroutineContext().ensureActive()
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data:")) {
                                val data = line.removePrefix("data:").trim()
                                if (data == "[DONE]") break
                                val delta = extractTextDelta(data)
                                if (delta.isNotEmpty()) {
                                    accumulated.append(delta)
                                    onTextDelta(delta)
                                }
                            }
                        }
                        return@withContext RemoteChatOutcome.Success(accumulated.toString())
                    } else {
                        val rawBody = body.string()
                        val fullText = extractFullReplyText(rawBody)
                        if (fullText.isNotEmpty()) {
                            onTextDelta(fullText)
                        }
                        return@withContext RemoteChatOutcome.Success(fullText)
                    }
                }
            } catch (e: IOException) {
                val msg = e.message.orEmpty()
                if (msg.contains("Canceled", ignoreCase = true) ||
                    msg.contains("Socket closed", ignoreCase = true)
                ) {
                    return@withContext RemoteChatOutcome.Cancelled
                }
                return@withContext RemoteChatOutcome.Error(formatException(e))
            } catch (e: kotlinx.coroutines.CancellationException) {
                return@withContext RemoteChatOutcome.Cancelled
            } catch (e: Exception) {
                return@withContext RemoteChatOutcome.Error(formatException(e))
            }
        }

        RemoteChatOutcome.Error(lastFailure)
    }

    private sealed interface SessionFetchResult {
        data class Loaded(val sessions: List<SessionItem>) : SessionFetchResult
        data class AuthFailed(val errorMessage: String) : SessionFetchResult
    }

    private fun tryFetchRemoteSessionsStrict(
        normalizedUrl: String,
        password: String
    ): SessionFetchResult {
        return runCatching {
            executeWithAuthRetry(
                password = password,
                buildRequest = { authHeader ->
                    Request.Builder()
                        .url("$normalizedUrl/session")
                        .get()
                        .header("Accept", "application/json")
                        .apply {
                            if (!authHeader.isNullOrBlank()) {
                                header("Authorization", authHeader)
                            }
                        }
                        .build()
                }
            ).use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> SessionFetchResult.Loaded(parseSessionsJson(body))
                    response.code == 401 || response.code == 403 ->
                        SessionFetchResult.AuthFailed(formatHttpError(response.code, response.message, body))
                    else -> SessionFetchResult.Loaded(emptyList())
                }
            }
        }.getOrElse {
            SessionFetchResult.Loaded(emptyList())
        }
    }

    private fun tryFetchRemoteSessions(
        normalizedUrl: String,
        password: String
    ): List<SessionItem> {
        return when (val result = tryFetchRemoteSessionsStrict(normalizedUrl, password)) {
            is SessionFetchResult.Loaded -> result.sessions
            is SessionFetchResult.AuthFailed -> emptyList()
        }
    }

    private fun executeWithAuthRetry(
        password: String,
        onCallCreated: ((Call) -> Unit)? = null,
        buildRequest: (authHeader: String?) -> Request
    ): okhttp3.Response {
        val trimmedPassword = password.trim()
        val initialAuth = if (trimmedPassword.isNotEmpty()) "Bearer $trimmedPassword" else null
        val firstCall = client.newCall(buildRequest(initialAuth))
        onCallCreated?.invoke(firstCall)
        val firstResponse = firstCall.execute()

        if (firstResponse.code == 401 && trimmedPassword.isNotEmpty()) {
            val challenge = firstResponse.header("WWW-Authenticate").orEmpty()
            if (challenge.contains("Basic", ignoreCase = true)) {
                firstResponse.close()
                val basicAuth = Credentials.basic("pocketcode", trimmedPassword)
                val retryCall = client.newCall(buildRequest(basicAuth))
                onCallCreated?.invoke(retryCall)
                return retryCall.execute()
            }
        }
        return firstResponse
    }

    internal fun parseSessionsJson(raw: String): List<SessionItem> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching {
            val array = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                JSONObject(trimmed).optJSONArray("sessions") ?: JSONArray()
            }
            val list = mutableListOf<SessionItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                if (id.isEmpty()) continue
                val title = obj.optString("title")
                    .ifBlank { obj.optString("projectName") }
                    .ifBlank { "Remote session" }
                val folder = obj.optString("directory")
                    .ifBlank { obj.optString("projectFolder") }
                list.add(
                    SessionItem(
                        id = id,
                        projectName = title,
                        projectFolder = folder,
                        lastActivityAtMs = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    internal fun extractTextDelta(jsonLine: String): String {
        val obj = runCatching { JSONObject(jsonLine) }.getOrNull() ?: return jsonLine
        val choices = obj.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val first = choices.optJSONObject(0)
            val delta = first?.optJSONObject("delta")
            if (delta != null) {
                val content = delta.optString("content", "")
                if (content.isNotEmpty()) return content
            }
            val msg = first?.optJSONObject("message")?.optString("content", "")
            if (!msg.isNullOrEmpty()) return msg
        }
        return obj.optString("delta")
            .ifEmpty { obj.optString("content") }
            .ifEmpty { obj.optString("text") }
    }

    internal fun extractFullReplyText(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return ""
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return trimmed
        val choices = obj.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val content = choices.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isNotEmpty()) return content
        }
        obj.optJSONObject("message")?.optString("content")?.takeIf { it.isNotEmpty() }?.let { return it }
        obj.optString("reply").takeIf { it.isNotEmpty() }?.let { return it }
        obj.optString("text").takeIf { it.isNotEmpty() }?.let { return it }
        obj.optString("content").takeIf { it.isNotEmpty() }?.let { return it }

        val parts = obj.optJSONArray("parts")
        if (parts != null) {
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optString("type", "text") == "text") {
                    sb.append(part.optString("text", ""))
                }
            }
            if (sb.isNotEmpty()) return sb.toString()
        }
        return trimmed
    }

    private fun normalizeUrl(raw: String): String? {
        val effective = raw.trim().ifBlank { "http://127.0.0.1:4096" }.trimEnd('/')
        return if (effective.startsWith("http://") || effective.startsWith("https://")) {
            effective
        } else {
            null
        }
    }

    private fun formatHttpError(code: Int, statusMessage: String, body: String): String {
        val extracted = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("detail").takeIf { it.isNotBlank() }
                ?: json.optString("error").takeIf { it.isNotBlank() }
        }.getOrNull().orEmpty()
        val detail = extracted.ifBlank {
            body.trim().take(180).ifBlank { statusMessage.ifBlank { "Request failed" } }
        }
        return "HTTP $code: $detail"
    }

    private fun formatException(e: Exception): String {
        val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        return "${e.javaClass.simpleName}: $msg"
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
