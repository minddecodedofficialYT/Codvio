package com.example.data

import com.example.model.ProviderApiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface FetchModelsOutcome {
    data class Success(val models: List<String>, val latencyMs: Long) : FetchModelsOutcome
    data class Failure(val errorMessage: String) : FetchModelsOutcome
}

sealed interface TestConnectionOutcome {
    data class Success(val message: String, val latencyMs: Long) : TestConnectionOutcome
    data class Failure(val errorMessage: String) : TestConnectionOutcome
}

class ProviderNetworkClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        apiStyle: ProviderApiStyle
    ): FetchModelsOutcome = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
            ?: return@withContext FetchModelsOutcome.Failure("Base URL must start with http:// or https://")

        val startedAt = System.currentTimeMillis()
        try {
            val requestBuilder = Request.Builder()
                .url("$normalizedUrl/models")
                .get()
                .header("Accept", "application/json")

            applyAuthHeaders(
                builder = requestBuilder,
                apiStyle = apiStyle,
                apiKey = apiKey,
                normalizedBaseUrl = normalizedUrl
            )

            client.newCall(requestBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - startedAt
                val bodyString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext FetchModelsOutcome.Failure(
                        formatHttpError(response.code, response.message, bodyString)
                    )
                }

                val models = parseModelsResponse(bodyString, apiStyle)
                if (models.isEmpty()) {
                    FetchModelsOutcome.Failure("Provider responded (HTTP ${response.code}), but returned 0 models.")
                } else {
                    FetchModelsOutcome.Success(models = models, latencyMs = latency)
                }
            }
        } catch (e: Exception) {
            FetchModelsOutcome.Failure(formatException(e))
        }
    }

    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        apiStyle: ProviderApiStyle,
        customModelIds: List<String> = emptyList()
    ): TestConnectionOutcome = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
            ?: return@withContext TestConnectionOutcome.Failure("Base URL must start with http:// or https://")

        val startedAt = System.currentTimeMillis()
        try {
            // OpenRouter's /models is public and does not validate keys; /auth/key validates the Bearer token.
            if (normalizedUrl.contains("openrouter.ai", ignoreCase = true)) {
                val request = Request.Builder()
                    .url("$normalizedUrl/auth/key")
                    .get()
                    .header("Accept", "application/json")
                    .apply {
                        if (apiKey.isNotBlank()) {
                            header("Authorization", "Bearer ${apiKey.trim()}")
                        }
                    }
                    .build()

                client.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startedAt
                    val body = response.body?.string().orEmpty()
                    return@withContext if (response.isSuccessful) {
                        TestConnectionOutcome.Success(
                            message = "Connected to OpenRouter (${latency} ms)",
                            latencyMs = latency
                        )
                    } else {
                        TestConnectionOutcome.Failure(
                            formatHttpError(response.code, response.message, body)
                        )
                    }
                }
            }

            // If explicit model IDs were entered on a custom endpoint, probe the inference endpoint directly
            // when style is OpenAI-compatible or Anthropic; otherwise verify via authenticated /models.
            val explicitModel = customModelIds.firstOrNull { it.isNotBlank() }?.trim()
            if (explicitModel != null && apiStyle == ProviderApiStyle.ANTHROPIC) {
                val payload = JSONObject().apply {
                    put("model", explicitModel)
                    put("max_tokens", 1)
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", "hi")
                    }))
                }
                val request = Request.Builder()
                    .url("$normalizedUrl/messages")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("Accept", "application/json")
                    .apply {
                        applyAuthHeaders(this, apiStyle, apiKey, normalizedUrl)
                    }
                    .build()

                client.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startedAt
                    val body = response.body?.string().orEmpty()
                    return@withContext if (response.isSuccessful) {
                        TestConnectionOutcome.Success(
                            message = "Connected · $explicitModel responded (${latency} ms)",
                            latencyMs = latency
                        )
                    } else {
                        TestConnectionOutcome.Failure(
                            formatHttpError(response.code, response.message, body)
                        )
                    }
                }
            }

            // Standard authenticated small probe against /models (or ?pageSize=1 for Gemini)
            val probeUrl = if (apiStyle == ProviderApiStyle.GEMINI && !normalizedUrl.endsWith("/openai")) {
                "$normalizedUrl/models?pageSize=1"
            } else {
                "$normalizedUrl/models"
            }

            val requestBuilder = Request.Builder()
                .url(probeUrl)
                .get()
                .header("Accept", "application/json")

            applyAuthHeaders(requestBuilder, apiStyle, apiKey, normalizedUrl)

            client.newCall(requestBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - startedAt
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val models = parseModelsResponse(body, apiStyle)
                    val detail = if (models.isNotEmpty()) {
                        "Connected · ${models.size} model(s) available (${latency} ms)"
                    } else {
                        "Connected · HTTP ${response.code} (${latency} ms)"
                    }
                    return@withContext TestConnectionOutcome.Success(
                        message = detail,
                        latencyMs = latency
                    )
                }

                // Fallback for minimal OpenAI-compatible servers that don't expose /models
                if ((response.code == 404 || response.code == 405) &&
                    explicitModel != null &&
                    apiStyle == ProviderApiStyle.OPENAI_COMPATIBLE
                ) {
                    return@withContext testOpenAiChatCompletion(
                        normalizedUrl = normalizedUrl,
                        apiKey = apiKey,
                        model = explicitModel,
                        startedAt = startedAt
                    )
                }

                return@withContext TestConnectionOutcome.Failure(
                    formatHttpError(response.code, response.message, body)
                )
            }
        } catch (e: Exception) {
            TestConnectionOutcome.Failure(formatException(e))
        }
    }

    private fun testOpenAiChatCompletion(
        normalizedUrl: String,
        apiKey: String,
        model: String,
        startedAt: Long
    ): TestConnectionOutcome {
        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "hi")
            }))
        }
        val request = Request.Builder()
            .url("$normalizedUrl/chat/completions")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .apply {
                if (apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${apiKey.trim()}")
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val latency = System.currentTimeMillis() - startedAt
            val body = response.body?.string().orEmpty()
            return if (response.isSuccessful) {
                TestConnectionOutcome.Success(
                    message = "Connected · $model responded (${latency} ms)",
                    latencyMs = latency
                )
            } else {
                TestConnectionOutcome.Failure(
                    formatHttpError(response.code, response.message, body)
                )
            }
        }
    }

    private fun applyAuthHeaders(
        builder: Request.Builder,
        apiStyle: ProviderApiStyle,
        apiKey: String,
        normalizedBaseUrl: String
    ) {
        val trimmedKey = apiKey.trim()
        when (apiStyle) {
            ProviderApiStyle.ANTHROPIC -> {
                if (trimmedKey.isNotEmpty()) {
                    builder.header("x-api-key", trimmedKey)
                }
                builder.header("anthropic-version", "2023-06-01")
            }
            ProviderApiStyle.GEMINI -> {
                if (normalizedBaseUrl.endsWith("/openai")) {
                    if (trimmedKey.isNotEmpty()) {
                        builder.header("Authorization", "Bearer $trimmedKey")
                    }
                } else if (trimmedKey.isNotEmpty()) {
                    builder.header("x-goog-api-key", trimmedKey)
                }
            }
            ProviderApiStyle.OPENAI_COMPATIBLE -> {
                if (trimmedKey.isNotEmpty()) {
                    builder.header("Authorization", "Bearer $trimmedKey")
                }
            }
        }
    }

    internal fun parseModelsResponse(body: String, apiStyle: ProviderApiStyle): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()

        return try {
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                extractIdsFromJsonArray(arr, apiStyle)
            } else {
                val obj = JSONObject(trimmed)
                val dataArr = obj.optJSONArray("data")
                val modelsArr = obj.optJSONArray("models")
                when {
                    dataArr != null -> extractIdsFromJsonArray(dataArr, apiStyle)
                    modelsArr != null -> extractIdsFromJsonArray(modelsArr, apiStyle)
                    else -> emptyList()
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractIdsFromJsonArray(
        array: JSONArray,
        apiStyle: ProviderApiStyle
    ): List<String> {
        val results = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            when (item) {
                is String -> if (item.isNotBlank()) results.add(item.trim())
                is JSONObject -> {
                    if (apiStyle == ProviderApiStyle.GEMINI) {
                        val methods = item.optJSONArray("supportedGenerationMethods")
                        if (methods != null) {
                            var supportsGenerate = false
                            for (m in 0 until methods.length()) {
                                if (methods.optString(m) == "generateContent") {
                                    supportsGenerate = true
                                    break
                                }
                            }
                            if (!supportsGenerate) continue
                        }
                        val rawName = item.optString("name").ifBlank { item.optString("id") }
                        val cleaned = rawName.removePrefix("models/").trim()
                        if (cleaned.isNotEmpty()) results.add(cleaned)
                    } else {
                        val id = item.optString("id")
                            .ifBlank { item.optString("name") }
                            .ifBlank { item.optString("model") }
                            .trim()
                        if (id.isNotEmpty()) results.add(id)
                    }
                }
            }
        }
        return results.distinct().sorted()
    }

    internal fun formatHttpError(code: Int, statusMessage: String, body: String): String {
        val extracted = try {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            when {
                errObj != null -> errObj.optString("message")
                    .ifBlank { errObj.optString("type") }
                json.has("message") -> json.optString("message")
                json.has("detail") -> json.optString("detail")
                json.has("error") -> json.optString("error")
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }

        val detail = extracted.ifBlank {
            body.trim().take(180).ifBlank { statusMessage.ifBlank { "Request failed" } }
        }
        return "HTTP $code: $detail"
    }

    private fun formatException(e: Exception): String {
        val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        return "${e.javaClass.simpleName}: $msg"
    }

    private fun normalizeBaseUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return null
        }
        return trimmed
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
