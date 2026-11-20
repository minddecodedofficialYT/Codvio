package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BgNearBlack
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardShadowAmbient
import com.example.ui.theme.GlassShineTop
import com.example.ui.theme.GlassTopHighlight
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.LiveGreenBorder
import com.example.ui.theme.LiveGreenContainer
import com.example.ui.theme.LiveGreenHighlight
import com.example.ui.theme.LocalPocketColors
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.VioletPrimary
import kotlinx.coroutines.delay

@Composable
fun FuturisticBackdrop(
    modifier: Modifier = Modifier,
    showTopGlowBand: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val bgCanvas = BgNearBlack
    val glowPrimary = VioletPrimary
    val glowIndigo = IndigoPrimary
    val glowAccent = BlueLightAccent
    val isDark = LocalPocketColors.current.isDark
    val glowAlpha1 = if (isDark) 0.17f else 0.13f
    val glowAlpha2 = if (isDark) 0.16f else 0.10f
    val glowAlpha3 = if (isDark) 0.13f else 0.10f
    val bandAlpha1 = if (isDark) 0.22f else 0.12f
    val bandAlpha2 = if (isDark) 0.12f else 0.06f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgCanvas)
            .drawBehind {
                // Soft blurred primary glow top-right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowPrimary.copy(alpha = glowAlpha1),
                            glowPrimary.copy(alpha = glowAlpha1 * 0.3f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.10f),
                        radius = size.minDimension * 0.78f
                    ),
                    radius = size.minDimension * 0.78f,
                    center = Offset(size.width * 0.85f, size.height * 0.10f)
                )

                // Soft blurred indigo glow mid-left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowIndigo.copy(alpha = glowAlpha2),
                            glowIndigo.copy(alpha = glowAlpha2 * 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.48f),
                        radius = size.minDimension * 0.72f
                    ),
                    radius = size.minDimension * 0.72f,
                    center = Offset(size.width * 0.12f, size.height * 0.48f)
                )

                // Soft blurred accent glow bottom-right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowAccent.copy(alpha = glowAlpha3),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.75f, size.height * 0.88f),
                        radius = size.minDimension * 0.75f
                    ),
                    radius = size.minDimension * 0.75f,
                    center = Offset(size.width * 0.75f, size.height * 0.88f)
                )

                if (showTopGlowBand) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                glowPrimary.copy(alpha = bandAlpha1),
                                glowIndigo.copy(alpha = bandAlpha2),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.34f
                        ),
                        size = Size(size.width, size.height * 0.34f)
                    )
                }
            },
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    containerColor: Color = SurfaceCard,
    isGlowingActive: Boolean = false,
    borderColor: Color = if (isGlowingActive) BorderNeonGlow else BorderSubtle,
    topHighlightColor: Color = GlassTopHighlight,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) 0.982f else 1f,
        animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing),
        label = "glass_card_scale"
    )

    val shadowColor = if (isGlowingActive) VioletGlow else CardShadowAmbient
    val shadowElevation = if (isGlowingActive) 16.dp else 10.dp

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .clip(shape)
            .background(containerColor)
            .drawWithContent {
                drawContent()
                // Soft top glass sheen inside panel
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.32f
                    )
                )
                // Thin bright violet top-edge specular highlight
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            topHighlightColor,
                            Color.Transparent
                        )
                    ),
                    start = Offset(cornerRadius.toPx() * 0.65f, 1.dp.toPx()),
                    end = Offset(size.width - cornerRadius.toPx() * 0.65f, 1.dp.toPx()),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
            .border(
                width = if (isGlowingActive) 1.4.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.material3.ripple(color = BlueLightAccent),
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Column(content = content)
    }
}

@Composable
fun GlossyCardReveal(
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 10 }
            )
    ) {
        content()
    }
}

