package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.VioMood
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun rememberIsAppResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return isResumed
}

@Composable
fun VioFloatingOverlay(
    mood: VioMood,
    sideRight: Boolean,
    yFraction: Float,
    onPositionChanged: (sideRight: Boolean, yFraction: Float) -> Unit,
    onHideVio: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isResumed = rememberIsAppResumed()
    val isAnimationsPaused = !isResumed
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val mascotSize = 56.dp
    val edgeMargin = 10.dp

    var menuExpanded by remember { mutableStateOf(false) }
    var tapBounceCounter by remember { mutableIntStateOf(0) }
    var spinCounter by remember { mutableIntStateOf(0) }
    var trickSpinCounter by remember { mutableIntStateOf(0) }
    var localAction by remember { mutableStateOf(VioLocalAction.NONE) }
    var trickCheerPhase by remember { mutableStateOf(false) }
    var lastTapUptimeMs by remember { mutableLongStateOf(0L) }

    val particles = remember { mutableStateListOf<VioParticle>() }
    var nextParticleId by remember { mutableLongStateOf(1L) }
    var frameClockMs by remember { mutableLongStateOf(0L) }

    val latestSideRight by rememberUpdatedState(sideRight)
    val latestYFraction by rememberUpdatedState(yFraction)
    val latestOnPositionChanged by rememberUpdatedState(onPositionChanged)
    val latestMood by rememberUpdatedState(mood)
    val latestLocalAction by rememberUpdatedState(localAction)

    val effectiveExpression = resolveVioExpression(mood, localAction, trickCheerPhase)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("vio_overlay_container")
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val mascotSizePx = with(density) { mascotSize.toPx() }
        val marginPx = with(density) { edgeMargin.toPx() }

        val leftSnapX = marginPx.coerceAtMost((containerWidthPx - mascotSizePx).coerceAtLeast(0f))
        val rightSnapX = (containerWidthPx - mascotSizePx - marginPx).coerceAtLeast(leftSnapX)
        val minY = marginPx.coerceAtMost((containerHeightPx - mascotSizePx).coerceAtLeast(0f))
        val maxY = (containerHeightPx - mascotSizePx - marginPx).coerceAtLeast(minY)
        val ySpan = (maxY - minY).coerceAtLeast(1f)

        val targetX = if (sideRight) rightSnapX else leftSnapX
        val targetY = minY + ySpan * yFraction.coerceIn(0f, 1f)

        val posX = remember { Animatable(targetX) }
        val posY = remember { Animatable(targetY) }
        var hasInitializedPosition by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }

        fun currentVioCenter(): Offset = Offset(
            posX.value + mascotSizePx / 2f,
            posY.value + mascotSizePx / 2f
        )

        fun trimParticlesIfNeeded(maxCount: Int = 48) {
            while (particles.size > maxCount) {
                particles.removeAt(0)
            }
        }

        fun spawnHeartBurst(count: Int, bigCelebration: Boolean) {
            if (isAnimationsPaused) return
            val center = currentVioCenter()
            val nowMs = frameClockMs
            repeat(count) { idx ->
                val spreadAngle = if (bigCelebration) {
                    val fraction = idx.toFloat() / count.coerceAtLeast(1)
                    (-145f + 110f * fraction + Random.nextFloat() * 18f) * (Math.PI / 180.0)
                } else {
                    val fraction = idx.toFloat() / count.coerceAtLeast(1)
                    (-130f + 80f * fraction + Random.nextFloat() * 14f) * (Math.PI / 180.0)
                }
                val speed = if (bigCelebration) {
                    with(density) { (75f + Random.nextFloat() * 115f).dp.toPx() }
                } else {
                    with(density) { (45f + Random.nextFloat() * 55f).dp.toPx() }
                }
                val particleSize = if (bigCelebration) {
                    with(density) { (8f + Random.nextFloat() * 6f).dp.toPx() }
                } else {
                    with(density) { (7f + Random.nextFloat() * 4.5f).dp.toPx() }
                }
                val duration = if (bigCelebration) {
                    2100L + Random.nextLong(700L)
                } else {
                    950L + Random.nextLong(350L)
                }
                val isPink = idx % 2 == 0
                particles.add(
                    VioParticle(
                        id = nextParticleId++,
                        type = if (isPink) VioParticleType.HEART_PINK else VioParticleType.HEART_RED,
                        originX = center.x + with(density) { ((Random.nextFloat() - 0.5f) * 20f).dp.toPx() },
                        originY = center.y - mascotSizePx * 0.18f,
                        vx = (cos(spreadAngle) * speed).toFloat(),
                        vy = (sin(spreadAngle) * speed).toFloat() - with(density) { 18.dp.toPx() },
                        sizePx = particleSize,
                        durationMs = duration,
                        createdAtMs = nowMs,
                        swayAmplitude = with(density) { (4f + Random.nextFloat() * 8f).dp.toPx() },
                        rotationDeg = (Random.nextFloat() - 0.5f) * 24f
                    )
                )
            }
            trimParticlesIfNeeded()
        }

        fun spawnDanceParticles() {
            if (isAnimationsPaused) return
            val center = currentVioCenter()
            val nowMs = frameClockMs
            val sideOffset = with(density) { ((Random.nextFloat() - 0.5f) * 34f).dp.toPx() }
            particles.add(
                VioParticle(
                    id = nextParticleId++,
                    type = if (Random.nextBoolean()) VioParticleType.MUSIC_NOTE else VioParticleType.SPARKLE,
                    originX = center.x + sideOffset,
                    originY = center.y - mascotSizePx * 0.25f,
                    vx = with(density) { ((Random.nextFloat() - 0.5f) * 28f).dp.toPx() },
                    vy = -with(density) { (46f + Random.nextFloat() * 28f).dp.toPx() },
                    sizePx = with(density) { (9f + Random.nextFloat() * 4f).dp.toPx() },
                    durationMs = 1200L,
                    createdAtMs = nowMs,
                    swayAmplitude = with(density) { 7.dp.toPx() }
                )
            )
            trimParticlesIfNeeded()
        }

        fun spawnSparkleBurst(count: Int) {
            if (isAnimationsPaused) return
            val center = currentVioCenter()
            val nowMs = frameClockMs
            repeat(count) { idx ->
                val angle = (idx * (360f / count) - 90f) * (Math.PI / 180.0)
                val speed = with(density) { (55f + Random.nextFloat() * 40f).dp.toPx() }
                particles.add(
                    VioParticle(
                        id = nextParticleId++,
                        type = VioParticleType.SPARKLE,
                        originX = center.x,
                        originY = center.y,
                        vx = (cos(angle) * speed).toFloat(),
                        vy = (sin(angle) * speed).toFloat(),
                        sizePx = with(density) { (8f + Random.nextFloat() * 4f).dp.toPx() },
                        durationMs = 950L,
                        createdAtMs = nowMs
                    )
                )
            }
            trimParticlesIfNeeded()
        }

        fun spawnZzzParticle() {
            if (isAnimationsPaused) return
            val center = currentVioCenter()
            val nowMs = frameClockMs
            particles.add(
                VioParticle(
                    id = nextParticleId++,
                    type = VioParticleType.ZZZ,
                    originX = center.x + mascotSizePx * 0.28f,
                    originY = center.y - mascotSizePx * 0.36f,
                    vx = with(density) { 12.dp.toPx() },
                    vy = -with(density) { 30.dp.toPx() },
                    sizePx = with(density) { 10.dp.toPx() },
                    durationMs = 1550L,
                    createdAtMs = nowMs,
                    swayAmplitude = with(density) { 5.dp.toPx() }
                )
            )
            trimParticlesIfNeeded()
        }

        fun handleSingleTap() {
            if (latestLocalAction == VioLocalAction.NAP) {
                localAction = VioLocalAction.NONE
            }
            tapBounceCounter++
            spawnHeartBurst(count = 7, bigCelebration = false)
        }

        fun handleDoubleTap() {
            if (latestLocalAction == VioLocalAction.NAP) {
                localAction = VioLocalAction.NONE
            }
            spinCounter++
        }

        // Particle frame loop — runs ONLY when particles exist and app is resumed
        LaunchedEffect(particles.isNotEmpty(), isAnimationsPaused) {
            if (isAnimationsPaused || particles.isEmpty()) return@LaunchedEffect
            var baseFrameMs = -1L
            while (particles.isNotEmpty()) {
                withFrameMillis { ms ->
                    if (baseFrameMs < 0L) baseFrameMs = ms
                    val now = frameClockMs + (ms - baseFrameMs).coerceAtLeast(0L)
                    baseFrameMs = ms
                    frameClockMs = now
                    particles.removeAll { (now - it.createdAtMs) >= it.durationMs }
                }
            }
        }

        // Real task reactions override fun actions and trigger celebration hearts
        LaunchedEffect(mood) {
            if (mood != VioMood.IDLE) {
                localAction = VioLocalAction.NONE
                trickCheerPhase = false
            }
            if (mood == VioMood.CHEER) {
                spawnHeartBurst(count = 26, bigCelebration = true)
            }
        }

        // Active fun action choreography (Dance ~5s, Do a trick, Take a nap)
        LaunchedEffect(localAction, isAnimationsPaused) {
            if (isAnimationsPaused) return@LaunchedEffect
            when (localAction) {
                VioLocalAction.DANCE -> {
                    repeat(12) { step ->
                        if (latestMood != VioMood.IDLE) {
                            localAction = VioLocalAction.NONE
                            return@LaunchedEffect
                        }
                        spawnDanceParticles()
                        if (step == 3 || step == 8) {
                            spinCounter++
                        }
                        delay(420L)
                    }
                    localAction = VioLocalAction.NONE
                }

                VioLocalAction.TRICK -> {
                    trickCheerPhase = false
                    trickSpinCounter++
                    delay(660L)
                    if (latestMood != VioMood.IDLE) {
                        trickCheerPhase = false
                        localAction = VioLocalAction.NONE
                        return@LaunchedEffect
                    }
                    trickCheerPhase = true
                    spawnHeartBurst(count = 10, bigCelebration = false)
                    spawnSparkleBurst(count = 6)
                    delay(1450L)
                    trickCheerPhase = false
                    localAction = VioLocalAction.NONE
                }

                VioLocalAction.NAP -> {
                    while (latestLocalAction == VioLocalAction.NAP && latestMood == VioMood.IDLE) {
                        spawnZzzParticle()
                        delay(1250L)
                    }
                }

                VioLocalAction.NONE -> Unit
            }
        }

        LaunchedEffect(targetX, targetY, containerWidthPx, containerHeightPx) {
            if (isDragging) return@LaunchedEffect
            if (!hasInitializedPosition) {
                posX.snapTo(targetX)
                posY.snapTo(targetY)
                hasInitializedPosition = true
            } else {
                launch {
                    posX.animateTo(
                        targetValue = targetX,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                launch {
                    posY.animateTo(
                        targetValue = targetY,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        }

        // Non-blocking particle canvas above screen content
        if (particles.isNotEmpty() && !isAnimationsPaused) {
            val musicNoteColor = BlueLightAccent
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("vio_particle_canvas")
            ) {
                val now = frameClockMs
                for (i in particles.indices) {
                    val p = particles[i]
                    val ageMs = (now - p.createdAtMs).coerceAtLeast(0L)
                    val t = (ageMs.toFloat() / p.durationMs.toFloat()).coerceIn(0f, 1f)
                    val sec = ageMs / 1000f
                    val alpha = when {
                        t < 0.14f -> (t / 0.14f)
                        t > 0.65f -> (1f - (t - 0.65f) / 0.35f)
                        else -> 1f
                    }.coerceIn(0f, 1f)
                    if (alpha <= 0.01f) continue

                    val swayX = if (p.swayAmplitude != 0f) {
                        sin(t * Math.PI * 2.5).toFloat() * p.swayAmplitude
                    } else {
                        0f
                    }
                    val drawPos = Offset(
                        x = p.originX + p.vx * sec + swayX,
                        y = p.originY + p.vy * sec
                    )

                    when (p.type) {
                        VioParticleType.HEART_PINK -> {
                            rotate(degrees = p.rotationDeg, pivot = drawPos) {
                                drawHeartParticle(
                                    center = drawPos,
                                    size = p.sizePx,
                                    color = Color(0xFFFF7EB6).copy(alpha = alpha)
                                )
                            }
                        }

                        VioParticleType.HEART_RED -> {
                            rotate(degrees = p.rotationDeg, pivot = drawPos) {
                                drawHeartParticle(
                                    center = drawPos,
                                    size = p.sizePx,
                                    color = Color(0xFFFF476F).copy(alpha = alpha)
                                )
                            }
                        }

                        VioParticleType.MUSIC_NOTE -> {
                            drawMusicNoteParticle(
                                center = drawPos,
                                size = p.sizePx,
                                color = musicNoteColor.copy(alpha = alpha)
                            )
                        }

                        VioParticleType.SPARKLE -> {
                            drawFourPointSparkle(
                                center = drawPos,
                                radius = p.sizePx * 0.55f,
                                color = Color(0xFFFFD86B).copy(alpha = alpha)
                            )
                        }

                        VioParticleType.ZZZ -> {
                            drawZzzParticle(
                                center = drawPos,
                                size = p.sizePx * (0.85f + 0.25f * t),
                                color = Color(0xFFE9D5FF).copy(alpha = alpha)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(posX.value.roundToInt(), posY.value.roundToInt()) }
                .size(mascotSize)
                .minimumInteractiveComponentSize()
                .semantics {
                    contentDescription = "Vio mascot (${effectiveExpression.semanticLabel})"
                    onClick(label = "Interact with Vio") {
                        handleSingleTap()
                        true
                    }
                    onLongClick(label = "Open Vio menu") {
                        menuExpanded = true
                        true
                    }
                }
                .pointerInput(containerWidthPx, containerHeightPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis.coerceAtLeast(320L)
                    val maxXBoundary = (containerWidthPx - mascotSizePx).coerceAtLeast(0f)

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragStarted = false
                        var longPressFired = false
                        var totalDelta = Offset.Zero

                        try {
                            withTimeout(longPressTimeout) {
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                        ?: break

                                    val moveDelta = change.positionChange()
                                    totalDelta += moveDelta
                                    if (totalDelta.getDistance() > touchSlop) {
                                        dragStarted = true
                                        isDragging = true
                                        menuExpanded = false
                                        change.consume()
                                        val nextX = (posX.value + moveDelta.x).coerceIn(0f, maxXBoundary)
                                        val nextY = (posY.value + moveDelta.y).coerceIn(minY, maxY)
                                        scope.launch {
                                            posX.snapTo(nextX)
                                            posY.snapTo(nextY)
                                        }
                                        break
                                    }

                                    if (event.changes.all { !it.pressed || it.changedToUpIgnoreConsumed() }) {
                                        val nowUptime = change.uptimeMillis
                                        val tapGap = nowUptime - lastTapUptimeMs
                                        if (lastTapUptimeMs > 0L && tapGap in 1L..doubleTapTimeout) {
                                            lastTapUptimeMs = 0L
                                            handleDoubleTap()
                                        } else {
                                            lastTapUptimeMs = nowUptime
                                            handleSingleTap()
                                        }
                                        break
                                    }
                                }
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            longPressFired = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuExpanded = true
                        }

                        if (dragStarted) {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: break

                                val moveDelta = change.positionChange()
                                if (change.pressed) {
                                    change.consume()
                                    val nextX = (posX.value + moveDelta.x).coerceIn(0f, maxXBoundary)
                                    val nextY = (posY.value + moveDelta.y).coerceIn(minY, maxY)
                                    scope.launch {
                                        posX.snapTo(nextX)
                                        posY.snapTo(nextY)
                                    }
                                }

                                val allUp = event.changes.all { !it.pressed || it.changedToUpIgnoreConsumed() }
                                if (allUp) {
                                    isDragging = false
                                    val centerX = posX.value + mascotSizePx / 2f
                                    val snapToRight = centerX >= containerWidthPx / 2f
                                    val snapX = if (snapToRight) rightSnapX else leftSnapX
                                    val newYFraction = ((posY.value - minY) / ySpan).coerceIn(0f, 1f)
                                    scope.launch {
                                        posX.animateTo(
                                            targetValue = snapX,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                    if (snapToRight != latestSideRight || abs(newYFraction - latestYFraction) > 0.005f) {
                                        latestOnPositionChanged(snapToRight, newYFraction)
                                    }
                                    break
                                }
                            }
                        } else if (longPressFired) {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                event.changes.forEach { it.consume() }
                                if (event.changes.all { !it.pressed || it.changedToUpIgnoreConsumed() }) {
                                    break
                                }
                            }
                        }
                    }
                }
                .testTag("vio_mascot"),
            contentAlignment = Alignment.Center
        ) {
            VioOrbAvatar(
                mood = mood,
                size = mascotSize,
                isAnimationsPaused = isAnimationsPaused || isDragging,
                bounceTrigger = tapBounceCounter,
                spinTrigger = spinCounter,
                trickSpinTrigger = trickSpinCounter,
                localAction = localAction,
                trickCheerPhase = trickCheerPhase
            )

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = SurfaceDialog,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.2.dp, BorderNeonGlow),
                shadowElevation = 14.dp,
                modifier = Modifier.testTag("vio_context_menu")
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Dance",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = BlueLightAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        if (latestMood == VioMood.IDLE) {
                            trickCheerPhase = false
                            localAction = VioLocalAction.DANCE
                        }
                    },
                    modifier = Modifier.testTag("vio_menu_dance")
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Do a trick",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = BlueLightAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        if (latestMood == VioMood.IDLE) {
                            trickCheerPhase = false
                            localAction = VioLocalAction.TRICK
                        }
                    },
                    modifier = Modifier.testTag("vio_menu_trick")
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Take a nap",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Bedtime,
                            contentDescription = null,
                            tint = BlueLightAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        if (latestMood == VioMood.IDLE) {
                            trickCheerPhase = false
                            localAction = VioLocalAction.NAP
                        }
                    },
                    modifier = Modifier.testTag("vio_menu_nap")
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Hide Vio",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            tint = BlueLightAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onHideVio()
                    },
                    modifier = Modifier.testTag("vio_menu_hide")
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Appearance settings",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = BlueLightAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onOpenAppearanceSettings()
                    },
                    modifier = Modifier.testTag("vio_menu_appearance")
                )
            }
        }
    }
}

@Composable
fun VioIntroDialog(
    showVio: Boolean,
    onToggleShowVio: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val isResumed = rememberIsAppResumed()
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .testTag("vio_intro_card"),
            cornerRadius = 26.dp,
            containerColor = SurfaceDialog,
            isGlowingActive = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VioOrbAvatar(
                    mood = VioMood.IDLE,
                    size = 78.dp,
                    isAnimationsPaused = !isResumed,
                    modifier = Modifier.testTag("vio_intro_avatar")
                )

                Text(
                    text = "Meet Vio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("vio_intro_title")
                )

                Text(
                    text = "Vio floats over Codvio, thinks while the agent works, and cheers when a task is done.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Show Vio",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Switch(
                            checked = showVio,
                            onCheckedChange = onToggleShowVio,
                            modifier = Modifier.testTag("vio_intro_show_toggle"),
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

                Spacer(modifier = Modifier.height(2.dp))

                PocketPrimaryButton(
                    text = "Let's go!",
                    onClick = onDismiss,
                    testTag = "vio_intro_dismiss_button",
                    height = 48.dp
                )
            }
        }
    }
}
