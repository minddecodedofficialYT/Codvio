package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.VioMood
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.GlassShineTop
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.VioletGlow
import com.example.ui.theme.VioletPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

enum class VioExpression(val semanticLabel: String) {
    IDLE("idle"),
    THINKING("thinking"),
    CHEER("cheer"),
    SAD("sad"),
    DANCE("dancing"),
    TRICK("trick"),
    NAP("sleeping")
}

fun resolveVioExpression(
    mood: VioMood,
    localAction: VioLocalAction,
    trickCheerPhase: Boolean
): VioExpression {
    return when (mood) {
        VioMood.THINKING -> VioExpression.THINKING
        VioMood.CHEER -> VioExpression.CHEER
        VioMood.SAD -> VioExpression.SAD
        VioMood.IDLE -> when (localAction) {
            VioLocalAction.DANCE -> VioExpression.DANCE
            VioLocalAction.TRICK -> if (trickCheerPhase) VioExpression.CHEER else VioExpression.TRICK
            VioLocalAction.NAP -> VioExpression.NAP
            VioLocalAction.NONE -> VioExpression.IDLE
        }
    }
}

enum class VioLocalAction {
    NONE,
    DANCE,
    TRICK,
    NAP
}

enum class VioParticleType {
    HEART_PINK,
    HEART_RED,
    MUSIC_NOTE,
    SPARKLE,
    ZZZ
}

data class VioParticle(
    val id: Long,
    val type: VioParticleType,
    val originX: Float,
    val originY: Float,
    val vx: Float,
    val vy: Float,
    val sizePx: Float,
    val durationMs: Long,
    val createdAtMs: Long,
    val swayAmplitude: Float = 0f,
    val rotationDeg: Float = 0f
)

