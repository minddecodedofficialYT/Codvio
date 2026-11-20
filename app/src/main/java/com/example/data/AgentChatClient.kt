package com.example.data

import com.example.model.ChatAttachment
import com.example.model.ProviderApiStyle
import com.example.model.ProviderConfig
import com.example.model.ReasoningEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class DynamicToolSpec(
    val name: String,
    val description: String,
    val schema: JSONObject
)

sealed interface LlmTurn {
    data class User(
        val text: String,
        val attachment: ChatAttachment? = null
    ) : LlmTurn

    data class Assistant(
        val text: String,
        val toolCalls: List<LlmToolCall> = emptyList()
    ) : LlmTurn

    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val output: String
    ) : LlmTurn
}

sealed interface StreamTurnOutcome {
    data class Success(
        val text: String,
        val toolCalls: List<LlmToolCall>
    ) : StreamTurnOutcome

    data object Cancelled : StreamTurnOutcome
    data class Error(
        val errorMessage: String,
        val isRateLimit: Boolean = false
    ) : StreamTurnOutcome
}

class AgentChatClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    suspend fun streamTurn(
        provider: ProviderConfig,
        model: String,
        systemPrompt: String,
        turns: List<LlmTurn>,
        includeTools: Boolean,
        reasoningEffort: ReasoningEffort = ReasoningEffort.AUTO,
        extraTools: List<DynamicToolSpec> = emptyList(),
        onCallCreated: (Call) -> Unit,
        onTextDelta: suspend (String) -> Unit
    ): StreamTurnOutcome = withContext(Dispatchers.IO) {
        val normalizedBase = provider.baseUrl.trim().trimEnd('/')
        if (!normalizedBase.startsWith("http://") && !normalizedBase.startsWith("https://")) {
            return@withContext StreamTurnOutcome.Error("Invalid provider Base URL: ${provider.baseUrl}")
        }

        val effectiveStyle = if (
            provider.apiStyle == ProviderApiStyle.GEMINI &&
            normalizedBase.endsWith("/openai")
        ) {
            ProviderApiStyle.OPENAI_COMPATIBLE
        } else {
            provider.apiStyle
        }

        return@withContext when (effectiveStyle) {
            ProviderApiStyle.OPENAI_COMPATIBLE -> streamOpenAi(
                baseUrl = normalizedBase,
                apiKey = provider.activeApiKey,
                model = model,
                systemPrompt = systemPrompt,
                turns = turns,
                includeTools = includeTools,
                reasoningEffort = reasoningEffort,
                extraTools = extraTools,
                onCallCreated = onCallCreated,
                onTextDelta = onTextDelta
            )

            ProviderApiStyle.ANTHROPIC -> streamAnthropic(
                baseUrl = normalizedBase,
                apiKey = provider.activeApiKey,
                model = model,
                systemPrompt = systemPrompt,
                turns = turns,
                includeTools = includeTools,
                reasoningEffort = reasoningEffort,
                extraTools = extraTools,
                onCallCreated = onCallCreated,
                onTextDelta = onTextDelta
            )

            ProviderApiStyle.GEMINI -> streamGemini(
                baseUrl = normalizedBase,
                apiKey = provider.activeApiKey,
                model = model,
                systemPrompt = systemPrompt,
                turns = turns,
                includeTools = includeTools,
                reasoningEffort = reasoningEffort,
                extraTools = extraTools,
                onCallCreated = onCallCreated,
                onTextDelta = onTextDelta
            )
        }
    }

    private suspend fun streamOpenAi(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmTurn>,
        includeTools: Boolean,
        reasoningEffort: ReasoningEffort,
        extraTools: List<DynamicToolSpec>,
        onCallCreated: (Call) -> Unit,
        onTextDelta: suspend (String) -> Unit
    ): StreamTurnOutcome {
        val messagesArr = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            turns.forEach { turn ->
                when (turn) {
                    is LlmTurn.User -> {
                        put(JSONObject().apply {
                            put("role", "user")
                            val attachment = turn.attachment
                            if (attachment != null && attachment.base64Data.isNotBlank()) {
                                val parts = JSONArray().apply {
                                    if (turn.text.isNotBlank()) {
                                        put(JSONObject().apply {
                                            put("type", "text")
                                            put("text", turn.text)
                                        })
                                    }
                                    put(JSONObject().apply {
                                        put("type", "image_url")
                                        put("image_url", JSONObject().apply {
                                            put("url", "data:${attachment.mimeType};base64,${attachment.base64Data}")
                                        })
                                    })
                                }
                                put("content", parts)
                            } else {
                                put("content", turn.text)
                            }
                        })
                    }

                    is LlmTurn.Assistant -> {
                        put(JSONObject().apply {
                            put("role", "assistant")
                            if (turn.text.isNotEmpty()) {
                                put("content", turn.text)
                            } else if (turn.toolCalls.isEmpty()) {
                                put("content", "")
                            } else {
                                put("content", JSONObject.NULL)
                            }
                            if (turn.toolCalls.isNotEmpty()) {
                                val tcArr = JSONArray()
                                turn.toolCalls.forEach { tc ->
                                    tcArr.put(JSONObject().apply {
                                        put("id", tc.id)
                                        put("type", "function")
                                        put("function", JSONObject().apply {
                                            put("name", tc.name)
                                            put("arguments", tc.argumentsJson.ifBlank { "{}" })
                                        })
                                    })
                                }
                                put("tool_calls", tcArr)
                            }
                        })
                    }

                    is LlmTurn.ToolResult -> {
                        put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", turn.toolCallId)
                            put("content", turn.output)
                        })
                    }
                }
            }
        }

        val bodyJson = buildOpenAiRequestBody(
            model = model,
            messagesArr = messagesArr,
            includeTools = includeTools,
            reasoningEffort = reasoningEffort,
            extraTools = extraTools
        )

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "text/event-stream")
            .apply {
                if (apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${apiKey.trim()}")
                }
            }
            .build()

        val call = client.newCall(request)
        onCallCreated(call)

        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    val msg = formatHttpError(response.code, response.message, errBody)
                    return StreamTurnOutcome.Error(
                        errorMessage = msg,
                        isRateLimit = classifyRateLimitError(response.code, msg, errBody)
                    )
                }

                val body = response.body ?: return StreamTurnOutcome.Error("Empty response body")
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("text/event-stream", ignoreCase = true) &&
                    contentType.contains("application/json", ignoreCase = true)
                ) {
                    // Non-streaming fallback if server returned plain JSON
                    val raw = body.string()
                    val json = JSONObject(raw)
                    val message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    val text = message?.optString("content").orEmpty()
                    if (text.isNotEmpty()) onTextDelta(text)
                    val parsedCalls = mutableListOf<LlmToolCall>()
                    val tcArr = message?.optJSONArray("tool_calls")
                    if (tcArr != null) {
                        for (i in 0 until tcArr.length()) {
                            val tc = tcArr.optJSONObject(i) ?: continue
                            val fn = tc.optJSONObject("function") ?: continue
                            parsedCalls.add(
                                LlmToolCall(
                                    id = tc.optString("id").ifBlank { UUID.randomUUID().toString() },
                                    name = fn.optString("name"),
                                    argumentsJson = fn.optString("arguments", "{}")
                                )
                            )
                        }
                    }
                    return StreamTurnOutcome.Success(text = text, toolCalls = parsedCalls)
                }

                val textBuilder = StringBuilder()
                val toolAccumulators = linkedMapOf<Int, MutableToolCall>()

                readSseLines(body.source()) { dataLine ->
                    currentCoroutineContext().ensureActive()
                    if (dataLine == "[DONE]") return@readSseLines false

                    val chunk = runCatching { JSONObject(dataLine) }.getOrNull() ?: return@readSseLines true
                    val errObj = chunk.optJSONObject("error")
                    if (errObj != null) {
                        val msg = errObj.optString("message").ifBlank { errObj.toString() }
                        val codeStr = errObj.optString("code").ifBlank { errObj.optString("type") }
                        throw ProviderApiException(
                            message = msg,
                            isRateLimit = classifyRateLimitError(null, "$codeStr $msg", errObj.toString())
                        )
                    }

                    val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: return@readSseLines true
                    val delta = choice.optJSONObject("delta") ?: return@readSseLines true

                    val contentDelta = if (delta.isNull("content")) "" else delta.optString("content", "")
                    if (contentDelta.isNotEmpty()) {
                        textBuilder.append(contentDelta)
                        onTextDelta(contentDelta)
                    }

                    val tcDeltas = delta.optJSONArray("tool_calls")
                    if (tcDeltas != null) {
                        for (i in 0 until tcDeltas.length()) {
                            val tcDelta = tcDeltas.optJSONObject(i) ?: continue
                            val index = tcDelta.optInt("index", i)
                            val acc = toolAccumulators.getOrPut(index) { MutableToolCall() }
                            val idPart = tcDelta.optString("id")
                            if (idPart.isNotEmpty()) acc.id = idPart
                            val fn = tcDelta.optJSONObject("function")
                            if (fn != null) {
                                val namePart = fn.optString("name")
                                if (namePart.isNotEmpty()) acc.name += namePart
                                val argsPart = fn.optString("arguments")
                                if (argsPart.isNotEmpty()) acc.arguments.append(argsPart)
                            }
                        }
                    }
                    true
                }

                val toolCalls = toolAccumulators.entries
                    .sortedBy { it.key }
                    .mapNotNull { (_, acc) ->
                        if (acc.name.isBlank()) null
                        else LlmToolCall(
                            id = acc.id.ifBlank { UUID.randomUUID().toString() },
                            name = acc.name,
                            argumentsJson = acc.arguments.toString().ifBlank { "{}" }
                        )
                    }

                StreamTurnOutcome.Success(text = textBuilder.toString(), toolCalls = toolCalls)
            }
        } catch (e: ProviderApiException) {
            StreamTurnOutcome.Error(e.message ?: "Provider error", isRateLimit = e.isRateLimit)
        } catch (e: IOException) {
            if (call.isCanceled()) StreamTurnOutcome.Cancelled
            else StreamTurnOutcome.Error("${e.javaClass.simpleName}: ${e.message ?: "Network error"}")
        } catch (e: Exception) {
            if (call.isCanceled()) StreamTurnOutcome.Cancelled
            else StreamTurnOutcome.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun streamAnthropic(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmTurn>,
        includeTools: Boolean,
        reasoningEffort: ReasoningEffort,
        extraTools: List<DynamicToolSpec>,
        onCallCreated: (Call) -> Unit,
        onTextDelta: suspend (String) -> Unit
    ): StreamTurnOutcome {
        val messagesArr = JSONArray()
        var pendingToolResults = mutableListOf<LlmTurn.ToolResult>()

        fun flushToolResults() {
            if (pendingToolResults.isEmpty()) return
            val contentArr = JSONArray()
            pendingToolResults.forEach { res ->
                contentArr.put(JSONObject().apply {
                    put("type", "tool_result")
                    put("tool_use_id", res.toolCallId)
                    put("content", res.output)
                })
            }
            messagesArr.put(JSONObject().apply {
                put("role", "user")
                put("content", contentArr)
            })
            pendingToolResults = mutableListOf()
        }

        turns.forEach { turn ->
            when (turn) {
                is LlmTurn.ToolResult -> pendingToolResults.add(turn)
                is LlmTurn.User -> {
                    flushToolResults()
                    messagesArr.put(JSONObject().apply {
                        put("role", "user")
                        val attachment = turn.attachment
                        if (attachment != null && attachment.base64Data.isNotBlank()) {
                            val blocks = JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "image")
                                    put("source", JSONObject().apply {
                                        put("type", "base64")
                                        put("media_type", attachment.mimeType)
                                        put("data", attachment.base64Data)
                                    })
                                })
                                if (turn.text.isNotBlank()) {
                                    put(JSONObject().apply {
                                        put("type", "text")
                                        put("text", turn.text)
                                    })
                                }
                            }
                            put("content", blocks)
                        } else {
                            put("content", turn.text)
                        }
                    })
                }
                is LlmTurn.Assistant -> {
                    flushToolResults()
                    val blocks = JSONArray()
                    if (turn.text.isNotEmpty()) {
                        blocks.put(JSONObject().apply {
                            put("type", "text")
                            put("text", turn.text)
                        })
                    }
                    turn.toolCalls.forEach { tc ->
                        val inputObj = runCatching { JSONObject(tc.argumentsJson) }.getOrDefault(JSONObject())
                        blocks.put(JSONObject().apply {
                            put("type", "tool_use")
                            put("id", tc.id)
                            put("name", tc.name)
                            put("input", inputObj)
                        })
                    }
                    messagesArr.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", blocks)
                    })
                }
            }
        }
        flushToolResults()

        val bodyJson = buildAnthropicRequestBody(
            model = model,
            systemPrompt = systemPrompt,
            messagesArr = messagesArr,
            includeTools = includeTools,
            reasoningEffort = reasoningEffort,
            extraTools = extraTools
        )

        val request = Request.Builder()
            .url("$baseUrl/messages")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "text/event-stream")
            .header("anthropic-version", "2023-06-01")
            .apply {
                if (apiKey.isNotBlank()) {
                    header("x-api-key", apiKey.trim())
                }
            }
            .build()

        val call = client.newCall(request)
        onCallCreated(call)

        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    val msg = formatHttpError(response.code, response.message, errBody)
                    return StreamTurnOutcome.Error(
                        errorMessage = msg,
                        isRateLimit = classifyRateLimitError(response.code, msg, errBody)
                    )
                }

                val body = response.body ?: return StreamTurnOutcome.Error("Empty response body")
                val textBuilder = StringBuilder()
                val toolAccumulators = linkedMapOf<Int, MutableToolCall>()

                readSseLines(body.source()) { dataLine ->
                    currentCoroutineContext().ensureActive()
                    val event = runCatching { JSONObject(dataLine) }.getOrNull() ?: return@readSseLines true
                    when (event.optString("type")) {
                        "content_block_start" -> {
                            val index = event.optInt("index", 0)
                            val block = event.optJSONObject("content_block")
                            if (block?.optString("type") == "tool_use") {
                                toolAccumulators[index] = MutableToolCall(
                                    id = block.optString("id"),
                                    name = block.optString("name")
                                )
                            }
                        }

                        "content_block_delta" -> {
                            val index = event.optInt("index", 0)
                            val delta = event.optJSONObject("delta") ?: return@readSseLines true
                            when (delta.optString("type")) {
                                "text_delta" -> {
                                    val txt = delta.optString("text")
                                    if (txt.isNotEmpty()) {
                                        textBuilder.append(txt)
                                        onTextDelta(txt)
                                    }
                                }

                                "input_json_delta" -> {
                                    val partial = delta.optString("partial_json")
                                    if (partial.isNotEmpty()) {
                                        toolAccumulators[index]?.arguments?.append(partial)
                                    }
                                }
                            }
                        }

                        "error" -> {
                            val err = event.optJSONObject("error")
                            val errType = err?.optString("type").orEmpty()
                            val msg = err?.optString("message").orEmpty().ifBlank { event.toString() }
                            throw ProviderApiException(
                                message = msg,
                                isRateLimit = classifyRateLimitError(null, "$errType $msg", event.toString())
                            )
                        }
                    }
                    true
                }

                val toolCalls = toolAccumulators.entries
                    .sortedBy { it.key }
                    .mapNotNull { (_, acc) ->
                        if (acc.name.isBlank()) null
                        else LlmToolCall(
                            id = acc.id.ifBlank { UUID.randomUUID().toString() },
                            name = acc.name,
                            argumentsJson = acc.arguments.toString().ifBlank { "{}" }
                        )
                    }

                StreamTurnOutcome.Success(text = textBuilder.toString(), toolCalls = toolCalls)
            }
        } catch (e: ProviderApiException) {
            StreamTurnOutcome.Error(e.message ?: "Provider error", isRateLimit = e.isRateLimit)
        } catch (e: IOException) {
            if (call.isCanceled()) StreamTurnOutcome.Cancelled
            else StreamTurnOutcome.Error("${e.javaClass.simpleName}: ${e.message ?: "Network error"}")
        } catch (e: Exception) {
            if (call.isCanceled()) StreamTurnOutcome.Cancelled
            else StreamTurnOutcome.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun streamGemini(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmTurn>,
        includeTools: Boolean,
        reasoningEffort: ReasoningEffort,
        extraTools: List<DynamicToolSpec>,
        onCallCreated: (Call) -> Unit,
        onTextDelta: suspend (String) -> Unit
    ): StreamTurnOutcome {
        val contentsArr = JSONArray()
        var pendingResponses = mutableListOf<LlmTurn.ToolResult>()

        fun flushResponses() {
            if (pendingResponses.isEmpty()) return
            val parts = JSONArray()
            pendingResponses.forEach { res ->
                parts.put(JSONObject().apply {
                    put("functionResponse", JSONObject().apply {
                        put("name", res.toolName)
                        put("response", JSONObject().apply {
                            put("result", res.output)
                        })
                    })
                })
            }
            contentsArr.put(JSONObject().apply {
                put("role", "user")
                put("parts", parts)
            })
            pendingResponses = mutableListOf()
        }

        turns.forEach { turn ->
            when (turn) {
                is LlmTurn.ToolResult -> pendingResponses.add(turn)
                is LlmTurn.User -> {
                    flushResponses()
                    val parts = JSONArray()
                    if (turn.text.isNotBlank()) {
                        parts.put(JSONObject().apply { put("text", turn.text) })
                    }
                    val attachment = turn.attachment
                    if (attachment != null && attachment.base64Data.isNotBlank()) {
                        parts.put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", attachment.mimeType)
                                put("data", attachment.base64Data)
                            })
                        })
                    }
                    contentsArr.put(JSONObject().apply {
                        put("role", "user")
                        put("parts", parts)
                    })
                }
                is LlmTurn.Assistant -> {
                    flushResponses()
                    val parts = JSONArray()
                    if (turn.text.isNotEmpty()) {
                        parts.put(JSONObject().apply { put("text", turn.text) })
                    }
                    turn.toolCalls.forEach { tc ->
                        val argsObj = runCatching { JSONObject(tc.argumentsJson) }.getOrDefault(JSONObject())
                        parts.put(JSONObject().apply {
                            put("functionCall", JSONObject().apply {
                                put("name", tc.name)
                                put("args", argsObj)
                            })
                        })
                    }
                    contentsArr.put(JSONObject().apply {
                        put("role", "model")
                        put("parts", parts)
                    })
                }
            }
        }
        flushResponses()

        val bodyJson = buildGeminiRequestBody(
            model = model,
            systemPrompt = systemPrompt,
            contentsArr = contentsArr,
            includeTools = includeTools,
            reasoningEffort = reasoningEffort,
            extraTools = extraTools
        )

        val cleanModel = model.removePrefix("models/")
        val request = Request.Builder()
            .url("$baseUrl/models/$cleanModel:streamGenerateContent?alt=sse")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "text/event-stream")
            .apply {
                if (apiKey.isNotBlank()) {
                    header("x-goog-api-key", apiKey.trim())
                }
            }
            .build()

        val call = client.newCall(request)
        onCallCreated(call)

        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    val msg = formatHttpError(response.code, response.message, errBody)
                    return StreamTurnOutcome.Error(
                        errorMessage = msg,
                        isRateLimit = classifyRateLimitError(response.code, msg, errBody)
                    )
                }

                val body = response.body ?: return StreamTurnOutcome.Error("Empty response body")
                val textBuilder = StringBuilder()
                val collectedToolCalls = mutableListOf<LlmToolCall>()

                readSseLines(body.source()) { dataLine ->
                    currentCoroutineContext().ensureActive()
                    val chunk = runCatching { JSONObject(dataLine) }.getOrNull() ?: return@readSseLines true
                    val err = chunk.optJSONObject("error")
                    if (err != null) {
                        val status = err.optString("status")
                        val msg = err.optString("message").ifBlank { err.toString() }
                        throw ProviderApiException(
                            message = msg,
                            isRateLimit = classifyRateLimitError(err.optInt("code", 0), "$status $msg", err.toString())
                        )
                    }

                    val candidate = chunk.optJSONArray("candidates")?.optJSONObject(0) ?: return@readSseLines true
                    val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return@readSseLines true
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        if (part.optBoolean("thought", false)) continue
                        val txt = part.optString("text")
                        if (txt.isNotEmpty()) {
                            textBuilder.append(txt)
                            onTextDelta(txt)
                        }
                        val fc = part.optJSONObject("functionCall")
                        if (fc != null) {
                            collectedToolCalls.add(
                                LlmToolCall(
                                    id = UUID.randomUUID().toString(),
                                    name = fc.optString("name"),
                                    argumentsJson = fc.optJSONObject("args")?.toString() ?: "{}"
                                )
                            )
                        }
                    }
                    true
                }

                StreamTurnOutcome.Success(text = textBuilder.toString(), toolCalls = collectedToolCalls)
            }
        } catch (e: ProviderApiException) {
            StreamTurnOutcome.Error(e.message ?: "Provider error", isRateLimit = e.isRateLimit)
        } catch (e: IOException) {
            if (call.isCanceled()) StreamTurnOutcome.Cancelled
            else StreamTurnOutcome.Error("${e.javaClass.simpleName}: ${e.message ?: "Network error"}")
        } catch (e: Exception) {
            if (call.isCanceled()) StreamTurnOutcome.Cancelled
            else StreamTurnOutcome.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun readSseLines(
        source: BufferedSource,
        onData: suspend (String) -> Boolean
    ) {
        while (!source.exhausted()) {
            currentCoroutineContext().ensureActive()
            val line = source.readUtf8Line() ?: break
            if (line.startsWith("data:")) {
                val payload = line.removePrefix("data:").trim()
                if (payload.isNotEmpty()) {
                    val shouldContinue = onData(payload)
                    if (!shouldContinue) break
                }
            }
        }
    }

    private fun allTools(includeBuiltIns: Boolean, extraTools: List<DynamicToolSpec>): List<ToolSpec> {
        val base = if (includeBuiltIns) TOOL_SPECS else emptyList()
        val extra = extraTools.map { ToolSpec(it.name, it.description, it.schema) }
        return (base + extra).distinctBy { it.name }
    }

    private fun buildOpenAiTools(tools: List<ToolSpec>): JSONArray {
        val arr = JSONArray()
        tools.forEach { spec ->
            arr.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", spec.name)
                    put("description", spec.description)
                    put("parameters", spec.schema)
                })
            })
        }
        return arr
    }

    private fun buildAnthropicTools(tools: List<ToolSpec>): JSONArray {
        val arr = JSONArray()
        tools.forEach { spec ->
            arr.put(JSONObject().apply {
                put("name", spec.name)
                put("description", spec.description)
                put("input_schema", spec.schema)
            })
        }
        return arr
    }

    private fun buildGeminiTools(tools: List<ToolSpec>): JSONArray {
        val arr = JSONArray()
        tools.forEach { spec ->
            arr.put(JSONObject().apply {
                put("name", spec.name)
                put("description", spec.description)
                put("parameters", sanitizeGeminiSchema(spec.schema))
            })
        }
        return arr
    }

    private fun sanitizeGeminiSchema(raw: JSONObject): JSONObject {
        val cleaned = JSONObject()
        val keys = raw.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "\$schema" || key == "additionalProperties") continue
            val value = raw.get(key)
            cleaned.put(
                key,
                when (value) {
                    is JSONObject -> sanitizeGeminiSchema(value)
                    else -> value
                }
            )
        }
        if (!cleaned.has("type")) {
            cleaned.put("type", "object")
        }
        return cleaned
    }

    internal fun buildOpenAiRequestBody(
        model: String,
        messagesArr: JSONArray,
        includeTools: Boolean,
        reasoningEffort: ReasoningEffort,
        extraTools: List<DynamicToolSpec> = emptyList()
    ): JSONObject {
        val tools = allTools(includeBuiltIns = includeTools, extraTools = extraTools)
        return JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", messagesArr)
            if (tools.isNotEmpty()) {
                put("tools", buildOpenAiTools(tools))
            }
            val apiEffort = reasoningEffort.apiValue
            if (apiEffort != null && supportsOpenAiReasoningEffort(model)) {
                put("reasoning_effort", apiEffort)
            }
        }
    }

    internal fun buildAnthropicRequestBody(
        model: String,
        systemPrompt: String,
        messagesArr: JSONArray,
        includeTools: Boolean,
        reasoningEffort: ReasoningEffort,
        extraTools: List<DynamicToolSpec> = emptyList()
    ): JSONObject {
        val tools = allTools(includeBuiltIns = includeTools, extraTools = extraTools)
        val thinkingBudget = anthropicThinkingBudget(model, reasoningEffort)
        return JSONObject().apply {
            put("model", model)
            put("max_tokens", if (thinkingBudget != null) maxOf(4096, thinkingBudget + 4096) else 4096)
            put("stream", true)
            put("system", systemPrompt)
            put("messages", messagesArr)
            if (tools.isNotEmpty()) {
                put("tools", buildAnthropicTools(tools))
            }
            if (thinkingBudget != null) {
                put("thinking", JSONObject().apply {
                    put("type", "enabled")
                    put("budget_tokens", thinkingBudget)
                })
            }
        }
    }

    internal fun buildGeminiRequestBody(
        model: String,
        systemPrompt: String,
        contentsArr: JSONArray,
        includeTools: Boolean,
        reasoningEffort: ReasoningEffort,
        extraTools: List<DynamicToolSpec> = emptyList()
    ): JSONObject {
        val tools = allTools(includeBuiltIns = includeTools, extraTools = extraTools)
        return JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", systemPrompt) }))
            })
            put("contents", contentsArr)
            if (tools.isNotEmpty()) {
                put("tools", JSONArray().put(JSONObject().apply {
                    put("functionDeclarations", buildGeminiTools(tools))
                }))
            }
            geminiThinkingConfig(model, reasoningEffort)?.let { thinkingCfg ->
                put("generationConfig", JSONObject().apply {
                    put("thinkingConfig", thinkingCfg)
                })
            }
        }
    }

    internal fun supportsOpenAiReasoningEffort(model: String): Boolean {
        val lower = model.lowercase().trim()
        if (lower.isEmpty()) return false
        return lower.contains("gpt-5") ||
            Regex("""(^|/)o[134]([._-]|$|\b)""").containsMatchIn(lower) ||
            lower.contains("deepseek-reasoner") ||
            lower.contains("deepseek-r1") ||
            lower.contains("grok-3-mini") ||
            lower.contains("grok-4") ||
            lower.contains("qwq") ||
            lower.contains("thinking")
    }

    internal fun anthropicThinkingBudget(model: String, effort: ReasoningEffort): Int? {
        if (effort == ReasoningEffort.AUTO) return null
        val lower = model.lowercase().trim()
        val supportsExtendedThinking = lower.contains("claude-3-7") ||
            lower.contains("claude-3.7") ||
            lower.contains("claude-sonnet-4") ||
            lower.contains("claude-opus-4") ||
            lower.contains("claude-haiku-4-5") ||
            lower.contains("claude-haiku-4.5") ||
            lower.contains("claude-4")
        if (!supportsExtendedThinking) return null
        return when (effort) {
            ReasoningEffort.AUTO -> null
            ReasoningEffort.LOW -> 1024
            ReasoningEffort.MEDIUM -> 4096
            ReasoningEffort.HIGH -> 8192
        }
    }

    internal fun geminiThinkingConfig(model: String, effort: ReasoningEffort): JSONObject? {
        if (effort == ReasoningEffort.AUTO) return null
        val lower = model.removePrefix("models/").lowercase().trim()
        val isGemini25 = lower.contains("gemini-2.5")
        val isGemini3Or4 = lower.contains("gemini-3") || lower.contains("gemini-4")
        val isGenericThinking = lower.contains("thinking")
        if (!isGemini25 && !isGemini3Or4 && !isGenericThinking) return null

        return JSONObject().apply {
            if (isGemini3Or4) {
                put("thinkingLevel", effort.apiValue!!.uppercase())
            } else {
                val budget = when (effort) {
                    ReasoningEffort.AUTO -> return null
                    ReasoningEffort.LOW -> 1024
                    ReasoningEffort.MEDIUM -> 8192
                    ReasoningEffort.HIGH -> 16384
                }
                put("thinkingBudget", budget)
            }
        }
    }

    internal fun classifyRateLimitError(
        httpCode: Int?,
        errorMessage: String,
        rawBody: String = ""
    ): Boolean {
        if (httpCode == 429) return true
        val combined = "$errorMessage $rawBody".lowercase()
        // Never misclassify authentication / wrong key / bad request errors as rate limits
        if (httpCode == 401 ||
            combined.contains("invalid_api_key") ||
            combined.contains("incorrect api key") ||
            combined.contains("authentication_error") ||
            combined.contains("invalid x-api-key")
        ) {
            return false
        }
        return combined.contains("rate_limit") ||
            combined.contains("rate limit") ||
            combined.contains("ratelimit") ||
            combined.contains("too many requests") ||
            combined.contains("insufficient_quota") ||
            combined.contains("resource_exhausted") ||
            combined.contains("quota exceeded") ||
            combined.contains("exceeded your current quota") ||
            combined.contains("usage limit") ||
            combined.contains("limit reached") ||
            combined.contains("requests per min") ||
            combined.contains("tokens per min") ||
            combined.contains("requests per day")
    }

    private fun formatHttpError(code: Int, statusMessage: String, body: String): String {
        val extracted = try {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            when {
                errObj != null -> errObj.optString("message").ifBlank { errObj.optString("type") }
                json.has("message") -> json.optString("message")
                json.has("detail") -> json.optString("detail")
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
        val detail = extracted.ifBlank { body.trim().take(220).ifBlank { statusMessage.ifBlank { "Request failed" } } }
        return "HTTP $code: $detail"
    }

    private data class MutableToolCall(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    )

    private class ProviderApiException(
        message: String,
        val isRateLimit: Boolean = false
    ) : Exception(message)

    private data class ToolSpec(
        val name: String,
        val description: String,
        val schema: JSONObject
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val TOOL_SPECS = listOf(
            ToolSpec(
                name = "list_files",
                description = "List files and subdirectories inside the project folder.",
                schema = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Optional relative subdirectory path inside project folder"}},"required":[]}""")
            ),
            ToolSpec(
                name = "read_file",
                description = "Read the text contents of a file inside the project folder.",
                schema = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Relative file path inside the project folder"}},"required":["path"]}""")
            ),
            ToolSpec(
                name = "search_text",
                description = "Search for a text query across files inside the project folder.",
                schema = JSONObject("""{"type":"object","properties":{"query":{"type":"string","description":"Text to search for"},"path":{"type":"string","description":"Optional subdirectory to search in"}},"required":["query"]}""")
            ),
            ToolSpec(
                name = "create_file",
                description = "Create a new file (or write full contents) inside the project folder. Requires user approval.",
                schema = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Relative file path inside the project folder"},"content":{"type":"string","description":"Full file content to write"}},"required":["path","content"]}""")
            ),
            ToolSpec(
                name = "edit_file",
                description = "Edit or patch an existing file inside the project folder. Provide old_text and new_text to patch, or content for full replacement. Requires user approval.",
                schema = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Relative file path inside the project folder"},"old_text":{"type":"string","description":"Exact snippet to replace"},"new_text":{"type":"string","description":"Replacement text"},"content":{"type":"string","description":"Optional full replacement content"}},"required":["path"]}""")
            ),
            ToolSpec(
                name = "run_command",
                description = "Run a shell command inside the project folder on the device. Requires user approval.",
                schema = JSONObject("""{"type":"object","properties":{"command":{"type":"string","description":"Shell command to execute inside the project folder"}},"required":["command"]}""")
            )
        )
    }
}
