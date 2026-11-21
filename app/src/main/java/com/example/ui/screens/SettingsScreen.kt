package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.FetchModelsOutcome
import com.example.data.TestConnectionOutcome
import com.example.util.StorageUtils
import com.example.model.AppThemeMode
import com.example.model.AppUiState
import com.example.model.PermissionRuleAction
import com.example.model.PermissionRuleType
import com.example.model.ProviderApiStyle
import com.example.model.ProviderConfig
import com.example.model.ProviderOperationState
import com.example.model.ProviderPreset
import com.example.model.ReasoningEffort
import com.example.ui.components.FuturisticBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlossyCardReveal
import com.example.ui.components.PocketPrimaryButton
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: AppUiState,
    onNavigateBack: () -> Unit,
    onSaveProvider: (
        editingId: String?,
        name: String,
        subtitleLabel: String,
        baseUrl: String,
        apiStyle: ProviderApiStyle,
        isCustom: Boolean,
        apiKey: String,
        customModelIds: List<String>,
        fetchedModels: List<String>
    ) -> Unit,
    onToggleProvider: (providerId: String, enabled: Boolean) -> Unit,
    onDeleteProvider: (providerId: String) -> Unit,
    onAddProviderKey: (providerId: String, label: String, value: String) -> Unit,
    onDeleteProviderKey: (providerId: String, keyId: String) -> Unit,
    onSelectActiveKey: (providerId: String, keyId: String) -> Unit,
    onFetchModelsForProvider: (providerId: String) -> Unit,
    onClearOperationState: () -> Unit,
    onFetchModelsAdHoc: suspend (String, String, ProviderApiStyle) -> FetchModelsOutcome,
    onTestConnectionAdHoc: suspend (String, String, ProviderApiStyle) -> TestConnectionOutcome,
    onExportBackup: suspend (android.net.Uri) -> Result<Int>,
    onCreateShareBackupFile: suspend () -> Result<File>,
    onImportBackup: suspend (android.net.Uri) -> Result<Int>,
    activeLimitFlagCount: Int,
    onToggleAutoSwitch: (Boolean) -> Unit,
    onAddFallbackEntry: (providerId: String, model: String) -> Unit,
    onMoveFallbackUp: (entryId: String) -> Unit,
    onMoveFallbackDown: (entryId: String) -> Unit,
    onRemoveFallbackEntry: (entryId: String) -> Unit,
    onResetLimitFlags: () -> Unit,
    onSelectDefaultReasoningEffort: (ReasoningEffort) -> Unit,
    onToggleAutoApproveAllPermissions: (Boolean) -> Unit,
    onAddMcpServer: (name: String, url: String) -> Unit,
    onTestMcpServer: (serverId: String) -> Unit,
    onDeleteMcpServer: (serverId: String) -> Unit,
    onAddPermissionRule: (type: PermissionRuleType, pattern: String, action: PermissionRuleAction) -> Unit,
    onDeletePermissionRule: (ruleId: String) -> Unit,
    onAddCustomCommand: (name: String, prompt: String) -> Unit,
    onDeleteCustomCommand: (commandId: String) -> Unit,
    onAddSkill: (name: String, instructions: String) -> Unit,
    onDeleteSkill: (skillId: String) -> Unit,
    onToggleVoiceAnnouncement: (Boolean) -> Unit = {},
    onTestVoice: (onError: (String) -> Unit) -> Unit = {},
    onSelectThemeMode: (AppThemeMode) -> Unit = {},
    onToggleDynamicColor: (Boolean) -> Unit = {},
    onToggleShowVio: (Boolean) -> Unit = {},
    scrollToAppearanceNonce: Int = 0,
    onStorageAccessChanged: (Boolean) -> Unit = {},
    onDisconnect: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToAppearanceNonce) {
        if (scrollToAppearanceNonce > 0) {
            val appearanceIndex = 3 + uiState.providers.size.coerceAtLeast(1) + 8
            listState.animateScrollToItem(appearanceIndex)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onStorageAccessChanged(StorageUtils.hasStorageAccess(context))
    }

    var showPresetPicker by remember { mutableStateOf(false) }
    var selectedPresetForForm by remember { mutableStateOf<ProviderPreset?>(null) }
    var editingPresetProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var editingCustomProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var keysDialogProviderId by remember { mutableStateOf<String?>(null) }
    var modelsDialogProviderId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var showAddFallbackDialog by remember { mutableStateOf(false) }
    var showAddMcpServerDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showAddCommandDialog by remember { mutableStateOf(false) }
    var showAddSkillDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                onExportBackup(uri).fold(
                    onSuccess = { count ->
                        snackbarHostState.showSnackbar("Exported $count provider(s) to JSON")
                    },
                    onFailure = { err ->
                        snackbarHostState.showSnackbar("Export failed: ${err.message}")
                    }
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                onImportBackup(uri).fold(
                    onSuccess = { count ->
                        snackbarHostState.showSnackbar("Imported $count provider(s)")
                    },
                    onFailure = { err ->
                        snackbarHostState.showSnackbar("Import failed: ${err.message}")
                    }
                )
            }
        }
    }

    FuturisticBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextPrimary
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize()
                        .testTag("settings_lazy_column"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Single section: Providers & API keys
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Providers & API keys",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Add several providers at once. The provider whose switch is ON is the one in use; other providers' keys stay saved.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    // Two separate primary action buttons: "+ Add provider" and "Custom endpoint"
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PocketPrimaryButton(
                                text = "Add provider",
                                onClick = { showPresetPicker = true },
                                testTag = "add_provider_button",
                                modifier = Modifier.weight(1f),
                                height = 48.dp,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )

                            GlassOutlinedButton(
                                text = "Custom endpoint",
                                onClick = {
                                    editingCustomProvider = null
                                    showCustomDialog = true
                                },
                                testTag = "custom_endpoint_button",
                                modifier = Modifier.weight(1f),
                                height = 48.dp,
                                contentColor = BlueLightAccent,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = null,
                                        tint = BlueLightAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }

                    // Three small backup buttons: Export, Share, Import
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BackupSmallButton(
                                text = "Export",
                                icon = Icons.Filled.Upload,
                                onClick = { exportLauncher.launch("pocketcode-providers.json") },
                                testTag = "settings_export_button",
                                modifier = Modifier.weight(1f)
                            )
                            BackupSmallButton(
                                text = "Share",
                                icon = Icons.Filled.Share,
                                onClick = {
                                    scope.launch {
                                        onCreateShareBackupFile().fold(
                                            onSuccess = { file ->
                                                val shareUri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/json"
                                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                                    putExtra(Intent.EXTRA_SUBJECT, "Codvio providers backup")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(
                                                    Intent.createChooser(sendIntent, "Share providers JSON")
                                                )
                                            },
                                            onFailure = { err ->
                                                snackbarHostState.showSnackbar("Share failed: ${err.message}")
                                            }
                                        )
                                    }
                                },
                                testTag = "settings_share_button",
                                modifier = Modifier.weight(1f)
                            )
                            BackupSmallButton(
                                text = "Import",
                                icon = Icons.Filled.Download,
                                onClick = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                testTag = "settings_import_button",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Empty state or Provider Cards
                    if (uiState.providers.isEmpty()) {
                        item {
                            GlossyCardReveal(delayMillis = 50) {
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 22.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 38.dp, horizontal = 20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = SurfaceElevated,
                                            border = BorderStroke(1.dp, BorderActive),
                                            modifier = Modifier.size(54.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.Key,
                                                    contentDescription = null,
                                                    tint = BlueLightAccent,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "No provider connected yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextSecondary,
                                            modifier = Modifier.testTag("settings_empty_providers_text")
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(uiState.providers, key = { _, p -> p.id }) { index, provider ->
                            val isOperatingHere = uiState.activeOperationProviderId == provider.id
                            GlossyCardReveal(delayMillis = index * 35) {
                                ProviderCard(
                                    provider = provider,
                                    operationState = if (isOperatingHere) {
                                        uiState.providerOperationState
                                    } else {
                                        ProviderOperationState.Idle
                                    },
                                    onToggleEnabled = { enabled ->
                                        onToggleProvider(provider.id, enabled)
                                    },
                                    onEditClick = {
                                        onClearOperationState()
                                        if (provider.isCustom) {
                                            editingCustomProvider = provider
                                            showCustomDialog = true
                                        } else {
                                            editingPresetProvider = provider
                                        }
                                    },
                                    onDeleteClick = {
                                        pendingDeleteProvider = provider
                                    },
                                    onKeysClick = {
                                        keysDialogProviderId = provider.id
                                    },
                                    onFetchModelsClick = {
                                        onFetchModelsForProvider(provider.id)
                                    },
                                    onViewModelsClick = {
                                        modelsDialogProviderId = provider.id
                                    }
                                )
                            }
                        }
                    }

                    // Section 2: Auto-switch (when a limit is reached)
                    item {
                        AutoSwitchSettingsSection(
                            autoSwitchEnabled = uiState.autoSwitchEnabled,
                            fallbackOrder = uiState.fallbackOrder,
                            providers = uiState.providers,
                            activeLimitFlagCount = activeLimitFlagCount,
                            onToggleAutoSwitch = onToggleAutoSwitch,
                            onOpenAddFallback = { showAddFallbackDialog = true },
                            onMoveFallbackUp = onMoveFallbackUp,
                            onMoveFallbackDown = onMoveFallbackDown,
                            onRemoveFallback = onRemoveFallbackEntry,
                            onResetLimitFlags = {
                                onResetLimitFlags()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Limit flags cleared")
                                }
                            }
                        )
                    }

                    // Section 3: Reasoning
                    item {
                        ReasoningSettingsSection(
                            selectedEffort = uiState.defaultReasoningEffort,
                            onSelectEffort = onSelectDefaultReasoningEffort
                        )
                    }

                    // Section 4: Permissions
                    item {
                        PermissionsSettingsSection(
                            autoApproveAllPermissions = uiState.autoApproveAllPermissions,
                            onToggleAutoApprove = onToggleAutoApproveAllPermissions
                        )
                    }

                    // Section 5: MCP servers
                    item {
                        McpServersSettingsSection(
                            mcpServers = uiState.mcpServers,
                            onOpenAddServer = { showAddMcpServerDialog = true },
                            onTestServer = onTestMcpServer,
                            onDeleteServer = onDeleteMcpServer
                        )
                    }

                    // Section 6: Permission rules
                    item {
                        PermissionRulesSettingsSection(
                            rules = uiState.permissionRules,
                            onOpenAddRule = { showAddRuleDialog = true },
                            onDeleteRule = onDeletePermissionRule
                        )
                    }

                    // Section 7: Custom commands
                    item {
                        CustomCommandsSettingsSection(
                            commands = uiState.customCommands,
                            onOpenAddCommand = { showAddCommandDialog = true },
                            onDeleteCommand = onDeleteCustomCommand
                        )
                    }

                    // Section 8: Skills
                    item {
                        SkillsSettingsSection(
                            skills = uiState.skills,
                            onOpenAddSkill = { showAddSkillDialog = true },
                            onDeleteSkill = onDeleteSkill
                        )
                    }

                    // Section 9: Background work & voice
                    item {
                        BackgroundVoiceSettingsSection(
                            voiceAnnouncementEnabled = uiState.voiceAnnouncementEnabled,
                            onToggleVoiceAnnouncement = onToggleVoiceAnnouncement,
                            onTestVoice = {
                                onTestVoice { errMsg ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(errMsg)
                                    }
                                }
                            },
                            onOpenSettingsError = { errMsg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(errMsg)
                                }
                            }
                        )
                    }

                    // Section 10: Appearance
                    item {
                        AppearanceSettingsSection(
                            themeMode = uiState.themeMode,
                            dynamicColorEnabled = uiState.dynamicColorEnabled,
                            showVio = uiState.showVio,
                            onSelectThemeMode = onSelectThemeMode,
                            onToggleDynamicColor = onToggleDynamicColor,
                            onToggleShowVio = onToggleShowVio
                        )
                    }

                    // Section 11: Connection
                    item {
                        ConnectionSettingsSection(
                            activeConnectionMode = uiState.activeConnectionMode,
                            projectFolder = uiState.onDeviceProjectFolder,
                            hasStorageAccess = uiState.hasStorageAccess,
                            serverUrl = uiState.serverUrl,
                            onDisconnect = onDisconnect
                        )
                    }

                    // Section 12: About
                    item {
                        AboutSettingsSection()
                    }
                }
            }
        }
    }

    if (showAddMcpServerDialog) {
        AddMcpServerDialog(
            onDismiss = { showAddMcpServerDialog = false },
            onAdd = { name, url ->
                onAddMcpServer(name, url)
                showAddMcpServerDialog = false
            }
        )
    }

    if (showAddRuleDialog) {
        AddPermissionRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onAdd = { type, pattern, action ->
                onAddPermissionRule(type, pattern, action)
                showAddRuleDialog = false
            }
        )
    }

    if (showAddCommandDialog) {
        AddCustomCommandDialog(
            onDismiss = { showAddCommandDialog = false },
            onAdd = { name, prompt ->
                onAddCustomCommand(name, prompt)
                showAddCommandDialog = false
            }
        )
    }

    if (showAddSkillDialog) {
        AddSkillDialog(
            onDismiss = { showAddSkillDialog = false },
            onAdd = { name, instructions ->
                onAddSkill(name, instructions)
                showAddSkillDialog = false
            }
        )
    }

    if (showAddFallbackDialog) {
        AddFallbackDialog(
            providers = uiState.providers,
            onDismiss = { showAddFallbackDialog = false },
            onAdd = { providerId, model ->
                onAddFallbackEntry(providerId, model)
                showAddFallbackDialog = false
            }
        )
    }

    if (showPresetPicker) {
        PresetPickerDialog(
            onDismiss = { showPresetPicker = false },
            onSelectPreset = { preset ->
                showPresetPicker = false
                selectedPresetForForm = preset
            }
        )
    }

    selectedPresetForForm?.let { preset ->
        PresetProviderFormDialog(
            initialName = preset.name,
            initialBaseUrl = preset.baseUrl,
            subtitleLabel = preset.name,
            apiStyle = preset.apiStyle,
            onFetchModels = onFetchModelsAdHoc,
            onTestConnection = onTestConnectionAdHoc,
            onDismiss = { selectedPresetForForm = null },
            onSave = { name, baseUrl, apiKey, fetched ->
                onSaveProvider(
                    null,
                    name,
                    preset.name,
                    baseUrl,
                    preset.apiStyle,
                    false,
                    apiKey,
                    emptyList(),
                    fetched
                )
                selectedPresetForForm = null
            }
        )
    }

    editingPresetProvider?.let { provider ->
        PresetProviderFormDialog(
            initialName = provider.name,
            initialBaseUrl = provider.baseUrl,
            subtitleLabel = provider.subtitleLabel,
            apiStyle = provider.apiStyle,
            initialApiKey = provider.activeApiKey,
            initialFetchedModels = provider.fetchedModels,
            isEditing = true,
            onFetchModels = onFetchModelsAdHoc,
            onTestConnection = onTestConnectionAdHoc,
            onDismiss = { editingPresetProvider = null },
            onSave = { name, baseUrl, apiKey, fetched ->
                onSaveProvider(
                    provider.id,
                    name,
                    provider.subtitleLabel,
                    baseUrl,
                    provider.apiStyle,
                    false,
                    apiKey,
                    provider.customModelIds,
                    fetched
                )
                editingPresetProvider = null
            }
        )
    }

    if (showCustomDialog) {
        CustomProviderDialog(
            initialProvider = editingCustomProvider,
            onFetchModels = onFetchModelsAdHoc,
            onDismiss = {
                showCustomDialog = false
                editingCustomProvider = null
            },
            onSave = { name, baseUrl, modelIds, apiKey, apiStyle, fetched ->
                onSaveProvider(
                    editingCustomProvider?.id,
                    name,
                    "Custom",
                    baseUrl,
                    apiStyle,
                    true,
                    apiKey,
                    modelIds,
                    fetched
                )
                showCustomDialog = false
                editingCustomProvider = null
            }
        )
    }

    keysDialogProviderId?.let { targetId ->
        val targetProvider = uiState.providers.firstOrNull { it.id == targetId }
        if (targetProvider != null) {
            ProviderKeysDialog(
                provider = targetProvider,
                onSelectActiveKey = { keyId -> onSelectActiveKey(targetId, keyId) },
                onAddKey = { label, value -> onAddProviderKey(targetId, label, value) },
                onDeleteKey = { keyId -> onDeleteProviderKey(targetId, keyId) },
                onDismiss = { keysDialogProviderId = null }
            )
        } else {
            keysDialogProviderId = null
        }
    }

    modelsDialogProviderId?.let { targetId ->
        val targetProvider = uiState.providers.firstOrNull { it.id == targetId }
        if (targetProvider != null) {
            FetchedModelsDialog(
                providerName = targetProvider.name,
                models = targetProvider.availableModels,
                onDismiss = { modelsDialogProviderId = null }
            )
        } else {
            modelsDialogProviderId = null
        }
    }

    pendingDeleteProvider?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteProvider = null },
            containerColor = SurfaceDialog,
            shape = RoundedCornerShape(24.dp),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Delete ${target.name}?") },
            text = {
                Text("This removes ${target.name} and its saved API keys from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProvider(target.id)
                        pendingDeleteProvider = null
                    }
                ) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProvider = null }) {
                    Text("Cancel", color = BlueLightAccent)
                }
            }
        )
    }
}

