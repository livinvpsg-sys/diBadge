package com.fabxdi.dibadge.ui.my_tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.ReminderEntity
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
                        // Past date: overdue!
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

        // Group by date and sort latest date first!
        val grouped = overdueTasksWithDate.groupBy({ it.first }, { it.second })
            .toSortedMap(Comparator { d1, d2 -> d2.compareTo(d1) })

        val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

        grouped.map { (date, tasks) ->
            val label = when (date) {
                todayDate -> "Today"
                todayDate.minusDays(1) -> "Yesterday"
                else -> date.format(dateFormatter)
            }
            val sortedTasks = tasks.sortedByDescending { parseReminderTime(it.time) }
            OverdueGroup(label, date, sortedTasks)
        }
    }

    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBgColor = MaterialTheme.colorScheme.surface

    if (overdueGroups.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No overdue tasks",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.Top
        ) {
            overdueGroups.forEach { group ->
                // Date Group Heading (12sp bold, onSurfaceVariant, 20dp left inset)
                item(key = "header_${group.dateLabel}_${group.date}") {
                    Text(
                        text = group.dateLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 6.dp)
                    )
                }

                // Date Group Block Card (surface, shapes.medium 14dp, 20dp side margin)
                item(key = "group_block_${group.dateLabel}_${group.date}") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = cardBgColor,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            group.tasks.forEachIndexed { index, reminder ->
                                TaskCardItem(
                                    reminder = reminder,
                                    isCompleted = false,
                                    viewDate = group.date,
                                    showDivider = index < group.tasks.lastIndex,
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
        }
    }
}
