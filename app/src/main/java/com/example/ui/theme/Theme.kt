package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeMode

private fun buildDarkColorScheme(palette: PocketColorPalette) = darkColorScheme(
    primary = palette.violetPrimary,
    onPrimary = Color.White,
    primaryContainer = palette.blueContainer,
    onPrimaryContainer = palette.blueLightAccent,
    secondary = palette.liveGreen,
    onSecondary = Color.Black,
    secondaryContainer = palette.liveGreenContainer,
    onSecondaryContainer = palette.liveGreen,
    tertiary = palette.blueLightAccent,
    onTertiary = palette.bgNearBlack,
    background = palette.bgNearBlack,
    onBackground = palette.textPrimary,
    surface = palette.surfaceCard,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.surfaceElevated,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.borderSubtle,
    outlineVariant = palette.borderActive,
    error = palette.errorRed,
    onError = Color.Black
)

private fun buildLightColorScheme(palette: PocketColorPalette) = lightColorScheme(
    primary = palette.violetPrimary,
    onPrimary = Color.White,
    primaryContainer = palette.blueContainer,
    onPrimaryContainer = palette.blueLightAccent,
    secondary = palette.liveGreen,
    onSecondary = Color.White,
    secondaryContainer = palette.liveGreenContainer,
    onSecondaryContainer = palette.liveGreen,
    tertiary = palette.blueLightAccent,
    onTertiary = Color.White,
    background = palette.bgNearBlack,
    onBackground = palette.textPrimary,
    surface = palette.surfaceCard,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.surfaceElevated,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.borderSubtle,
    outlineVariant = palette.borderActive,
    error = palette.errorRed,
    onError = Color.White
)

val PocketCodeShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun resolveIsDarkTheme(themeMode: AppThemeMode): Boolean {
    return when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
}

@Composable
fun PocketCodeTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColorEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = resolveIsDarkTheme(themeMode)
    val context = LocalContext.current

    val useDynamic = dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicScheme = if (useDynamic) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }

    val palette = if (dynamicScheme != null) {
        buildDynamicPocketPalette(
            isDark = isDark,
            primary = dynamicScheme.primary,
            secondary = dynamicScheme.secondary,
            primaryContainer = dynamicScheme.primaryContainer,
            background = dynamicScheme.background,
            surface = dynamicScheme.surface,
            surfaceVariant = dynamicScheme.surfaceVariant
        )
    } else {
        if (isDark) DarkVioletPalette else LightVioletPalette
    }

    val colorScheme = if (dynamicScheme != null) {
        dynamicScheme.copy(
            background = palette.bgNearBlack,
            onBackground = palette.textPrimary,
            surface = palette.surfaceCard,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary
        )
    } else {
        if (isDark) buildDarkColorScheme(palette) else buildLightColorScheme(palette)
    }

    CompositionLocalProvider(LocalPocketColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = PocketCodeShapes,
            content = content
        )
    }
}
