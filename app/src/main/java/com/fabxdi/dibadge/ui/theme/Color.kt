package com.fabxdi.dibadge.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// diBadge palette: charcoal / cool grey with indigo.
// Do not use these directly in screens: read them from MaterialTheme.colorScheme
// or DiBadgeTheme.colors so light and dark stay in sync.
// ---------------------------------------------------------------------------

// Dark theme: charcoal page, slightly lighter cards, one indigo accent
val DarkBackground       = Color(0xFF1F1F21)
val DarkSurface          = Color(0xFF2A2A2D) // cards, chips, message box, sheets, dialogs
val DarkOutline          = Color(0xFF3A3A3F) // hairline borders and dividers
val DarkOnBackground     = Color(0xFFF2F2F4)
val DarkOnSurfaceVariant = Color(0xFFA3A3AA)
val DarkTabInactive      = Color(0xFF7C7C84)
val DarkAccent           = Color(0xFF8F9CD8) // indigo text: names, Send, links
val DarkAccentFill       = Color(0xFF5B6BC4) // filled actions: + button, Approve, own bubbles, badges
val DarkError            = Color(0xFFE8837A)
val DarkContainer        = Color(0xFF2C3550) // selected chips, table label cells, avatar tint
val DarkErrorContainer   = Color(0xFF3A1F1D)
val DarkControlBorder    = Color(0xFF5E5E66) // outlines of chips/buttons/inputs
val DarkRaised           = Color(0xFF34343A) // surfaces above a card (audio tiles, pickers)

// Light theme: cool light page, white cards, deep indigo accent
val LightBackground       = Color(0xFFF5F6F8)
val LightSurface          = Color(0xFFFFFFFF) // cards, message box, sheets, dialogs
val LightOutline          = Color(0xFFE3E5EA)
val LightOnBackground     = Color(0xFF15171C)
val LightOnSurfaceVariant = Color(0xFF667085)
val LightTabInactive      = Color(0xFF7A8090)
val LightAccent           = Color(0xFF3A4694)
val LightAccentFill       = Color(0xFF3A4694)
val LightError            = Color(0xFFC4433A)
val LightContainer        = Color(0xFFE4E8F6)
val LightErrorContainer   = Color(0xFFF6DAD7)
val LightOnErrorContainer = Color(0xFF7A1F19)
val LightControlBorder    = Color(0xFF7E868E)
val LightRaised           = Color(0xFFE9EBEF)

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