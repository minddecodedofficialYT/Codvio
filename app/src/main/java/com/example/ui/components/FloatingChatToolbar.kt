package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.model.ChatWorkspacePanel
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.VioletPrimary
import kotlin.math.roundToInt

@Composable
fun FloatingChatToolbarOverlay(
    activePanel: ChatWorkspacePanel,
    bottomReservedPx: Int,
    onSelectPanel: (ChatWorkspacePanel) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isCollapsed by remember { mutableStateOf(false) }
    var yFraction by remember { mutableFloatStateOf(0.28f) }
    var stripHeightPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f)
    ) {
        val topPaddingPx = with(density) { 10.dp.roundToPx() }
        val bottomGapPx = with(density) { 10.dp.roundToPx() }
        val maxY = (constraints.maxHeight - bottomReservedPx - stripHeightPx - bottomGapPx)
            .coerceAtLeast(topPaddingPx)
        val clampedY = (topPaddingPx + (maxY - topPaddingPx) * yFraction.coerceIn(0f, 1f))
            .roundToInt()
            .coerceIn(topPaddingPx, maxY)

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 6.dp)
                .offset { IntOffset(x = 0, y = clampedY) }
                .onSizeChanged { stripHeightPx = it.height }
                .pointerInput(maxY, topPaddingPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val usableRange = (maxY - topPaddingPx).coerceAtLeast(1)
                        val nextPx = (clampedY + dragAmount.y).coerceIn(topPaddingPx.toFloat(), maxY.toFloat())
                        yFraction = ((nextPx - topPaddingPx) / usableRange.toFloat()).coerceIn(0f, 1f)
                    }
                }
                .testTag("chat_floating_toolbar")
        ) {
            if (isCollapsed) {
                Surface(
                    shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 10.dp, bottomEnd = 10.dp),
                    color = SurfaceDialog,
                    border = BorderStroke(1.dp, BorderNeonGlow),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 10.dp, bottomEnd = 10.dp))
                        .clickable { isCollapsed = false }
                        .semantics { contentDescription = "Expand floating toolbar" }
                        .testTag("toolbar_expand_handle")
                ) {
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "Expand toolbar",
                            tint = BlueLightAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceDialog,
                    border = BorderStroke(1.2.dp, BorderNeonGlow),
                    modifier = Modifier
                        .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = VioletGlow, spotColor = VioletGlow)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Drag indicator handle
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Drag toolbar vertically",
                            tint = TextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(16.dp)
                                .testTag("toolbar_drag_handle")
                        )

                        ToolbarPanelIconButton(
                            panel = ChatWorkspacePanel.CHAT,
                            icon = Icons.AutoMirrored.Filled.Chat,
                            isSelected = activePanel == ChatWorkspacePanel.CHAT,
                            onClick = { onSelectPanel(ChatWorkspacePanel.CHAT) }
                        )
                        ToolbarPanelIconButton(
                            panel = ChatWorkspacePanel.TERMINAL,
                            icon = Icons.Filled.Terminal,
                            isSelected = activePanel == ChatWorkspacePanel.TERMINAL,
                            onClick = { onSelectPanel(ChatWorkspacePanel.TERMINAL) }
                        )
                        ToolbarPanelIconButton(
                            panel = ChatWorkspacePanel.BROWSER,
                            icon = Icons.Filled.Language,
                            isSelected = activePanel == ChatWorkspacePanel.BROWSER,
                            onClick = { onSelectPanel(ChatWorkspacePanel.BROWSER) }
                        )
                        ToolbarPanelIconButton(
                            panel = ChatWorkspacePanel.NOTES,
                            icon = Icons.Filled.EditNote,
                            isSelected = activePanel == ChatWorkspacePanel.NOTES,
                            onClick = { onSelectPanel(ChatWorkspacePanel.NOTES) }
                        )

                        HorizontalDivider(
                            color = BorderSubtle,
                            modifier = Modifier
                                .width(26.dp)
                                .padding(vertical = 2.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { isCollapsed = true }
                                .semantics { contentDescription = "Collapse floating toolbar" }
                                .testTag("toolbar_collapse_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Collapse toolbar",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarPanelIconButton(
    panel: ChatWorkspacePanel,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgModifier = if (isSelected) {
        Modifier
            .background(
                brush = Brush.linearGradient(listOf(VioletPrimary, IndigoPrimary)),
                shape = CircleShape
            )
            .border(1.dp, BorderNeonGlow, CircleShape)
    } else {
        Modifier
            .background(SurfaceCard.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, Color.Transparent, CircleShape)
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(bgModifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = panel.label }
            .testTag("toolbar_icon_${panel.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = panel.label,
            tint = if (isSelected) Color.White else BlueLightAccent,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun FloatingWorkspaceSheetContainer(
    visible: Boolean,
    bottomReservedPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val bottomReservedDp = with(density) { bottomReservedPx.toDp() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 6 }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 6 }),
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 10.dp,
                    top = 8.dp,
                    end = 62.dp,
                    bottom = bottomReservedDp + 6.dp
                )
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("floating_workspace_sheet"),
                cornerRadius = 22.dp,
                containerColor = SurfaceDialog,
                isGlowingActive = true,
                borderColor = BorderActive,
                content = content
            )
        }
    }
}
