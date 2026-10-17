package com.fabxdi.dibadge.ui.my_tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TaskCardItem(
    reminder: ReminderEntity,
    isCompleted: Boolean,
    viewDate: LocalDate? = null,
    badgeText: String? = null,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    onStarClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val strokeColor = colors.onSurfaceVariant
    val accentColor = diBadgeColors.accent
    val doneCheckColor = colors.background
    val overdueRed = colors.error
    val dividerColor = colors.outline

    val today = LocalDate.now()
    val currentViewDate = viewDate ?: reminder.date ?: reminder.startDate ?: today

    val isOverdue = remember(reminder.time, isCompleted, currentViewDate) {
        if (isCompleted || currentViewDate.isAfter(today)) {
            false
        } else if (currentViewDate.isBefore(today)) {
            true
        } else {
            if (!reminder.time.isNullOrBlank()) {
                val parsedTime = parseReminderTime(reminder.time)
                parsedTime != LocalTime.MAX && parsedTime.isBefore(LocalTime.now())
            } else {
                false
            }
        }
    }

    val displayTitle = remember(reminder.title, reminder.content) {
        if (reminder.title.isNotBlank()) {
            reminder.title
        } else if (reminder.content.isNotBlank()) {
            reminder.content.take(35).let { if (reminder.content.length > 35) "$it..." else it }
        } else {
            "Task"
        }
    }

    val hasDetails = reminder.title.isNotBlank() && reminder.content.isNotBlank()
    val detailsText = if (hasDetails) {
        reminder.content.take(45).let { if (reminder.content.length > 45) "$it..." else it }
    } else ""

    val timeDisplay = remember(reminder.time) {
        if (reminder.time.isNullOrBlank()) {
            "Anytime"
        } else {
            reminder.time
        }
    }

    val starColor by animateColorAsState(
        targetValue = if (reminder.isImportant) accentColor else strokeColor,
        animationSpec = tween(durationMillis = 150),
        label = "StarColor"
    )

    val accessibilityRowDescription = buildString {
        append(displayTitle)
        if (hasDetails) append(". $detailsText")
        append(". $timeDisplay")
        if (isCompleted) append(". Done") else if (isOverdue) append(". Overdue. Not done") else append(". Not done")
        if (reminder.isImportant) append(". Important") else append(". Not important")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityRowDescription
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. CHECK CIRCLE (22dp, 48dp touch target)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onCheckedChange(!isCompleted) }
                        .semantics {
                            role = Role.Checkbox
                            contentDescription = if (isCompleted) "Mark as not done" else "Mark as done"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = CircleShape,
                            color = accentColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = doneCheckColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, strokeColor)
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 2. TEXT BLOCK (Title & Details)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isCompleted) secondaryTextColor else primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (hasDetails) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = detailsText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3. STAR & TIME COLUMN (Fixed 56dp min width, centered)
                Column(
                    modifier = Modifier.widthIn(min = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Star Icon (20dp, 48dp touch target)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(onClick = onStarClick)
                            .semantics {
                                role = Role.Button
                                contentDescription = if (reminder.isImportant) "Remove important" else "Mark as important"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (reminder.isImportant) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = starColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Time Text (12sp Regular secondaryTextColor, or soft red if overdue and not done)
                    Text(
                        text = timeDisplay,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = if (isOverdue && !isCompleted) overdueRed else secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                thickness = 1.dp,
                color = dividerColor
            )
        }
    }
}
