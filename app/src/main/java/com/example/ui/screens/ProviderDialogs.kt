package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FetchModelsOutcome
import com.example.data.TestConnectionOutcome
import com.example.model.ProviderApiStyle
import com.example.model.ProviderConfig
import com.example.model.ProviderPreset
import com.example.model.ProviderPresets
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.PillSegmentedSelector
import com.example.ui.components.PocketPrimaryButton
import com.example.ui.theme.BlueContainer
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.LiveGreenContainer
import com.example.ui.theme.LiveGreenBorder
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceTerminal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.VioletPrimary
import com.example.util.StorageUtils
import kotlinx.coroutines.launch

@Composable
fun PresetPickerDialog(
    onDismiss: () -> Unit,
    onSelectPreset: (ProviderPreset) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            borderColor = BorderActive
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Choose provider",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = BlueLightAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ProviderPresets.officialPresets, key = { it.id }) { preset ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onSelectPreset(preset) }
                                .testTag("preset_item_${preset.id}"),
                            shape = RoundedCornerShape(20.dp),
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProviderLetterAvatar(name = preset.name, size = 40.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = preset.baseUrl,
                                        style = MonoTextStyle.copy(color = TextSecondary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Select ${preset.name}",
                                    tint = BlueLightAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresetProviderFormDialog(
    initialName: String,
    initialBaseUrl: String,
    subtitleLabel: String,
    apiStyle: ProviderApiStyle,
    initialApiKey: String = "",
    initialFetchedModels: List<String> = emptyList(),
    isEditing: Boolean = false,
    onFetchModels: suspend (baseUrl: String, apiKey: String, apiStyle: ProviderApiStyle) -> FetchModelsOutcome,
    onTestConnection: suspend (baseUrl: String, apiKey: String, apiStyle: ProviderApiStyle) -> TestConnectionOutcome,
    onDismiss: () -> Unit,
    onSave: (name: String, baseUrl: String, apiKey: String, fetchedModels: List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialName) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf(initialFetchedModels) }

    var isBusy by remember { mutableStateOf(false) }
    var statusIsError by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            borderColor = BorderActive
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isEditing) "Edit $subtitleLabel" else "Add $subtitleLabel",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_form_name_input"),
                    label = { Text("Provider name") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = pocketTextFieldColors()
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_form_url_input"),
                    label = { Text("Base URL") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = pocketTextFieldColors()
                )

                ApiKeyInputField(
                    label = "API key",
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    showKey = showKey,
                    onToggleShow = { showKey = !showKey },
                    onPaste = {
                        StorageUtils.pasteFromClipboard(context)?.let { pasted ->
                            apiKey = pasted
                        }
                    },
                    testTag = "preset_form_key_input"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassOutlinedButton(
                        text = "Fetch models",
                        onClick = {
                            scope.launch {
                                isBusy = true
                                statusMessage = null
                                when (val result = onFetchModels(baseUrl, apiKey, apiStyle)) {
                                    is FetchModelsOutcome.Success -> {
                                        fetchedModels = result.models
                                        statusIsError = false
                                        statusMessage = "Loaded ${result.models.size} models (${result.latencyMs} ms)"
                                    }
                                    is FetchModelsOutcome.Failure -> {
                                        statusIsError = true
                                        statusMessage = result.errorMessage
                                    }
                                }
                                isBusy = false
                            }
                        },
                        enabled = !isBusy && baseUrl.isNotBlank(),
                        testTag = "preset_form_fetch_models_button",
                        modifier = Modifier.weight(1f),
                        contentColor = BlueLightAccent
                    )

                    GlassOutlinedButton(
                        text = "Test connection",
                        onClick = {
                            scope.launch {
                                isBusy = true
                                statusMessage = null
                                when (val result = onTestConnection(baseUrl, apiKey, apiStyle)) {
                                    is TestConnectionOutcome.Success -> {
                                        statusIsError = false
                                        statusMessage = result.message
                                    }
                                    is TestConnectionOutcome.Failure -> {
                                        statusIsError = true
                                        statusMessage = result.errorMessage
                                    }
                                }
                                isBusy = false
                            }
                        },
                        enabled = !isBusy && baseUrl.isNotBlank(),
                        testTag = "preset_form_test_button",
                        modifier = Modifier.weight(1f),
                        contentColor = TextPrimary
                    )
                }

                if (isBusy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = VioletPrimary
                        )
                        Text(
                            text = "Contacting provider...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                statusMessage?.let { msg ->
                    StatusBanner(message = msg, isError = statusIsError)
                }

                if (fetchedModels.isNotEmpty()) {
                    ModelsPreviewBox(models = fetchedModels)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        testTag = "preset_form_cancel_button",
                        modifier = Modifier.weight(1f),
                        contentColor = TextSecondary
                    )

                    PocketPrimaryButton(
                        text = if (isEditing) "Save" else "Add",
                        onClick = { onSave(name, baseUrl, apiKey, fetchedModels) },
                        enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                        testTag = "preset_form_add_button",
                        modifier = Modifier.weight(1f),
                        height = 48.dp
                    )
                }
            }
        }
    }
}

