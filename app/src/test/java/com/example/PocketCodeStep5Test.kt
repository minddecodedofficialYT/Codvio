package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import com.example.data.AgentChatClient
import com.example.data.AgentOrchestrator
import com.example.data.CustomCommandExpander
import com.example.data.McpClient
import com.example.data.McpTestOutcome
import com.example.data.PermissionRuleEvaluator
import com.example.data.ProjectWorkspace
import com.example.data.ProviderJsonCodec
import com.example.data.ProviderVaultSnapshot
import com.example.model.AgentWorkMode
import com.example.model.ChatMessageItem
import com.example.model.ChatRole
import com.example.model.McpServerConfig
import com.example.model.PermissionPromptState
import com.example.model.PermissionRule
import com.example.model.PermissionRuleAction
import com.example.model.PermissionRuleType
import com.example.model.ProjectType
import com.example.model.ProviderApiStyle
import com.example.model.ProviderConfig
import com.example.model.ProviderKeyEntry
import com.example.model.SessionItem
import com.example.model.ToolExecutionItem
import com.example.model.ToolKind
import com.example.model.ToolStatus
import com.example.viewmodel.PocketCodeViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PocketCodeStep5Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `default permissions false and five built-in skills seed once and deletion persists`() {
        val vm = PocketCodeViewModel()
        assertFalse(vm.uiState.value.autoApproveAllPermissions)
        assertEquals(
            listOf(
                "code-review",
                "port-from-project",
                "skill-creator",
                "spike",
                "systematic-debugging"
            ),
            vm.uiState.value.skills.map { it.name }
        )

        // Delete one built-in skill and add a user skill
        val codeReviewId = vm.uiState.value.skills.first { it.name == "code-review" }.id
        vm.deleteSkill(codeReviewId)
        vm.addSkill("android-compose", "Always use Material 3 components.")

        val snapshot = ProviderVaultSnapshot(
            skills = vm.uiState.value.skills,
            skillsInitialized = vm.uiState.value.skillsInitialized,
            autoApproveAllPermissions = true
        )
        val encoded = ProviderJsonCodec.encodeSnapshot(snapshot)
        val decoded = ProviderJsonCodec.decodeSnapshot(encoded)

        assertTrue(decoded.autoApproveAllPermissions)
        assertTrue(decoded.skillsInitialized)
        assertFalse(decoded.skills.any { it.name == "code-review" })
        assertTrue(decoded.skills.any { it.name == "android-compose" })
        assertEquals(5, decoded.skills.size)

        // Legacy JSON without skillsInitialized seeds the 5 built-in skills automatically
        val legacyJson = """{"version":2,"providers":[]}"""
        val legacyDecoded = ProviderJsonCodec.decodeSnapshot(legacyJson)
        assertTrue(legacyDecoded.skillsInitialized)
        assertEquals(5, legacyDecoded.skills.size)
    }

    @Test
    fun `permission rule evaluator last match wins and pocketcode json rules parse cleanly`() {
        val rules = listOf(
            PermissionRule("1", PermissionRuleType.FILE, "*.md", PermissionRuleAction.ALLOW),
            PermissionRule("2", PermissionRuleType.FILE, "README.md", PermissionRuleAction.DENY),
            PermissionRule("3", PermissionRuleType.COMMAND, "git *", PermissionRuleAction.ALLOW),
            PermissionRule("4", PermissionRuleType.COMMAND, "git push*", PermissionRuleAction.ASK)
        )

        assertEquals(PermissionRuleAction.ALLOW, PermissionRuleEvaluator.evaluate(rules, PermissionRuleType.FILE, "docs/guide.md"))
        assertEquals(PermissionRuleAction.DENY, PermissionRuleEvaluator.evaluate(rules, PermissionRuleType.FILE, "README.md"))
        assertEquals(PermissionRuleAction.ALLOW, PermissionRuleEvaluator.evaluate(rules, PermissionRuleType.COMMAND, "git status"))
        assertEquals(PermissionRuleAction.ASK, PermissionRuleEvaluator.evaluate(rules, PermissionRuleType.COMMAND, "git push origin main"))
        assertEquals(PermissionRuleAction.ASK, PermissionRuleEvaluator.evaluate(rules, PermissionRuleType.FILE, "MainActivity.kt"))

        val projectJson = """
            {
              "permissionRules": [
                { "type": "file", "pattern": "secrets/*", "action": "deny" },
                { "type": "command", "pattern": "echo *", "action": "allow" }
              ]
            }
        """.trimIndent()
        val parsed = PermissionRuleEvaluator.parsePocketCodeJsonRules(projectJson)
        assertEquals(2, parsed.size)
        assertEquals(PermissionRuleAction.DENY, PermissionRuleEvaluator.evaluate(parsed, PermissionRuleType.FILE, "secrets/key.txt"))
        assertEquals(PermissionRuleAction.ALLOW, PermissionRuleEvaluator.evaluate(parsed, PermissionRuleType.COMMAND, "echo hello"))
    }

    @Test
    fun `custom command expands ARGUMENTS and positional args and loads project commands`() = runBlocking {
        assertEquals(
            "Review App.kt and Main.kt for null safety.",
            CustomCommandExpander.expandPrompt(
                "Review \$ARGUMENTS for null safety.",
                "App.kt and Main.kt"
            )
        )
        assertEquals(
            "Port AuthRepo from LegacyAuth to ModernAuth.",
            CustomCommandExpander.expandPrompt(
                "Port \$1 from \$2 to \$3.",
                "AuthRepo \"LegacyAuth\" ModernAuth"
            )
        )
        assertEquals(Pair("review", "src/App.kt"), CustomCommandExpander.parseInvocation("/review src/App.kt"))
        assertNull(CustomCommandExpander.parseInvocation("/init"))
        assertNull(CustomCommandExpander.parseInvocation("/effort high"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val projectDir = tempFolder.newFolder("cmd_proj")
        File(projectDir, ".pocketcode/commands").mkdirs()
        File(projectDir, ".pocketcode/commands/explain.md").writeText(
            "---\n" +
                "description: Explain a file\n" +
                "---\n" +
                "Explain how \$1 works with context: \$ARGUMENTS"
        )
        val workspace = ProjectWorkspace.forSession(
            context,
            SessionItem(id = "s1", projectName = "CmdProj", projectFolder = projectDir.absolutePath)
        )
        val projectCmds = workspace.readProjectCustomCommands()
        assertEquals(1, projectCmds.size)
        assertEquals("explain", projectCmds.first().name)
        assertTrue(projectCmds.first().prompt.startsWith("Explain how"))
    }

    @Test
    fun `real MCP client discovers tools and reports actual server failures`() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val serverThread = thread {
            // Request 1: initialize (JSON)
            // Request 2: notifications/initialized (JSON)
            // Request 3: tools/list (SSE)
            // Request 4: fail with HTTP 500
            repeat(4) { idx ->
                val socket = serverSocket.accept()
                socket.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                    val reqBody = if (contentLength > 0) {
                        val chars = CharArray(contentLength)
                        reader.read(chars, 0, contentLength)
                        String(chars)
                    } else ""
                    val reqJson = runCatching { JSONObject(reqBody) }.getOrNull()
                    val reqId = reqJson?.optInt("id", 1) ?: 1

                    val out = s.getOutputStream()
                    when (idx) {
                        0 -> {
                            val body = """{"jsonrpc":"2.0","id":$reqId,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}}}}"""
                            val hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nMcp-Session-Id: sess-abc\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n"
                            out.write((hdr + body).toByteArray())
                        }
                        1 -> {
                            val hdr = "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            out.write(hdr.toByteArray())
                        }
                        2 -> {
                            val ssePayload = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":$reqId,\"result\":{\"tools\":[{\"name\":\"resolve-library-id\",\"description\":\"Find library ID\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"libraryName\":{\"type\":\"string\"}},\"required\":[\"libraryName\"]}},{\"name\":\"query-docs\",\"description\":\"Query documentation\"}]}}\n\n"
                            val hdr = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${ssePayload.toByteArray().size}\r\nConnection: close\r\n\r\n"
                            out.write((hdr + ssePayload).toByteArray())
                        }
                        else -> {
                            val errBody = """{"error":{"message":"Unauthorized MCP token"}}"""
                            val hdr = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nContent-Length: ${errBody.toByteArray().size}\r\nConnection: close\r\n\r\n"
                            out.write((hdr + errBody).toByteArray())
                        }
                    }
                    out.flush()
                }
            }
            serverSocket.close()
        }

        val mcpClient = McpClient()
        val okOutcome = mcpClient.testAndDiscoverTools("http://127.0.0.1:$port/mcp")
        assertTrue(okOutcome is McpTestOutcome.Success)
        val success = okOutcome as McpTestOutcome.Success
        assertEquals("sess-abc", success.sessionId)
        assertEquals(2, success.tools.size)
        assertEquals(listOf("resolve-library-id", "query-docs"), success.tools.map { it.name })

        val errOutcome = mcpClient.testAndDiscoverTools("http://127.0.0.1:$port/mcp")
        assertTrue(errOutcome is McpTestOutcome.Failure)
        assertTrue((errOutcome as McpTestOutcome.Failure).errorMessage.contains("Unauthorized MCP token"))

        serverThread.join(4000)
    }

    @Test
    fun `orchestrator runs load_skill MCP tool and enforces permission rules and auto approve`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val projectDir = tempFolder.newFolder("step5_orch")
        File(projectDir, "pocketcode.json").writeText(
            """{"permissionRules":[{"type":"file","pattern":"blocked.txt","action":"deny"},{"type":"file","pattern":"allowed.txt","action":"allow"}]}"""
        )
        File(projectDir, ".pocketcode/skills/deploy-check").mkdirs()
        File(projectDir, ".pocketcode/skills/deploy-check/SKILL.md").writeText("Run smoke checks before tagging.")

        val mcpSocket = ServerSocket(0)
        val mcpPort = mcpSocket.localPort
        val llmSocket = ServerSocket(0)
        val llmPort = llmSocket.localPort

        val mcpThread = thread {
            val s = mcpSocket.accept()
            s.use { sock ->
                val reader = sock.getInputStream().bufferedReader()
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                val body = if (contentLength > 0) {
                    val chars = CharArray(contentLength)
                    reader.read(chars, 0, contentLength)
                    String(chars)
                } else ""
                val reqId = runCatching { JSONObject(body).optInt("id", 1) }.getOrDefault(1)
                val rpcOut = """{"jsonrpc":"2.0","id":$reqId,"result":{"content":[{"type":"text","text":"Library ID: /vercel/next.js"}]}}"""
                val hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${rpcOut.toByteArray().size}\r\nConnection: close\r\n\r\n"
                sock.getOutputStream().write((hdr + rpcOut).toByteArray())
                sock.getOutputStream().flush()
            }
            mcpSocket.close()
        }

        val llmBodiesSeen = mutableListOf<String>()
        val llmThread = thread {
            // Turn 1: Call load_skill + MCP tool + create blocked.txt (denied by rule) + create allowed.txt (allowed by rule)
            // Turn 2: Final assistant text summarizing tool results
            repeat(2) { idx ->
                val s = llmSocket.accept()
                s.use { sock ->
                    val reader = sock.getInputStream().bufferedReader()
                    var len = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            len = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                    val req = if (len > 0) {
                        val chars = CharArray(len)
                        reader.read(chars, 0, len)
                        String(chars)
                    } else ""
                    llmBodiesSeen.add(req)

                    val jsonBody = if (idx == 0) {
                        """{"id":"c1","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"","tool_calls":[{"id":"t1","type":"function","function":{"name":"load_skill","arguments":"{\"name\":\"deploy-check\"}"}},{"id":"t2","type":"function","function":{"name":"mcp_0_resolve-library-id","arguments":"{\"libraryName\":\"nextjs\"}"}},{"id":"t3","type":"function","function":{"name":"create_file","arguments":"{\"path\":\"blocked.txt\",\"content\":\"secret\"}"}},{"id":"t4","type":"function","function":{"name":"create_file","arguments":"{\"path\":\"allowed.txt\",\"content\":\"ok\"}"}}]}}]}"""
                    } else {
                        """{"id":"c2","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"Done with skill and MCP."}}]}"""
                    }
                    val hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${jsonBody.toByteArray().size}\r\nConnection: close\r\n\r\n"
                    sock.getOutputStream().write((hdr + jsonBody).toByteArray())
                    sock.getOutputStream().flush()
                }
            }
            llmSocket.close()
        }

        val provider = ProviderConfig(
            id = "p1",
            name = "TestProvider",
            subtitleLabel = "OpenAI",
            baseUrl = "http://127.0.0.1:$llmPort/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            isCustom = true,
            isEnabled = true,
            keys = listOf(ProviderKeyEntry("k1", "Primary", "sk-test")),
            activeKeyId = "k1"
        )
        val mcpServer = McpServerConfig(
            id = "m1",
            name = "Context7",
            url = "http://127.0.0.1:$mcpPort/mcp",
            isConnected = true,
            sessionId = "sess-1",
            tools = listOf(com.example.model.McpToolInfo("resolve-library-id", "Resolve library ID"))
        )
        val workspace = ProjectWorkspace.forSession(
            context,
            SessionItem(id = "s_orch", projectName = "Step5", projectFolder = projectDir.absolutePath)
        )

        val toolCards = mutableMapOf<String, ToolExecutionItem>()
        var popupCount = 0
        var finalText = ""

        val orchestrator = AgentOrchestrator(AgentChatClient())
        val outcome = orchestrator.runAgentTurn(
            provider = provider,
            model = "gpt-5",
            mode = AgentWorkMode.BUILD,
            projectType = ProjectType.GENERAL,
            workspace = workspace,
            historyMessages = listOf(
                ChatMessageItem("u1", ChatRole.USER, "Check deploy & docs")
            ),
            assistantMessageId = "a1",
            autoApproveAllPermissions = false,
            mcpServers = listOf(mcpServer),
            callbacks = object : AgentOrchestrator.Callbacks {
                override fun onCallCreated(call: Call) = Unit
                override suspend fun onStatusUpdate(status: String) = Unit
                override suspend fun onTextDelta(assistantMessageId: String, delta: String) {
                    finalText += delta
                }
                override suspend fun onToolCardAdded(assistantMessageId: String, tool: ToolExecutionItem) {
                    toolCards[tool.id] = tool
                }
                override suspend fun onToolCardUpdated(assistantMessageId: String, tool: ToolExecutionItem) {
                    toolCards[tool.id] = tool
                }
                override suspend fun requestUserPermission(prompt: PermissionPromptState): Boolean {
                    popupCount++
                    return true
                }
                override suspend fun onWorkspaceMutated() = Unit
            }
        )

        assertEquals(AgentOrchestrator.RunOutcome.Completed, outcome)
        assertEquals(0, popupCount) // blocked.txt denied by rule, allowed.txt allowed by rule
        assertFalse(File(projectDir, "blocked.txt").exists())
        assertTrue(File(projectDir, "allowed.txt").exists())

        val skillCard = toolCards.values.firstOrNull { it.kind == ToolKind.LOAD_SKILL }
        assertNotNull(skillCard)
        assertEquals("Skill loaded: deploy-check", skillCard!!.titleOverride)
        assertEquals(ToolStatus.DONE, skillCard.status)

        val mcpCard = toolCards.values.firstOrNull { it.kind == ToolKind.MCP_TOOL }
        assertNotNull(mcpCard)
        assertEquals("Context7: resolve-library-id", mcpCard!!.titleOverride)
        assertEquals(ToolStatus.DONE, mcpCard.status)
        assertTrue(mcpCard.fullOutput.contains("/vercel/next.js"))

        val secondTurnPayload = llmBodiesSeen[1]
        assertTrue(secondTurnPayload.contains("Run smoke checks before tagging."))
        assertTrue(
            secondTurnPayload.contains("Library ID: /vercel/next.js") ||
                secondTurnPayload.contains("Library ID: \\/vercel\\/next.js")
        )
        assertTrue(secondTurnPayload.contains("blocked by permission rule"))
        assertEquals("Done with skill and MCP.", finalText)

        mcpThread.join(4000)
        llmThread.join(4000)
    }

    @Test
    fun `viewmodel custom command expands arguments into modelPromptText before sending`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val projectDir = tempFolder.newFolder("vm_cmd_proj")
        val llmSocket = ServerSocket(0)
        val llmPort = llmSocket.localPort
        val bodiesSeen = mutableListOf<String>()

        val llmThread = thread {
            val s = llmSocket.accept()
            s.use { sock ->
                val reader = sock.getInputStream().bufferedReader()
                var len = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        len = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                val req = if (len > 0) {
                    val chars = CharArray(len)
                    reader.read(chars, 0, len)
                    String(chars)
                } else ""
                bodiesSeen.add(req)
                val body = """{"id":"c1","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"Reviewed!"}}]}"""
                val hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n"
                sock.getOutputStream().write((hdr + body).toByteArray())
                sock.getOutputStream().flush()
            }
            llmSocket.close()
        }

        val vm = PocketCodeViewModel(appContext = context)
        vm.saveProvider(
            editingProviderId = null,
            name = "CustomOpenAI",
            subtitleLabel = "Custom",
            baseUrl = "http://127.0.0.1:$llmPort/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            isCustom = true,
            apiKeyInput = "sk-test",
            customModelIds = listOf("gpt-5")
        )
        vm.selectModel("gpt-5")
        vm.addCustomCommand("review", "Carefully audit \$ARGUMENTS for bugs.")
        val session = vm.createSession("CmdApp", projectDir.absolutePath, null)

        vm.sendMessage(session.id, overrideText = "/review Main.kt")
        val deadline = System.currentTimeMillis() + 5000
        while (vm.uiState.value.activeStreamingSessionId != null && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val msgs = vm.uiState.value.sessionMessages[session.id].orEmpty()
        val userMsg = msgs.first { it.role == ChatRole.USER }
        assertEquals("/review Main.kt", userMsg.text)
        assertEquals("Carefully audit Main.kt for bugs.", userMsg.modelPromptText)
        assertTrue(bodiesSeen.first().contains("Carefully audit Main.kt for bugs."))

        llmThread.join(4000)
    }
}
