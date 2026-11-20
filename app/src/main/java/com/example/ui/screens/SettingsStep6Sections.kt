package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.BuildConfig
import com.example.model.AgentConnectionMode
import com.example.model.AppThemeMode
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlossyCardReveal
import com.example.ui.components.PillSegmentedSelector
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.LiveGreenBorder
import com.example.ui.theme.LiveGreenContainer
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.util.BackgroundSettingsHelper

@Composable
fun BackgroundVoiceSettingsSection(
    voiceAnnouncementEnabled: Boolean,
    onToggleVoiceAnnouncement: (Boolean) -> Unit,
    onTestVoice: () -> Unit,
    onOpenSettingsError: (String) -> Unit
) {
    val context = LocalContext.current
    var snapshot by remember {
        mutableStateOf(BackgroundSettingsHelper.readSnapshot(context))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        snapshot = BackgroundSettingsHelper.readSnapshot(context)
    }

    GlossyCardReveal(delayMillis = 190) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_background_voice_section"),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Step6SectionHeader(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Background work & voice"
                )

                Text(
                    text = "Keep agent runs alive when you switch apps or lock the screen, and hear an alert when a task finishes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                BackgroundStatusRow(
                    title = "Notifications",
                    isAllowed = snapshot.notificationsAllowed,
                    statusTag = "status_notifications",
                    buttonTag = "open_notifications_settings_button",
                    onOpenSettings = {
                        if (!BackgroundSettingsHelper.openNotificationSettings(context)) {
                            onOpenSettingsError("Could not open Android notification settings")
                        }
                    }
                )

                BackgroundStatusRow(
                    title = "Run in background (battery)",
                    isAllowed = snapshot.batteryOptimizationIgnored,
                    statusTag = "status_battery",
                    buttonTag = "open_battery_settings_button",
                    onOpenSettings = {
                        if (!BackgroundSettingsHelper.openBatteryOptimizationSettings(context)) {
                            onOpenSettingsError("Could not open Android battery settings")
                        }
                    }
                )

                BackgroundStatusRow(
                    title = "All files access",
                    isAllowed = snapshot.allFilesAccessAllowed,
                    statusTag = "status_all_files",
                    buttonTag = "open_all_files_settings_button",
                    onOpenSettings = {
                        if (!BackgroundSettingsHelper.openAllFilesAccessSettings(context)) {
                            onOpenSettingsError("Could not open Android file access settings")
                        }
                    }
                )

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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Xiaomi, Oppo, Vivo, Realme, Samsung",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "On these phones, also allow Auto-start for this app",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        GlassOutlinedButton(
                            text = "Open app settings",
                            onClick = {
                                if (!BackgroundSettingsHelper.openAppDetailsSettings(context)) {
                                    onOpenSettingsError("Could not open Android app settings")
                                }
                            },
                            testTag = "open_app_settings_button",
                            modifier = Modifier.fillMaxWidth(),
                            height = 44.dp,
                            contentColor = BlueLightAccent,
                            borderColor = BorderActive,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.SettingsApplications,
                                    contentDescription = null,
                                    tint = BlueLightAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = null,
                                tint = BlueLightAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Voice message when a task is done",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Speaks \"Your task is completed, sir.\" when finished in the background or locked",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = voiceAnnouncementEnabled,
                                onCheckedChange = onToggleVoiceAnnouncement,
                                modifier = Modifier.testTag("voice_announcement_toggle"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = VioletPrimary,
                                    uncheckedThumbColor = BlueLightAccent,
                                    uncheckedTrackColor = SurfaceElevated,
                                    uncheckedBorderColor = BorderActive
                                )
                            )
                        }

                        GlassOutlinedButton(
                            text = "Test voice",
                            onClick = onTestVoice,
                            testTag = "test_voice_button",
                            modifier = Modifier.fillMaxWidth(),
                            height = 44.dp,
                            contentColor = BlueLightAccent,
                            borderColor = BorderActive,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.VolumeUp,
                                    contentDescription = "Test voice",
                                    tint = BlueLightAccent,
                                    modifier = Modifier.size(17.dp)
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
private fun BackgroundStatusRow(
    title: String,
    isAllowed: Boolean,
    statusTag: String,
    buttonTag: String,
    onOpenSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, if (isAllowed) LiveGreenBorder else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Surface(
                    shape = CircleShape,
                    color = if (isAllowed) LiveGreenContainer else WarningAmberContainer,
                    border = BorderStroke(
                        1.dp,
                        if (isAllowed) LiveGreen.copy(alpha = 0.55f) else WarningAmber.copy(alpha = 0.55f)
                    )
                ) {
                    Text(
                        text = if (isAllowed) "Allowed" else "Not allowed",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAllowed) LiveGreen else WarningAmber,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag(statusTag)
                    )
                }
            }

            GlassOutlinedButton(
                text = if (isAllowed) "Settings" else "Allow",
                onClick = onOpenSettings,
                testTag = buttonTag,
                height = 42.dp,
                contentColor = BlueLightAccent,
                borderColor = BorderActive
            )
        }
    }
}

