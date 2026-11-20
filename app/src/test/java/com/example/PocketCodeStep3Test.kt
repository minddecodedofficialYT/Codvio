package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AgentChatClient
import com.example.data.AgentOrchestrator
import com.example.data.ProjectWorkspace
import com.example.data.SessionHistoryCodec
import com.example.data.SessionsHistorySnapshot
import com.example.model.AgentWorkMode
import com.example.model.ChatMessageItem
import com.example.model.ChatRole
import com.example.model.PermissionPromptState
import com.example.model.ProjectType
import com.example.model.ProviderApiStyle
import com.example.model.ProviderConfig
import com.example.model.ProviderKeyEntry
import com.example.model.SessionItem
import com.example.model.ToolExecutionItem
import com.example.model.ToolKind
import com.example.model.ToolStatus
import kotlinx.coroutines.runBlocking
import okhttp3.Call
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
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PocketCodeStep3Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `workspace blocks path traversal and absolute paths outside project root`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val projectDir = tempFolder.newFolder("sandbox_proj")
        val session = SessionItem(
            id = "s1",
            projectName = "Sandbox",
            projectFolder = projectDir.absolutePath
        )
        val workspace = ProjectWorkspace.forSession(context, session)

        val traversalList = workspace.listFiles("../")
        assertTrue(traversalList.isFailure)

        val traversalRead = workspace.readFile("../../etc/passwd")
        assertTrue(traversalRead.isFailure)

        val absOutsideCreate = workspace.createFile("/tmp/evil_outside.txt", "nope")
        assertFalse(absOutsideCreate.success)
        assertEquals("Access denied", absOutsideCreate.summary)

        val absInsideCreate = workspace.createFile("${projectDir.absolutePath}/hello.txt", "hi")
        assertTrue(absInsideCreate.success)
        assertEquals("hi", workspace.readFile("hello.txt").getOrNull())
    }

    @Test
    fun `workspace create edit search command and undo work strictly inside project`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val projectDir = tempFolder.newFolder("demo_web")
        val session = SessionItem(
            id = "s2",
            projectName = "DemoWeb",
            projectFolder = projectDir.absolutePath
        )
        val workspace = ProjectWorkspace.forSession(context, session)

        // Create new file & search
        val createRes = workspace.createFile("app.js", "console.log('alpha');\nconsole.log('beta');")
        assertTrue(createRes.success)
        assertFalse(createRes.existedBefore)

        val matches = workspace.searchText("beta").getOrThrow()
        assertEquals(1, matches.size)
        assertTrue(matches[0].startsWith("app.js:2:"))

        // Edit file
        val editRes = workspace.editFile("app.js", "beta", "gamma", null)
        assertTrue(editRes.success)
        assertTrue(workspace.readFile("app.js").getOrThrow().contains("gamma"))

        // Undo edit restores previous content
        val undoEdit = workspace.undoFileMutation("app.js", editRes.existedBefore, editRes.previousContent)
        assertTrue(undoEdit.isSuccess)
        assertTrue(workspace.readFile("app.js").getOrThrow().contains("beta"))

        // Undo initial creation deletes newly created file
        val undoCreate = workspace.undoFileMutation("app.js", createRes.existedBefore, createRes.previousContent)
        assertTrue(undoCreate.isSuccess)
        assertFalse(File(projectDir, "app.js").exists())

        // Shell command runs in project folder
        val cmdRes = workspace.runCommand("pwd && echo ok_shell")
        assertTrue(cmdRes.success)
        assertEquals(0, cmdRes.exitCode)
        assertTrue(cmdRes.output.contains("ok_shell"))
    }

    @Test
    fun `workspace detects Web vs Native Android projects`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val webDir = tempFolder.newFolder("web_project")
        File(webDir, "index.html").writeText("<html><body>Hello</body></html>")
        val webWs = ProjectWorkspace.forSession(
            context,
            SessionItem(id = "w1", projectName = "Web", projectFolder = webDir.absolutePath)
        )
        val (webType, hasHtml) = webWs.detectProjectInfo()
        assertEquals(ProjectType.WEB, webType)
        assertTrue(hasHtml)

        val androidDir = tempFolder.newFolder("android_project")
        File(androidDir, "build.gradle.kts").writeText("plugins {}")
        val androidWs = ProjectWorkspace.forSession(
            context,
            SessionItem(id = "a1", projectName = "Android", projectFolder = androidDir.absolutePath)
        )
        val (androidType, androidHasHtml) = androidWs.detectProjectInfo()
        assertEquals(ProjectType.NATIVE_ANDROID, androidType)
        assertFalse(androidHasHtml)
    }

    @Test
    fun `session history codec preserves sessions messages and tool undo backups`() {
        val session = SessionItem(
            id = "sess_1",
            projectName = "Portfolio",
            projectFolder = "/tmp/portfolio",
            mode = AgentWorkMode.BUILD,
            model = "gpt-4o",
            lastActivityAtMs = 1710000123456L,
            projectType = ProjectType.WEB,
            hasIndexHtml = true
        )
        val tool = ToolExecutionItem(
            id = "t1",
            kind = ToolKind.EDIT_FILE,
            target = "index.html",
            summary = "Edited index.html",
            fullOutput = "Updated index.html (12 lines)",
            status = ToolStatus.DONE,
            canUndo = true,
            backupRelativePath = "index.html",
            previousContent = "<h1>Old</h1>",
            existedBefore = true
        )
        val msgs = listOf(
            ChatMessageItem(id = "m1", role = ChatRole.USER, text = "Update heading", timestampMs = 100L),
            ChatMessageItem(
                id = "m2",
                role = ChatRole.ASSISTANT,
                text = "Updated the heading.",
                timestampMs = 200L,
                toolCalls = listOf(tool)
            )
        )
        val snapshot = SessionsHistorySnapshot(
            sessions = listOf(session),
            messagesBySession = mapOf(session.id to msgs)
        )

        val encoded = SessionHistoryCodec.encode(snapshot)
        val decoded = SessionHistoryCodec.decode(encoded)

        assertEquals(1, decoded.sessions.size)
        val dSess = decoded.sessions.first()
        assertEquals("Portfolio", dSess.projectName)
        assertEquals(1710000123456L, dSess.lastActivityAtMs)
        assertEquals(ProjectType.WEB, dSess.projectType)
        assertTrue(dSess.hasIndexHtml)

        val dMsgs = decoded.messagesBySession["sess_1"].orEmpty()
        assertEquals(2, dMsgs.size)
        assertEquals("<h1>Old</h1>", dMsgs[1].toolCalls.first().previousContent)
        assertTrue(dMsgs[1].toolCalls.first().canUndo)
    }

    @Test
    fun `orchestrator streams response handles tool denial and propagates HTTP errors`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val projectDir = tempFolder.newFolder("agent_test_proj")
        val workspace = ProjectWorkspace.forSession(
            context,
            SessionItem(id = "s_orch", projectName = "Orch", projectFolder = projectDir.absolutePath)
        )

        // Spin up minimal local HTTP server returning an SSE tool_call followed by final answer, then an HTTP 401 error
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val serverThread = thread {
            repeat(3) { requestIndex ->
                val socket = serverSocket.accept()
                socket.use { s ->
                    val input = s.getInputStream().bufferedReader()
                    var contentLength = 0
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                    if (contentLength > 0) {
                        val buf = CharArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val r = input.read(buf, read, contentLength - read)
                            if (r <= 0) break
                            read += r
                        }
                    }

                    val out = s.getOutputStream()
                    if (requestIndex == 0) {
                        val sse = buildString {
                            append("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"create_file\",\"arguments\":\"{\\\"path\\\":\\\"note.txt\\\",\\\"content\\\":\\\"hi\\\"}\"}}]}}]}\n\n")
                            append("data: [DONE]\n\n")
                        }
                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${sse.toByteArray().size}\r\nConnection: close\r\n\r\n$sse"
                        out.write(resp.toByteArray())
                    } else if (requestIndex == 1) {
                        val sse = buildString {
                            append("data: {\"choices\":[{\"delta\":{\"content\":\"Understood, skipped file.\"}}]}\n\n")
                            append("data: [DONE]\n\n")
                        }
                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${sse.toByteArray().size}\r\nConnection: close\r\n\r\n$sse"
                        out.write(resp.toByteArray())
                    } else {
                        val errJson = """{"error":{"message":"Invalid API key"}}"""
                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nContent-Length: ${errJson.toByteArray().size}\r\nConnection: close\r\n\r\n$errJson"
                        out.write(resp.toByteArray())
                    }
                    out.flush()
                }
            }
            serverSocket.close()
        }

        val provider = ProviderConfig(
            id = "p1",
            name = "TestLocal",
            subtitleLabel = "Custom",
            baseUrl = "http://127.0.0.1:$port/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            isCustom = true,
            isEnabled = true,
            keys = listOf(ProviderKeyEntry("k1", "Key", "bad-key")),
            activeKeyId = "k1"
        )

        val orchestrator = AgentOrchestrator(AgentChatClient())
        val streamedText = StringBuilder()
        val toolCards = mutableListOf<ToolExecutionItem>()
        var askedPrompt: PermissionPromptState? = null

        val outcome1 = orchestrator.runAgentTurn(
            provider = provider,
            model = "test-model",
            mode = AgentWorkMode.BUILD,
            projectType = ProjectType.GENERAL,
            workspace = workspace,
            historyMessages = listOf(ChatMessageItem(id = "u1", role = ChatRole.USER, text = "Create note.txt")),
            assistantMessageId = "a1",
            callbacks = object : AgentOrchestrator.Callbacks {
                override fun onCallCreated(call: Call) = Unit
                override suspend fun onStatusUpdate(status: String) = Unit
                override suspend fun onTextDelta(assistantMessageId: String, delta: String) {
                    streamedText.append(delta)
                }
                override suspend fun onToolCardAdded(assistantMessageId: String, tool: ToolExecutionItem) {
                    toolCards.add(tool)
                }
                override suspend fun onToolCardUpdated(assistantMessageId: String, tool: ToolExecutionItem) {
                    val idx = toolCards.indexOfFirst { it.id == tool.id }
                    if (idx >= 0) toolCards[idx] = tool
                }
                override suspend fun requestUserPermission(prompt: PermissionPromptState): Boolean {
                    askedPrompt = prompt
                    return false // Deny
                }
                override suspend fun onWorkspaceMutated() = Unit
            }
        )

        assertTrue(outcome1 is AgentOrchestrator.RunOutcome.Completed)
        assertEquals("note.txt", askedPrompt?.target)
        assertFalse(File(projectDir, "note.txt").exists())
        assertEquals(ToolStatus.DENIED, toolCards.first().status)
        assertEquals("Understood, skipped file.", streamedText.toString())

        // Verify real provider HTTP error is surfaced directly
        val outcomeErr = orchestrator.runAgentTurn(
            provider = provider,
            model = "test-model",
            mode = AgentWorkMode.PLAN,
            projectType = ProjectType.GENERAL,
            workspace = workspace,
            historyMessages = listOf(ChatMessageItem(id = "u2", role = ChatRole.USER, text = "Plan it")),
            assistantMessageId = "a2",
            callbacks = object : AgentOrchestrator.Callbacks {
                override fun onCallCreated(call: Call) = Unit
                override suspend fun onStatusUpdate(status: String) = Unit
                override suspend fun onTextDelta(assistantMessageId: String, delta: String) = Unit
                override suspend fun onToolCardAdded(assistantMessageId: String, tool: ToolExecutionItem) = Unit
                override suspend fun onToolCardUpdated(assistantMessageId: String, tool: ToolExecutionItem) = Unit
                override suspend fun requestUserPermission(prompt: PermissionPromptState): Boolean = false
                override suspend fun onWorkspaceMutated() = Unit
            }
        )

        assertTrue(outcomeErr is AgentOrchestrator.RunOutcome.Error)
        assertEquals("HTTP 401: Invalid API key", (outcomeErr as AgentOrchestrator.RunOutcome.Error).message)
        serverThread.join(3000)
        assertNull(File(projectDir, "note.txt").takeIf { it.exists() })
    }
}
