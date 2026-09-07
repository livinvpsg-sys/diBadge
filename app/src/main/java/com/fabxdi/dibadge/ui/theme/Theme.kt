package com.fabxdi.dibadge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class DiBadgeSpacing(
    val default: Dp = 0.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val xxl: Dp = 64.dp
)

val LocalSpacing = staticCompositionLocalOf { DiBadgeSpacing() }

object DiBadgeTheme {
    val spacing: DiBadgeSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current
}

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
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = AccentWhite,
            onPrimary = JetBlack,
            background = JetBlack,
            onBackground = AccentWhite,
            surface = SlateSurface,
            onSurface = AccentWhite,
            onSurfaceVariant = MutedGray,
            outline = CoolAsh,
            error = ErrorRed
        )
    } else {
        lightColorScheme(
            primary = SlateCharcoal,
            onPrimary = AccentWhite,
            background = SoftOffWhite,
            onBackground = SlateCharcoal,
            surface = AccentWhite,
            onSurface = SlateCharcoal,
            surfaceVariant = PaperWhite,
            onSurfaceVariant = CoolAsh,
            outline = SoftBorder,
            error = ErrorRed
        )
    }

    CompositionLocalProvider(
        LocalSpacing provides DiBadgeSpacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}