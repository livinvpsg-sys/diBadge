package com.fabxdi.dibadge.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiBadgeStatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val containerAlpha = if (isSystemInDarkTheme()) 0.22f else 0.12f

    Surface(
        color = color.copy(alpha = containerAlpha),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.wrapContentSize()
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        )
    }
}

@Composable
fun DiBadgeButton(
    text: String,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    // Logic for monochromatic selection
    val containerColor = if (isSelected) colors.onBackground else Color.Transparent
    val contentColor = if (isSelected) colors.background else colors.onBackground
    val borderStroke = if (isSelected) null else BorderStroke(
        width = 1.dp,
        color = colors.outline.copy(alpha = if (isEnabled) 1f else 0.3f)
    )

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        border = borderStroke,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isEnabled) contentColor else colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun DiBadgeInfoMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    // Accessibility: Ensures blue is dark enough for light backgrounds
    val infoColor = if (isSystemInDarkTheme()) InfoBlue else PendingBlue

    Text(
        text = message,
        color = infoColor,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(vertical = DiBadgeTheme.spacing.small)
    )
}