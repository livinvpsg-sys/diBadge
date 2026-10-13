package com.fabxdi.dibadge.ui.my_tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.fabxdi.dibadge.data.ToDoEntity
import java.time.LocalDate
import java.time.LocalTime

// Color Tokens
val RowTitleColor = Color(0xFFF5F5F5)
val RowDetailsColor = Color(0xFF77777C)
val RowUncompletedRingColor = Color(0xFFCFCFD2)
val RowDoneTeal = Color(0xFF14B8A6)
val RowDoneCheckMarkColor = Color(0xFF00201D)
val RowOverdueRed = Color(0xFFFF8A80)
val RowStarUnimportant = Color(0xFF3A3A3C)
val RowDividerColor = Color(0xFF2A2A2C)

@Composable
fun TaskCardItem(
    reminder: ReminderEntity,
    isCompleted: Boolean,
    viewDate: LocalDate? = null,
    badgeText: String? = null,
    onCheckedChange: (Boolean) -> Unit,
    onStarClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val today = LocalDate.now()
    val currentViewDate = viewDate ?: reminder.date ?: reminder.startDate ?: today

    val isOverdue = remember(reminder.time, isCompleted, currentViewDate) {
        if (isCompleted || currentViewDate.isAfter(today)) {
            false
        } else if (currentViewDate.isBefore(today)) {
            true
        } else {
            // Today
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
            reminder.time.lowercase()
        }
    }

    val ringColor by animateColorAsState(
        targetValue = when {
            isCompleted -> RowDoneTeal
            isOverdue -> RowOverdueRed
            else -> RowUncompletedRingColor
        },
        animationSpec = tween(durationMillis = 150),
        label = "RingColor"
    )

    val starColor by animateColorAsState(
        targetValue = if (reminder.isImportant) RowDoneTeal else RowStarUnimportant,
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
                .heightIn(min = 66.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. CHECK CIRCLE (22dp, 48dp touch target, 14dp gap)
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
                            color = ringColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = RowDoneCheckMarkColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, ringColor)
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.width(6.dp)) // 6dp + 8dp inside 48dp box = 14dp gap to text

                // 2. TEXT BLOCK (Remaining width, vertically centered)
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
                        color = if (isCompleted) RowDetailsColor else RowTitleColor,
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
                            color = RowDetailsColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3. STAR AND TIME COLUMN (Fixed width 56dp, items centered, 4dp apart)
                Column(
                    modifier = Modifier.width(56.dp),
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

                    // Time Text (12sp Regular #77777C, or #FF8A80 if overdue and not done)
                    Text(
                        text = timeDisplay,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = if (isOverdue && !isCompleted) RowOverdueRed else RowDetailsColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 1dp Divider under EVERY row
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            thickness = 1.dp,
            color = RowDividerColor
        )
    }
}
