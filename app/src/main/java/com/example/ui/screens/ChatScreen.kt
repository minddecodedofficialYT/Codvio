package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.ProjectWorkspace
import com.example.data.TerminalEnvironmentManager
import com.example.data.TerminalSessionController
import com.example.model.AgentConnectionMode
import com.example.model.AgentWorkMode
import com.example.model.AppUiState
import com.example.model.ChatWorkspacePanel
import com.example.model.SessionItem
import com.example.ui.components.AttachmentThumbnail
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.FloatingChatToolbarOverlay
import com.example.ui.components.FloatingWorkspaceSheetContainer
import com.example.ui.components.FuturisticBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlossyCardReveal
import com.example.ui.components.PermissionPromptDialog
import com.example.ui.components.PillDropdownChip
import com.example.ui.components.PocketCodeLogo
import com.example.ui.components.PocketPrimaryButton
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardShadowAmbient
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GlassShineTop
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.WarningAmber
import com.example.util.StorageUtils
import kotlinx.coroutines.launch

private val SUGGESTION_CHIPS = listOf(
    "Explain this project",
    "Find and fix bugs",
    "Write tests for this code",
    "/init"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    session: SessionItem,
    uiState: AppUiState,
    workspaceProvider: () -> ProjectWorkspace?,
    onOpenSession: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectWorkMode: (AgentWorkMode) -> Unit,
    onDraftMessageChange: (String) -> Unit,
    onStorageAccessChanged: (Boolean) -> Unit,
    onSendMessage: (overrideText: String?) -> Unit,
    onStopRun: () -> Unit,
    onResolvePermission: (allow: Boolean) -> Unit,
    onUndoTool: (messageId: String, toolId: String, onFeedback: (String) -> Unit) -> Unit,
    onAttachImage: (Uri, onError: (String) -> Unit) -> Unit,
    onClearAttachment: () -> Unit,
    onAddNote: (title: String, content: String) -> Unit = { _, _ -> },
    onUpdateNote: (id: String, title: String, content: String) -> Unit = { _, _, _ -> },
    onDeleteNote: (id: String) -> Unit = {},
    onAddTodo: (text: String) -> Unit = {},
    onToggleTodo: (id: String) -> Unit = {},
    onDeleteTodo: (id: String) -> Unit = {},
    terminalEnvManagerFactory: (Context) -> TerminalEnvironmentManager = { ctx -> TerminalEnvironmentManager(ctx) }
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showModelMenu by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showPlusMenu by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showWebPreview by remember { mutableStateOf(false) }
    var activePanel by remember(session.id) { mutableStateOf(ChatWorkspacePanel.CHAT) }
    var bottomInputBarHeightPx by remember { mutableIntStateOf(0) }

    val terminalEnvManager = remember(context) { terminalEnvManagerFactory(context) }
    val terminalController = remember(terminalEnvManager) {
        TerminalSessionController(terminalEnvManager)
    }

    DisposableEffect(terminalController) {
        onDispose { terminalController.dispose() }
    }

    BackHandler(enabled = activePanel != ChatWorkspacePanel.CHAT) {
        activePanel = ChatWorkspacePanel.CHAT
    }

    LaunchedEffect(session.id) {
        onOpenSession(session.id)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onStorageAccessChanged(StorageUtils.hasStorageAccess(context))
    }

    val messages = uiState.sessionMessages[session.id].orEmpty()
    val isWorking = uiState.activeStreamingSessionId == session.id
    val listState = rememberLazyListState()

    val lastMessageSignature = messages.lastOrNull()?.let {
        "${it.id}:${it.text.length}:${it.toolCalls.size}:${it.isStreaming}"
    }
    LaunchedEffect(messages.size, lastMessageSignature) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAttachImage(uri) { err ->
                scope.launch {
                    snackbarHostState.showSnackbar(err)
                }
            }
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onStorageAccessChanged(StorageUtils.hasStorageAccess(context))
    }

    var askedNotificationPermission by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val isRemoteMode = uiState.activeConnectionMode == AgentConnectionMode.REMOTE_SERVER

    fun validateAndSend(overrideText: String?) {
        val effectiveText = (overrideText ?: uiState.draftMessage).trim()
        val isEffortCommand = effectiveText.lowercase() in setOf(
            "/effort low",
            "/effort medium",
            "/effort high",
            "/effort auto"
        )
        if (isEffortCommand && (overrideText != null || uiState.pendingAttachment == null)) {
            onSendMessage(overrideText)
            return
        }
        if (isWorking) return
        if (!isRemoteMode) {
            if (!uiState.hasConnectedProvider) {
                scope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "First add and enable a provider in Settings",
                        actionLabel = "Settings",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        onOpenProviderSettings()
                    }
                }
                return
            }
            if (uiState.selectedModel.isBlank()) {
                scope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "Select a model from the model dropdown first",
                        actionLabel = "Models",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        showModelMenu = true
                    }
                }
                return
            }
        }
        if (effectiveText.isEmpty() && (overrideText != null || uiState.pendingAttachment == null)) {
            scope.launch {
                snackbarHostState.showSnackbar("Type a message or pick a suggestion")
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotificationPermission) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                askedNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        onSendMessage(overrideText)
    }

    FuturisticBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize()
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to sessions",
                                tint = TextPrimary
                            )
                        }

                        Text(
                            text = session.projectName,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        )

                        if (session.hasIndexHtml) {
                            Surface(
                                shape = CircleShape,
                                color = SurfaceCard,
                                border = BorderStroke(1.dp, BorderNeonGlow.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .padding(end = 4.dp)
                                    .clip(CircleShape)
                                    .clickable { showWebPreview = true }
                                    .testTag("chat_web_preview_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Language,
                                        contentDescription = "Preview index.html",
                                        tint = BlueLightAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Preview",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("chat_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Session settings",
                                tint = BlueLightAccent
                            )
                        }
                    }

                    // Pill dropdowns under title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            val activeProvider = uiState.activeProvider
                            val availableModels = uiState.activeProviderModels
                            val displayModelText = when {
                                isRemoteMode && uiState.selectedModel.isNotBlank() ->
                                    "remote/${uiState.selectedModel}"
                                isRemoteMode -> "remote · server"
                                uiState.selectedModel.isNotBlank() && uiState.hasConnectedProvider ->
                                    "${activeProvider?.name?.lowercase() ?: "provider"}/${uiState.selectedModel}"
                                else -> "provider/model"
                            }

                            PillDropdownChip(
                                text = displayModelText,
                                isPlaceholder = !isRemoteMode &&
                                    (uiState.selectedModel.isBlank() || !uiState.hasConnectedProvider),
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Select model",
                                        tint = BlueLightAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    if (!isRemoteMode && !uiState.hasConnectedProvider) {
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "First add and enable a provider in Settings",
                                                actionLabel = "Settings",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                onOpenProviderSettings()
                                            }
                                        }
                                    } else {
                                        showModelMenu = true
                                    }
                                },
                                testTag = "chat_model_selector"
                            )
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false },
                                containerColor = SurfaceDialog
                            ) {
                                if (isRemoteMode && availableModels.isEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Remote server: ${uiState.serverUrl}",
                                                color = BlueLightAccent
                                            )
                                        },
                                        onClick = { showModelMenu = false }
                                    )
                                } else if (availableModels.isEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "No models yet — Tap to fetch in Settings",
                                                color = BlueLightAccent
                                            )
                                        },
                                        onClick = {
                                            showModelMenu = false
                                            onOpenProviderSettings()
                                        }
                                    )
                                } else {
                                    availableModels.forEach { modelName ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = modelName,
                                                    style = MonoTextStyle.copy(
                                                        color = if (modelName == uiState.selectedModel) {
                                                            BlueLightAccent
                                                        } else {
                                                            TextPrimary
                                                        }
                                                    )
                                                )
                                            },
                                            onClick = {
                                                onSelectModel(modelName)
                                                showModelMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Box {
                            PillDropdownChip(
                                text = uiState.selectedWorkMode.label,
                                leadingContent = {
                                    Icon(
                                        imageVector = if (uiState.selectedWorkMode == AgentWorkMode.BUILD) {
                                            Icons.Filled.Build
                                        } else {
                                            Icons.Filled.Psychology
                                        },
                                        contentDescription = null,
                                        tint = BlueLightAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Select mode",
                                        tint = BlueLightAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { showModeMenu = true },
                                testTag = "chat_mode_selector"
                            )
                            DropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false },
                                containerColor = SurfaceDialog
                            ) {
                                AgentWorkMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = mode.label,
                                                color = if (mode == uiState.selectedWorkMode) {
                                                    BlueLightAccent
                                                } else {
                                                    TextPrimary
                                                }
                                            )
                                        },
                                        onClick = {
                                            onSelectWorkMode(mode)
                                            showModeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Messages or empty hero state
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            GlossyCardReveal(delayMillis = 20) {
                                PocketCodeLogo(size = 86.dp)
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "What do you want to build today?",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Type below or pick a suggestion",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(26.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SUGGESTION_CHIPS.forEachIndexed { index, suggestion ->
                                    val isSelected = uiState.draftMessage.trim() == suggestion
                                    val isCommand = suggestion.startsWith("/")

                                    GlossySuggestionChip(
                                        text = suggestion,
                                        isSelected = isSelected,
                                        isCommand = isCommand,
                                        onClick = { validateAndSend(suggestion) },
                                        testTag = "chat_suggestion_chip_$index"
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("chat_messages_list"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                ChatMessageBubble(
                                    message = msg,
                                    workingStatusText = if (msg.isStreaming) uiState.workingStatusText else null,
                                    onUndoTool = { toolId ->
                                        onUndoTool(msg.id, toolId) { feedback ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(feedback)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Bottom glossy message bar (measured so floating strip/panels never cover it)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { bottomInputBarHeightPx = it.height }
                    ) {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            cornerRadius = 28.dp,
                            borderColor = BorderActive
                        ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val pendingAttachment = uiState.pendingAttachment
                            if (pendingAttachment != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 12.dp, top = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AttachmentThumbnail(
                                        attachment = pendingAttachment,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = pendingAttachment.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = onClearAttachment,
                                        modifier = Modifier.testTag("chat_remove_attachment_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove attached image",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    IconButton(
                                        onClick = { showPlusMenu = true },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("chat_plus_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "Add attachment or command",
                                            tint = BlueLightAccent
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showPlusMenu,
                                        onDismissRequest = { showPlusMenu = false },
                                        containerColor = SurfaceDialog
                                    ) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Image,
                                                    contentDescription = null,
                                                    tint = BlueLightAccent
                                                )
                                            },
                                            text = { Text("Attach image", color = TextPrimary) },
                                            onClick = {
                                                showPlusMenu = false
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                                    )
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Terminal,
                                                    contentDescription = null,
                                                    tint = BlueLightAccent
                                                )
                                            },
                                            text = { Text("Insert /init", color = TextPrimary) },
                                            onClick = {
                                                showPlusMenu = false
                                                onDraftMessageChange("/init")
                                            }
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (uiState.draftMessage.isEmpty()) {
                                        Text(
                                            text = "Type a message...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = TextMuted
                                        )
                                    }
                                    BasicTextField(
                                        value = uiState.draftMessage,
                                        onValueChange = onDraftMessageChange,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                                        cursorBrush = SolidColor(BlueLightAccent),
                                        maxLines = 4,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("chat_message_input")
                                    )
                                }

                                if (isWorking) {
                                    GlossyStopButton(onClick = onStopRun)
                                } else {
                                    val canSend = uiState.draftMessage.isNotBlank() || uiState.pendingAttachment != null
                                    GlossySendButton(
                                        canSend = canSend,
                                        onClick = { validateAndSend(null) }
                                    )
                                }
                            }
                        }
                        }
                    }
                }

                FloatingWorkspaceSheetContainer(
                    visible = activePanel != ChatWorkspacePanel.CHAT,
                    bottomReservedPx = bottomInputBarHeightPx
                ) {
                    when (activePanel) {
                        ChatWorkspacePanel.CHAT -> Unit
                        ChatWorkspacePanel.TERMINAL -> {
                            TerminalPanel(
                                envManager = terminalEnvManager,
                                controller = terminalController,
                                projectDir = workspaceProvider()?.directDirectory,
                                onCloseToChat = { activePanel = ChatWorkspacePanel.CHAT },
                                onFeedback = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                        ChatWorkspacePanel.BROWSER -> {
                            BrowserPanel(
                                session = session,
                                workspace = workspaceProvider(),
                                onClose = { activePanel = ChatWorkspacePanel.CHAT }
                            )
                        }
                        ChatWorkspacePanel.NOTES -> {
                            NotesPanel(
                                notes = uiState.notes,
                                todos = uiState.todos,
                                onAddNote = onAddNote,
                                onUpdateNote = onUpdateNote,
                                onDeleteNote = onDeleteNote,
                                onAddTodo = onAddTodo,
                                onToggleTodo = onToggleTodo,
                                onDeleteTodo = onDeleteTodo,
                                onClose = { activePanel = ChatWorkspacePanel.CHAT }
                            )
                        }
                    }
                }

                FloatingChatToolbarOverlay(
                    activePanel = activePanel,
                    bottomReservedPx = bottomInputBarHeightPx,
                    onSelectPanel = { panel -> activePanel = panel }
                )
            }
        }
    }

    uiState.permissionPrompt?.let { prompt ->
        PermissionPromptDialog(
            prompt = prompt,
            onAllow = { onResolvePermission(true) },
            onDeny = { onResolvePermission(false) }
        )
    }

    if (showWebPreview) {
        val workspace = workspaceProvider()
        if (workspace != null) {
            WebPreviewDialog(
                workspace = workspace,
                projectName = session.projectName,
                onDismiss = { showWebPreview = false }
            )
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = SurfaceDialog,
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Project settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = session.projectName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = session.projectFolder.ifBlank { "Private app workspace" },
                            style = MonoTextStyle.copy(color = TextSecondary)
                        )
                        Text(
                            text = "Detected type: ${session.projectType.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = BlueLightAccent
                        )
                        val effectiveEffort = session.reasoningEffortOverride ?: uiState.defaultReasoningEffort
                        Text(
                            text = "Reasoning effort: ${effectiveEffort.label}" +
                                if (session.reasoningEffortOverride != null) " (session)" else " (default)",
                            style = MaterialTheme.typography.labelMedium,
                            color = BlueLightAccent
                        )
                        Text(
                            text = "Storage access: ${if (uiState.hasStorageAccess) "ON" else "OFF"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.hasStorageAccess) LiveGreen else WarningAmber
                        )
                    }
                }

                if (!uiState.hasStorageAccess) {
                    PocketPrimaryButton(
                        text = "Grant storage access",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                StorageUtils.launchAllFilesAccessSettings(context)
                            } else {
                                legacyPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        },
                        testTag = "chat_sheet_grant_storage_button"
                    )
                }

                GlassOutlinedButton(
                    text = "Open connection setup",
                    onClick = {
                        showSettingsSheet = false
                        onOpenSetup()
                    },
                    testTag = "chat_sheet_open_setup_button",
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = BlueLightAccent,
                    borderColor = BorderActive
                )
            }
        }
    }
}