@Composable
fun VioOrbAvatar(
    mood: VioMood,
    size: Dp = 56.dp,
    isAnimationsPaused: Boolean = false,
    bounceTrigger: Int = 0,
    spinTrigger: Int = 0,
    trickSpinTrigger: Int = 0,
    localAction: VioLocalAction = VioLocalAction.NONE,
    trickCheerPhase: Boolean = false,
    modifier: Modifier = Modifier
) {
    val expression = resolveVioExpression(mood, localAction, trickCheerPhase)

    val violetPrimary = VioletPrimary
    val indigoPrimary = IndigoPrimary
    val accentColor = BlueLightAccent
    val neonGlow = BorderNeonGlow
    val glowTint = VioletGlow
    val glassShine = GlassShineTop

    // Lightweight infinite phase animations — completely paused when app is backgrounded
    val (bobProgress, pulseProgress, sparkleTwinkle) = if (!isAnimationsPaused) {
        val infiniteTransition = rememberInfiniteTransition(label = "vio_idle_pulse")
        val bob by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "vio_bob"
        )
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "vio_pulse"
        )
        val twinkle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "vio_gold_sparkle"
        )
        Triple(bob, pulse, twinkle)
    } else {
        Triple(0.5f, 0.25f, 0.7f)
    }

    // Natural occasional blink while idle (paused in background)
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(expression, isAnimationsPaused) {
        isBlinking = false
        if (isAnimationsPaused || expression != VioExpression.IDLE) return@LaunchedEffect
        while (true) {
            delay(3100L)
            isBlinking = true
            delay(130L)
            isBlinking = false
        }
    }

    // Single-tap squash-and-bounce animation
    val tapScaleX = remember { Animatable(1f) }
    val tapScaleY = remember { Animatable(1f) }
    val tapHopDp = remember { Animatable(0f) }
    LaunchedEffect(bounceTrigger) {
        if (bounceTrigger <= 0) return@LaunchedEffect
        launch {
            tapScaleX.snapTo(1.26f)
            tapScaleX.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        launch {
            tapScaleY.snapTo(0.74f)
            tapScaleY.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        launch {
            tapHopDp.snapTo(-6f)
            tapHopDp.animateTo(
                0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Double-tap / dance happy 360-degree spin
    val spinRotation = remember { Animatable(0f) }
    LaunchedEffect(spinTrigger) {
        if (spinTrigger <= 0) return@LaunchedEffect
        spinRotation.snapTo(0f)
        spinRotation.animateTo(
            targetValue = 360f,
            animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
        )
        spinRotation.snapTo(0f)
    }

    // "Do a trick" big jump + full spin
    var trickJumpDp by remember { mutableFloatStateOf(0f) }
    val trickSpinDeg = remember { Animatable(0f) }
    LaunchedEffect(trickSpinTrigger) {
        if (trickSpinTrigger <= 0) return@LaunchedEffect
        trickJumpDp = 0f
        launch {
            val phase = Animatable(0f)
            phase.animateTo(
                targetValue = PI.toFloat(),
                animationSpec = tween(durationMillis = 660, easing = LinearEasing)
            ) {
                trickJumpDp = -28f * sin(value)
            }
            trickJumpDp = 0f
        }
        launch {
            trickSpinDeg.snapTo(0f)
            trickSpinDeg.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 640, easing = FastOutSlowInEasing)
            )
            trickSpinDeg.snapTo(0f)
        }
    }

    // Cheer jump, Dance side-to-side groove, and Sad dizzy wobble
    var cheerJumpDp by remember { mutableFloatStateOf(0f) }
    var danceOffsetXDp by remember { mutableFloatStateOf(0f) }
    var danceOffsetYDp by remember { mutableFloatStateOf(0f) }
    var danceWiggleDeg by remember { mutableFloatStateOf(0f) }
    val sadWobbleDeg = remember { Animatable(0f) }

    LaunchedEffect(expression, isAnimationsPaused) {
        cheerJumpDp = 0f
        danceOffsetXDp = 0f
        danceOffsetYDp = 0f
        danceWiggleDeg = 0f
        sadWobbleDeg.snapTo(0f)
        if (isAnimationsPaused) return@LaunchedEffect

        when (expression) {
            VioExpression.CHEER -> {
                val jumpPhase = Animatable(0f)
                while (true) {
                    jumpPhase.snapTo(0f)
                    jumpPhase.animateTo(
                        targetValue = PI.toFloat(),
                        animationSpec = tween(durationMillis = 500, easing = LinearEasing)
                    ) {
                        cheerJumpDp = -10f * sin(value)
                    }
                    cheerJumpDp = 0f
                    delay(120L)
                }
            }
            VioExpression.DANCE -> {
                val phase = Animatable(0f)
                while (true) {
                    phase.snapTo(0f)
                    phase.animateTo(
                        targetValue = (2f * PI).toFloat(),
                        animationSpec = tween(durationMillis = 820, easing = LinearEasing)
                    ) {
                        danceOffsetXDp = 7.5f * sin(value)
                        danceOffsetYDp = -5.5f * kotlin.math.abs(sin(value))
                        danceWiggleDeg = 14f * sin(value)
                    }
                }
            }
            VioExpression.SAD -> {
                sadWobbleDeg.animateTo(10f, tween(130))
                sadWobbleDeg.animateTo(-10f, tween(180))
                sadWobbleDeg.animateTo(6f, tween(160))
                sadWobbleDeg.animateTo(-5f, tween(150))
                sadWobbleDeg.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
            }
            else -> Unit
        }
    }

    val idleBobDp = when {
        isAnimationsPaused -> 0f
        expression == VioExpression.IDLE -> (bobProgress - 0.5f) * 5f
        expression == VioExpression.NAP -> (bobProgress - 0.5f) * 2.5f
        else -> 0f
    }

    val totalTranslateXDp = danceOffsetXDp
    val totalTranslateYDp = idleBobDp + cheerJumpDp + danceOffsetYDp + tapHopDp.value + trickJumpDp
    val totalRotation = spinRotation.value + trickSpinDeg.value + danceWiggleDeg + sadWobbleDeg.value

    val density = LocalDensity.current
    val translationXPx = with(density) { totalTranslateXDp.dp.toPx() }
    val translationYPx = with(density) { totalTranslateYDp.dp.toPx() }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationX = translationXPx
                translationY = translationYPx
                scaleX = tapScaleX.value
                scaleY = tapScaleY.value
                rotationZ = totalRotation
            }
    ) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val baseRadius = (minOf(w, h) / 2f) * 0.78f

        // 1. Soft ambient violet halo glow (pulses strongly in THINKING & CHEER & DANCE)
        val glowAlpha = when (expression) {
            VioExpression.THINKING -> 0.36f + 0.38f * pulseProgress
            VioExpression.CHEER, VioExpression.DANCE -> 0.45f + 0.34f * pulseProgress
            VioExpression.NAP -> 0.16f + 0.06f * bobProgress
            VioExpression.SAD -> 0.14f
            else -> 0.24f + 0.10f * bobProgress
        }
        val glowRadius = when (expression) {
            VioExpression.THINKING -> baseRadius * (1.30f + 0.18f * pulseProgress)
            VioExpression.CHEER, VioExpression.DANCE -> baseRadius * 1.40f
            else -> baseRadius * 1.26f
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowTint.copy(alpha = glowAlpha),
                    accentColor.copy(alpha = glowAlpha * 0.45f),
                    Color.Transparent
                ),
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center
        )

        // 2. Rounder, softer, glossier violet blob body (mochi-like chubby cheeks curve)
        val blobPath = buildSoftBlobPath(center = center, r = baseRadius)
        val highlightViolet = lerp(violetPrimary, Color.White, 0.38f)
        val deepShadow = lerp(indigoPrimary, Color(0xFF14072B), 0.34f)
        val blobBrush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to highlightViolet,
                0.48f to violetPrimary,
                0.86f to indigoPrimary,
                1.0f to deepShadow
            ),
            center = Offset(center.x - baseRadius * 0.28f, center.y - baseRadius * 0.32f),
            radius = baseRadius * 1.45f
        )
        drawPath(path = blobPath, brush = blobBrush)

        // Soft glossy inner rim highlight
        drawPath(
            path = blobPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    neonGlow.copy(alpha = 0.88f),
                    violetPrimary.copy(alpha = 0.45f),
                    indigoPrimary.copy(alpha = 0.72f)
                )
            ),
            style = Stroke(width = 1.4.dp.toPx(), join = StrokeJoin.Round)
        )

        // 3. Big soft highlight shine on top-left + secondary specular dot
        rotate(degrees = -26f, pivot = center) {
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.62f),
                        glassShine.copy(alpha = 0.24f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(center.x - baseRadius * 0.56f, center.y - baseRadius * 0.82f),
                size = Size(baseRadius * 0.94f, baseRadius * 0.44f)
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = baseRadius * 0.095f,
            center = Offset(center.x + baseRadius * 0.30f, center.y - baseRadius * 0.56f)
        )

        // 4. Rosy pink blush circles on both cheeks (softer in SAD)
        val blushAlpha = if (expression == VioExpression.SAD) 0.26f else 0.58f
        val blushColor = Color(0xFFFF7EB6)
        val blushRadius = baseRadius * 0.22f
        val blushY = center.y + baseRadius * 0.21f
        val blushXOffset = baseRadius * 0.50f
        listOf(
            Offset(center.x - blushXOffset, blushY),
            Offset(center.x + blushXOffset, blushY)
        ).forEach { cheekCenter ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        blushColor.copy(alpha = blushAlpha),
                        blushColor.copy(alpha = blushAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = cheekCenter,
                    radius = blushRadius
                ),
                radius = blushRadius,
                center = cheekCenter
            )
        }

        // 5. Much bigger, sparkling eyes & cute smile
        val eyeSpacing = baseRadius * 0.34f
        val eyeY = center.y - baseRadius * 0.04f
        val leftEye = Offset(center.x - eyeSpacing, eyeY)
        val rightEye = Offset(center.x + eyeSpacing, eyeY)
        val eyeW = baseRadius * 0.38f
        val eyeH = baseRadius * 0.44f
        val pupilColor = Color(0xFF130829)

        when (expression) {
            VioExpression.IDLE, VioExpression.THINKING, VioExpression.DANCE, VioExpression.TRICK -> {
                val blinkScaleY = if (expression == VioExpression.IDLE && isBlinking) 0.14f else 1f
                val effectiveH = eyeH * blinkScaleY

                listOf(leftEye, rightEye).forEach { eyePos ->
                    if (expression == VioExpression.IDLE && isBlinking) {
                        val blinkArc = Path().apply {
                            val hw = eyeW * 0.45f
                            moveTo(eyePos.x - hw, eyePos.y)
                            quadraticTo(eyePos.x, eyePos.y + eyeH * 0.18f, eyePos.x + hw, eyePos.y)
                        }
                        drawPath(
                            path = blinkArc,
                            color = Color.White,
                            style = Stroke(width = baseRadius * 0.085f, cap = StrokeCap.Round)
                        )
                    } else {
                        // Bright sclera outline + big dark sparkling pupil
                        drawOval(
                            color = Color.White,
                            topLeft = Offset(eyePos.x - eyeW / 2f, eyePos.y - effectiveH / 2f),
                            size = Size(eyeW, effectiveH)
                        )

                        val gazeOffset = if (expression == VioExpression.THINKING) {
                            Offset(eyeW * 0.08f, -effectiveH * 0.16f)
                        } else {
                            Offset.Zero
                        }
                        val pupilW = eyeW * 0.78f
                        val pupilH = effectiveH * 0.80f
                        val pupilCenter = eyePos + gazeOffset

                        drawOval(
                            color = pupilColor,
                            topLeft = Offset(pupilCenter.x - pupilW / 2f, pupilCenter.y - pupilH / 2f),
                            size = Size(pupilW, pupilH)
                        )

                        // TWO white sparkle highlights inside each dark pupil!
                        drawCircle(
                            color = Color.White,
                            radius = pupilW * 0.24f,
                            center = Offset(
                                pupilCenter.x - pupilW * 0.18f,
                                pupilCenter.y - pupilH * 0.19f
                            )
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.92f),
                            radius = pupilW * 0.125f,
                            center = Offset(
                                pupilCenter.x + pupilW * 0.18f,
                                pupilCenter.y + pupilH * 0.18f
                            )
                        )
                    }
                }

                // Mouth
                if (expression == VioExpression.THINKING) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.95f),
                        radius = baseRadius * 0.075f,
                        center = Offset(center.x + baseRadius * 0.04f, center.y + baseRadius * 0.34f),
                        style = Stroke(width = baseRadius * 0.065f)
                    )
                } else {
                    val smile = Path().apply {
                        val mw = baseRadius * 0.21f
                        val my = center.y + baseRadius * 0.28f
                        moveTo(center.x - mw, my)
                        quadraticTo(center.x, my + baseRadius * 0.18f, center.x + mw, my)
                    }
                    drawPath(
                        path = smile,
                        color = Color.White,
                        style = Stroke(width = baseRadius * 0.085f, cap = StrokeCap.Round)
                    )
                }
            }

            VioExpression.CHEER -> {
                // Joyful closed ^ ^ happy eyes
                val arcStroke = Stroke(
                    width = baseRadius * 0.095f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                listOf(leftEye, rightEye).forEach { eyePos ->
                    val arch = Path().apply {
                        val hw = eyeW * 0.44f
                        moveTo(eyePos.x - hw, eyePos.y + eyeH * 0.08f)
                        quadraticTo(eyePos.x, eyePos.y - eyeH * 0.34f, eyePos.x + hw, eyePos.y + eyeH * 0.08f)
                    }
                    drawPath(path = arch, color = Color.White, style = arcStroke)
                }

                val bigSmile = Path().apply {
                    val mw = baseRadius * 0.27f
                    val my = center.y + baseRadius * 0.25f
                    moveTo(center.x - mw, my)
                    quadraticTo(center.x, my + baseRadius * 0.26f, center.x + mw, my)
                }
                drawPath(
                    path = bigSmile,
                    color = Color.White,
                    style = Stroke(width = baseRadius * 0.095f, cap = StrokeCap.Round)
                )
            }

            VioExpression.NAP -> {
                // Peaceful closed sleepy U-eyes (◡ ◡)
                val sleepStroke = Stroke(width = baseRadius * 0.085f, cap = StrokeCap.Round)
                listOf(leftEye, rightEye).forEach { eyePos ->
                    val lid = Path().apply {
                        val hw = eyeW * 0.42f
                        moveTo(eyePos.x - hw, eyePos.y - eyeH * 0.04f)
                        quadraticTo(eyePos.x, eyePos.y + eyeH * 0.24f, eyePos.x + hw, eyePos.y - eyeH * 0.04f)
                    }
                    drawPath(path = lid, color = Color.White, style = sleepStroke)
                }

                // Tiny peaceful resting mouth
                val restMouth = Path().apply {
                    val mw = baseRadius * 0.14f
                    val my = center.y + baseRadius * 0.31f
                    moveTo(center.x - mw, my)
                    quadraticTo(center.x, my + baseRadius * 0.09f, center.x + mw, my)
                }
                drawPath(
                    path = restMouth,
                    color = Color.White.copy(alpha = 0.90f),
                    style = Stroke(width = baseRadius * 0.07f, cap = StrokeCap.Round)
                )
            }

            VioExpression.SAD -> {
                // Dizzy / worried X eyes
                val strokeW = baseRadius * 0.085f
                listOf(leftEye, rightEye).forEach { eyePos ->
                    val r = eyeW * 0.32f
                    drawLine(
                        color = Color.White,
                        start = Offset(eyePos.x - r, eyePos.y - r),
                        end = Offset(eyePos.x + r, eyePos.y + r),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(eyePos.x + r, eyePos.y - r),
                        end = Offset(eyePos.x - r, eyePos.y + r),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }

                val frown = Path().apply {
                    val mw = baseRadius * 0.22f
                    val my = center.y + baseRadius * 0.39f
                    moveTo(center.x - mw, my)
                    quadraticTo(center.x, my - baseRadius * 0.16f, center.x + mw, my)
                }
                drawPath(
                    path = frown,
                    color = Color.White.copy(alpha = 0.92f),
                    style = Stroke(width = baseRadius * 0.08f, cap = StrokeCap.Round)
                )
            }
        }

        // 6. Tiny golden sparkle floating next to Vio & gently twinkling
        val sparkleScale = 0.65f + 0.45f * sparkleTwinkle
        val sparkleAlpha = if (expression == VioExpression.NAP) 0.45f else (0.65f + 0.35f * sparkleTwinkle)
        val sparklePos = Offset(
            center.x + baseRadius * 0.96f,
            center.y - baseRadius * 0.78f - (sparkleTwinkle - 0.5f) * baseRadius * 0.12f
        )
        drawFourPointSparkle(
            center = sparklePos,
            radius = baseRadius * 0.22f * sparkleScale,
            color = Color(0xFFFFD86B).copy(alpha = sparkleAlpha),
            coreColor = Color(0xFFFFF8D6).copy(alpha = sparkleAlpha)
        )
    }
}

