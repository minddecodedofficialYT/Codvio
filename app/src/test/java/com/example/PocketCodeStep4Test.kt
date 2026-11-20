package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AgentChatClient
import com.example.data.AgentOrchestrator
import com.example.data.ProjectWorkspace
import com.example.data.ProviderJsonCodec
import com.example.data.ProviderVaultSnapshot
import com.example.data.RateLimitSwitchOutcome
import com.example.model.AgentWorkMode
import com.example.model.ChatMessageItem
import com.example.model.ChatRole
import com.example.model.FallbackEntry
import com.example.model.LimitFlagEntry
import com.example.model.PermissionPromptState
import com.example.model.ProjectType
import com.example.model.ProviderApiStyle
import com.example.model.ProviderConfig
import com.example.model.ProviderKeyEntry
import com.example.model.ReasoningEffort
import com.example.model.SessionItem
import com.example.model.ToolExecutionItem
import com.example.viewmodel.PocketCodeViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PocketCodeStep4Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `defaults fallback reordering and daily flag expiration work properly`() {
        val vm = PocketCodeViewModel(todayDateProvider = { "2026-11-20" })
        assertTrue(vm.uiState.value.autoSwitchEnabled)
        assertEquals(ReasoningEffort.AUTO, vm.uiState.value.defaultReasoningEffort)

        vm.addFallbackEntry("p1", "gpt-5")
        vm.addFallbackEntry("p2", "claude-sonnet-4")
        vm.addFallbackEntry("p3", "gemini-2.5-pro")

        val list0 = vm.uiState.value.fallbackOrder
        assertEquals(listOf("gpt-5", "claude-sonnet-4", "gemini-2.5-pro"), list0.map { it.model })

        vm.moveFallbackEntryUp(list0[2].id)
        assertEquals(
            listOf("gpt-5", "gemini-2.5-pro", "claude-sonnet-4"),
            vm.uiState.value.fallbackOrder.map { it.model }
        )

        vm.moveFallbackEntryDown(list0[0].id)
        assertEquals(
            listOf("gemini-2.5-pro", "gpt-5", "claude-sonnet-4"),
            vm.uiState.value.fallbackOrder.map { it.model }
        )

        vm.removeFallbackEntry(list0[0].id)
        assertEquals(
            listOf("gemini-2.5-pro", "claude-sonnet-4"),
            vm.uiState.value.fallbackOrder.map { it.model }
        )

        // Daily limit flags clear automatically on next day
        val snapshot = ProviderVaultSnapshot(
            providers = emptyList(),
            selectedModel = "gpt-5",
            autoSwitchEnabled = false,
            fallbackOrder = listOf(FallbackEntry("f1", "p1", "gpt-5")),
            limitFlags = listOf(
                LimitFlagEntry("p1", "k1", "2026-11-19"),
                LimitFlagEntry("p2", "k2", "2026-11-20")
            ),
            defaultReasoningEffort = ReasoningEffort.HIGH
        )
        val encoded = ProviderJsonCodec.encodeSnapshot(snapshot)
        val decodedSameDay = ProviderJsonCodec.decodeSnapshot(encoded, todayDate = "2026-11-20")
        assertFalse(decodedSameDay.autoSwitchEnabled)
        assertEquals(ReasoningEffort.HIGH, decodedSameDay.defaultReasoningEffort)
        assertEquals(1, decodedSameDay.limitFlags.size)
        assertEquals("p2", decodedSameDay.limitFlags.first().providerId)

        val decodedNextDay = ProviderJsonCodec.decodeSnapshot(encoded, todayDate = "2026-11-21")
        assertEquals(0, decodedNextDay.limitFlags.size)
    }

    @Test
    fun `rate limit auto-switches across same provider keys then fallback list and stops if all hit limits`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val projectDir = tempFolder.newFolder("autoswitch_proj")

        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val authHeadersSeen = mutableListOf<String>()
        val modelsSeen = mutableListOf<String>()

        val serverThread = thread {
            repeat(5) { idx ->
                val socket = serverSocket.accept()
                socket.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    var contentLength = 0
                    var auth = ""
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) {
                            auth = line.substringAfter(":").trim()
                        }
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                    val bodyBuf = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val r = reader.read(bodyBuf, read, contentLength - read)
                        if (r <= 0) break
                        read += r
                    }
                    val bodyStr = String(bodyBuf)
                    val modelMatch = Regex(""""model"\s*:\s*"([^"]+)"""").find(bodyStr)?.groupValues?.get(1).orEmpty()
                    authHeadersSeen.add(auth)
                    modelsSeen.add(modelMatch)

                    val out = s.getOutputStream()
                    // Call 0 (P1 key1): 429 -> switch to P1 key2
                    // Call 1 (P1 key2): 429 -> switch to P2 fallback model
                    // Call 2 (P2 fbKey): 200 SSE success!
                    // Call 3 (after test resets or second turn): P2 fbKey -> 429 -> all exhausted!
                    // Call 4 (for 401 wrong key test): 401 -> no auto-switch!
                    when (idx) {
                        0, 1, 3 -> {
                            val err = """{"error":{"message":"Rate limit reached for requests"}}"""
                            val resp = "HTTP/1.1 429 Too Many Requests\r\nContent-Type: application/json\r\nContent-Length: ${err.toByteArray().size}\r\nConnection: close\r\n\r\n$err"
                            out.write(resp.toByteArray())
                        }
                        2 -> {
                            val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Recovered on fallback!\"}}]}\n\ndata: [DONE]\n\n"
                            val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${sse.toByteArray().size}\r\nConnection: close\r\n\r\n$sse"
                            out.write(resp.toByteArray())
                        }
                        4 -> {
                            val err = """{"error":{"message":"Incorrect API key provided","code":"invalid_api_key"}}"""
                            val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nContent-Length: ${err.toByteArray().size}\r\nConnection: close\r\n\r\n$err"
                            out.write(resp.toByteArray())
                        }
                    }
                    out.flush()
                }
            }
            serverSocket.close()
        }

        val vm = PocketCodeViewModel(appContext = context, todayDateProvider = { "2026-11-20" })
        vm.saveProvider(
            editingProviderId = null,
            name = "PrimaryCo",
            subtitleLabel = "PrimaryCo",
            baseUrl = "http://127.0.0.1:$port/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            isCustom = true,
            apiKeyInput = "key-primary-1",
            fetchedModels = listOf("primary-model")
        )
        val p1 = vm.uiState.value.providers.first()
        vm.addProviderKey(p1.id, "BackupKey", "key-primary-2")

        vm.saveProvider(
            editingProviderId = null,
            name = "FallbackCo",
            subtitleLabel = "FallbackCo",
            baseUrl = "http://127.0.0.1:$port/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            isCustom = true,
            apiKeyInput = "key-fallback-1",
            fetchedModels = listOf("fallback-model")
        )
        val p2 = vm.uiState.value.providers[1]
        vm.selectModel("primary-model")
        vm.addFallbackEntry(p2.id, "fallback-model")

        val session = vm.createSession("AutoSwitchTest", projectDir.absolutePath, null)
        val workspace = ProjectWorkspace.forSession(context, session)
        val orchestrator = AgentOrchestrator(AgentChatClient())
        val text1 = StringBuilder()

        val outcome1 = orchestrator.runAgentTurn(
            provider = vm.uiState.value.activeProvider!!,
            model = "primary-model",
            mode = AgentWorkMode.PLAN,
            projectType = ProjectType.GENERAL,
            workspace = workspace,
            historyMessages = listOf(ChatMessageItem(id = "u1", role = ChatRole.USER, text = "Hello")),
            assistantMessageId = "a1",
            callbacks = object : AgentOrchestrator.Callbacks {
                override fun onCallCreated(call: Call) = Unit
                override suspend fun onStatusUpdate(status: String) = Unit
                override suspend fun onTextDelta(assistantMessageId: String, delta: String) {
                    text1.append(delta)
                }
                override suspend fun onToolCardAdded(assistantMessageId: String, tool: ToolExecutionItem) = Unit
                override suspend fun onToolCardUpdated(assistantMessageId: String, tool: ToolExecutionItem) = Unit
                override suspend fun requestUserPermission(prompt: PermissionPromptState): Boolean = false
                override suspend fun onWorkspaceMutated() = Unit
                override suspend fun onRateLimitHit(
                    failedProvider: ProviderConfig,
                    failedModel: String,
                    errorMessage: String
                ): RateLimitSwitchOutcome {
                    return vm.handleRateLimitSwitch(session.id, "a1", failedProvider, failedModel)
                }
            }
        )

        assertTrue(outcome1 is AgentOrchestrator.RunOutcome.Completed)
        assertEquals("Recovered on fallback!", text1.toString())
        assertEquals(
            listOf("Bearer key-primary-1", "Bearer key-primary-2", "Bearer key-fallback-1"),
            authHeadersSeen.take(3)
        )
        assertEquals(listOf("primary-model", "primary-model", "fallback-model"), modelsSeen.take(3))
        assertEquals(2, vm.uiState.value.activeLimitFlagsCount("2026-11-20"))

        val notices = vm.uiState.value.sessionMessages[session.id].orEmpty().filter { it.role == ChatRole.NOTICE }
        assertEquals(2, notices.size)
        assertEquals("Switched to PrimaryCo / primary-model — the previous one hit its limit", notices[0].text)
        assertEquals("Switched to FallbackCo / fallback-model — the previous one hit its limit", notices[1].text)

        // Now FallbackCo also hits 429 -> all exhausted
        val outcomeExhausted = orchestrator.runAgentTurn(
            provider = vm.uiState.value.activeProvider!!,
            model = vm.uiState.value.selectedModel,
            mode = AgentWorkMode.PLAN,
            projectType = ProjectType.GENERAL,
            workspace = workspace,
            historyMessages = listOf(ChatMessageItem(id = "u2", role = ChatRole.USER, text = "Next")),
            assistantMessageId = "a2",
            callbacks = object : AgentOrchestrator.Callbacks {
                override fun onCallCreated(call: Call) = Unit
                override suspend fun onStatusUpdate(status: String) = Unit
                override suspend fun onTextDelta(assistantMessageId: String, delta: String) = Unit
                override suspend fun onToolCardAdded(assistantMessageId: String, tool: ToolExecutionItem) = Unit
                override suspend fun onToolCardUpdated(assistantMessageId: String, tool: ToolExecutionItem) = Unit
                override suspend fun requestUserPermission(prompt: PermissionPromptState): Boolean = false
                override suspend fun onWorkspaceMutated() = Unit
                override suspend fun onRateLimitHit(
                    failedProvider: ProviderConfig,
                    failedModel: String,
                    errorMessage: String
                ): RateLimitSwitchOutcome {
                    return vm.handleRateLimitSwitch(session.id, "a2", failedProvider, failedModel)
                }
            }
        )

        assertTrue(outcomeExhausted is AgentOrchestrator.RunOutcome.Error)
        assertEquals(3, vm.uiState.value.activeLimitFlagsCount("2026-11-20"))

        vm.resetLimitFlags()
        assertEquals(0, vm.uiState.value.activeLimitFlagsCount("2026-11-20"))

        // 401 wrong key must NOT auto-switch
        val outcome401 = orchestrator.runAgentTurn(
            provider = vm.uiState.value.providers.first(),
            model = "primary-model",
            mode = AgentWorkMode.PLAN,
            projectType = ProjectType.GENERAL,
            workspace = workspace,
            historyMessages = listOf(ChatMessageItem(id = "u3", role = ChatRole.USER, text = "Auth test")),
            assistantMessageId = "a3",
            callbacks = object : AgentOrchestrator.Callbacks {
                override fun onCallCreated(call: Call) = Unit
                override suspend fun onStatusUpdate(status: String) = Unit
                override suspend fun onTextDelta(assistantMessageId: String, delta: String) = Unit
                override suspend fun onToolCardAdded(assistantMessageId: String, tool: ToolExecutionItem) = Unit
                override suspend fun onToolCardUpdated(assistantMessageId: String, tool: ToolExecutionItem) = Unit
                override suspend fun requestUserPermission(prompt: PermissionPromptState): Boolean = false
                override suspend fun onWorkspaceMutated() = Unit
                override suspend fun onRateLimitHit(
                    failedProvider: ProviderConfig,
                    failedModel: String,
                    errorMessage: String
                ): RateLimitSwitchOutcome {
                    return vm.handleRateLimitSwitch(session.id, "a3", failedProvider, failedModel)
                }
            }
        )
        assertTrue(outcome401 is AgentOrchestrator.RunOutcome.Error)
        assertFalse((outcome401 as AgentOrchestrator.RunOutcome.Error).isRateLimit)
        assertEquals(0, vm.uiState.value.activeLimitFlagsCount("2026-11-20"))

        serverThread.join(3000)
    }

    @Test
    fun `reasoning setting is included only for supporting reasoning models`() {
        val client = AgentChatClient()

        // OpenAI reasoning model gets reasoning_effort; gpt-4o ignores it; AUTO omits it
        val gpt5High = client.buildOpenAiRequestBody("gpt-5", JSONArray(), false, ReasoningEffort.HIGH)
        assertEquals("high", gpt5High.optString("reasoning_effort"))

        val o3Med = client.buildOpenAiRequestBody("o3-mini", JSONArray(), false, ReasoningEffort.MEDIUM)
        assertEquals("medium", o3Med.optString("reasoning_effort"))

        val gpt4oHigh = client.buildOpenAiRequestBody("gpt-4o", JSONArray(), false, ReasoningEffort.HIGH)
        assertFalse(gpt4oHigh.has("reasoning_effort"))

        val gpt5Auto = client.buildOpenAiRequestBody("gpt-5", JSONArray(), false, ReasoningEffort.AUTO)
        assertFalse(gpt5Auto.has("reasoning_effort"))

        // Anthropic extended thinking
        val claudeSonnet4 = client.buildAnthropicRequestBody("claude-sonnet-4-20250514", "sys", JSONArray(), false, ReasoningEffort.HIGH)
        assertEquals("enabled", claudeSonnet4.optJSONObject("thinking")?.optString("type"))
        assertEquals(8192, claudeSonnet4.optJSONObject("thinking")?.optInt("budget_tokens"))

        val claudeHaiku3 = client.buildAnthropicRequestBody("claude-3-haiku-20240307", "sys", JSONArray(), false, ReasoningEffort.HIGH)
        assertFalse(claudeHaiku3.has("thinking"))

        // Gemini thinkingConfig
        val gemini25 = client.buildGeminiRequestBody("gemini-2.5-pro", "sys", JSONArray(), false, ReasoningEffort.LOW)
        assertEquals(
            1024,
            gemini25.optJSONObject("generationConfig")?.optJSONObject("thinkingConfig")?.optInt("thinkingBudget")
        )
        val gemini15 = client.buildGeminiRequestBody("gemini-1.5-pro", "sys", JSONArray(), false, ReasoningEffort.HIGH)
        assertFalse(gemini15.has("generationConfig"))
    }

    @Test
    fun `effort command overrides reasoning effort for active session only without calling model`() {
        val vm = PocketCodeViewModel()
        vm.setDefaultReasoningEffort(ReasoningEffort.LOW)

        val s1 = vm.createSession("SessionOne", "", null)
        val s2 = vm.createSession("SessionTwo", "", null)

        vm.sendMessage(s1.id, "/effort high")

        // Settings default remains LOW; s2 remains null; s1 becomes HIGH
        assertEquals(ReasoningEffort.LOW, vm.uiState.value.defaultReasoningEffort)
        assertEquals(ReasoningEffort.HIGH, vm.getSession(s1.id)?.reasoningEffortOverride)
        assertNull(vm.getSession(s2.id)?.reasoningEffortOverride)

        // Confirmation notice appears cleanly and no network run started
        assertNull(vm.uiState.value.activeStreamingSessionId)
        val msgs = vm.uiState.value.sessionMessages[s1.id].orEmpty()
        assertEquals(1, msgs.size)
        assertEquals(ChatRole.NOTICE, msgs.first().role)
        assertEquals("Reasoning effort for this session: high", msgs.first().text)
    }
}