@Composable
fun AppearanceSettingsSection(
    themeMode: AppThemeMode,
    dynamicColorEnabled: Boolean,
    showVio: Boolean = true,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleShowVio: (Boolean) -> Unit = {}
) {
    GlossyCardReveal(delayMillis = 210) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_appearance_section"),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Step6SectionHeader(
                    icon = Icons.Filled.Palette,
                    title = "Appearance"
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    PillSegmentedSelector(
                        options = AppThemeMode.entries.map { it to it.label },
                        selected = themeMode,
                        onSelect = onSelectThemeMode,
                        testTagPrefix = "theme_mode"
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dynamic color",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Material You — colours from the wallpaper, Android 12+",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = dynamicColorEnabled,
                            onCheckedChange = onToggleDynamicColor,
                            modifier = Modifier.testTag("dynamic_color_toggle"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = VioletPrimary,
                                uncheckedThumbColor = BlueLightAccent,
                                uncheckedTrackColor = SurfaceElevated,
                                uncheckedBorderColor = BorderActive
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Vio",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Floating mascot that thinks and cheers with your agent",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = showVio,
                            onCheckedChange = onToggleShowVio,
                            modifier = Modifier.testTag("show_vio_toggle"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = VioletPrimary,
                                uncheckedThumbColor = BlueLightAccent,
                                uncheckedTrackColor = SurfaceElevated,
                                uncheckedBorderColor = BorderActive
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionSettingsSection(
    activeConnectionMode: AgentConnectionMode,
    projectFolder: String,
    hasStorageAccess: Boolean,
    serverUrl: String,
    onDisconnect: () -> Unit
) {
    val modeLabel = if (activeConnectionMode == AgentConnectionMode.ON_DEVICE) {
        "On-device"
    } else {
        "Remote server"
    }
    val folderLabel = projectFolder.ifBlank { "App's private workspace" }
    val storageLabel = if (hasStorageAccess) "ON" else "OFF"

    GlossyCardReveal(delayMillis = 230) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_connection_section"),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Step6SectionHeader(
                    icon = Icons.Filled.FolderOpen,
                    title = "Connection"
                )

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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Mode: $modeLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            modifier = Modifier.testTag("connection_mode_value")
                        )
                        if (activeConnectionMode == AgentConnectionMode.REMOTE_SERVER && serverUrl.isNotBlank()) {
                            Text(
                                text = "Server: $serverUrl",
                                style = MonoTextStyle.copy(color = BlueLightAccent),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Project folder: $folderLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.testTag("connection_folder_value")
                        )
                        Text(
                            text = "Storage access: $storageLabel",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasStorageAccess) LiveGreen else WarningAmber,
                            modifier = Modifier.testTag("connection_storage_value")
                        )
                    }
                }

                GlassOutlinedButton(
                    text = "Disconnect",
                    onClick = onDisconnect,
                    testTag = "settings_disconnect_button",
                    modifier = Modifier.fillMaxWidth(),
                    height = 46.dp,
                    contentColor = ErrorRed,
                    borderColor = ErrorRed.copy(alpha = 0.5f),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.LinkOff,
                            contentDescription = "Disconnect",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun AboutSettingsSection() {
    GlossyCardReveal(delayMillis = 250) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_about_section"),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Step6SectionHeader(
                    icon = Icons.Filled.Info,
                    title = "About"
                )

                Text(
                    text = "Codvio",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                    color = BlueLightAccent
                )
                Text(
                    text = "Your API keys stay on this device and are sent only to the provider you choose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun Step6SectionHeader(
    icon: ImageVector,
    title: String
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = BlueLightAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}