private fun buildSoftBlobPath(center: Offset, r: Float): Path {
    // Round, slightly plump mochi-orb silhouette with softer cheek fullness
    val topY = center.y - r * 0.96f
    val bottomY = center.y + r * 0.96f
    val leftX = center.x - r * 1.03f
    val rightX = center.x + r * 1.03f

    return Path().apply {
        moveTo(center.x, topY)
        cubicTo(
            center.x + r * 0.62f, topY,
            rightX, center.y - r * 0.52f,
            rightX, center.y + r * 0.08f
        )
        cubicTo(
            rightX, center.y + r * 0.68f,
            center.x + r * 0.64f, bottomY,
            center.x, bottomY
        )
        cubicTo(
            center.x - r * 0.64f, bottomY,
            leftX, center.y + r * 0.68f,
            leftX, center.y + r * 0.08f
        )
        cubicTo(
            leftX, center.y - r * 0.52f,
            center.x - r * 0.62f, topY,
            center.x, topY
        )
        close()
    }
}

fun DrawScope.drawFourPointSparkle(
    center: Offset,
    radius: Float,
    color: Color,
    coreColor: Color = Color.White.copy(alpha = color.alpha)
) {
    val inner = radius * 0.28f
    val starPath = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y - inner, center.x + radius, center.y)
        quadraticTo(center.x, center.y + inner, center.x, center.y + radius)
        quadraticTo(center.x, center.y + inner, center.x - radius, center.y)
        quadraticTo(center.x, center.y - inner, center.x, center.y - radius)
        close()
    }
    drawPath(path = starPath, color = color)
    drawCircle(color = coreColor, radius = radius * 0.24f, center = center)
}