@Composable
private fun GlossySuggestionChip(
    text: String,
    isSelected: Boolean,
    isCommand: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(110, easing = FastOutSlowInEasing),
        label = "chip_scale"
    )

    val shineTop = GlassShineTop

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .minimumInteractiveComponentSize()
            .then(
                if (isSelected) {
                    Modifier
                        .shadow(12.dp, CircleShape, ambientColor = VioletGlow, spotColor = VioletGlow)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(VioletPrimary, IndigoPrimary)))
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(shineTop, Color.Transparent),
                                    endY = size.height * 0.55f
                                )
                            )
                        }
                        .border(1.dp, BorderNeonGlow, CircleShape)
                } else {
                    Modifier
                        .shadow(6.dp, CircleShape, ambientColor = CardShadowAmbient, spotColor = CardShadowAmbient)
                        .clip(CircleShape)
                        .background(SurfaceCard)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                                    endY = size.height * 0.5f
                                )
                            )
                        }
                        .border(1.dp, BorderActive, CircleShape)
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = BlueLightAccent),
                onClick = onClick
            )
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = if (isCommand) {
                MonoTextStyle.copy(
                    color = if (isSelected) Color.White else BlueLightAccent,
                    fontWeight = FontWeight.Medium
                )
            } else {
                MaterialTheme.typography.labelLarge.copy(
                    color = if (isSelected) Color.White else BlueLightAccent
                )
            }
        )
    }
}

