package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AgentConnectionMode
import com.example.model.AppUiState
import com.example.model.SessionItem
import com.example.ui.components.FuturisticBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.GlossyCardReveal
import com.example.ui.components.LiveStatusChip
import com.example.ui.components.PocketCodeLogo
import com.example.ui.theme.BlueContainer
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.LiveGreenBorder
import com.example.ui.theme.LiveGreenContainer
import com.example.ui.theme.LiveGreenHighlight
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    uiState: AppUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateSession: (projectName: String, projectFolder: String, folderUri: String?) -> Unit,
    onOpenSession: (SessionItem) -> Unit
) {
    var showNewSessionDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing)
        ),
        label = "refresh_angle"
    )

    val statusText = if (uiState.activeConnectionMode == AgentConnectionMode.ON_DEVICE) {
        "live · on-device"
    } else {
        "live · remote"
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
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PocketCodeLogo(size = 34.dp)
                            Text(
                                text = "Sessions",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                onRefresh()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Sessions refreshed")
                                }
                            },
                            modifier = Modifier.testTag("sessions_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh sessions",
                                tint = BlueLightAccent,
                                modifier = if (uiState.isRefreshingSessions) {
                                    Modifier.rotate(spinAngle)
                                } else {
                                    Modifier
                                }
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("sessions_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = BlueLightAccent
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                GlossyNewSessionFab(
                    onClick = { showNewSessionDialog = true }
                )
            }
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
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    LiveStatusChip(
                        label = statusText,
                        modifier = Modifier.testTag("sessions_status_chip")
                    )

                    if (uiState.activeConnectionMode == AgentConnectionMode.ON_DEVICE && !uiState.hasConnectedProvider) {
                        Spacer(modifier = Modifier.height(16.dp))
                        GlossyCardReveal(delayMillis = 40) {
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sessions_add_provider_card"),
                                cornerRadius = 22.dp,
                                containerColor = LiveGreenContainer,
                                borderColor = LiveGreenBorder,
                                topHighlightColor = LiveGreenHighlight,
                                onClick = onOpenSettings
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = LiveGreen.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, LiveGreenBorder),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Key,
                                                contentDescription = null,
                                                tint = LiveGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "First add a provider API key",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = LiveGreen
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Anthropic, OpenAI, OpenRouter, Gemini, Groq, DeepSeek, Ollama…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary.copy(alpha = 0.90f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Open provider settings",
                                        tint = LiveGreen
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (uiState.sessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            GlossyCardReveal(delayMillis = 70) {
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 24.dp
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 42.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = SurfaceElevated,
                                            border = BorderStroke(1.dp, BorderActive),
                                            modifier = Modifier.size(66.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Forum,
                                                    contentDescription = "No sessions",
                                                    tint = BlueLightAccent,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "No sessions — Tap New session below to start",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.testTag("sessions_empty_text")
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(uiState.sessions, key = { _, s -> s.id }) { index, session ->
                                GlossyCardReveal(delayMillis = index * 35) {
                                    SessionCard(
                                        session = session,
                                        isActiveCard = index == 0,
                                        onClick = { onOpenSession(session) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewSessionDialog) {
        NewSessionDialog(
            existingSessions = uiState.sessions,
            defaultProjectFolder = uiState.onDeviceProjectFolder,
            onDismiss = { showNewSessionDialog = false },
            onStart = { name, folder, uri ->
                showNewSessionDialog = false
                onCreateSession(name, folder, uri)
            }
        )
    }
}

@Composable
private fun GlossyNewSessionFab(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "fab_scale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(16.dp, CircleShape, ambientColor = VioletGlow, spotColor = VioletGlow)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(VioletPrimary, IndigoPrimary)))
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                        endY = size.height * 0.55f
                    )
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent)
                    ),
                    start = Offset(20.dp.toPx(), 1.dp.toPx()),
                    end = Offset(size.width - 20.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .border(1.dp, BorderNeonGlow.copy(alpha = 0.65f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = Color.White),
                onClick = onClick
            )
            .testTag("new_session_fab")
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "New session",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionItem,
    isActiveCard: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_card_${session.id}"),
        cornerRadius = 22.dp,
        isGlowingActive = isActiveCard,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BlueContainer,
                border = BorderStroke(1.dp, BorderActive),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = BlueLightAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.projectName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = session.projectFolder.ifBlank { "Private app workspace" },
                    style = MonoTextStyle.copy(color = TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = formatSessionActivityTime(session.lastActivityAtMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("session_last_activity_${session.id}")
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = SurfaceElevated,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = session.mode.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = BlueLightAccent,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open session",
                tint = TextMuted
            )
        }
    }
}

private fun formatSessionActivityTime(timestampMs: Long): String {
    val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    return when {
        minutes < 1 -> "Active just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestampMs))
    }
}