fun DrawScope.drawHeartParticle(
    center: Offset,
    size: Float,
    color: Color
) {
    val halfW = size * 0.52f
    val topDip = center.y - size * 0.14f
    val bottomTip = center.y + size * 0.46f
    val path = Path().apply {
        moveTo(center.x, bottomTip)
        cubicTo(
            center.x - halfW * 1.15f, center.y + size * 0.14f,
            center.x - halfW * 1.10f, center.y - size * 0.48f,
            center.x, topDip
        )
        cubicTo(
            center.x + halfW * 1.10f, center.y - size * 0.48f,
            center.x + halfW * 1.15f, center.y + size * 0.14f,
            center.x, bottomTip
        )
        close()
    }
    drawPath(path = path, color = color)
    drawCircle(
        color = Color.White.copy(alpha = color.alpha * 0.45f),
        radius = size * 0.10f,
        center = Offset(center.x - halfW * 0.34f, center.y - size * 0.16f)
    )
}

fun DrawScope.drawMusicNoteParticle(
    center: Offset,
    size: Float,
    color: Color
) {
    val headR = size * 0.24f
    val stemTop = Offset(center.x + headR * 0.72f, center.y - size * 0.48f)
    val stemBottom = Offset(center.x + headR * 0.72f, center.y + size * 0.18f)
    drawOval(
        color = color,
        topLeft = Offset(center.x - headR * 1.4f, center.y - headR * 0.15f),
        size = Size(headR * 2.3f, headR * 1.65f)
    )
    drawLine(
        color = color,
        start = stemBottom,
        end = stemTop,
        strokeWidth = size * 0.13f,
        cap = StrokeCap.Round
    )
    val flag = Path().apply {
        moveTo(stemTop.x, stemTop.y)
        quadraticTo(stemTop.x + size * 0.42f, stemTop.y + size * 0.16f, stemTop.x + size * 0.28f, stemTop.y + size * 0.42f)
    }
    drawPath(
        path = flag,
        color = color,
        style = Stroke(width = size * 0.13f, cap = StrokeCap.Round)
    )
}

fun DrawScope.drawZzzParticle(
    center: Offset,
    size: Float,
    color: Color
) {
    fun drawSingleZ(c: Offset, halfW: Float, halfH: Float, strokeW: Float) {
        val zPath = Path().apply {
            moveTo(c.x - halfW, c.y - halfH)
            lineTo(c.x + halfW, c.y - halfH)
            lineTo(c.x - halfW, c.y + halfH)
            lineTo(c.x + halfW, c.y + halfH)
        }
        drawPath(
            path = zPath,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    drawSingleZ(center + Offset(-size * 0.42f, size * 0.16f), size * 0.24f, size * 0.28f, size * 0.12f)
    drawSingleZ(center + Offset(size * 0.05f, -size * 0.04f), size * 0.19f, size * 0.22f, size * 0.10f)
    drawSingleZ(center + Offset(size * 0.44f, -size * 0.24f), size * 0.14f, size * 0.17f, size * 0.085f)
}
