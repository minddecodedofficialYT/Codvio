package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ChatAttachment
import com.example.model.ChatMessageItem
import com.example.model.ChatRole
import com.example.model.PermissionPromptState
import com.example.model.ToolExecutionItem
import com.example.model.ToolKind
import com.example.model.ToolStatus
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GlassTopHighlight
import com.example.ui.theme.IndigoPrimary
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
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import java.io.File

@Composable
fun ChatMessageBubble(
    message: ChatMessageItem,
    workingStatusText: String?,
    onUndoTool: (toolId: String) -> Unit
) {
    when (message.role) {
        ChatRole.USER -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_user_message_${message.id}"),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = VioletGlow, spotColor = VioletGlow)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    VioletPrimary.copy(alpha = 0.90f),
                                    IndigoPrimary.copy(alpha = 0.94f)
                                )
                            )
                        )
                        .border(1.dp, BorderNeonGlow.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 15.dp, vertical = 11.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (message.attachment != null) {
                            AttachmentThumbnail(
                                attachment = message.attachment,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        ChatRole.ASSISTANT -> {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_assistant_message_${message.id}"),
                cornerRadius = 20.dp,
                isGlowingActive = message.isStreaming
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (message.isStreaming) {
                        WorkingIndicatorRow(
                            statusText = workingStatusText ?: "Working..."
                        )
                    }

                    message.toolCalls.forEach { tool ->
                        ToolExecutionCard(
                            tool = tool,
                            onUndo = { onUndoTool(tool.id) }
                        )
                    }

                    if (message.text.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    } else if (message.isStreaming && message.toolCalls.isEmpty()) {
                        Text(
                            text = "Waiting for model response...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        ChatRole.ERROR -> {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_error_message"),
                cornerRadius = 20.dp,
                containerColor = ErrorContainer,
                borderColor = ErrorRed.copy(alpha = 0.65f),
                topHighlightColor = ErrorRed.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Error",
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Provider error",
                            style = MaterialTheme.typography.labelLarge,
                            color = ErrorRed
                        )
                        SelectionContainer {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        ChatRole.NOTICE -> {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_notice_message_${message.id}"),
                cornerRadius = 18.dp,
                containerColor = SurfaceElevated,
                borderColor = BorderActive
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = BlueLightAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = BlueLightAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkingIndicatorRow(statusText: String) {
    val pulseTransition = rememberInfiniteTransition(label = "working_pulse")
    val alpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "working_alpha"
    )

    Surface(
        shape = CircleShape,
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderActive),
        modifier = Modifier.testTag("chat_working_indicator")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 2.dp,
                color = BlueLightAccent
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = BlueLightAccent.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ToolExecutionCard(
    tool: ToolExecutionItem,
    onUndo: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val (statusLabel, statusColor, statusBg) = when (tool.status) {
        ToolStatus.RUNNING -> Triple("Running", BlueLightAccent, SurfaceElevated)
        ToolStatus.DONE -> Triple("Done", LiveGreen, LiveGreenContainer)
        ToolStatus.FAILED -> Triple("Failed", ErrorRed, ErrorContainer)
        ToolStatus.DENIED -> Triple("Denied", WarningAmber, WarningAmberContainer)
        ToolStatus.UNDONE -> Triple("Undone", TextSecondary, SurfaceElevated)
    }

    val toolIcon = when (tool.kind) {
        ToolKind.LIST_FILES -> Icons.Filled.FolderOpen
        ToolKind.READ_FILE -> Icons.Filled.Description
        ToolKind.SEARCH_TEXT -> Icons.Filled.Search
        ToolKind.CREATE_FILE -> Icons.Filled.NoteAdd
        ToolKind.EDIT_FILE -> Icons.Filled.Edit
        ToolKind.RUN_COMMAND -> Icons.Filled.Terminal
        ToolKind.LOAD_SKILL -> Icons.Filled.Psychology
        ToolKind.MCP_TOOL -> Icons.Filled.Extension
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderActive.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .testTag("tool_card_${tool.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = toolIcon,
                    contentDescription = tool.titleOverride ?: tool.kind.displayName,
                    tint = BlueLightAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tool.titleOverride ?: tool.kind.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = CircleShape,
                    color = statusBg,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (tool.status == ToolStatus.RUNNING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.6.dp,
                                color = statusColor
                            )
                        } else if (tool.status == ToolStatus.DONE) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse output" else "Expand output",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (tool.target.isNotBlank()) {
                Text(
                    text = tool.target,
                    style = MonoTextStyle.copy(color = BlueLightAccent),
                    maxLines = if (expanded) 4 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tool.summary.ifBlank { "Tap to inspect" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (tool.canUndo && tool.status == ToolStatus.DONE) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = SurfaceDialog,
                        border = BorderStroke(1.dp, BorderActive),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onUndo)
                            .testTag("tool_undo_button_${tool.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo change",
                                tint = BlueLightAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Undo",
                                style = MaterialTheme.typography.labelMedium,
                                color = BlueLightAccent
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded && tool.fullOutput.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceTerminal,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = tool.fullOutput,
                            style = MonoTextStyle.copy(color = TextPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionPromptDialog(
    prompt: PermissionPromptState,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    Dialog(
        onDismissRequest = onDeny,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .testTag("permission_prompt_dialog"),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            isGlowingActive = true,
            borderColor = BorderNeonGlow,
            topHighlightColor = GlassTopHighlight
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderActive),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (prompt.toolKind) {
                                    ToolKind.RUN_COMMAND -> Icons.Filled.Terminal
                                    ToolKind.CREATE_FILE, ToolKind.EDIT_FILE -> Icons.Filled.Build
                                    else -> Icons.Filled.Shield
                                },
                                contentDescription = null,
                                tint = BlueLightAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prompt.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = prompt.toolKind.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = BlueLightAccent
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = prompt.target,
                        style = MonoTextStyle.copy(color = BlueLightAccent),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceTerminal,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = prompt.previewText,
                        style = MonoTextStyle.copy(color = TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp, max = 210.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassOutlinedButton(
                        text = "Deny",
                        onClick = onDeny,
                        testTag = "permission_deny_button",
                        modifier = Modifier.weight(1f),
                        contentColor = ErrorRed,
                        borderColor = ErrorRed.copy(alpha = 0.55f)
                    )
                    PocketPrimaryButton(
                        text = "Allow",
                        onClick = onAllow,
                        testTag = "permission_allow_button",
                        modifier = Modifier.weight(1f),
                        height = 48.dp
                    )
                }
            }
        }
    }
}

@Composable
fun AttachmentThumbnail(
    attachment: ChatAttachment,
    modifier: Modifier = Modifier
) {
    val imageBitmap: ImageBitmap? = remember(attachment.id) {
        decodeAttachmentBitmap(attachment)?.asImageBitmap()
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = attachment.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, BorderActive, RoundedCornerShape(12.dp))
        )
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = modifier
        ) {
            Text(
                text = attachment.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

private fun decodeAttachmentBitmap(attachment: ChatAttachment): Bitmap? {
    attachment.localFilePath?.let { path ->
        val file = File(path)
        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
        }
    }
    if (attachment.base64Data.isNotBlank()) {
        return runCatching {
            val bytes = Base64.decode(attachment.base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    return null
}
