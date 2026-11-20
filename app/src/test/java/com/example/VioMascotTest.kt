package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProviderJsonCodec
import com.example.data.ProviderVaultSnapshot
import com.example.model.AppThemeMode
import com.example.model.AppUiState
import com.example.model.ProviderApiStyle
import com.example.model.ProviderConfig
import com.example.model.ProviderKeyEntry
import com.example.model.VioMood
import com.example.navigation.PocketCodeNavGraph
import com.example.service.AgentBackgroundCoordinator
import com.example.ui.components.VioFloatingOverlay
import com.example.ui.components.VioIntroDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.PocketCodeTheme
import com.example.viewmodel.PocketCodeViewModel
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
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class VioMascotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `provider json codec round-trips vio visibility, intro flag, and edge position`() {
        val snapshot = ProviderVaultSnapshot(
            showVio = false,
            vioIntroShown = true,
            vioSideRight = false,
            vioYFraction = 0.35f
        )
        val encoded = ProviderJsonCodec.encodeSnapshot(snapshot, pretty = true)
        val decoded = ProviderJsonCodec.decodeSnapshot(encoded)

        assertFalse(decoded.showVio)
        assertTrue(decoded.vioIntroShown)
        assertFalse(decoded.vioSideRight)
        assertEquals(0.35f, decoded.vioYFraction, 0.001f)

        // Existing/legacy vault defaults: showVio ON, intro NOT yet shown, right edge
        val legacy = ProviderJsonCodec.decodeSnapshot("""{"version":3,"providers":[]}""")
        assertTrue(legacy.showVio)
        assertFalse(legacy.vioIntroShown)
        assertTrue(legacy.vioSideRight)
        assertEquals(0.72f, legacy.vioYFraction, 0.001f)
    }

    @Test
    fun `vio reacts to real agent thinking, completion cheer, and error sad states`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        var completedNotifications = 0

        val serverThread = thread {
            // Turn 1: delayed 200 OK SSE response -> tests THINKING then CHEER
            serverSocket.accept().use { s ->
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
                Thread.sleep(160)
                val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"All done!\"}}]}\n\ndata: [DONE]\n\n"
                val out = s.getOutputStream()
                out.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${sse.length}\r\nConnection: close\r\n\r\n$sse")
                        .toByteArray()
                )
                out.flush()
            }

            // Turn 2: HTTP 500 error -> tests SAD then IDLE
            serverSocket.accept().use { s ->
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
                val errBody = """{"error":{"message":"Upstream failure"}}"""
                val out = s.getOutputStream()
                out.write(
                    ("HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json\r\nContent-Length: ${errBody.length}\r\nConnection: close\r\n\r\n$errBody")
                        .toByteArray()
                )
                out.flush()
            }
            runCatching { serverSocket.close() }
        }

        val coordinator = AgentBackgroundCoordinator(
            appContext = context,
            voiceSpeaker = null,
            serviceStarter = {},
            serviceStopper = {},
            completionNotifier = { _, _ -> completedNotifications++ },
            deviceLockedProvider = { false }
        )
        // Simulate background completion so we prove the exact same task-completion event fires both notification and Vio cheer
        coordinator.onAppLeavingOrBackgrounded()

        val viewModel = PocketCodeViewModel(
            appContext = context,
            backgroundCoordinator = coordinator,
            vioCheerDurationMs = 250L,
            vioSadDurationMs = 220L
        )
        viewModel.saveProvider(
            editingProviderId = null,
            name = "LocalMock",
            subtitleLabel = "Custom",
            baseUrl = "http://127.0.0.1:$port/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            isCustom = true,
            apiKeyInput = "sk-test",
            customModelIds = listOf("gpt-test")
        )
        viewModel.selectModel("gpt-test")
        val session = viewModel.createSession("VioTest", "", null)

        assertEquals(VioMood.IDLE, viewModel.uiState.value.effectiveVioMood)

        // Start turn 1 -> immediately THINKING
        viewModel.sendMessage(session.id, "Say hello")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(VioMood.THINKING, viewModel.uiState.value.effectiveVioMood)

        // Wait for run completion -> CHEER & task completed notification fired from same event
        val turn1Deadline = System.currentTimeMillis() + 5000
        while (viewModel.uiState.value.activeStreamingSessionId != null && System.currentTimeMillis() < turn1Deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, completedNotifications)
        assertEquals(VioMood.CHEER, viewModel.uiState.value.effectiveVioMood)

        // Advance Robolectric looper past cheer duration (250ms) to return to IDLE
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
        assertEquals(VioMood.IDLE, viewModel.uiState.value.effectiveVioMood)

        // Start turn 2 -> fails with HTTP 500 -> SAD -> returns to IDLE
        viewModel.sendMessage(session.id, "Trigger failure")
        val turn2Deadline = System.currentTimeMillis() + 5000
        while (viewModel.uiState.value.activeStreamingSessionId != null && System.currentTimeMillis() < turn2Deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, completedNotifications) // Error does NOT fire completion notification
        assertEquals(VioMood.SAD, viewModel.uiState.value.effectiveVioMood)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
        assertEquals(VioMood.IDLE, viewModel.uiState.value.effectiveVioMood)

        serverThread.join(4000)
    }

    @Test
    fun `overlay allows underlying touches, snaps on drag, and opens long-press menu`() {
        var underlyingClickedCount = 0
        var snappedSideRight: Boolean? = null
        var snappedYFraction: Float? = null
        var hiddenCalled = false
        var appearanceCalled = false

        composeRule.setContent {
            PocketCodeTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { underlyingClickedCount++ },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("underlying_action_button")
                    ) {
                        Text("Tap underneath")
                    }

                    VioFloatingOverlay(
                        mood = VioMood.IDLE,
                        sideRight = true,
                        yFraction = 0.72f,
                        onPositionChanged = { right, yFrac ->
                            snappedSideRight = right
                            snappedYFraction = yFrac
                        },
                        onHideVio = { hiddenCalled = true },
                        onOpenAppearanceSettings = { appearanceCalled = true }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vio_mascot").assertIsDisplayed()

        // Underlying screen button stays completely clickable
        composeRule.onNodeWithTag("underlying_action_button").performClick()
        assertEquals(1, underlyingClickedCount)

        // Tap Vio plays bounce without consuming underlying clicks or opening menu
        composeRule.onNodeWithTag("vio_mascot").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vio_context_menu").assertDoesNotExist()

        // Drag Vio toward the left side -> snaps to left edge
        composeRule.onNodeWithTag("vio_mascot").performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - 900f, durationMillis = 250)
        }
        composeRule.waitForIdle()
        assertEquals(false, snappedSideRight)
        assertTrue((snappedYFraction ?: -1f) in 0f..1f)

        // Long-press opens glossy menu with all 5 actions
        composeRule.onNodeWithTag("vio_mascot").performTouchInput {
            longClick(Offset(centerX, centerY))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Dance").assertIsDisplayed()
        composeRule.onNodeWithText("Do a trick").assertIsDisplayed()
        composeRule.onNodeWithText("Take a nap").assertIsDisplayed()
        composeRule.onNodeWithText("Hide Vio").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance settings").assertIsDisplayed()

        composeRule.onNodeWithTag("vio_menu_appearance").performClick()
        assertTrue(appearanceCalled)

        // Re-open menu and tap "Hide Vio"
        composeRule.onNodeWithTag("vio_mascot").performTouchInput {
            longClick(Offset(centerX, centerY))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vio_menu_hide").performClick()
        assertTrue(hiddenCalled)
    }

    @Test
    fun `single tap emits hearts, double tap spins, and action menu supports dance trick nap and task takeover`() {
        var currentMood by mutableStateOf(VioMood.IDLE)
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            PocketCodeTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    VioFloatingOverlay(
                        mood = currentMood,
                        sideRight = true,
                        yFraction = 0.72f,
                        onPositionChanged = { _, _ -> },
                        onHideVio = {},
                        onOpenAppearanceSettings = {}
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithTag("vio_mascot").assertIsDisplayed()

        // Single tap -> spawns heart particles mid-flight, then fades away cleanly
        composeRule.onNodeWithTag("vio_mascot").performTouchInput {
            click()
        }
        composeRule.mainClock.advanceTimeBy(150L)
        composeRule.onNodeWithTag("vio_particle_canvas").assertExists()

        // Wait for tap hearts to fade away
        composeRule.mainClock.advanceTimeBy(1600L)
        composeRule.onNodeWithTag("vio_particle_canvas").assertDoesNotExist()

        // Double tap -> spins happily without opening context menu
        composeRule.onNodeWithTag("vio_mascot").performTouchInput {
            doubleClick()
        }
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.onNodeWithTag("vio_context_menu").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(500L)

        fun stepFramesAndLooper(frames: Int) {
            repeat(frames) {
                composeRule.mainClock.advanceTimeByFrame()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
            }
        }

        // Long-press -> Dance (~5s with music notes/sparkles)
        composeRule.onNodeWithTag("vio_mascot").performTouchInput {
            longClick(Offset(centerX, centerY))
        }
        stepFramesAndLooper(15)
        composeRule.onNodeWithTag("vio_menu_dance").performClick()
        stepFramesAndLooper(15)
        composeRule.onNodeWithTag("vio_context_menu").assertDoesNotExist()
        composeRule.onNodeWithTag("vio_particle_canvas").assertExists()
        assertTrue(
            composeRule.onNodeWithTag("vio_mascot").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .any { it.contains("dancing") }
        )

        // Real agent task starts while dancing -> real task state takes over immediately
        currentMood = VioMood.THINKING
        stepFramesAndLooper(6)
        assertTrue(
            composeRule.onNodeWithTag("vio_mascot").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .any { it.contains("thinking") }
        )

        // Task completes -> cheer state + big shower of hearts
        currentMood = VioMood.CHEER
        stepFramesAndLooper(8)
        assertTrue(
            composeRule.onNodeWithTag("vio_mascot").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .any { it.contains("cheer") }
        )
        composeRule.onNodeWithTag("vio_particle_canvas").assertExists()

        // Return to idle and let celebration hearts finish
        currentMood = VioMood.IDLE
        composeRule.mainClock.advanceTimeBy(3200L)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // Take a nap -> stays asleep until user taps to wake up
        composeRule.onNodeWithTag("vio_mascot").performTouchInput {
            longClick(Offset(centerX, centerY))
        }
        stepFramesAndLooper(15)
        composeRule.onNodeWithTag("vio_menu_nap").performClick()
        stepFramesAndLooper(15)
        assertTrue(
            composeRule.onNodeWithTag("vio_mascot").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .any { it.contains("sleeping") }
        )
        composeRule.onNodeWithTag("vio_particle_canvas").assertExists()

        // Advance 3 seconds: stays sleeping until tapped
        composeRule.mainClock.advanceTimeBy(3000L)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            composeRule.onNodeWithTag("vio_mascot").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .any { it.contains("sleeping") }
        )

        // Tap wakes Vio up back to idle
        composeRule.onNodeWithTag("vio_mascot").performClick()
        stepFramesAndLooper(6)
        assertTrue(
            composeRule.onNodeWithTag("vio_mascot").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .any { it.contains("idle") }
        )

        // Do a trick -> trick jump then cheer landing then idle
        composeRule.onNodeWithTag("vio_mascot").performTouchInput {
            longClick(Offset(centerX, centerY))
        }
        stepFramesAndLooper(15)
        composeRule.onNodeWithTag("vio_menu_trick").performClick()
        stepFramesAndLooper(10)
        assertTrue(
            composeRule.onNodeWithTag("vio_mascot").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .any { it.contains("trick") }
        )

        composeRule.mainClock.advanceTimeBy(700L)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            composeRule.onNodeWithTag("vio_mascot").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .any { it.contains("cheer") }
        )

        composeRule.mainClock.advanceTimeBy(1600L)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            composeRule.onNodeWithTag("vio_mascot").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .any { it.contains("idle") }
        )
    }

    @Test
    fun `settings appearance Show Vio toggle hides and restores mascot`() {
        var showVio by mutableStateOf(true)

        composeRule.setContent {
            PocketCodeTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        uiState = AppUiState(
                            showVio = showVio,
                            providers = listOf(
                                ProviderConfig(
                                    id = "p1",
                                    name = "OpenAI",
                                    subtitleLabel = "OpenAI",
                                    baseUrl = "https://api.openai.com/v1",
                                    apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
                                    isCustom = false,
                                    isEnabled = true,
                                    keys = listOf(ProviderKeyEntry("k1", "Primary", "sk-1"))
                                )
                            )
                        ),
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
                        onToggleShowVio = { showVio = it }
                    )

                    if (showVio) {
                        VioFloatingOverlay(
                            mood = VioMood.IDLE,
                            sideRight = true,
                            yFraction = 0.72f,
                            onPositionChanged = { _, _ -> },
                            onHideVio = { showVio = false },
                            onOpenAppearanceSettings = {}
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vio_mascot").assertIsDisplayed()

        val list = composeRule.onNodeWithTag("settings_lazy_column")
        list.performScrollToNode(hasTestTag("show_vio_toggle"))
        composeRule.onNodeWithText("Show Vio").assertIsDisplayed()
        composeRule.onNodeWithTag("show_vio_toggle").performClick()
        composeRule.waitForIdle()

        assertFalse(showVio)
        composeRule.onNodeWithTag("vio_mascot").assertDoesNotExist()
    }

    @Test
    fun `one-time Meet Vio intro card displays once and dismisses permanently`() {
        val viewModel = PocketCodeViewModel()

        composeRule.setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PocketCodeTheme {
                PocketCodeNavGraph(viewModel = viewModel)
                // Ensure intro state reflects initial un-shown status
                assertFalse(state.isVaultStatePending)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vio_intro_card").assertIsDisplayed()
        composeRule.onNodeWithText("Meet Vio").assertIsDisplayed()
        composeRule.onNodeWithTag("vio_intro_avatar").assertIsDisplayed()
        composeRule.onNodeWithTag("vio_intro_show_toggle").assertIsDisplayed()
        composeRule.onNodeWithText("Let's go!").assertIsDisplayed()

        // Toggle Show Vio off then back on from intro card
        composeRule.onNodeWithTag("vio_intro_show_toggle").performClick()
        assertFalse(viewModel.uiState.value.showVio)
        composeRule.onNodeWithTag("vio_mascot").assertDoesNotExist()
        composeRule.onNodeWithTag("vio_intro_show_toggle").performClick()
        assertTrue(viewModel.uiState.value.showVio)

        // Dismiss intro card
        composeRule.onNodeWithTag("vio_intro_dismiss_button").performClick()
        composeRule.waitForIdle()

        assertTrue(viewModel.uiState.value.vioIntroShown)
        composeRule.onNodeWithTag("vio_intro_card").assertDoesNotExist()
        composeRule.onNodeWithTag("vio_mascot").assertIsDisplayed()
    }
}
