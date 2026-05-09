package com.fitness.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Direction-A semantic palette exposed via CompositionLocal so screens can
 * pull surface/fg/line/accent variants without round-tripping through
 * MaterialTheme.colorScheme (which is a lossy mapping for non-M3 tokens).
 */
data class FitnessColors(
    val bg: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
    val surface2: androidx.compose.ui.graphics.Color,
    val fg: androidx.compose.ui.graphics.Color,
    val fgDim: androidx.compose.ui.graphics.Color,
    val fgFaint: androidx.compose.ui.graphics.Color,
    val line: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
    val onAccent: androidx.compose.ui.graphics.Color,
    val success: androidx.compose.ui.graphics.Color,
    val isDark: Boolean
)

private val LightFit = FitnessColors(
    bg = BgLight, surface = SurfaceLight, surface2 = Surface2Light,
    fg = FgLight, fgDim = FgDimLight, fgFaint = FgFaintLight, line = LineLight,
    accent = AccentOrange, onAccent = OnAccent, success = Success, isDark = false
)

private val DarkFit = FitnessColors(
    bg = BgDark, surface = SurfaceDark, surface2 = Surface2Dark,
    fg = FgDark, fgDim = FgDimDark, fgFaint = FgFaintDark, line = LineDark,
    accent = AccentOrange, onAccent = OnAccent, success = Success, isDark = true
)

val LocalFitnessColors = staticCompositionLocalOf { LightFit }

private val LightColors = lightColorScheme(
    primary = AccentOrange,
    onPrimary = OnAccent,
    primaryContainer = AccentOrangeDim,
    onPrimaryContainer = AccentOrange,
    secondary = FgLight,
    onSecondary = SurfaceLight,
    background = BgLight,
    onBackground = FgLight,
    surface = SurfaceLight,
    onSurface = FgLight,
    surfaceVariant = Surface2Light,
    onSurfaceVariant = FgDimLight,
    outline = LineLight,
    outlineVariant = FgFaintLight,
    tertiary = Success,
    onTertiary = OnAccent
)

private val DarkColors = darkColorScheme(
    primary = AccentOrange,
    onPrimary = OnAccent,
    primaryContainer = AccentOrangeDim,
    onPrimaryContainer = AccentOrange,
    secondary = FgDark,
    onSecondary = SurfaceDark,
    background = BgDark,
    onBackground = FgDark,
    surface = SurfaceDark,
    onSurface = FgDark,
    surfaceVariant = Surface2Dark,
    onSurfaceVariant = FgDimDark,
    outline = LineDark,
    outlineVariant = FgFaintDark,
    tertiary = Success,
    onTertiary = OnAccent
)

@Composable
fun FitnessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic color is intentionally off — the brand orange is core to the
    // identity. We still respect system dark/light.
    val colors = if (darkTheme) DarkColors else LightColors
    val fit = if (darkTheme) DarkFit else LightFit

    CompositionLocalProvider(LocalFitnessColors provides fit) {
        MaterialTheme(
            colorScheme = colors,
            typography = FitnessTypography,
            content = content
        )
    }
}
