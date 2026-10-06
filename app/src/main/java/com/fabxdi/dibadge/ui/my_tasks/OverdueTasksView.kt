package com.fabxdi.dibadge.ui.my_tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class OverdueGroup(
    val dateLabel: String,
    val date: LocalDate,
    val tasks: List<ReminderEntity>
)

@Composable
fun OverdueTasksView(
    allReminders: List<ReminderEntity>,
    onToggleCompleted: (ReminderEntity, LocalDate) -> Unit,
    onTaskClick: (ReminderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayDate = remember { LocalDate.now() }
    val nowTime = remember { LocalTime.now() }

    val overdueGroups = remember(allReminders, todayDate, nowTime) {
        val overdueTasksWithDate = allReminders.flatMap { reminder ->
            val overduePairs = mutableListOf<Pair<LocalDate, ReminderEntity>>()
            val baseStart = reminder.startDate ?: reminder.date ?: todayDate
            val searchStart = if (baseStart.isAfter(todayDate.minusDays(60))) baseStart else todayDate.minusDays(60)

            var currDate = searchStart
            while (!currDate.isAfter(todayDate)) {
                val currDateStr = currDate.toString()
                val isCompleted = reminder.completedDates.contains(currDateStr)

                if (!isCompleted && isReminderOnDate(reminder, currDate)) {
                    if (currDate.isBefore(todayDate)) {
                        // Past date (including Yesterday): date is overdue!
                        overduePairs.add(currDate to reminder)
                    } else if (currDate == todayDate && !reminder.time.isNullOrBlank()) {
                        // Today: overdue if scheduled time has passed!
                        val parsedTime = parseReminderTime(reminder.time)
                        if (parsedTime != LocalTime.MAX && parsedTime.isBefore(nowTime)) {
                            overduePairs.add(todayDate to reminder)
                        }
                    }
                }
                currDate = currDate.plusDays(1)
            }
            overduePairs
        }

        val grouped = overdueTasksWithDate.groupBy({ it.first }, { it.second })
            .toSortedMap(Comparator { d1, d2 -> d2.compareTo(d1) })

        val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())

        grouped.map { (date, tasks) ->
            val label = when (date) {
                todayDate -> "Today"
                todayDate.minusDays(1) -> "Yesterday"
                else -> date.format(dateFormatter)
            }
            val sortedTasks = tasks.sortedBy { parseReminderTime(it.time) }
            OverdueGroup(label, date, sortedTasks)
        }
    }

    if (overdueGroups.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No overdue tasks",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = DiBadgeTheme.spacing.medium, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            overdueGroups.forEach { group ->
                // Date Section Header (small font, subtle color, no background card)
                item(key = "header_${group.dateLabel}_${group.date}") {
                    Text(
                        text = group.dateLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                    )
                }

                // Tasks in this date group
                items(group.tasks, key = { "task_${group.date}_${it.id}" }) { reminder ->
                    TaskCardItem(
                        reminder = reminder,
                        isCompleted = false,
                        badgeText = "To Do",
                        onCheckedChange = {
                            onToggleCompleted(reminder, group.date)
                        },
                        onClick = {
                            onTaskClick(reminder)
                        }
                    )
                }
            }
        }
    }
}
