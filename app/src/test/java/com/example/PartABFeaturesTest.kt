package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.EncryptedProviderStorage
import com.example.data.NotesStorage
import com.example.data.SessionStorage
import com.example.data.TarGzExtractor
import com.example.data.TerminalArchResolution
import com.example.data.TerminalEnvironmentManager
import com.example.data.TerminalInstallOutcome
import com.example.data.TerminalInstallStage
import com.example.data.TerminalMetadataOutcome
import com.example.model.AppUiState
import com.example.model.McpServerSuggestions
import com.example.model.SessionItem
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.PocketCodeTheme
import com.example.viewmodel.PocketCodeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class PartABFeaturesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tar gz extractor unpacks files symlinks directories prevents traversal`() {
        val tarGzBytes = buildMiniRootfsTarGz(includeTraversalEntry = true)
        val archiveFile = File(tempFolder.root, "rootfs.tar.gz").apply {
            writeBytes(tarGzBytes)
        }
        val extractDir = File(tempFolder.root, "out_rootfs")

        val summary = TarGzExtractor.extract(archiveFile, extractDir)

        assertTrue(summary.directoriesCreated >= 2)
        assertTrue(summary.filesExtracted >= 2)
        assertTrue(summary.symlinksCreated >= 1)

        val busybox = File(extractDir, "bin/busybox")
        assertTrue(busybox.exists())
        assertTrue(busybox.canExecute())

        val release = File(extractDir, "etc/alpine-release")
        assertTrue(release.exists())
        assertEquals("3.20.3\n", release.readText())

        val shLink = File(extractDir, "bin/sh")
        assertTrue(Files.isSymbolicLink(shLink.toPath()))
        assertEquals("/bin/busybox", Files.readSymbolicLink(shLink.toPath()).toString())

        // Path traversal file must never be created outside extractDir
        assertFalse(File(tempFolder.root, "escape.txt").exists())
    }

    @Test
    fun `terminal environment manager queries real sizes verifies sha256 installs and removes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rootfsBytes = buildMiniRootfsTarGz(includeTraversalEntry = false)
        val rootfsSha = sha256Hex(rootfsBytes)
        val fakeProotBytes = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) +
            ByteArray(2048) { 0x01 }

        val releaseYaml = """
            - arch: aarch64
              file: alpine-minirootfs-3.20.3-aarch64.tar.gz
              flavor: alpine-minirootfs
              version: 3.20.3
              sha256: $rootfsSha
        """.trimIndent()

        var serveCorruptedSha = false
        val server = startLocalTerminalServer(
            yamlBody = releaseYaml,
            rootfsBytes = rootfsBytes,
            prootBytes = fakeProotBytes,
            shaBodyProvider = {
                val activeSha = if (serveCorruptedSha) "0".repeat(64) else rootfsSha
                "$activeSha  alpine-minirootfs-3.20.3-aarch64.tar.gz\n"
            }
        )

        try {
            val baseUrl = "http://127.0.0.1:${server.localPort}"

            // Unsupported architecture is reported honestly
            val unsupportedMgr = TerminalEnvironmentManager(
                context = context,
                abiProvider = { arrayOf("x86_64") }
            )
            assertTrue(unsupportedMgr.resolveArchitecture() is TerminalArchResolution.Unsupported)

            val mgr = TerminalEnvironmentManager(
                context = context,
                abiProvider = { arrayOf("arm64-v8a") },
                alpineLatestReleasesUrlProvider = { "$baseUrl/latest-releases.yaml" },
                alpineFileUrlProvider = { _, file -> "$baseUrl/$file" },
                prootUrlProvider = { "$baseUrl/proot" }
            )
            runBlocking { mgr.removeEnvironment() }
            assertFalse(mgr.isInstalled())

            val planOutcome = runBlocking { mgr.queryDownloadPlan() }
            assertTrue(planOutcome is TerminalMetadataOutcome.Ready)
            val plan = (planOutcome as TerminalMetadataOutcome.Ready).plan
            assertEquals(rootfsBytes.size.toLong(), plan.rootfsSizeBytes)
            assertEquals(fakeProotBytes.size.toLong(), plan.prootSizeBytes)
            assertEquals("aarch64", plan.arch.alpineArch)

            // 1. Checksum mismatch deletes downloaded temp files cleanly
            serveCorruptedSha = true
            val badOutcome = runBlocking {
                mgr.downloadVerifyAndInstall(plan = plan, onProgress = {}, isCancelled = { false })
            }
            assertTrue(badOutcome is TerminalInstallOutcome.Failure)
            assertTrue((badOutcome as TerminalInstallOutcome.Failure).message.contains("SHA256"))
            assertFalse(File(mgr.terminalRootDir, "download_tmp").exists())
            assertFalse(mgr.isInstalled())

            // 2. Cancel during download cleans up and returns Cancelled
            serveCorruptedSha = false
            val cancelOutcome = runBlocking {
                mgr.downloadVerifyAndInstall(plan = plan, onProgress = {}, isCancelled = { true })
            }
            assertTrue(cancelOutcome is TerminalInstallOutcome.Cancelled)
            assertFalse(mgr.isInstalled())

            // 3. Valid install passes SHA256, extracts rootfs + resolv.conf + proot
            val stages = mutableListOf<TerminalInstallStage>()
            val okOutcome = runBlocking {
                mgr.downloadVerifyAndInstall(
                    plan = plan,
                    onProgress = { stages += it },
                    isCancelled = { false }
                )
            }
            assertTrue(okOutcome is TerminalInstallOutcome.Success)
            assertTrue(mgr.isInstalled())
            assertTrue(File(mgr.rootfsDir, "etc/resolv.conf").readText().contains("nameserver"))
            assertTrue(stages.any { it is TerminalInstallStage.Verifying })
            assertTrue(stages.any { it is TerminalInstallStage.SettingUp })

            // 4. Remove environment deletes terminal directory cleanly
            runBlocking { mgr.removeEnvironment() }
            assertFalse(mgr.isInstalled())
        } finally {
            server.close()
        }
    }

    @Test
    fun `notes and todos persist cleanly in local json across restarts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "notes_todos.json").delete()

        val notesStorage = NotesStorage(context)
        val vm1 = PocketCodeViewModel(
            appContext = context,
            notesStorage = notesStorage
        )
        runBlocking { kotlinx.coroutines.delay(50) }

        vm1.addNote("Build Checklist", "Test PRoot + Alpine")
        val noteId = vm1.uiState.value.notes.first().id
        vm1.updateNote(noteId, "Build Checklist", "Test PRoot + Alpine + Notes")

        vm1.addTodo("Verify MCP suggestions")
        val todoId = vm1.uiState.value.todos.first().id
        vm1.toggleTodo(todoId)

        // Pump main looper & IO save until notes_todos.json has persisted
        val notesFile = File(context.filesDir, "notes_todos.json")
        val saveDeadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < saveDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (notesFile.exists() && notesFile.readText().contains("Verify MCP suggestions") &&
                notesFile.readText().contains("Test PRoot + Alpine + Notes")
            ) {
                break
            }
            Thread.sleep(25)
        }

        val vm2 = PocketCodeViewModel(
            appContext = context,
            notesStorage = NotesStorage(context)
        )
        val loadDeadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < loadDeadline && vm2.uiState.value.notes.isEmpty()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, vm2.uiState.value.notes.size)
        assertEquals("Build Checklist", vm2.uiState.value.notes.first().title)
        assertEquals("Test PRoot + Alpine + Notes", vm2.uiState.value.notes.first().content)
        assertEquals(1, vm2.uiState.value.todos.size)
        assertEquals("Verify MCP suggestions", vm2.uiState.value.todos.first().text)
        assertTrue(vm2.uiState.value.todos.first().isDone)

        vm2.deleteNote(noteId)
        vm2.deleteTodo(todoId)
        assertTrue(vm2.uiState.value.notes.isEmpty())
        assertTrue(vm2.uiState.value.todos.isEmpty())
    }

    @Test
    fun `chat toolbar has only 4 icons opens terminal real size prompt and notes panel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rootfsBytes = buildMiniRootfsTarGz(includeTraversalEntry = false)
        val prootBytes = ByteArray(1024 * 1200) { 0x01 } // ~1.1 MB so formatted MB is clearly > 0
        val releaseYaml = """
            - arch: aarch64
              file: alpine-minirootfs-3.20.3-aarch64.tar.gz
              flavor: alpine-minirootfs
              version: 3.20.3
        """.trimIndent()
        val server = startLocalTerminalServer(
            yamlBody = releaseYaml,
            rootfsBytes = rootfsBytes,
            prootBytes = prootBytes,
            shaBodyProvider = { "${sha256Hex(rootfsBytes)}  alpine-minirootfs-3.20.3-aarch64.tar.gz\n" }
        )
        val baseUrl = "http://127.0.0.1:${server.localPort}"
        val testEnvMgr = TerminalEnvironmentManager(
            context = context,
            abiProvider = { arrayOf("arm64-v8a") },
            alpineLatestReleasesUrlProvider = { "$baseUrl/latest-releases.yaml" },
            alpineFileUrlProvider = { _, file -> "$baseUrl/$file" },
            prootUrlProvider = { "$baseUrl/proot" }
        )
        runBlocking { testEnvMgr.removeEnvironment() }

        try {
            val vm = PocketCodeViewModel(appContext = context)
            val session = SessionItem(
                id = "s1",
                projectName = "DemoApp",
                projectFolder = tempFolder.root.absolutePath
            )

            composeRule.setContent {
                val state = vm.uiState.collectAsStateWithLifecycle().value
                PocketCodeTheme(themeMode = state.themeMode) {
                    ChatScreen(
                        session = session,
                        uiState = state,
                        workspaceProvider = { null },
                        onOpenSession = {},
                        onNavigateBack = {},
                        onOpenSetup = {},
                        onOpenProviderSettings = {},
                        onSelectModel = {},
                        onSelectWorkMode = {},
                        onDraftMessageChange = vm::updateDraftMessage,
                        onStorageAccessChanged = {},
                        onSendMessage = {},
                        onStopRun = {},
                        onResolvePermission = {},
                        onUndoTool = { _, _, _ -> },
                        onAttachImage = { _, _ -> },
                        onClearAttachment = {},
                        onAddNote = vm::addNote,
                        onUpdateNote = vm::updateNote,
                        onDeleteNote = vm::deleteNote,
                        onAddTodo = vm::addTodo,
                        onToggleTodo = vm::toggleTodo,
                        onDeleteTodo = vm::deleteTodo,
                        terminalEnvManagerFactory = { testEnvMgr }
                    )
                }
            }

            // 1. All 4 icons present (Chat, Terminal, Browser, Notes) and input bar visible
            composeRule.onNodeWithTag("toolbar_icon_chat").assertIsDisplayed()
            composeRule.onNodeWithTag("toolbar_icon_terminal").assertIsDisplayed()
            composeRule.onNodeWithTag("toolbar_icon_browser").assertIsDisplayed()
            composeRule.onNodeWithTag("toolbar_icon_notes").assertIsDisplayed()
            composeRule.onNodeWithTag("chat_message_input").assertIsDisplayed()

            // 2. Collapse and expand toolbar
            composeRule.onNodeWithTag("toolbar_collapse_button").performClick()
            composeRule.onNodeWithTag("toolbar_expand_handle").assertIsDisplayed()
            composeRule.onNodeWithTag("toolbar_expand_handle").performClick()
            composeRule.onNodeWithTag("toolbar_icon_terminal").assertIsDisplayed()

            // 3. First open of Terminal fetches real server header size and shows prompt
            composeRule.onNodeWithTag("toolbar_icon_terminal").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeRule.onNodeWithTag("terminal_download_prompt_text").assertIsDisplayed()
                }.isSuccess
            }
            val expectedMb = TerminalEnvironmentManager(context).let {
                com.example.data.TerminalDownloadPlan.formatMegabytes(
                    rootfsBytes.size.toLong() + prootBytes.size.toLong()
                )
            }
            composeRule.onNodeWithText(
                "Terminal needs a one-time environment download (Alpine Linux). Size: $expectedMb MB. Download now?"
            ).assertIsDisplayed()
            composeRule.onNodeWithTag("terminal_download_confirm_button").assertIsDisplayed()
            composeRule.onNodeWithTag("terminal_not_now_button").performClick()

            // 4. Open Notes panel, add a to-do and a note, then return via Chat icon
            composeRule.onNodeWithTag("toolbar_icon_notes").performClick()
            composeRule.onNodeWithTag("notes_panel").assertIsDisplayed()
            composeRule.onNodeWithTag("notes_tab_to-dos").performClick()
            composeRule.onNodeWithTag("todo_input").performTextInput("Ship Codvio update")
            composeRule.onNodeWithTag("todo_add_button").performClick()
            composeRule.onNodeWithText("Ship Codvio update").assertIsDisplayed()

            // Bottom chat input bar remains visible & uncovered even when sheet is open
            composeRule.onNodeWithTag("chat_message_input").assertIsDisplayed()

            // Tapping Chat icon returns cleanly to chat
            composeRule.onNodeWithTag("toolbar_icon_chat").performClick()
            composeRule.onNodeWithTag("notes_panel").assertDoesNotExist()
        } finally {
            server.close()
        }
    }

    @Test
    fun `settings mcp servers section displays 6 suggestions with sign-in badges and add state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = PocketCodeViewModel(appContext = context)

        composeRule.setContent {
            val state = vm.uiState.collectAsStateWithLifecycle().value
            PocketCodeTheme(themeMode = state.themeMode) {
                SettingsScreen(
                    uiState = state,
                    onNavigateBack = {},
                    onSaveProvider = { _, _, _, _, _, _, _, _, _ -> },
                    onToggleProvider = { _, _ -> },
                    onDeleteProvider = {},
                    onAddProviderKey = { _, _, _ -> },
                    onDeleteProviderKey = { _, _ -> },
                    onSelectActiveKey = { _, _ -> },
                    onFetchModelsForProvider = {},
                    onClearOperationState = {},
                    onFetchModelsAdHoc = { _, _, _ -> error("unused") },
                    onTestConnectionAdHoc = { _, _, _ -> error("unused") },
                    onExportBackup = { Result.success(0) },
                    onCreateShareBackupFile = { Result.failure(UnsupportedOperationException()) },
                    onImportBackup = { Result.success(0) },
                    activeLimitFlagCount = 0,
                    onToggleAutoSwitch = {},
                    onAddFallbackEntry = { _, _ -> },
                    onMoveFallbackUp = {},
                    onMoveFallbackDown = {},
                    onRemoveFallbackEntry = {},
                    onResetLimitFlags = {},
                    onSelectDefaultReasoningEffort = {},
                    onToggleAutoApproveAllPermissions = {},
                    onAddMcpServer = vm::addMcpServer,
                    onTestMcpServer = {},
                    onDeleteMcpServer = vm::deleteMcpServer,
                    onAddPermissionRule = { _, _, _ -> },
                    onDeletePermissionRule = {},
                    onAddCustomCommand = { _, _ -> },
                    onDeleteCustomCommand = {},
                    onAddSkill = { _, _ -> },
                    onDeleteSkill = {},
                    onToggleVoiceAnnouncement = {},
                    onTestVoice = {},
                    onSelectThemeMode = {},
                    onToggleDynamicColor = {},
                    onToggleShowVio = {},
                    onStorageAccessChanged = {},
                    onDisconnect = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.waitForIdle()

        // Verify all 6 exact suggestions and their descriptions
        assertEquals(6, McpServerSuggestions.all.size)
        val settingsList = composeRule.onNodeWithTag("settings_lazy_column")
        settingsList.performScrollToNode(hasTestTag("settings_mcp_servers_section"))
        composeRule.waitForIdle()
        settingsList.performScrollToNode(hasTestTag("mcp_suggestions_header"))
        composeRule.onNodeWithText("Suggestions").assertIsDisplayed()

        McpServerSuggestions.all.forEach { suggestion ->
            settingsList.performScrollToNode(hasTestTag("mcp_suggestion_row_${suggestion.id}"))
            composeRule.onNodeWithText(suggestion.name).assertIsDisplayed()
            composeRule.onNodeWithText(suggestion.description).assertIsDisplayed()
            if (suggestion.needsSignIn) {
                composeRule.onNodeWithTag("mcp_suggestion_auth_${suggestion.id}").assertIsDisplayed()
            } else {
                composeRule.onNodeWithTag("mcp_suggestion_auth_${suggestion.id}").assertDoesNotExist()
            }
        }

        // Click Add on GitHub -> server gets added & button switches to disabled "Added"
        settingsList.performScrollToNode(hasTestTag("mcp_suggestion_row_github"))
        composeRule.onNodeWithTag("mcp_suggestion_add_github").performClick()

        composeRule.onNodeWithTag("mcp_suggestion_add_github").assertIsNotEnabled()
        assertTrue(
            vm.uiState.value.mcpServers.any {
                it.name == "GitHub" && it.url == "https://api.githubcopilot.com/mcp/"
            }
        )
    }

    private fun startLocalTerminalServer(
        yamlBody: String,
        rootfsBytes: ByteArray,
        prootBytes: ByteArray,
        shaBodyProvider: () -> String
    ): ServerSocket {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        val requestLine = reader.readLine() ?: return@use
                        while (true) {
                            val h = reader.readLine() ?: break
                            if (h.isEmpty()) break
                        }
                        val parts = requestLine.split(" ")
                        val method = parts.getOrElse(0) { "GET" }
                        val path = parts.getOrElse(1) { "/" }

                        val (code, bytes) = when {
                            path.endsWith("latest-releases.yaml") ->
                                200 to yamlBody.toByteArray(StandardCharsets.UTF_8)
                            path.endsWith(".tar.gz.sha256") ->
                                200 to shaBodyProvider().toByteArray(StandardCharsets.UTF_8)
                            path.endsWith(".tar.gz") ->
                                200 to rootfsBytes
                            path.endsWith("/proot") ->
                                200 to prootBytes
                            else -> 404 to ByteArray(0)
                        }
                        val statusText = if (code == 200) "OK" else "Not Found"
                        val out = s.getOutputStream()
                        val headers = buildString {
                            append("HTTP/1.1 $code $statusText\r\n")
                            append("Content-Length: ${bytes.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(StandardCharsets.US_ASCII)
                        out.write(headers)
                        if (method != "HEAD" && bytes.isNotEmpty()) {
                            out.write(bytes)
                        }
                        out.flush()
                    }
                }
            }
        }
        return server
    }

    private fun buildMiniRootfsTarGz(includeTraversalEntry: Boolean): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gz ->
            gz.write(tarHeader(name = "bin/", typeFlag = '5', mode = 493, size = 0))
            gz.write(tarHeader(name = "etc/", typeFlag = '5', mode = 493, size = 0))

            val busyboxContent = "#!/bin/sh\necho hi\n".toByteArray(StandardCharsets.UTF_8)
            gz.write(tarHeader(name = "bin/busybox", typeFlag = '0', mode = 493, size = busyboxContent.size.toLong()))
            gz.write(pad512(busyboxContent))

            gz.write(
                tarHeader(
                    name = "bin/sh",
                    typeFlag = '2',
                    mode = 511,
                    size = 0,
                    linkName = "/bin/busybox"
                )
            )

            val relContent = "3.20.3\n".toByteArray(StandardCharsets.UTF_8)
            gz.write(tarHeader(name = "etc/alpine-release", typeFlag = '0', mode = 420, size = relContent.size.toLong()))
            gz.write(pad512(relContent))

            if (includeTraversalEntry) {
                val evilContent = "evil".toByteArray(StandardCharsets.UTF_8)
                gz.write(
                    tarHeader(
                        name = "../../escape.txt",
                        typeFlag = '0',
                        mode = 420,
                        size = evilContent.size.toLong()
                    )
                )
                gz.write(pad512(evilContent))
            }

            gz.write(ByteArray(1024)) // EOF two zero blocks
        }
        return baos.toByteArray()
    }

    private fun tarHeader(
        name: String,
        typeFlag: Char,
        mode: Int,
        size: Long,
        linkName: String = ""
    ): ByteArray {
        val hdr = ByteArray(512)
        writeAscii(hdr, 0, 100, name)
        writeAscii(hdr, 100, 8, "%07o\u0000".format(mode))
        writeAscii(hdr, 108, 8, "%07o\u0000".format(0))
        writeAscii(hdr, 116, 8, "%07o\u0000".format(0))
        writeAscii(hdr, 124, 12, "%011o\u0000".format(size))
        writeAscii(hdr, 136, 12, "%011o\u0000".format(0))
        for (i in 148 until 156) hdr[i] = ' '.code.toByte()
        hdr[156] = typeFlag.code.toByte()
        writeAscii(hdr, 157, 100, linkName)
        writeAscii(hdr, 257, 6, "ustar\u0000")
        writeAscii(hdr, 263, 2, "00")
        var sum = 0
        for (b in hdr) sum += (b.toInt() and 0xFF)
        writeAscii(hdr, 148, 8, "%06o\u0000 ".format(sum))
        return hdr
    }

    private fun writeAscii(buf: ByteArray, offset: Int, maxLen: Int, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val count = minOf(bytes.size, maxLen)
        System.arraycopy(bytes, 0, buf, offset, count)
    }

    private fun pad512(data: ByteArray): ByteArray {
        val rem = data.size % 512
        if (rem == 0) return data
        return data + ByteArray(512 - rem)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
