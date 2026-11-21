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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AgentSkill
import com.example.model.CustomCommand
import com.example.model.McpServerConfig
import com.example.model.PermissionRule
import com.example.model.PermissionRuleAction
import com.example.model.PermissionRuleType
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
import com.example.ui.theme.LiveGreenBorder
import com.example.ui.theme.LiveGreenContainer
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceTerminal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.WarningAmber

@Composable
fun PermissionsSettingsSection(
    autoApproveAllPermissions: Boolean,
    onToggleAutoApprove: (Boolean) -> Unit
) {
    GlossyCardReveal(delayMillis = 100) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_permissions_section"),
            cornerRadius = 22.dp,
            isGlowingActive = autoApproveAllPermissions
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionHeaderRow(
                    icon = Icons.Filled.Security,
                    title = "Permissions"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-approve all permissions",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Risky: edits and shell commands run without asking",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (autoApproveAllPermissions) WarningAmber else TextSecondary
                        )
                    }

                    Switch(
                        checked = autoApproveAllPermissions,
                        onCheckedChange = onToggleAutoApprove,
                        modifier = Modifier.testTag("auto_approve_permissions_toggle"),
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

@Composable
fun McpServersSettingsSection(
    mcpServers: List<McpServerConfig>,
    onOpenAddServer: () -> Unit,
    onTestServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit
) {
    GlossyCardReveal(delayMillis = 120) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_mcp_servers_section"),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeaderRow(
                        icon = Icons.Filled.Extension,
                        title = "MCP servers",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassOutlinedButton(
                        text = "+ Add MCP server",
                        onClick = onOpenAddServer,
                        testTag = "add_mcp_server_button",
                        height = 40.dp,
                        contentColor = BlueLightAccent,
                        borderColor = BorderActive
                    )
                }

                Text(
                    text = "Remote HTTP MCP servers; their tools become available to the agent automatically during chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (mcpServers.isEmpty()) {
                    EmptySectionBox(
                        text = "No servers yet",
                        testTag = "mcp_servers_empty_text"
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        mcpServers.forEach { server ->
                            McpServerCard(
                                server = server,
                                onTestClick = { onTestServer(server.id) },
                                onDeleteClick = { onDeleteServer(server.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerCard(
    server: McpServerConfig,
    onTestClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(
            1.dp,
            if (server.isConnected) LiveGreenBorder else BorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mcp_server_card_${server.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudQueue,
                    contentDescription = null,
                    tint = if (server.isConnected) LiveGreen else BlueLightAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = server.url,
                        style = MonoTextStyle.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                GlassOutlinedButton(
                    text = if (server.isTesting) "Testing" else "Test",
                    onClick = onTestClick,
                    enabled = !server.isTesting,
                    testTag = "mcp_test_button_${server.id}",
                    height = 38.dp,
                    contentColor = BlueLightAccent,
                    borderColor = BorderActive,
                    leadingIcon = {
                        if (server.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = BlueLightAccent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = BlueLightAccent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                )

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("mcp_delete_button_${server.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete ${server.name}",
                        tint = TextSecondary
                    )
                }
            }

            server.lastTestStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = LiveGreen,
                    modifier = Modifier.testTag("mcp_status_${server.id}")
                )
            }

            server.lastTestError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    modifier = Modifier.testTag("mcp_error_${server.id}")
                )
            }
        }
    }
}

@Composable
fun PermissionRulesSettingsSection(
    rules: List<PermissionRule>,
    onOpenAddRule: () -> Unit,
    onDeleteRule: (String) -> Unit
) {
    GlossyCardReveal(delayMillis = 140) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_permission_rules_section"),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeaderRow(
                        icon = Icons.Filled.Gavel,
                        title = "Permission rules",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassOutlinedButton(
                        text = "+ Add rule",
                        onClick = onOpenAddRule,
                        testTag = "add_permission_rule_button",
                        height = 40.dp,
                        contentColor = BlueLightAccent,
                        borderColor = BorderActive
                    )
                }

                Text(
                    text = "Match file paths or shell commands with * wildcards. Last matching rule wins; rules from pocketcode.json in the project folder also apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (rules.isEmpty()) {
                    EmptySectionBox(
                        text = "No rules yet",
                        testTag = "permission_rules_empty_text"
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rules.forEach { rule ->
                            PermissionRuleRow(
                                rule = rule,
                                onDeleteClick = { onDeleteRule(rule.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRuleRow(
    rule: PermissionRule,
    onDeleteClick: () -> Unit
) {
    val actionColor = when (rule.action) {
        PermissionRuleAction.ALLOW -> LiveGreen
        PermissionRuleAction.ASK -> BlueLightAccent
        PermissionRuleAction.DENY -> ErrorRed
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_rule_card_${rule.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceDialog,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = rule.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }

            Text(
                text = rule.pattern,
                style = MonoTextStyle.copy(color = TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Surface(
                shape = CircleShape,
                color = actionColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, actionColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = rule.action.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = actionColor,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.testTag("delete_rule_${rule.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete rule",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
fun CustomCommandsSettingsSection(
    commands: List<CustomCommand>,
    onOpenAddCommand: () -> Unit,
    onDeleteCommand: (String) -> Unit
) {
    GlossyCardReveal(delayMillis = 160) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_custom_commands_section"),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeaderRow(
                        icon = Icons.Filled.Terminal,
                        title = "Custom commands",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassOutlinedButton(
                        text = "+ Add command",
                        onClick = onOpenAddCommand,
                        testTag = "add_custom_command_button",
                        height = 40.dp,
                        contentColor = BlueLightAccent,
                        borderColor = BorderActive
                    )
                }

                Text(
                    text = "Run reusable prompts with /name in chat using \$ARGUMENTS or \$1, \$2 placeholders. Also loads .pocketcode/commands/*.md from the project folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (commands.isEmpty()) {
                    EmptySectionBox(
                        text = "No custom commands yet",
                        testTag = "custom_commands_empty_text"
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        commands.forEach { cmd ->
                            CustomCommandCard(
                                command = cmd,
                                onDeleteClick = { onDeleteCommand(cmd.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomCommandCard(
    command: CustomCommand,
    onDeleteClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_command_card_${command.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "/${command.name}",
                    style = MonoTextStyle.copy(
                        color = BlueLightAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = command.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.testTag("delete_custom_command_${command.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete /${command.name}",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
fun SkillsSettingsSection(
    skills: List<AgentSkill>,
    onOpenAddSkill: () -> Unit,
    onDeleteSkill: (String) -> Unit
) {
    GlossyCardReveal(delayMillis = 180) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_skills_section"),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeaderRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Skills",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassOutlinedButton(
                        text = "+ Add skill",
                        onClick = onOpenAddSkill,
                        testTag = "add_skill_button",
                        height = 40.dp,
                        contentColor = BlueLightAccent,
                        borderColor = BorderActive
                    )
                }

                Text(
                    text = "Specialized instructions the agent can load by itself when a task matches. Also auto-detects .pocketcode/skills/<name>/SKILL.md in the project folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (skills.isEmpty()) {
                    EmptySectionBox(
                        text = "No skills added",
                        testTag = "skills_empty_text"
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        skills.forEach { skill ->
                            SkillCard(
                                skill = skill,
                                onDeleteClick = { onDeleteSkill(skill.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: AgentSkill,
    onDeleteClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("skill_card_${skill.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = skill.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (skill.isBuiltIn) {
                        Surface(
                            shape = CircleShape,
                            color = SurfaceDialog,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Text(
                                text = "Built-in",
                                style = MaterialTheme.typography.labelSmall,
                                color = BlueLightAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = skill.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.testTag("delete_skill_${skill.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete ${skill.name}",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .testTag("add_mcp_server_dialog"),
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
                    text = "Add MCP server",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Context7") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = step5TextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_name_input")
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://mcp.context7.com/mcp") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MonoTextStyle.copy(color = TextPrimary),
                    colors = step5TextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcp_url_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Quick fill:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Surface(
                        shape = CircleShape,
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderActive),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                url = "https://mcp.context7.com/mcp"
                                if (name.isBlank()) name = "Context7"
                            }
                            .testTag("mcp_quick_chip_context7")
                    ) {
                        Text(
                            text = "Context7",
                            style = MaterialTheme.typography.labelLarge,
                            color = BlueLightAccent,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                DialogButtonsRow(
                    onCancel = onDismiss,
                    confirmText = "Add",
                    confirmEnabled = name.trim().isNotEmpty() &&
                        (url.trim().startsWith("http://") || url.trim().startsWith("https://")),
                    cancelTag = "mcp_dialog_cancel_button",
                    confirmTag = "mcp_dialog_confirm_button",
                    onConfirm = { onAdd(name, url) }
                )
            }
        }
    }
}

@Composable
fun AddPermissionRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (type: PermissionRuleType, pattern: String, action: PermissionRuleAction) -> Unit
) {
    var ruleType by remember { mutableStateOf(PermissionRuleType.FILE) }
    var pattern by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(PermissionRuleAction.ALLOW) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .testTag("add_permission_rule_dialog"),
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
                    text = "Add permission rule",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                Text(
                    text = "Target type",
                    style = MaterialTheme.typography.labelLarge,
                    color = BlueLightAccent
                )

                PillSegmentedSelector(
                    options = PermissionRuleType.entries.map { it to it.label },
                    selected = ruleType,
                    onSelect = { ruleType = it },
                    testTagPrefix = "rule_type"
                )

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern (supports *)") },
                    placeholder = {
                        Text(if (ruleType == PermissionRuleType.FILE) "*.md" else "git *")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MonoTextStyle.copy(color = TextPrimary),
                    colors = step5TextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule_pattern_input")
                )

                Text(
                    text = "Action",
                    style = MaterialTheme.typography.labelLarge,
                    color = BlueLightAccent
                )

                PillSegmentedSelector(
                    options = PermissionRuleAction.entries.map { it to it.label },
                    selected = action,
                    onSelect = { action = it },
                    testTagPrefix = "rule_action"
                )

                DialogButtonsRow(
                    onCancel = onDismiss,
                    confirmText = "Add",
                    confirmEnabled = pattern.trim().isNotEmpty(),
                    cancelTag = "rule_dialog_cancel_button",
                    confirmTag = "rule_dialog_confirm_button",
                    onConfirm = { onAdd(ruleType, pattern, action) }
                )
            }
        }
    }
}

@Composable
fun AddCustomCommandDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, prompt: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .testTag("add_custom_command_dialog"),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            isGlowingActive = true,
            borderColor = BorderNeonGlow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add custom command",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.removePrefix("/") },
                    label = { Text("Name") },
                    placeholder = { Text("review") },
                    prefix = { Text("/", color = BlueLightAccent) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MonoTextStyle.copy(color = TextPrimary),
                    colors = step5TextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_command_name_input")
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt") },
                    placeholder = { Text("Review \$ARGUMENTS for bugs and edge cases.") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(16.dp),
                    colors = step5TextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_command_prompt_input")
                )

                Text(
                    text = "Supports \$ARGUMENTS (all text after command) or \$1, \$2 positional arguments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                DialogButtonsRow(
                    onCancel = onDismiss,
                    confirmText = "Add",
                    confirmEnabled = name.trim().isNotEmpty() && prompt.trim().isNotEmpty(),
                    cancelTag = "command_dialog_cancel_button",
                    confirmTag = "command_dialog_confirm_button",
                    onConfirm = { onAdd(name, prompt) }
                )
            }
        }
    }
}

@Composable
fun AddSkillDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, instructions: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .testTag("add_skill_dialog"),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            isGlowingActive = true,
            borderColor = BorderNeonGlow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add skill",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("api-migration") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = step5TextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skill_name_input")
                )

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Instructions") },
                    placeholder = { Text("Step-by-step instructions the agent follows when loading this skill...") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(16.dp),
                    colors = step5TextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skill_instructions_input")
                )

                DialogButtonsRow(
                    onCancel = onDismiss,
                    confirmText = "Add",
                    confirmEnabled = name.trim().isNotEmpty() && instructions.trim().isNotEmpty(),
                    cancelTag = "skill_dialog_cancel_button",
                    confirmTag = "skill_dialog_confirm_button",
                    onConfirm = { onAdd(name, instructions) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
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

@Composable
private fun EmptySectionBox(
    text: String,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier
                .padding(14.dp)
                .testTag(testTag)
        )
    }
}

@Composable
private fun DialogButtonsRow(
    onCancel: () -> Unit,
    confirmText: String,
    confirmEnabled: Boolean,
    cancelTag: String,
    confirmTag: String,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassOutlinedButton(
            text = "Cancel",
            onClick = onCancel,
            testTag = cancelTag,
            modifier = Modifier.weight(1f)
        )
        PocketPrimaryButton(
            text = confirmText,
            onClick = onConfirm,
            enabled = confirmEnabled,
            testTag = confirmTag,
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

@Composable
private fun step5TextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = VioletPrimary,
    unfocusedBorderColor = BorderSubtle,
    focusedLabelColor = BlueLightAccent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = BlueLightAccent,
    focusedContainerColor = SurfaceTerminal,
    unfocusedContainerColor = SurfaceTerminal
)
