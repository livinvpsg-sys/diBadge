package com.fabxdi.dibadge.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class DiBadgeSpacing(
    val default: Dp = 0.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val xxl: Dp = 64.dp
)

@Immutable
data class DiBadgeColors(
    val accent: Color,
    val tabInactive: Color,
    val well: Color,
    val fabContainer: Color,
    val fabIcon: Color,
    val isDark: Boolean
)

val LocalSpacing = staticCompositionLocalOf { DiBadgeSpacing() }

val LocalDiBadgeColors = staticCompositionLocalOf {
    DiBadgeColors(
        accent = DarkAccent,
        tabInactive = DarkTabInactive,
        well = Color(0xFF12161A),
        fabContainer = Color(0xFFF2F5F7),
        fabIcon = Color(0xFF0E1114),
        isDark = true
    )
}

object DiBadgeTheme {
    val spacing: DiBadgeSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val colors: DiBadgeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDiBadgeColors.current
}

private val DarkScheme = darkColorScheme(
    primary = DarkOnBackground,
    onPrimary = DarkBackground,
    secondary = DarkOnBackground,
    onSecondary = DarkBackground,
    tertiary = DarkOnBackground,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = DarkOnBackground,
    inverseOnSurface = DarkBackground,
    inversePrimary = LightAccent,
    error = DarkError,
    onError = DarkBackground,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    surfaceContainerLowest = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkSurface
)

private val LightScheme = lightColorScheme(
    primary = LightOnBackground,
    onPrimary = LightBackground,
    secondary = LightOnBackground,
    onSecondary = LightBackground,
    tertiary = LightOnBackground,
    onTertiary = LightBackground,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = LightOnBackground,
    inverseOnSurface = LightBackground,
    inversePrimary = DarkAccent,
    error = LightError,
    onError = Color.White,
    outline = LightOutline,
    outlineVariant = LightOutline,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSurface
)

private val DarkDiBadgeColors = DiBadgeColors(
    accent = DarkAccent,
    tabInactive = DarkTabInactive,
    well = Color(0xFF12161A),
    fabContainer = Color(0xFFF2F5F7),
    fabIcon = Color(0xFF0E1114),
    isDark = true
)

private val LightDiBadgeColors = DiBadgeColors(
    accent = LightAccent,
    tabInactive = LightTabInactive,
    well = Color(0xFFECEFF2),
    fabContainer = Color(0xFF14181C),
    fabIcon = Color(0xFFFFFFFF),
    isDark = false
)

@Composable
fun DiBadgeTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val diBadgeColors = if (darkTheme) DarkDiBadgeColors else LightDiBadgeColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides DiBadgeSpacing(),
        LocalDiBadgeColors provides diBadgeColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