@Composable
fun PocketCodeLogo(
    size: Dp,
    @Suppress("UNUSED_PARAMETER") isRound: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cornerRadius = size * 0.28f
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = VioletGlow,
                spotColor = VioletGlow
            )
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(VioletPrimary, IndigoPrimary)
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        VioletPrimary.copy(alpha = 0.45f)
                    )
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Top-half glossy glass shine
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = this.size.height * 0.56f
                ),
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                size = Size(this.size.width, this.size.height * 0.56f)
            )

            // Crisp bold white >_ terminal symbol
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.105f

            val chevronPath = Path().apply {
                moveTo(w * 0.25f, h * 0.33f)
                lineTo(w * 0.45f, h * 0.50f)
                lineTo(w * 0.25f, h * 0.67f)
            }
            drawPath(
                path = chevronPath,
                color = Color.White,
                style = Stroke(
                    width = strokeW,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            drawLine(
                color = Color.White,
                start = Offset(w * 0.52f, h * 0.67f),
                end = Offset(w * 0.76f, h * 0.67f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun <T> PillSegmentedSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, CircleShape, ambientColor = CardShadowAmbient, spotColor = CardShadowAmbient)
            .clip(CircleShape)
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, CircleShape)
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.97f else 1f,
                    animationSpec = tween(110),
                    label = "pill_tab_scale"
                )

                val shineTop = GlassShineTop
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .minimumInteractiveComponentSize()
                        .then(
                            if (isSelected) {
                                Modifier
                                    .shadow(10.dp, CircleShape, ambientColor = VioletGlow, spotColor = VioletGlow)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(VioletPrimary, IndigoPrimary))
                                    )
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
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                            }
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.material3.ripple(color = BlueLightAccent),
                            role = Role.Tab,
                            onClick = { onSelect(value) }
                        )
                        .testTag("${testTagPrefix}_${label.lowercase().replace(" ", "_")}")
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) Color.White else BlueLightAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LiveStatusChip(
    label: String,
    modifier: Modifier = Modifier
) {
    val greenHighlight = LiveGreenHighlight
    Box(
        modifier = modifier
            .shadow(8.dp, CircleShape, ambientColor = LiveGreen.copy(alpha = 0.25f), spotColor = LiveGreen.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(LiveGreenContainer)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                        endY = size.height * 0.55f
                    )
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, greenHighlight, Color.Transparent)
                    ),
                    start = Offset(16.dp.toPx(), 1.dp.toPx()),
                    end = Offset(size.width - 16.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .border(1.dp, LiveGreenBorder, CircleShape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(LiveGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MonoTextStyle.copy(
                    color = LiveGreen,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
fun PocketPrimaryButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "primary_btn_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (enabled) {
                    Modifier.shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        ambientColor = VioletGlow,
                        spotColor = VioletGlow
                    )
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(
                brush = if (enabled) {
                    Brush.linearGradient(listOf(VioletPrimary, IndigoPrimary))
                } else {
                    Brush.linearGradient(listOf(SurfaceElevated, SurfaceElevated))
                }
            )
            .drawWithContent {
                drawContent()
                if (enabled) {
                    // Glossy shine across top half
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.54f
                        )
                    )
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent)
                        ),
                        start = Offset(22.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 22.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .border(
                width = 1.dp,
                color = if (enabled) BorderNeonGlow.copy(alpha = 0.65f) else BorderSubtle,
                shape = CircleShape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = Color.White),
                role = Role.Button,
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GlassOutlinedButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    contentColor: Color = TextPrimary,
    borderColor: Color = BorderActive,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "outlined_btn_scale"
    )

    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = BlueLightAccent),
                role = Role.Button,
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(7.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) contentColor else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PillDropdownChip(
    text: String,
    isPlaceholder: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .shadow(8.dp, CircleShape, ambientColor = CardShadowAmbient, spotColor = CardShadowAmbient)
            .clip(CircleShape)
            .border(1.dp, BorderActive, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = CircleShape,
        color = SurfaceCard
    ) {
        Row(
            modifier = Modifier
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                            endY = size.height * 0.5f
                        )
                    )
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (isPlaceholder) TextSecondary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            trailingIcon()
        }
    }
}