@Composable
fun CustomProviderDialog(
    initialProvider: ProviderConfig? = null,
    onFetchModels: suspend (baseUrl: String, apiKey: String, apiStyle: ProviderApiStyle) -> FetchModelsOutcome,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        baseUrl: String,
        modelIds: List<String>,
        apiKey: String,
        apiStyle: ProviderApiStyle,
        fetchedModels: List<String>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialProvider?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(initialProvider?.baseUrl.orEmpty()) }
    var modelIdsText by remember {
        mutableStateOf(initialProvider?.customModelIds?.joinToString(", ").orEmpty())
    }
    var apiKey by remember { mutableStateOf(initialProvider?.activeApiKey.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }
    var apiStyle by remember {
        mutableStateOf(
            if (initialProvider?.apiStyle == ProviderApiStyle.ANTHROPIC) {
                ProviderApiStyle.ANTHROPIC
            } else {
                ProviderApiStyle.OPENAI_COMPATIBLE
            }
        )
    }
    var fetchedModels by remember { mutableStateOf(initialProvider?.fetchedModels.orEmpty()) }
    var isBusy by remember { mutableStateOf(false) }
    var statusIsError by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            borderColor = BorderActive
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Custom provider",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                // Preset chips: Ollama & LM Studio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProviderPresets.customEndpointPresets.forEach { preset ->
                        val selected = name == preset.name && baseUrl == preset.baseUrl
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .then(
                                    if (selected) {
                                        Modifier
                                            .shadow(10.dp, CircleShape, ambientColor = VioletGlow, spotColor = VioletGlow)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(VioletPrimary, IndigoPrimary)))
                                            .border(1.dp, BorderNeonGlow, CircleShape)
                                    } else {
                                        Modifier
                                            .clip(CircleShape)
                                            .background(SurfaceElevated)
                                            .border(1.dp, BorderActive, CircleShape)
                                    }
                                )
                                .clickable {
                                    name = preset.name
                                    baseUrl = preset.baseUrl
                                    apiStyle = preset.apiStyle
                                    apiKey = ""
                                }
                                .testTag("custom_preset_chip_${preset.id}")
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.White else BlueLightAccent
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_provider_name_input"),
                    label = { Text("Name") },
                    placeholder = { Text("Ollama") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = pocketTextFieldColors()
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_provider_url_input"),
                    label = { Text("Base URL") },
                    placeholder = { Text("http://127.0.0.1:11434/v1") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = pocketTextFieldColors()
                )

                OutlinedTextField(
                    value = modelIdsText,
                    onValueChange = { modelIdsText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_provider_models_input"),
                    label = { Text("Model IDs (comma separated)") },
                    placeholder = { Text("qwen2.5-coder:7b, llama3.2") },
                    shape = RoundedCornerShape(20.dp),
                    colors = pocketTextFieldColors()
                )

                ApiKeyInputField(
                    label = "API key (optional)",
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    showKey = showKey,
                    onToggleShow = { showKey = !showKey },
                    onPaste = {
                        StorageUtils.pasteFromClipboard(context)?.let { pasted ->
                            apiKey = pasted
                        }
                    },
                    testTag = "custom_provider_key_input"
                )

                GlassOutlinedButton(
                    text = "Fetch models",
                    onClick = {
                        scope.launch {
                            isBusy = true
                            statusMessage = null
                            when (val result = onFetchModels(baseUrl, apiKey, apiStyle)) {
                                is FetchModelsOutcome.Success -> {
                                    fetchedModels = result.models
                                    modelIdsText = result.models.joinToString(", ")
                                    statusIsError = false
                                    statusMessage = "Loaded ${result.models.size} models (${result.latencyMs} ms)"
                                }
                                is FetchModelsOutcome.Failure -> {
                                    statusIsError = true
                                    statusMessage = result.errorMessage
                                }
                            }
                            isBusy = false
                        }
                    },
                    enabled = !isBusy && baseUrl.isNotBlank(),
                    testTag = "custom_provider_fetch_models_button",
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = BlueLightAccent
                )

                if (isBusy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = VioletPrimary
                        )
                        Text(
                            text = "Fetching models...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                statusMessage?.let { msg ->
                    StatusBanner(message = msg, isError = statusIsError)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "API style",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    PillSegmentedSelector(
                        options = listOf(
                            ProviderApiStyle.OPENAI_COMPATIBLE to "OpenAI-compatible",
                            ProviderApiStyle.ANTHROPIC to "Anthropic"
                        ),
                        selected = apiStyle,
                        onSelect = { apiStyle = it },
                        testTagPrefix = "custom_api_style"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        testTag = "custom_provider_cancel_button",
                        modifier = Modifier.weight(1f),
                        contentColor = TextSecondary
                    )

                    PocketPrimaryButton(
                        text = if (initialProvider == null) "Add" else "Save",
                        onClick = {
                            val parsedModels = modelIdsText
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .distinct()
                            onSave(name, baseUrl, parsedModels, apiKey, apiStyle, fetchedModels)
                        },
                        enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                        testTag = "custom_provider_add_button",
                        modifier = Modifier.weight(1f),
                        height = 48.dp
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderKeysDialog(
    provider: ProviderConfig,
    onSelectActiveKey: (keyId: String) -> Unit,
    onAddKey: (label: String, keyValue: String) -> Unit,
    onDeleteKey: (keyId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var newLabel by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    var showNewKey by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            borderColor = BorderActive
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${provider.name} keys",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Keys stay saved in encrypted storage even when inactive",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = BlueLightAccent
                        )
                    }
                }

                if (provider.keys.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = BlueLightAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "No keys saved for this provider yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        provider.keys.forEach { entry ->
                            val isActive = entry.id == provider.activeKeyEntry?.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onSelectActiveKey(entry.id) },
                                shape = RoundedCornerShape(18.dp),
                                color = SurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isActive) BorderNeonGlow else BorderSubtle
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isActive,
                                        onClick = { onSelectActiveKey(entry.id) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = VioletPrimary,
                                            unselectedColor = TextMuted
                                        )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = maskApiKey(entry.value),
                                            style = MonoTextStyle.copy(color = TextSecondary)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteKey(entry.id) },
                                        modifier = Modifier.testTag("delete_key_${entry.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete key ${entry.label}",
                                            tint = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderSubtle)

                Text(
                    text = "Add another key",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keys_dialog_label_input"),
                    label = { Text("Label (optional)") },
                    placeholder = { Text("Work / Personal / Backup") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = pocketTextFieldColors()
                )

                ApiKeyInputField(
                    label = "API key",
                    value = newKey,
                    onValueChange = { newKey = it },
                    showKey = showNewKey,
                    onToggleShow = { showNewKey = !showNewKey },
                    onPaste = {
                        StorageUtils.pasteFromClipboard(context)?.let { pasted ->
                            newKey = pasted
                        }
                    },
                    testTag = "keys_dialog_value_input"
                )

                PocketPrimaryButton(
                    text = "Add key",
                    onClick = {
                        onAddKey(newLabel, newKey)
                        newLabel = ""
                        newKey = ""
                    },
                    enabled = newKey.isNotBlank(),
                    testTag = "keys_dialog_add_button",
                    height = 48.dp
                )
            }
        }
    }
}

