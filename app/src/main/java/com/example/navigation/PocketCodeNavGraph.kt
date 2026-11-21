package com.example.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.SessionItem
import com.example.ui.components.VioFloatingOverlay
import com.example.ui.components.VioIntroDialog
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.SessionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.viewmodel.PocketCodeViewModel

object PocketCodeRoutes {
    const val ROUTE_SETUP = "setup"
    const val ROUTE_SESSIONS = "sessions"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_CHAT = "chat/{sessionId}"

    fun chatRoute(sessionId: String): String = "chat/$sessionId"
}

@Composable
fun PocketCodeNavGraph(
    viewModel: PocketCodeViewModel = viewModel(
        factory = PocketCodeViewModel.provideFactory(LocalContext.current)
    )
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    var scrollToAppearanceNonce by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = PocketCodeRoutes.ROUTE_SETUP,
            enterTransition = {
                fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 28 }
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(230, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            }
        ) {
            composable(route = PocketCodeRoutes.ROUTE_SETUP) {
                SetupScreen(
                    uiState = uiState,
                    onSelectTab = viewModel::selectSetupTab,
                    onProjectFolderChange = viewModel::updateOnDeviceProjectFolder,
                    onStorageAccessChanged = viewModel::updateStorageAccess,
                    onServerUrlChange = viewModel::updateServerUrl,
                    onServerPasswordChange = viewModel::updateServerPassword,
                    onStartOnDevice = {
                        viewModel.startOnDevice()
                        navController.navigate(PocketCodeRoutes.ROUTE_SESSIONS)
                    },
                    onConnectRemote = {
                        viewModel.connectRemoteServer(
                            onSuccess = {
                                navController.navigate(PocketCodeRoutes.ROUTE_SESSIONS)
                            }
                        )
                    }
                )
            }

            composable(route = PocketCodeRoutes.ROUTE_SESSIONS) {
                SessionsScreen(
                    uiState = uiState,
                    onRefresh = viewModel::refreshSessions,
                    onOpenSettings = {
                        navController.navigate(PocketCodeRoutes.ROUTE_SETTINGS)
                    },
                    onCreateSession = { name, folder, uri ->
                        val created = viewModel.createSession(name, folder, uri)
                        navController.navigate(PocketCodeRoutes.chatRoute(created.id))
                    },
                    onOpenSession = { session ->
                        navController.navigate(PocketCodeRoutes.chatRoute(session.id))
                    }
                )
            }

            composable(route = PocketCodeRoutes.ROUTE_SETTINGS) {
                SettingsScreen(
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                    onSaveProvider = viewModel::saveProvider,
                    onToggleProvider = viewModel::setProviderEnabled,
                    onDeleteProvider = viewModel::deleteProvider,
                    onAddProviderKey = viewModel::addProviderKey,
                    onDeleteProviderKey = viewModel::deleteProviderKey,
                    onSelectActiveKey = viewModel::selectActiveProviderKey,
                    onFetchModelsForProvider = viewModel::fetchModelsForProvider,
                    onClearOperationState = viewModel::clearProviderOperationState,
                    onFetchModelsAdHoc = viewModel::fetchModelsAdHoc,
                    onTestConnectionAdHoc = { url, key, style ->
                        viewModel.testConnectionAdHoc(url, key, style)
                    },
                    onExportBackup = viewModel::exportBackup,
                    onCreateShareBackupFile = viewModel::createShareBackupFile,
                    onImportBackup = viewModel::importBackup,
                    activeLimitFlagCount = uiState.activeLimitFlagsCount(
                        com.example.data.ProviderJsonCodec.currentDateString()
                    ),
                    onToggleAutoSwitch = viewModel::setAutoSwitchEnabled,
                    onAddFallbackEntry = viewModel::addFallbackEntry,
                    onMoveFallbackUp = viewModel::moveFallbackEntryUp,
                    onMoveFallbackDown = viewModel::moveFallbackEntryDown,
                    onRemoveFallbackEntry = viewModel::removeFallbackEntry,
                    onResetLimitFlags = viewModel::resetLimitFlags,
                    onSelectDefaultReasoningEffort = viewModel::setDefaultReasoningEffort,
                    onToggleAutoApproveAllPermissions = viewModel::setAutoApproveAllPermissions,
                    onAddMcpServer = viewModel::addMcpServer,
                    onTestMcpServer = viewModel::testMcpServer,
                    onDeleteMcpServer = viewModel::deleteMcpServer,
                    onAddPermissionRule = viewModel::addPermissionRule,
                    onDeletePermissionRule = viewModel::deletePermissionRule,
                    onAddCustomCommand = viewModel::addCustomCommand,
                    onDeleteCustomCommand = viewModel::deleteCustomCommand,
                    onAddSkill = viewModel::addSkill,
                    onDeleteSkill = viewModel::deleteSkill,
                    onToggleVoiceAnnouncement = viewModel::setVoiceAnnouncementEnabled,
                    onTestVoice = viewModel::testVoice,
                    onSelectThemeMode = viewModel::setThemeMode,
                    onToggleDynamicColor = viewModel::setDynamicColorEnabled,
                    onToggleShowVio = viewModel::setShowVio,
                    scrollToAppearanceNonce = scrollToAppearanceNonce,
                    onStorageAccessChanged = viewModel::updateStorageAccess,
                    onDisconnect = {
                        viewModel.disconnectConnection()
                        navController.navigate(PocketCodeRoutes.ROUTE_SETUP) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = PocketCodeRoutes.ROUTE_CHAT,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntryArg ->
                val sessionId = backStackEntryArg.arguments?.getString("sessionId").orEmpty()
                val session = viewModel.getSession(sessionId) ?: SessionItem(
                    id = sessionId,
                    projectName = "Workspace",
                    projectFolder = uiState.onDeviceProjectFolder
                )

                ChatScreen(
                    session = session,
                    uiState = uiState,
                    workspaceProvider = { viewModel.workspaceForSession(sessionId) },
                    onOpenSession = viewModel::refreshWorkspaceMetadata,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSetup = {
                        navController.popBackStack(
                            route = PocketCodeRoutes.ROUTE_SETUP,
                            inclusive = false
                        )
                    },
                    onOpenProviderSettings = {
                        navController.navigate(PocketCodeRoutes.ROUTE_SETTINGS)
                    },
                    onSelectModel = viewModel::selectModel,
                    onSelectWorkMode = viewModel::selectWorkMode,
                    onDraftMessageChange = viewModel::updateDraftMessage,
                    onStorageAccessChanged = viewModel::updateStorageAccess,
                    onSendMessage = { overrideText ->
                        viewModel.sendMessage(sessionId, overrideText)
                    },
                    onStopRun = viewModel::stopCurrentRun,
                    onResolvePermission = viewModel::resolvePermissionPrompt,
                    onUndoTool = { messageId, toolId, onFeedback ->
                        viewModel.undoToolExecution(sessionId, messageId, toolId, onFeedback)
                    },
                    onAttachImage = viewModel::attachImageFromUri,
                    onClearAttachment = viewModel::clearPendingAttachment
                )
            }
        }

        if (uiState.showVio) {
            VioFloatingOverlay(
                mood = uiState.effectiveVioMood,
                sideRight = uiState.vioSideRight,
                yFraction = uiState.vioYFraction,
                onPositionChanged = viewModel::updateVioPosition,
                onHideVio = { viewModel.setShowVio(false) },
                onOpenAppearanceSettings = {
                    scrollToAppearanceNonce++
                    if (backStackEntry?.destination?.route != PocketCodeRoutes.ROUTE_SETTINGS) {
                        navController.navigate(PocketCodeRoutes.ROUTE_SETTINGS)
                    }
                }
            )
        }

        if (!uiState.isVaultStatePending && !uiState.vioIntroShown) {
            VioIntroDialog(
                showVio = uiState.showVio,
                onToggleShowVio = viewModel::setShowVio,
                onDismiss = viewModel::dismissVioIntro
            )
        }
    }
}
