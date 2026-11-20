package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Immutable
data class PocketColorPalette(
    val isDark: Boolean,
    val bgNearBlack: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val surfaceDialog: Color,
    val surfaceTerminal: Color,
    val borderSubtle: Color,
    val borderActive: Color,
    val borderNeonGlow: Color,
    val glassTopHighlight: Color,
    val glassShineTop: Color,
    val violetPrimary: Color,
    val indigoPrimary: Color,
    val violetGlow: Color,
    val blueContainer: Color,
    val blueLightAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val liveGreen: Color,
    val liveGreenContainer: Color,
    val liveGreenBorder: Color,
    val liveGreenHighlight: Color,
    val warningAmber: Color,
    val warningAmberContainer: Color,
    val errorRed: Color,
    val errorContainer: Color,
    val cardShadowAmbient: Color
)

val DarkVioletPalette = PocketColorPalette(
    isDark = true,
    bgNearBlack = Color(0xFF0D0A1A),
    surfaceCard = Color(0xD91B1533),
    surfaceElevated = Color(0xDE231B42),
    surfaceDialog = Color(0xEB17112D),
    surfaceTerminal = Color(0xE6090714),
    borderSubtle = Color(0x428B5CF6),
    borderActive = Color(0x7A9F7AEA),
    borderNeonGlow = Color(0xCCC4B5FD),
    glassTopHighlight = Color(0x88DDD6FE),
    glassShineTop = Color(0x2EFFFFFF),
    violetPrimary = Color(0xFF8B5CF6),
    indigoPrimary = Color(0xFF6D28D9),
    violetGlow = Color(0x808B5CF6),
    blueContainer = Color(0x526D28D9),
    blueLightAccent = Color(0xFFC4B5FD),
    textPrimary = Color(0xFFF5F3FF),
    textSecondary = Color(0xFFA79FC2),
    textMuted = Color(0xFF7B7299),
    liveGreen = Color(0xFF22C55E),
    liveGreenContainer = Color(0xD90E291D),
    liveGreenBorder = Color(0x664ADE80),
    liveGreenHighlight = Color(0x66BBF7D0),
    warningAmber = Color(0xFFFBBF24),
    warningAmberContainer = Color(0xD92B1F0E),
    errorRed = Color(0xFFFB7185),
    errorContainer = Color(0xD92D121D),
    cardShadowAmbient = Color(0xB304020B)
)

val LightVioletPalette = PocketColorPalette(
    isDark = false,
    bgNearBlack = Color(0xFFF6F3FF),
    surfaceCard = Color(0xEBFFFFFF),
    surfaceElevated = Color(0xF2EFEAFF),
    surfaceDialog = Color(0xFAFAF8FF),
    surfaceTerminal = Color(0xFFEDE8FA),
    borderSubtle = Color(0x367C3AED),
    borderActive = Color(0x6E7C3AED),
    borderNeonGlow = Color(0xB87C3AED),
    glassTopHighlight = Color(0xD9FFFFFF),
    glassShineTop = Color(0x40FFFFFF),
    violetPrimary = Color(0xFF7C3AED),
    indigoPrimary = Color(0xFF5B21B6),
    violetGlow = Color(0x4A7C3AED),
    blueContainer = Color(0x247C3AED),
    blueLightAccent = Color(0xFF6D28D9),
    textPrimary = Color(0xFF1A1033),
    textSecondary = Color(0xFF544875),
    textMuted = Color(0xFF6A5F8A),
    liveGreen = Color(0xFF15803D),
    liveGreenContainer = Color(0xE8DCFCE7),
    liveGreenBorder = Color(0x6616A34A),
    liveGreenHighlight = Color(0xB3FFFFFF),
    warningAmber = Color(0xFFB45309),
    warningAmberContainer = Color(0xE8FEF3C7),
    errorRed = Color(0xFFE11D48),
    errorContainer = Color(0xEBFFE4E6),
    cardShadowAmbient = Color(0x284C1D95)
)

