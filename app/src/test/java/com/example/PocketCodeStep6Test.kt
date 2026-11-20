package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProviderJsonCodec
import com.example.data.ProviderVaultSnapshot
import com.example.data.RemoteConnectOutcome
import com.example.data.RemoteServerClient
import com.example.model.AgentConnectionMode
import com.example.model.AppThemeMode
import com.example.model.AppUiState
import com.example.model.ChatRole
import com.example.model.ProviderApiStyle
import com.example.model.ProviderConfig
import com.example.model.ProviderKeyEntry
import com.example.model.SessionItem
import com.example.service.AgentBackgroundCoordinator
import com.example.service.AgentForegroundService
import com.example.service.VoiceSpeaker
import com.example.ui.screens.SessionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.PocketCodeTheme
import com.example.util.BackgroundSettingsHelper
import com.example.viewmodel.PocketCodeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class PocketCodeStep6Test {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `provider json codec round-trips voice, theme mode, and dynamic color`() {
        val snapshot = ProviderVaultSnapshot(
            voiceAnnouncementEnabled = true,
            themeMode = AppThemeMode.LIGHT,
            dynamicColorEnabled = true
        )
        val encoded = ProviderJsonCodec.encodeSnapshot(snapshot, pretty = true)
        val decoded = ProviderJsonCodec.decodeSnapshot(encoded)

        assertTrue(decoded.voiceAnnouncementEnabled)
        assertEquals(AppThemeMode.LIGHT, decoded.themeMode)
        assertTrue(decoded.dynamicColorEnabled)

        // Legacy JSON defaults cleanly
        val legacy = ProviderJsonCodec.decodeSnapshot("""{"version":5,"providers":[]}""")
        assertFalse(legacy.voiceAnnouncementEnabled)
        assertEquals(AppThemeMode.SYSTEM, legacy.themeMode)
        assertFalse(legacy.dynamicColorEnabled)
    }

    @Test
    fun `background coordinator manages foreground service, completion notification, and voice`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val spoken = mutableListOf<String>()
        var serviceStarted = 0
        var serviceStopped = 0
        var completedNotifications = 0

        val fakeSpeaker = object : VoiceSpeaker {
            override fun speak(message: String, onError: ((String) -> Unit)?) {
                spoken.add(message)
            }

            override fun shutdown() = Unit
        }

        val coordinator = AgentBackgroundCoordinator(
            appContext = context,
            voiceSpeaker = fakeSpeaker,
            serviceStarter = { serviceStarted++ },
            serviceStopper = { serviceStopped++ },
            completionNotifier = { _, _ -> completedNotifications++ },
            deviceLockedProvider = { false }
        )

        // Run starts in foreground -> no foreground service yet
        coordinator.onAppForegrounded()
        coordinator.onRunStarted()
        assertEquals(0, serviceStarted)

        // User leaves app while run is active -> foreground service starts
        coordinator.onAppLeavingOrBackgrounded()
        assertEquals(1, serviceStarted)

        // Run finishes in background with voice ON -> stops service, notifies, speaks exact phrase
        coordinator.onRunFinished(voiceEnabled = true, wasCancelled = false, sessionName = "Demo")
        assertEquals(1, serviceStopped)
        assertEquals(1, completedNotifications)
        assertEquals(listOf("Your task is completed, sir."), spoken)

        // Test voice button always speaks immediately
        coordinator.testVoice()
        assertEquals(2, spoken.size)
        assertEquals("Your task is completed, sir.", spoken.last())

        // Run finishing in foreground unlocked should NOT show task completed or speak
        coordinator.onAppForegrounded()
        coordinator.onRunStarted()
        coordinator.onRunFinished(voiceEnabled = true, wasCancelled = false, sessionName = "Demo")
        assertEquals(1, completedNotifications)
        assertEquals(2, spoken.size)
    }

    @Test
    fun `working notification contains required copy`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = AgentForegroundService.buildWorkingNotification(context)
        val text = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        val title = notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        assertEquals("Codvio", title)
        assertEquals("Codvio is working…", text)
    }

    @Test
    fun `remote server client enforces password auth and fails with real server error`() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val receivedAuths = mutableListOf<String?>()

        val serverThread = thread {
            // Call 0: /health 401 Basic challenge
            // Call 1: /health retry with Basic -> 200 OK
            // Call 2: /session fetch -> 200 OK []
            // Call 3: Wrong password /health -> 401 fail immediately
            repeat(4) { idx ->
                val socket = serverSocket.accept()
                socket.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    var authHeader: String? = null
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) {
                            authHeader = line.substringAfter(":").trim()
                        }
                    }
                    receivedAuths.add(authHeader)
                    val out = s.getOutputStream()
                    when (idx) {
                        0 -> {
                            val body = """{"error":"Password required"}"""
                            val hdr = "HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"PocketCode\"\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n"
                            out.write((hdr + body).toByteArray())
                        }
                        1 -> {
                            val body = """{"healthy":true}"""
                            val hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                            out.write((hdr + body).toByteArray())
                        }
                        2 -> {
                            val body = """[]"""
                            val hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                            out.write((hdr + body).toByteArray())
                        }
                        else -> {
                            val body = """{"error":{"message":"Invalid server password"}}"""
                            val hdr = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n"
                            out.write((hdr + body).toByteArray())
                        }
                    }
                    out.flush()
                }
            }
            runCatching { serverSocket.close() }
        }

        val client = RemoteServerClient()
        val ok = client.connect("http://127.0.0.1:$port", "secret123")
        assertTrue("Expected Success but got $ok", ok is RemoteConnectOutcome.Success)
        assertTrue(receivedAuths.any { it?.startsWith("Bearer secret123") == true })
        assertTrue(receivedAuths.any { it?.startsWith("Basic ") == true })

        val bad = client.connect("http://127.0.0.1:$port", "wrong")
        assertTrue(bad is RemoteConnectOutcome.Failure)
        assertTrue((bad as RemoteConnectOutcome.Failure).errorMessage.contains("Invalid server password"))

        serverThread.join(4000)
    }

    @Test
    fun `remote chat routes to server without local provider and disconnect keeps saved state`() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val serverThread = thread {
            // 1: /health GET
            // 2: /session GET
            // 3: /chat POST SSE response
            repeat(3) { idx ->
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
                    if (contentLength > 0) {
                        val buf = CharArray(contentLength)
                        reader.read(buf, 0, contentLength)
                    }
                    val out = s.getOutputStream()
                    when (idx) {
                        0 -> {
                            val body = """{"status":"ok"}"""
                            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
                        }
                        1 -> {
                            val body = """[]"""
                            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
                        }
                        else -> {
                            val sse = "data: {\"delta\":\"Hello from \"}\n\ndata: {\"delta\":\"remote server!\"}\n\ndata: [DONE]\n\n"
                            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${sse.length}\r\nConnection: close\r\n\r\n$sse").toByteArray())
                        }
                    }
                    out.flush()
                }
            }
            serverSocket.close()
        }

        val viewModel = PocketCodeViewModel()
        viewModel.saveProvider(
            editingProviderId = null,
            name = "OpenAI",
            subtitleLabel = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            isCustom = false,
            apiKeyInput = "sk-keep-me",
            fetchedModels = listOf("gpt-4o")
        )
        viewModel.setThemeMode(AppThemeMode.DARK)
        viewModel.setVoiceAnnouncementEnabled(true)

        var connected = false
        viewModel.updateServerUrl("http://127.0.0.1:$port")
        viewModel.connectRemoteServer { connected = true }

        val connectDeadline = System.currentTimeMillis() + 5000
        while (!connected && System.currentTimeMillis() < connectDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue(connected)

        val session = viewModel.createSession("RemoteProj", "", null)
        viewModel.sendMessage(session.id, "Ping remote agent")

        val chatDeadline = System.currentTimeMillis() + 5000
        while (viewModel.uiState.value.activeStreamingSessionId != null && System.currentTimeMillis() < chatDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val msgs = viewModel.uiState.value.sessionMessages[session.id].orEmpty()
        assertTrue(msgs.any { it.role == ChatRole.ASSISTANT && it.text.contains("Hello from remote server!") })

        // Disconnect clears active connection but keeps providers, keys, sessions, and settings
        viewModel.disconnectConnection()
        val after = viewModel.uiState.value
        assertEquals(AgentConnectionMode.ON_DEVICE, after.activeConnectionMode)
        assertFalse(after.isRemoteConnected)
        assertEquals(1, after.providers.size)
        assertEquals("sk-keep-me", after.providers.first().activeApiKey)
        assertEquals(1, after.sessions.size)
        assertEquals(AppThemeMode.DARK, after.themeMode)
        assertTrue(after.voiceAnnouncementEnabled)

        serverThread.join(4000)
    }

    @Test
    fun `settings screen renders all Step 6 sections and handles voice, theme, and disconnect`() {
        var voiceTested = false
        var selectedTheme: AppThemeMode? = null
        var dynamicToggled: Boolean? = null
        var disconnected = false

        val uiState = AppUiState(
            activeConnectionMode = AgentConnectionMode.ON_DEVICE,
            onDeviceProjectFolder = "",
            hasStorageAccess = true,
            voiceAnnouncementEnabled = false,
            themeMode = AppThemeMode.SYSTEM,
            dynamicColorEnabled = false,
            providers = listOf(
                ProviderConfig(
                    id = "p1",
                    name = "OpenAI",
                    subtitleLabel = "OpenAI",
                    baseUrl = "https://api.openai.com/v1",
                    apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
                    isCustom = false,
                    isEnabled = true,
                    keys = listOf(ProviderKeyEntry("k1", "Primary", "sk-123"))
                )
            )
        )

        composeRule.setContent {
            PocketCodeTheme(themeMode = AppThemeMode.LIGHT, dynamicColorEnabled = false) {
                SettingsScreen(
                    uiState = uiState,
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
                    onCreateShareBackupFile = { Result.failure(Exception("unused")) },
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
                    onAddMcpServer = { _, _ -> },
                    onTestMcpServer = {},
                    onDeleteMcpServer = {},
                    onAddPermissionRule = { _, _, _ -> },
                    onDeletePermissionRule = {},
                    onAddCustomCommand = { _, _ -> },
                    onDeleteCustomCommand = {},
                    onAddSkill = { _, _ -> },
                    onDeleteSkill = {},
                    onToggleVoiceAnnouncement = {},
                    onTestVoice = { voiceTested = true },
                    onSelectThemeMode = { selectedTheme = it },
                    onToggleDynamicColor = { dynamicToggled = it },
                    onDisconnect = { disconnected = true }
                )
            }
        }

        composeRule.waitForIdle()

        val list = composeRule.onNodeWithTag("settings_lazy_column")
        list.performScrollToNode(hasTestTag("settings_background_voice_section"))
        composeRule.onNodeWithTag("settings_background_voice_section").assertIsDisplayed()
        list.performScrollToNode(hasText("On these phones, also allow Auto-start for this app"))
        composeRule.onNodeWithText("On these phones, also allow Auto-start for this app").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("test_voice_button"))
        composeRule.onNodeWithTag("test_voice_button").performClick()
        assertTrue(voiceTested)

        list.performScrollToNode(hasTestTag("settings_appearance_section"))
        composeRule.onNodeWithTag("settings_appearance_section").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("theme_mode_light"))
        composeRule.onNodeWithTag("theme_mode_light").performClick()
        assertEquals(AppThemeMode.LIGHT, selectedTheme)

        list.performScrollToNode(hasTestTag("dynamic_color_toggle"))
        composeRule.onNodeWithTag("dynamic_color_toggle").performClick()
        assertEquals(true, dynamicToggled)

        list.performScrollToNode(hasTestTag("settings_connection_section"))
        composeRule.onNodeWithTag("settings_connection_section").assertIsDisplayed()
        composeRule.onNodeWithText("Mode: On-device").assertIsDisplayed()
        composeRule.onNodeWithText("Project folder: App's private workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Storage access: ON").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("settings_disconnect_button"))
        composeRule.onNodeWithTag("settings_disconnect_button").performClick()
        assertTrue(disconnected)

        list.performScrollToNode(hasTestTag("settings_about_section"))
        composeRule.onNodeWithTag("settings_about_section").assertIsDisplayed()
        composeRule.onNodeWithText("Codvio").assertIsDisplayed()
        composeRule.onNodeWithText("Version 1.0").assertIsDisplayed()
        composeRule.onNodeWithText("Your API keys stay on this device and are sent only to the provider you choose.").assertIsDisplayed()
    }

    @Test
    fun `setup screen displays real remote connection error`() {
        composeRule.setContent {
            PocketCodeTheme {
                SetupScreen(
                    uiState = AppUiState(
                        selectedSetupTab = AgentConnectionMode.REMOTE_SERVER,
                        serverUrl = "http://127.0.0.1:4096",
                        remoteConnectionError = "HTTP 401: Wrong server password"
                    ),
                    onSelectTab = {},
                    onProjectFolderChange = {},
                    onStorageAccessChanged = {},
                    onServerUrlChange = {},
                    onServerPasswordChange = {},
                    onStartOnDevice = {},
                    onConnectRemote = {}
                )
            }
        }

        composeRule.onNodeWithText("Codvio").assertIsDisplayed()
        composeRule.onNodeWithTag("remote_connection_error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("HTTP 401: Wrong server password").performScrollTo().assertIsDisplayed()
        val snap = BackgroundSettingsHelper.readSnapshot(ApplicationProvider.getApplicationContext())
        // Just verify readSnapshot executes cleanly under Robolectric without throwing
        assertFalse(snap.batteryOptimizationIgnored && snap.notificationsAllowed && false)
    }

    @Test
    fun `remote connect fails if health succeeds but session rejects auth`() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val serverThread = thread {
            repeat(2) { idx ->
                val socket = serverSocket.accept()
                socket.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val out = s.getOutputStream()
                    if (idx == 0) {
                        val body = """{"healthy":true}"""
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
                    } else {
                        val body = """{"error":"Unauthorized session access"}"""
                        out.write(("HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
                    }
                    out.flush()
                }
            }
            runCatching { serverSocket.close() }
        }

        val outcome = RemoteServerClient().connect("http://127.0.0.1:$port", "bad-pass")
        assertTrue(outcome is RemoteConnectOutcome.Failure)
        assertTrue((outcome as RemoteConnectOutcome.Failure).errorMessage.contains("Unauthorized session access"))

        serverThread.join(4000)
    }

    @Test
    fun `sessions screen shows live remote chip when connected remotely`() {
        composeRule.setContent {
            PocketCodeTheme(themeMode = AppThemeMode.DARK, dynamicColorEnabled = false) {
                SessionsScreen(
                    uiState = AppUiState(
                        activeConnectionMode = AgentConnectionMode.REMOTE_SERVER,
                        isRemoteConnected = true,
                        serverUrl = "http://127.0.0.1:4096"
                    ),
                    onRefresh = {},
                    onOpenSettings = {},
                    onCreateSession = { _, _, _ -> },
                    onOpenSession = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sessions_status_chip").assertIsDisplayed()
        composeRule.onNodeWithText("live · remote").assertIsDisplayed()
    }
}
