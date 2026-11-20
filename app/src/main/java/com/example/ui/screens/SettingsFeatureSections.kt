package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.FallbackEntry
import com.example.model.ProviderConfig
import com.example.model.ReasoningEffort
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlossyCardReveal
import com.example.ui.components.PillSegmentedSelector
import com.example.ui.components.PocketPrimaryButton
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.WarningAmber

@Composable
fun AutoSwitchSettingsSection(
    autoSwitchEnabled: Boolean,
    fallbackOrder: List<FallbackEntry>,
    providers: List<ProviderConfig>,
    activeLimitFlagCount: Int,
    onToggleAutoSwitch: (Boolean) -> Unit,
    onOpenAddFallback: () -> Unit,
    onMoveFallbackUp: (String) -> Unit,
    onMoveFallbackDown: (String) -> Unit,
    onRemoveFallback: (String) -> Unit,
    onResetLimitFlags: () -> Unit
) {
    GlossyCardReveal(delayMillis = 60) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_auto_switch_section"),
            cornerRadius = 22.dp,
            isGlowingActive = autoSwitchEnabled
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderActive),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = BlueLightAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = "Auto-switch (when a limit is reached)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    Switch(
                        checked = autoSwitchEnabled,
                        onCheckedChange = onToggleAutoSwitch,
                        modifier = Modifier.testTag("auto_switch_toggle"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = VioletPrimary,
                            uncheckedThumbColor = BlueLightAccent,
                            uncheckedTrackColor = SurfaceElevated,
                            uncheckedBorderColor = BorderActive
                        )
                    )
                }

                Text(
                    text = "When the provider in use hits its limit, the app first tries the other saved keys of the same provider and model, then moves through the fallback order below; if every key and provider hits its limit, the app stops and tells the user.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Fallback order",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )

                    GlassOutlinedButton(
                        text = "+ Add fallback",
                        onClick = onOpenAddFallback,
                        testTag = "add_fallback_button",
                        height = 40.dp,
                        contentColor = BlueLightAccent,
                        borderColor = BorderActive
                    )
                }

                if (fallbackOrder.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No fallback providers added yet. Tap + Add fallback to set an order.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fallbackOrder.forEachIndexed { index, entry ->
                            val providerName = providers.firstOrNull { it.id == entry.providerId }?.name
                                ?: "Removed provider"
                            FallbackOrderRow(
                                index = index,
                                totalCount = fallbackOrder.size,
                                providerName = providerName,
                                model = entry.model,
                                entryId = entry.id,
                                onMoveUp = { onMoveFallbackUp(entry.id) },
                                onMoveDown = { onMoveFallbackDown(entry.id) },
                                onRemove = { onRemoveFallback(entry.id) }
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$activeLimitFlagCount key(s)/provider(s) flagged as limit reached",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (activeLimitFlagCount > 0) WarningAmber else LiveGreen,
                                    modifier = Modifier.testTag("limit_flags_count_text")
                                )
                                Text(
                                    text = "Flags clear automatically the next day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }

                        GlassOutlinedButton(
                            text = "Reset limit flags",
                            onClick = onResetLimitFlags,
                            testTag = "reset_limit_flags_button",
                            modifier = Modifier.fillMaxWidth(),
                            height = 42.dp,
                            contentColor = BlueLightAccent,
                            borderColor = BorderActive,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.RestartAlt,
                                    contentDescription = null,
                                    tint = BlueLightAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FallbackOrderRow(
    index: Int,
    totalCount: Int,
    providerName: String,
    model: String,
    entryId: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fallback_row_$entryId")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceDialog,
                border = BorderStroke(1.dp, BorderActive),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = BlueLightAccent
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = providerName,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = model,
                    style = MonoTextStyle.copy(color = TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.testTag("fallback_up_$entryId")
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Move up",
                    tint = if (index > 0) BlueLightAccent else TextMuted
                )
            }

            IconButton(
                onClick = onMoveDown,
                enabled = index < totalCount - 1,
                modifier = Modifier.testTag("fallback_down_$entryId")
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = if (index < totalCount - 1) BlueLightAccent else TextMuted
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("fallback_remove_$entryId")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Remove fallback",
                    tint = ErrorRed
                )
            }
        }
    }
}

