package com.example.data

import com.example.model.McpToolInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed interface McpTestOutcome {
    data class Success(
        val tools: List<McpToolInfo>,
        val sessionId: String?,
        val latencyMs: Long
    ) : McpTestOutcome

    data class Failure(val errorMessage: String) : McpTestOutcome
}

data class McpCallOutcome(
    val success: Boolean,
    val output: String,
    val sessionId: String? = null
)

class McpClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val requestIdCounter = AtomicInteger(1)

    suspend fun testAndDiscoverTools(serverUrl: String): McpTestOutcome = withContext(Dispatchers.IO) {
        val url = serverUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext McpTestOutcome.Failure("Invalid URL: must start with http:// or https://")
        }

        val startMs = System.currentTimeMillis()
        try {
            val initResult = sendInitialize(url)
            if (initResult.errorMessage != null && !initResult.methodNotFound) {
                return@withContext McpTestOutcome.Failure(initResult.errorMessage)
            }

            val sessionId = initResult.sessionId
            if (initResult.errorMessage == null) {
                sendInitializedNotification(url, sessionId)
            }

            val listResp = sendJsonRpc(
                url = url,
                sessionId = sessionId,
                method = "tools/list",
                params = JSONObject()
            )
            if (listResp.errorMessage != null) {
                return@withContext McpTestOutcome.Failure(listResp.errorMessage)
            }

            val toolsArr = listResp.resultObj?.optJSONArray("tools") ?: JSONArray()
            val tools = parseToolsArray(toolsArr)
            val elapsed = System.currentTimeMillis() - startMs

            McpTestOutcome.Success(
                tools = tools,
                sessionId = listResp.sessionId ?: sessionId,
                latencyMs = elapsed
            )
        } catch (e: Exception) {
            McpTestOutcome.Failure("${e.javaClass.simpleName}: ${e.message ?: "Connection failed"}")
        }
    }

    suspend fun callTool(
        serverUrl: String,
        sessionId: String?,
        toolName: String,
        argumentsJson: String
    ): McpCallOutcome = withContext(Dispatchers.IO) {
        val url = serverUrl.trim()
        val argsObj = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
        var activeSessionId = sessionId

        if (activeSessionId.isNullOrBlank()) {
            val init = sendInitialize(url)
            if (init.errorMessage == null) {
                activeSessionId = init.sessionId
                sendInitializedNotification(url, activeSessionId)
            }
        }

        val params = JSONObject().apply {
            put("name", toolName)
            put("arguments", argsObj)
        }

        var rpcResp = sendJsonRpc(
            url = url,
            sessionId = activeSessionId,
            method = "tools/call",
            params = params
        )

        // If session expired/missing on server, re-initialize once and retry
        if (rpcResp.errorMessage != null &&
            (rpcResp.httpCode == 400 || rpcResp.httpCode == 404) &&
            rpcResp.errorMessage.contains("session", ignoreCase = true)
        ) {
            val reInit = sendInitialize(url)
            if (reInit.errorMessage == null) {
                activeSessionId = reInit.sessionId
                sendInitializedNotification(url, activeSessionId)
                rpcResp = sendJsonRpc(
                    url = url,
                    sessionId = activeSessionId,
                    method = "tools/call",
                    params = params
                )
            }
        }

        if (rpcResp.errorMessage != null) {
            return@withContext McpCallOutcome(
                success = false,
                output = rpcResp.errorMessage,
                sessionId = rpcResp.sessionId ?: activeSessionId
            )
        }

        val resultObj = rpcResp.resultObj ?: JSONObject()
        val isError = resultObj.optBoolean("isError", false)
        val contentArr = resultObj.optJSONArray("content")
        val text = if (contentArr != null && contentArr.length() > 0) {
            buildString {
                for (i in 0 until contentArr.length()) {
                    val item = contentArr.optJSONObject(i) ?: continue
                    val partText = item.optString("text")
                        .ifBlank { item.optJSONObject("resource")?.optString("text").orEmpty() }
                        .ifBlank { item.toString() }
                    if (isNotEmpty()) appendLine()
                    append(partText)
                }
            }
        } else {
            resultObj.toString(2)
        }

        McpCallOutcome(
            success = !isError,
            output = text.ifBlank { "(empty MCP result)" },
            sessionId = rpcResp.sessionId ?: activeSessionId
        )
    }

    private fun sendInitialize(url: String): JsonRpcResponse {
        val params = JSONObject().apply {
            put("protocolVersion", "2024-11-05")
            put("capabilities", JSONObject())
            put("clientInfo", JSONObject().apply {
                put("name", "PocketCodeAgent")
                put("version", "1.0")
            })
        }
        return sendJsonRpc(url = url, sessionId = null, method = "initialize", params = params)
    }

    private fun sendInitializedNotification(url: String, sessionId: String?) {
        runCatching {
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "notifications/initialized")
            }
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json, text/event-stream")
                .apply {
                    if (!sessionId.isNullOrBlank()) {
                        header("Mcp-Session-Id", sessionId)
                    }
                }
                .build()
            client.newCall(request).execute().close()
        }
    }

    private fun sendJsonRpc(
        url: String,
        sessionId: String?,
        method: String,
        params: JSONObject
    ): JsonRpcResponse {
        val reqId = requestIdCounter.getAndIncrement()
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", reqId)
            put("method", method)
            put("params", params)
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json, text/event-stream")
            .apply {
                if (!sessionId.isNullOrBlank()) {
                    header("Mcp-Session-Id", sessionId)
                }
            }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val respSessionId = response.header("Mcp-Session-Id")
                    ?: response.header("mcp-session-id")
                    ?: sessionId
                val rawBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val extracted = extractErrorFromPayload(rawBody)
                        ?: rawBody.trim().take(220).ifBlank { response.message.ifBlank { "Request failed" } }
                    return JsonRpcResponse(
                        httpCode = response.code,
                        sessionId = respSessionId,
                        errorMessage = "HTTP ${response.code}: $extracted"
                    )
                }

                val rpcObj = parseRpcObjectFromBody(
                    rawBody = rawBody,
                    contentType = response.header("Content-Type").orEmpty(),
                    expectedId = reqId
                ) ?: return JsonRpcResponse(
                    httpCode = response.code,
                    sessionId = respSessionId,
                    errorMessage = "Invalid or empty MCP JSON-RPC response"
                )

                val errObj = rpcObj.optJSONObject("error")
                if (errObj != null) {
                    val code = errObj.optInt("code", 0)
                    val msg = errObj.optString("message").ifBlank { errObj.toString() }
                    return JsonRpcResponse(
                        httpCode = response.code,
                        sessionId = respSessionId,
                        errorMessage = "MCP error ($code): $msg",
                        methodNotFound = code == -32601
                    )
                }

                JsonRpcResponse(
                    httpCode = response.code,
                    sessionId = respSessionId,
                    resultObj = rpcObj.optJSONObject("result")
                )
            }
        } catch (e: Exception) {
            JsonRpcResponse(
                httpCode = 0,
                sessionId = sessionId,
                errorMessage = "${e.javaClass.simpleName}: ${e.message ?: "Network error"}"
            )
        }
    }

    internal fun parseRpcObjectFromBody(
        rawBody: String,
        contentType: String,
        expectedId: Int
    ): JSONObject? {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return null

        if (contentType.contains("text/event-stream", ignoreCase = true) || trimmed.startsWith("event:") || trimmed.startsWith("data:")) {
            var lastObj: JSONObject? = null
            trimmed.lineSequence().forEach { line ->
                if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    val obj = runCatching { JSONObject(payload) }.getOrNull()
                    if (obj != null) {
                        lastObj = obj
                        if (obj.optInt("id", -1) == expectedId || obj.has("result") || obj.has("error")) {
                            return obj
                        }
                    }
                }
            }
            return lastObj
        }

        return runCatching { JSONObject(trimmed) }.getOrNull()
    }

    internal fun parseToolsArray(toolsArr: JSONArray): List<McpToolInfo> {
        val list = mutableListOf<McpToolInfo>()
        for (i in 0 until toolsArr.length()) {
            val obj = toolsArr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isEmpty()) continue
            val desc = obj.optString("description", "")
            val schema = obj.optJSONObject("inputSchema")?.toString()
                ?: """{"type":"object","properties":{}}"""
            list.add(McpToolInfo(name = name, description = desc, inputSchemaJson = schema))
        }
        return list
    }

    private fun extractErrorFromPayload(raw: String): String? {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val err = obj.optJSONObject("error")
        return err?.optString("message")?.takeIf { it.isNotBlank() }
            ?: obj.optString("message").takeIf { it.isNotBlank() }
    }

    private data class JsonRpcResponse(
        val httpCode: Int,
        val sessionId: String? = null,
        val resultObj: JSONObject? = null,
        val errorMessage: String? = null,
        val methodNotFound: Boolean = false
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