@Composable
fun FetchedModelsDialog(
    providerName: String,
    models: List<String>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            borderColor = BorderActive
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "$providerName models",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "${models.size} models returned",
                            style = MaterialTheme.typography.bodySmall,
                            color = LiveGreen
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = BlueLightAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(models) { modelId ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceTerminal,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Text(
                                text = modelId,
                                style = MonoTextStyle.copy(color = BlueLightAccent),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderLetterAvatar(
    name: String,
    size: Dp = 44.dp
) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P"
    Box(
        modifier = Modifier
            .size(size)
            .shadow(8.dp, CircleShape, ambientColor = VioletGlow, spotColor = VioletGlow)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(VioletPrimary.copy(alpha = 0.7f), IndigoPrimary.copy(alpha = 0.85f))))
            .border(1.dp, BorderNeonGlow.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun ApiKeyInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    showKey: Boolean,
    onToggleShow: () -> Unit,
    onPaste: () -> Unit,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            label = { Text(label) },
            placeholder = { Text("sk-...") },
            visualTransformation = if (showKey) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = pocketTextFieldColors()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onToggleShow,
                modifier = Modifier.testTag("${testTag}_show_button")
            ) {
                Icon(
                    imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = BlueLightAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showKey) "Hide" else "Show",
                    color = BlueLightAccent
                )
            }
            TextButton(
                onClick = onPaste,
                modifier = Modifier.testTag("${testTag}_paste_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = null,
                    tint = BlueLightAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Paste",
                    color = BlueLightAccent
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    isError: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isError) ErrorContainer else LiveGreenContainer,
        border = BorderStroke(
            1.dp,
            if (isError) ErrorRed.copy(alpha = 0.55f) else LiveGreenBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isError) ErrorRed else LiveGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) ErrorRed else LiveGreen
            )
        }
    }
}

@Composable
private fun ModelsPreviewBox(models: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceTerminal,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Models (${models.size})",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            models.take(5).forEach { id ->
                Text(
                    text = id,
                    style = MonoTextStyle.copy(color = BlueLightAccent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (models.size > 5) {
                Text(
                    text = "+ ${models.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

private fun maskApiKey(key: String): String {
    val trimmed = key.trim()
    return if (trimmed.length <= 8) {
        "••••••••"
    } else {
        "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
    }
}