@Composable
fun ReasoningSettingsSection(
    selectedEffort: ReasoningEffort,
    onSelectEffort: (ReasoningEffort) -> Unit
) {
    GlossyCardReveal(delayMillis = 90) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_reasoning_section"),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderActive),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = BlueLightAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = "Reasoning",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "Reasoning effort (default)",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )

                PillSegmentedSelector(
                    options = ReasoningEffort.entries.map { it to it.label },
                    selected = selectedEffort,
                    onSelect = onSelectEffort,
                    testTagPrefix = "reasoning_effort"
                )

                Text(
                    text = "This applies only to reasoning models (for example GPT-5 / o-series, Gemini, Claude with extended thinking); for other models it is ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun AddFallbackDialog(
    providers: List<ProviderConfig>,
    onDismiss: () -> Unit,
    onAdd: (providerId: String, model: String) -> Unit
) {
    val providersWithModels = remember(providers) {
        providers.filter { it.availableModels.isNotEmpty() }
    }
    var selectedProviderId by remember(providersWithModels) {
        mutableStateOf(providersWithModels.firstOrNull()?.id ?: providers.firstOrNull()?.id.orEmpty())
    }
    val currentProvider = providers.firstOrNull { it.id == selectedProviderId }
    val modelsForSelected = currentProvider?.availableModels.orEmpty()
    var selectedModel by remember(selectedProviderId, modelsForSelected) {
        mutableStateOf(modelsForSelected.firstOrNull().orEmpty())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .testTag("add_fallback_dialog"),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            isGlowingActive = true,
            borderColor = BorderNeonGlow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add fallback",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                if (providersWithModels.isEmpty()) {
                    Text(
                        text = if (providers.isEmpty()) {
                            "Add at least one provider with models first."
                        } else {
                            "No saved providers have models yet. Tap \"Fetch models\" or add custom model IDs on a provider first."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    GlassOutlinedButton(
                        text = "Close",
                        onClick = onDismiss,
                        testTag = "fallback_dialog_close_button",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "1. Choose provider",
                        style = MaterialTheme.typography.labelLarge,
                        color = BlueLightAccent
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        providersWithModels.forEach { provider ->
                            val isSelected = provider.id == selectedProviderId
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) VioletPrimary.copy(alpha = 0.24f) else SurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) BorderNeonGlow else BorderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedProviderId = provider.id }
                                    .testTag("fallback_pick_provider_${provider.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${provider.availableModels.size} models",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "2. Choose model",
                        style = MaterialTheme.typography.labelLarge,
                        color = BlueLightAccent
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 170.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modelsForSelected.forEach { modelId ->
                            val isSelected = modelId == selectedModel
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) VioletPrimary.copy(alpha = 0.26f) else SurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) BorderNeonGlow else BorderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedModel = modelId }
                                    .testTag("fallback_pick_model_$modelId")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = modelId,
                                        style = MonoTextStyle.copy(color = TextPrimary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = BlueLightAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassOutlinedButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            testTag = "fallback_dialog_cancel_button",
                            modifier = Modifier.weight(1f)
                        )
                        PocketPrimaryButton(
                            text = "Add",
                            onClick = {
                                if (selectedProviderId.isNotBlank() && selectedModel.isNotBlank()) {
                                    onAdd(selectedProviderId, selectedModel)
                                }
                            },
                            enabled = selectedProviderId.isNotBlank() && selectedModel.isNotBlank(),
                            testTag = "confirm_add_fallback_button",
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
                    }
                }
            }
        }
    }
}
