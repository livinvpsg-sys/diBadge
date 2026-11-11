package com.fabxdi.dibadge.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Do not use these directly in screens: read them from MaterialTheme.colorScheme
// or DiBadgeTheme.colors so light and dark stay in sync.
// ---------------------------------------------------------------------------

// Dark theme
val DarkBackground       = Color(0xFF0E1114)
val DarkSurface          = Color(0xFF181C21) // bottom bar, cards, sheets, dialogs, + button
val DarkOutline          = Color(0xFF2A3037)
val DarkOnBackground     = Color(0xFFF0F3F5)
val DarkOnSurfaceVariant = Color(0xFF8A9199)
val DarkTabInactive      = Color(0xFF6E757C)
val DarkAccent           = Color(0xFF8F9CD8)
val DarkError            = Color(0xFFE8837A)
val DarkContainer        = Color(0xFF232A31) // selected chips, nav indicators, picker highlights
val DarkErrorContainer   = Color(0xFF3A1F1D)
val DarkControlBorder    = Color(0xFF5E656D) // outlines of chips/buttons/inputs: 3:1 against the page

// Light theme
val LightBackground       = Color(0xFFE8ECEF)
val LightSurface          = Color(0xFFF5F7F9) // bottom bar, cards, sheets, dialogs, + button
val LightOutline          = Color(0xFFD3D9DF)
val LightOnBackground     = Color(0xFF11161A)
val LightOnSurfaceVariant = Color(0xFF5A646D) // 5:1 on the page background (was 4.2:1)
val LightTabInactive      = Color(0xFF6F7780) // 3.8:1 (was 2.7:1)
val LightAccent           = Color(0xFF3A4694)
val LightError            = Color(0xFFC4433A)
val LightContainer        = Color(0xFFDDE2E7)
val LightErrorContainer   = Color(0xFFF6DAD7)
val LightOnErrorContainer = Color(0xFF7A1F19)
val LightControlBorder    = Color(0xFF7E868E) // 3.1:1 against the page

// Status colours (unchanged)
val InfoBlue      = Color(0xFF05C1F5)
val BadgeRed      = Color(0xFFDE0000)
val PendingBlue   = Color(0xFF2986CC)
val ApprovedGreen = Color(0xFF70CC66)
val TealGreen     = Color(0xFF008080)

// Status text/icons on LIGHT backgrounds need darker shades to be readable (4.5:1).
// In screens use DiBadgeTheme.colors.approved / pending / info, not the raw values above.
val LightApprovedGreen = Color(0xFF2E7D32)
val LightPendingBlue   = Color(0xFF1A6AA8)

// ---------------------------------------------------------------------------
// Legacy names. Old values kept so nothing changes silently. Every use is a
// leftover hard-coded colour: replace it with a theme token, then delete this
// block (the deprecation warnings list each one).
// ---------------------------------------------------------------------------
@Deprecated("Use MaterialTheme.colorScheme tokens") val JetBlack      = Color(0xFF000000)
@Deprecated("Use MaterialTheme.colorScheme tokens") val SlateCharcoal = Color(0xFF1C1C1E)
@Deprecated("Use MaterialTheme.colorScheme tokens") val SlateSurface  = Color(0xFF1E1E1E)
@Deprecated("Use MaterialTheme.colorScheme tokens") val CoolAsh       = Color(0xFF6E6E6E)
@Deprecated("Use MaterialTheme.colorScheme tokens") val MutedGray     = Color(0xFFB4B4B4)
@Deprecated("Use MaterialTheme.colorScheme tokens") val SoftBorder    = Color(0xFFE0E0E0)
@Deprecated("Use MaterialTheme.colorScheme tokens") val SoftOffWhite  = Color(0xFFEBECEF)
@Deprecated("Use MaterialTheme.colorScheme tokens") val PaperWhite    = Color(0xFFF4F4F6)
@Deprecated("Use MaterialTheme.colorScheme tokens") val AccentWhite   = Color(0xFFFFFFFF)
@Deprecated("Use MaterialTheme.colorScheme.error")  val ErrorRed      = Color(0xFFBF2C34)