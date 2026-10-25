package com.fabxdi.dibadge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

@Composable
fun DiBadgeFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val diBadgeColors = DiBadgeTheme.colors
    val containerColor = diBadgeColors.fabContainer
    val iconColor = diBadgeColors.fabIcon

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation = if (isPressed) 8.dp else 4.dp

    Surface(
        modifier = modifier
            .size(52.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = iconColor),
                onClick = onClick
            ),
        shape = CircleShape,
        color = containerColor,
        shadowElevation = elevation
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Plus icon: 14dp, 3.0dp stroke, rounded ends, centered
            Canvas(modifier = Modifier.size(14.dp)) {
                val strokeWidthPx = 3.0.dp.toPx()
                val cap = StrokeCap.Round

                // Horizontal line
                drawLine(
                    color = iconColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = strokeWidthPx,
                    cap = cap
                )

                // Vertical line
                drawLine(
                    color = iconColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = strokeWidthPx,
                    cap = cap
                )
            }
        }
    }
}

@Composable
fun DiBadgeExtendedFab(
    text: String,
    onClick: () -> Unit,
    contentDescription: String = text,
    modifier: Modifier = Modifier
) {
    val diBadgeColors = DiBadgeTheme.colors
    val containerColor = diBadgeColors.fabContainer
    val iconColor = diBadgeColors.fabIcon

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation = if (isPressed) 8.dp else 4.dp

    Surface(
        modifier = modifier
            .height(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = iconColor),
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(11.dp)) {
                val strokeWidthPx = 2.6.dp.toPx()
                val cap = StrokeCap.Round

                // Horizontal line
                drawLine(
                    color = iconColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = strokeWidthPx,
                    cap = cap
                )

                // Vertical line
                drawLine(
                    color = iconColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = strokeWidthPx,
                    cap = cap
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = iconColor
            )
        }
    }
}
