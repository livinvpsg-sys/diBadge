package com.fabxdi.dibadge.ui.my_tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.ReminderEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TaskCardItem(
    reminder: ReminderEntity,
    isCompleted: Boolean,
    badgeText: String? = null,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val displayTitle = remember(reminder.title, reminder.content) {
        if (reminder.title.isNotBlank()) {
            reminder.title
        } else if (reminder.content.isNotBlank()) {
            reminder.content.take(35).let { if (reminder.content.length > 35) "$it..." else it }
        } else {
            "Task"
        }
    }

    val contentPreview = remember(reminder.title, reminder.content) {
        if (reminder.title.isNotBlank() && reminder.content.isNotBlank()) {
            reminder.content.take(40).let { if (reminder.content.length > 40) "$it..." else it }
        } else {
            ""
        }
    }

    val isOverdueTime = remember(reminder.time, isCompleted, reminder.date, reminder.startDate) {
        if (isCompleted || reminder.time.isNullOrBlank()) {
            false
        } else {
            val parsedTime = parseReminderTime(reminder.time)
            if (parsedTime != LocalTime.MAX) {
                val taskDate = reminder.date ?: reminder.startDate ?: LocalDate.now()
                val today = LocalDate.now()
                if (taskDate.isBefore(today)) {
                    true
                } else if (taskDate == today) {
                    parsedTime.isBefore(LocalTime.now())
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    val softRedColor = Color(0xFFEF5350)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT SIDE: CIRCULAR CHECK BUTTON
            IconButton(
                onClick = { onCheckedChange(!isCompleted) },
                modifier = Modifier.size(32.dp)
            ) {
                if (isCompleted) {
                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = CircleShape,
                        color = Color(0xFF00A884)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    ) {
                        // Empty uncompleted circle
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // MIDDLE: TITLE & CONTENT PREVIEW
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Title
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle / Content Preview
                if (contentPreview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contentPreview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Badge (e.g. "To Do")
                if (!badgeText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // RIGHT SIDE: DUE TIME & OVERDUE LABEL
            if (!reminder.time.isNullOrBlank()) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = reminder.time.lowercase(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isOverdueTime) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = when {
                            isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            isOverdueTime -> softRedColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        }
                    )

                    if (isOverdueTime) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Overdue",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = softRedColor.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}
