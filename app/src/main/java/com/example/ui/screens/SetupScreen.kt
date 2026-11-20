package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.model.AgentConnectionMode
import com.example.model.AppUiState
import com.example.ui.components.FuturisticBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlossyCardReveal
import com.example.ui.components.PillSegmentedSelector
import com.example.ui.components.PocketCodeLogo
import com.example.ui.components.PocketPrimaryButton
import com.example.ui.theme.BlueContainer
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.LiveGreenContainer
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceTerminal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.util.StorageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SERVER_START_COMMAND = "pocketcode serve --port 4096"

@Composable
fun SetupScreen(
    uiState: AppUiState,
    onSelectTab: (AgentConnectionMode) -> Unit,
    onProjectFolderChange: (String) -> Unit,
    onStorageAccessChanged: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onServerPasswordChange: (String) -> Unit,
    onStartOnDevice: () -> Unit,
    onConnectRemote: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        onStorageAccessChanged(StorageUtils.hasStorageAccess(context))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onStorageAccessChanged(StorageUtils.hasStorageAccess(context))
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onStorageAccessChanged(StorageUtils.hasStorageAccess(context))
    }

    val openStoragePermissionFlow = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val launched = StorageUtils.launchAllFilesAccessSettings(context)
            if (!launched) {
                scope.launch {
                    snackbarHostState.showSnackbar("Unable to open Android storage settings")
                }
            }
        } else {
            val permissions = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            legacyPermissionLauncher.launch(permissions)
        }
    }

    FuturisticBackdrop(showTopGlowBand = true) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    PocketCodeLogo(size = 80.dp)

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Codvio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "AI coding agent, right on your phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    PillSegmentedSelector(
                        options = listOf(
                            AgentConnectionMode.ON_DEVICE to "On-device",
                            AgentConnectionMode.REMOTE_SERVER to "Remote server"
                        ),
                        selected = uiState.selectedSetupTab,
                        onSelect = onSelectTab,
                        testTagPrefix = "setup_tab"
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    AnimatedContent(
                        targetState = uiState.selectedSetupTab,
                        transitionSpec = {
                            fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(160, easing = FastOutSlowInEasing))
                        },
                        label = "setup_tab_content"
                    ) { tab ->
                        when (tab) {
                            AgentConnectionMode.ON_DEVICE -> {
                                OnDeviceSetupContent(
                                    projectFolder = uiState.onDeviceProjectFolder,
                                    hasStorageAccess = uiState.hasStorageAccess,
                                    onProjectFolderChange = onProjectFolderChange,
                                    onRequestStorageAccess = openStoragePermissionFlow,
                                    onStartClick = onStartOnDevice
                                )
                            }

                            AgentConnectionMode.REMOTE_SERVER -> {
                                RemoteServerSetupContent(
                                    serverUrl = uiState.serverUrl,
                                    serverPassword = uiState.serverPassword,
                                    serverUrlError = uiState.serverUrlError,
                                    isConnecting = uiState.isConnectingRemote,
                                    remoteConnectionError = uiState.remoteConnectionError,
                                    onServerUrlChange = onServerUrlChange,
                                    onServerPasswordChange = onServerPasswordChange,
                                    onConnectClick = onConnectRemote,
                                    onCopyCommand = {
                                        StorageUtils.copyToClipboard(
                                            context = context,
                                            label = "Codvio server command",
                                            text = SERVER_START_COMMAND
                                        )
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Command copied to clipboard")
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun OnDeviceSetupContent(
    projectFolder: String,
    hasStorageAccess: Boolean,
    onProjectFolderChange: (String) -> Unit,
    onRequestStorageAccess: () -> Unit,
    onStartClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        GlossyCardReveal(delayMillis = 30) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BlueContainer,
                        border = BorderStroke(1.dp, BorderActive),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "On-device info",
                                tint = BlueLightAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Runs entirely on your device",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "The whole agent runs on this phone — tools, build/plan agents, permissions, and sessions. Only one provider API key is needed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = projectFolder,
            onValueChange = onProjectFolderChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("on_device_project_folder_input"),
            label = { Text("Project folder") },
            placeholder = { Text("Leave empty for private workspace") },
            supportingText = {
                Text(
                    text = "Leaving it empty uses the app's private workspace",
                    color = TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = BlueLightAccent
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = pocketTextFieldColors()
        )

        GlossyCardReveal(delayMillis = 70) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("storage_access_button"),
                cornerRadius = 22.dp,
                borderColor = if (hasStorageAccess) LiveGreen.copy(alpha = 0.55f) else BorderActive,
                onClick = onRequestStorageAccess
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Storage,
                                    contentDescription = "Storage access",
                                    tint = if (hasStorageAccess) LiveGreen else BlueLightAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Storage access",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = if (hasStorageAccess) {
                                    "Full project folder access granted"
                                } else {
                                    "Tap to grant Android storage permission"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        shape = CircleShape,
                        color = if (hasStorageAccess) LiveGreenContainer else WarningAmberContainer,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (hasStorageAccess) {
                                LiveGreen.copy(alpha = 0.55f)
                            } else {
                                WarningAmber.copy(alpha = 0.55f)
                            }
                        )
                    ) {
                        Text(
                            text = if (hasStorageAccess) "ON" else "OFF",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (hasStorageAccess) LiveGreen else WarningAmber,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        PocketPrimaryButton(
            text = "Start",
            onClick = onStartClick,
            testTag = "setup_start_button"
        )
    }
}

@Composable
private fun RemoteServerSetupContent(
    serverUrl: String,
    serverPassword: String,
    serverUrlError: String?,
    isConnecting: Boolean,
    remoteConnectionError: String?,
    onServerUrlChange: (String) -> Unit,
    onServerPasswordChange: (String) -> Unit,
    onConnectClick: () -> Unit,
    onCopyCommand: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var copiedRecently by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_server_url_input"),
            label = { Text("Server URL") },
            placeholder = { Text("http://127.0.0.1:4096") },
            isError = serverUrlError != null,
            supportingText = serverUrlError?.let { errorMsg ->
                { Text(text = errorMsg, color = MaterialTheme.colorScheme.error) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = pocketTextFieldColors()
        )

        OutlinedTextField(
            value = serverPassword,
            onValueChange = onServerPasswordChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_server_password_input"),
            label = { Text("Password") },
            placeholder = { Text("Optional server password") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = BlueLightAccent
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.testTag("toggle_password_visibility")
                ) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = BlueLightAccent
                    )
                }
            },
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = pocketTextFieldColors()
        )

        if (!remoteConnectionError.isNullOrBlank() && remoteConnectionError != serverUrlError) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ErrorContainer,
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_connection_error")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = "Connection error",
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = remoteConnectionError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
            }
        }

        PocketPrimaryButton(
            text = if (isConnecting) "Connecting…" else "Connect",
            onClick = onConnectClick,
            enabled = !isConnecting,
            testTag = "remote_connect_button",
            leadingIcon = if (isConnecting) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }
            } else {
                null
            }
        )

        GlossyCardReveal(delayMillis = 60) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = BlueLightAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Command to start the server",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceTerminal, RoundedCornerShape(16.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = "$ $SERVER_START_COMMAND",
                            style = MonoTextStyle.copy(color = BlueLightAccent),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }

                    GlassOutlinedButton(
                        text = if (copiedRecently) "Copied" else "Copy command",
                        onClick = {
                            onCopyCommand()
                            copiedRecently = true
                            scope.launch {
                                delay(1800)
                                copiedRecently = false
                            }
                        },
                        testTag = "copy_server_command_button",
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = if (copiedRecently) LiveGreen else TextPrimary,
                        borderColor = if (copiedRecently) LiveGreen.copy(alpha = 0.55f) else BorderActive,
                        leadingIcon = {
                            Icon(
                                imageVector = if (copiedRecently) {
                                    Icons.Filled.Check
                                } else {
                                    Icons.Filled.ContentCopy
                                },
                                contentDescription = "Copy command",
                                tint = if (copiedRecently) LiveGreen else BlueLightAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun pocketTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SurfaceCard,
    unfocusedContainerColor = SurfaceCard,
    focusedBorderColor = VioletPrimary,
    unfocusedBorderColor = BorderSubtle,
    errorBorderColor = ErrorRed,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = BlueLightAccent,
    unfocusedLabelColor = TextSecondary,
    focusedPlaceholderColor = TextMuted,
    unfocusedPlaceholderColor = TextMuted,
    cursorColor = BlueLightAccent
)