@Composable
private fun GlossySendButton(
    canSend: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(110, easing = FastOutSlowInEasing),
        label = "send_btn_scale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (canSend) 14.dp else 8.dp,
                shape = CircleShape,
                ambientColor = VioletGlow,
                spotColor = VioletGlow
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = if (canSend) {
                        listOf(VioletPrimary, IndigoPrimary)
                    } else {
                        listOf(
                            VioletPrimary.copy(alpha = 0.72f),
                            IndigoPrimary.copy(alpha = 0.72f)
                        )
                    }
                )
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
                        endY = size.height * 0.55f
                    )
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent)
                    ),
                    start = Offset(10.dp.toPx(), 1.dp.toPx()),
                    end = Offset(size.width - 10.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .border(
                width = 1.dp,
                color = if (canSend) BorderNeonGlow else BorderActive,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = Color.White),
                onClick = onClick
            )
            .testTag("chat_send_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = "Send message",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun GlossyStopButton(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(110, easing = FastOutSlowInEasing),
        label = "stop_btn_scale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(14.dp, CircleShape, ambientColor = ErrorRed, spotColor = ErrorRed)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(ErrorRed, Color(0xFFBE123C))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = Color.White),
                onClick = onClick
            )
            .testTag("chat_stop_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Stop,
            contentDescription = "Stop agent",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
