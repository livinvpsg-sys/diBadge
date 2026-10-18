package com.fabxdi.dibadge.ui.theme

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val DISABLED_ALPHA = 0.45f

@Composable
fun DiBadgeStatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Uses the app's resolved theme, not the system setting.
    val containerAlpha = if (DiBadgeTheme.colors.isDark) 0.22f else 0.12f

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

/**
 * Chip / tab style button.
 * Unselected: 1dp outline border, secondary text.
 * Selected: 1dp primary-text border, primary text, bold. No fill, no accent.
 * Disabled: 45% alpha.
 */
@Composable
fun DiBadgeButton(
    text: String,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    val borderColor = if (isSelected) colors.onBackground else colors.outline
    val contentColor = if (isSelected) colors.onBackground else colors.onSurfaceVariant
    val alpha = if (isEnabled) 1f else DISABLED_ALPHA

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(width = 1.dp, color = borderColor.copy(alpha = alpha)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = contentColor.copy(alpha = alpha),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

/**
 * Filter chip / tab pill used in the My Tasks header (Today, Assigned to me, Overdue 1).
 * 32dp high, fully rounded, 14dp horizontal padding, 13sp, always one line.
 * Unselected: 1dp outline border, secondary text, regular.
 * Selected: 1dp primary-text border, primary text, bold. No fill, no accent.
 * Width is reserved for the bold label so selecting a chip never moves its neighbours.
 */
@Composable
fun DiBadgeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val borderColor = if (selected) colors.onBackground else colors.outline
    val contentColor = if (selected) colors.onBackground else colors.onSurfaceVariant
    val base = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, letterSpacing = 0.sp)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            // Invisible bold copy reserves the widest width.
            Text(
                text = text,
                style = base.copy(fontWeight = FontWeight.Bold),
                color = Color.Transparent,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics { }
            )
            Text(
                text = text,
                style = base.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
fun DiBadgeInfoMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    // Accessibility: keeps blue dark enough on light backgrounds.
    val infoColor = if (DiBadgeTheme.colors.isDark) InfoBlue else PendingBlue

    Text(
        text = message,
        color = infoColor,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(vertical = DiBadgeTheme.spacing.small)
    )
}

/**
 * Disables swipe-down dismissal on the modified content by consuming
 * all unhandled vertical drag gestures and nested scroll post-scrolls.
 * Used for bottom sheets so they can only be dragged from their handle/header.
 */
fun Modifier.disableBottomSheetDrag(): Modifier = this.then(
    Modifier
        .nestedScroll(
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    return available.copy(x = 0f)
                }
                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {
                    return available.copy(x = 0f)
                }
            }
        )
        .pointerInput(Unit) {
            detectVerticalDragGestures { _, _ -> }
        }
)