fun buildDynamicPocketPalette(
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    primaryContainer: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color
): PocketColorPalette {
    val base = if (isDark) DarkVioletPalette else LightVioletPalette

    // Keep glossy button gradient rich enough for crisp white foreground text
    val buttonStart = if (primary.luminance() > 0.30f) {
        lerp(primary, Color(0xFF1A1526), 0.48f)
    } else {
        primary
    }
    val buttonEnd = lerp(buttonStart, Color.Black, if (isDark) 0.28f else 0.22f)
    val accent = if (isDark) {
        if (primary.luminance() < 0.40f) lerp(primary, Color.White, 0.45f) else primary
    } else {
        buttonStart
    }

    val tintedBg = lerp(base.bgNearBlack, background, 0.55f)
    val tintedCard = lerp(base.surfaceCard, surface, 0.48f).copy(alpha = base.surfaceCard.alpha)
    val tintedElevated = lerp(base.surfaceElevated, surfaceVariant, 0.50f).copy(alpha = base.surfaceElevated.alpha)
    val tintedDialog = lerp(base.surfaceDialog, surface, 0.50f).copy(alpha = base.surfaceDialog.alpha)

    return base.copy(
        bgNearBlack = tintedBg,
        surfaceCard = tintedCard,
        surfaceElevated = tintedElevated,
        surfaceDialog = tintedDialog,
        surfaceTerminal = if (isDark) lerp(base.surfaceTerminal, background, 0.4f) else lerp(base.surfaceTerminal, surfaceVariant, 0.5f),
        borderSubtle = accent.copy(alpha = if (isDark) 0.26f else 0.22f),
        borderActive = accent.copy(alpha = if (isDark) 0.48f else 0.44f),
        borderNeonGlow = accent.copy(alpha = if (isDark) 0.80f else 0.72f),
        glassTopHighlight = if (isDark) accent.copy(alpha = 0.52f) else base.glassTopHighlight,
        violetPrimary = buttonStart,
        indigoPrimary = buttonEnd,
        violetGlow = buttonStart.copy(alpha = if (isDark) 0.48f else 0.30f),
        blueContainer = primaryContainer.copy(alpha = if (isDark) 0.55f else 0.45f),
        blueLightAccent = accent,
        cardShadowAmbient = if (isDark) base.cardShadowAmbient else secondary.copy(alpha = 0.16f)
    )
}

val LocalPocketColors = staticCompositionLocalOf { DarkVioletPalette }

val BgNearBlack: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.bgNearBlack

val SurfaceCard: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.surfaceCard

val SurfaceElevated: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.surfaceElevated

val SurfaceDialog: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.surfaceDialog

val SurfaceTerminal: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.surfaceTerminal

val BorderSubtle: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.borderSubtle

val BorderActive: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.borderActive

val BorderNeonGlow: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.borderNeonGlow

val GlassTopHighlight: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.glassTopHighlight

val GlassShineTop: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.glassShineTop

val VioletPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.violetPrimary

val IndigoPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.indigoPrimary

val VioletGlow: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.violetGlow

val BluePrimary: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.violetPrimary

val BluePrimaryPressed: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.indigoPrimary

val BlueContainer: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.blueContainer

val BlueLightAccent: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.blueLightAccent

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.textSecondary

val TextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.textMuted

val LiveGreen: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.liveGreen

val LiveGreenContainer: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.liveGreenContainer

val LiveGreenBorder: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.liveGreenBorder

val LiveGreenHighlight: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.liveGreenHighlight

val WarningAmber: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.warningAmber

val WarningAmberContainer: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.warningAmberContainer

val ErrorRed: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.errorRed

val ErrorContainer: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.errorContainer

val CardShadowAmbient: Color
    @Composable @ReadOnlyComposable get() = LocalPocketColors.current.cardShadowAmbient