@Composable
private fun BackupSmallButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    GlassOutlinedButton(
        text = text,
        onClick = onClick,
        testTag = testTag,
        modifier = modifier,
        height = 42.dp,
        contentColor = BlueLightAccent,
        borderColor = BorderSubtle,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = BlueLightAccent,
                modifier = Modifier.size(15.dp)
            )
        }
    )
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    operationState: ProviderOperationState,
    onToggleEnabled: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onKeysClick: () -> Unit,
    onFetchModelsClick: () -> Unit,
    onViewModelsClick: () -> Unit
) {
    val subtitleText = if (provider.isCustom) {
        "Custom · ${provider.baseUrl}"
    } else {
        provider.subtitleLabel
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}"),
        cornerRadius = 22.dp,
        isGlowingActive = provider.isEnabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProviderLetterAvatar(name = provider.name, size = 46.dp)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("edit_provider_${provider.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit ${provider.name}",
                                tint = BlueLightAccent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("delete_provider_${provider.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete ${provider.name}",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = LiveGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = subtitleText,
                            style = if (provider.isCustom) {
                                MonoTextStyle.copy(color = TextSecondary)
                            } else {
                                MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("provider_switch_${provider.id}"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = VioletPrimary,
                        uncheckedThumbColor = BlueLightAccent,
                        uncheckedTrackColor = SurfaceElevated,
                        uncheckedBorderColor = BorderActive
                    )
                )
            }

            // Links row: "Keys" and "Fetch models"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Keys (${provider.keys.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = BlueLightAccent,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onKeysClick)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag("provider_keys_link_${provider.id}")
                )

                Text(
                    text = "Fetch models",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = BlueLightAccent,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onFetchModelsClick)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag("provider_fetch_models_link_${provider.id}")
                )

                if (provider.availableModels.isNotEmpty()) {
                    Text(
                        text = "${provider.availableModels.size} models",
                        style = MaterialTheme.typography.labelMedium,
                        color = LiveGreen,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onViewModelsClick)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag("provider_view_models_link_${provider.id}")
                    )
                }
            }

            when (operationState) {
                is ProviderOperationState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BluePrimary
                        )
                        Text(
                            text = operationState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                is ProviderOperationState.Success -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onViewModelsClick)
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "${operationState.message} · Tap to view",
                            style = MaterialTheme.typography.bodySmall,
                            color = LiveGreen
                        )
                    }
                }
                is ProviderOperationState.Error -> {
                    Text(
                        text = operationState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
                ProviderOperationState.Idle -> Unit
            }
        }
    }
}
