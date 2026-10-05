package com.fabxdi.dibadge.ui.my_tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.ui.my_tasks.to_do.ReminderFormScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    onBack: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    initialReminderIdToEdit: Int? = null,
    openSnoozeTimePicker: Boolean = false,
    initialSnoozeReminder: ReminderEntity? = null,
    onSnoozeHandled: () -> Unit = {},
    reminderViewModel: ReminderViewModel = viewModel()
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTaskFilter by remember { mutableStateOf("To do") }

    val allReminders by reminderViewModel.allReminders.collectAsState(initial = emptyList())

    var showReminderForm by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var shouldOpenSnooze by remember { mutableStateOf(false) }

    LaunchedEffect(initialSnoozeReminder) {
        if (initialSnoozeReminder != null) {
            editingReminder = initialSnoozeReminder
            shouldOpenSnooze = true
            showReminderForm = true
        }
    }

    val sortedReminders = remember(allReminders, selectedDate) {
        allReminders.filter { isReminderOnDate(it, selectedDate) }
            .sortedBy { parseReminderTime(it.time) }
    }

    val uncompletedTasks = remember(sortedReminders, selectedDate) {
        val dateStr = selectedDate.toString()
        sortedReminders.filter { !it.completedDates.contains(dateStr) }
    }

    val completedTasks = remember(sortedReminders, selectedDate) {
        val dateStr = selectedDate.toString()
        sortedReminders.filter { it.completedDates.contains(dateStr) }
    }

    if (showReminderForm) {
        ReminderFormScreen(
            reminderToEdit = editingReminder,
            autoOpenTimePicker = shouldOpenSnooze,
            onSave = { title, content, date, start, end, repeat, time, isAlarm, attachments, id ->
                reminderViewModel.saveReminder(
                    title,
                    content,
                    date,
                    start ?: date,
                    end,
                    repeat,
                    time,
                    isAlarm,
                    attachments,
                    id
                )
                showReminderForm = false
                editingReminder = null
                shouldOpenSnooze = false
                onSnoozeHandled()
            },
            onDelete = { reminder ->
                reminderViewModel.deleteReminder(reminder)
                showReminderForm = false
                editingReminder = null
                shouldOpenSnooze = false
                onSnoozeHandled()
            },
            onCancel = {
                showReminderForm = false
                editingReminder = null
                shouldOpenSnooze = false
                onSnoozeHandled()
            }
        )
        return
    }

    val dayOfWeekFormatter = remember { DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()) }
    val fullDateDisplayFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()) }
    val fullDateFormatter = remember { DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()) }

    val topHeaderLabel = remember(selectedDate) {
        val dayName = selectedDate.format(dayOfWeekFormatter)
        if (selectedDate == LocalDate.now()) {
            "Today · $dayName"
        } else {
            dayName
        }
    }

    val todayDate = remember { LocalDate.now() }
    val overdueTasks = remember(allReminders, todayDate) {
        allReminders.filter { reminder ->
            val taskDate = reminder.date ?: reminder.startDate
            taskDate != null && taskDate.isBefore(todayDate) && !reminder.completedDates.contains(taskDate.toString())
        }.sortedBy { parseReminderTime(it.time) }
    }
    val overdueCount = overdueTasks.size

    val uncompletedCount = uncompletedTasks.size
    val assignedCount = 0

    val toDoLabel = if (uncompletedCount > 0) "To do $uncompletedCount" else "To do"
    val assignedLabel = if (assignedCount > 0) "Assigned to me $assignedCount" else "Assigned to me"
    val overdueLabel = if (overdueCount > 0) "Overdue $overdueCount" else "Overdue"

    val taskFilters = remember(toDoLabel, assignedLabel, overdueLabel) {
        listOf(
            "To do" to toDoLabel,
            "Assigned to me" to assignedLabel,
            "Overdue" to overdueLabel
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "Pick Date",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (selectedTaskFilter == "To do") {
                FloatingActionButton(
                    onClick = {
                        editingReminder = null
                        showReminderForm = true
                    },
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    ),
                    modifier = Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(16.dp)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Reminder",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Section: Day Name ("Today · Friday") and Main Date ("2 October 2026")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DiBadgeTheme.spacing.medium, vertical = 2.dp)
            ) {
                Text(
                    text = topHeaderLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = selectedDate.format(fullDateDisplayFormatter),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Task Filter Pills ("To do", "Assigned to me", "Overdue")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DiBadgeTheme.spacing.medium, vertical = 8.dp)
            ) {
                taskFilters.forEach { (filterKey, filterLabel) ->
                    val isSelected = selectedTaskFilter == filterKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTaskFilter = filterKey },
                        label = {
                            Text(
                                text = filterLabel,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.Transparent,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            selectedBorderColor = MaterialTheme.colorScheme.onSurface,
                            selectedBorderWidth = 1.5.dp
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // Task Content List View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (selectedTaskFilter == "To do") {
                    if (sortedReminders.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val emptyToDoText = if (selectedDate == LocalDate.now()) {
                                "No to-do tasks for today"
                            } else {
                                "No to-do tasks for ${selectedDate.format(fullDateFormatter)}"
                            }
                            Text(
                                text = emptyToDoText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = DiBadgeTheme.spacing.medium, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. UNCOMPLETED TASKS
                            items(uncompletedTasks, key = { it.id }) { reminder ->
                                TaskCardItem(
                                    reminder = reminder,
                                    isCompleted = false,
                                    onCheckedChange = {
                                        reminderViewModel.toggleTaskCompletedForDate(reminder, selectedDate)
                                    },
                                    onClick = {
                                        editingReminder = reminder
                                        showReminderForm = true
                                    }
                                )
                            }

                            // 2. COMPLETED TASKS
                            if (completedTasks.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Completed (${completedTasks.size})",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                items(completedTasks, key = { it.id }) { reminder ->
                                    TaskCardItem(
                                        reminder = reminder,
                                        isCompleted = true,
                                        onCheckedChange = {
                                            reminderViewModel.toggleTaskCompletedForDate(reminder, selectedDate)
                                        },
                                        onClick = {
                                            editingReminder = reminder
                                            showReminderForm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else if (selectedTaskFilter == "Overdue") {
                    if (overdueTasks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
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
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = DiBadgeTheme.spacing.medium, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(overdueTasks, key = { it.id }) { reminder ->
                                val taskDate = reminder.date ?: reminder.startDate ?: todayDate
                                TaskCardItem(
                                    reminder = reminder,
                                    isCompleted = false,
                                    onCheckedChange = {
                                        reminderViewModel.toggleTaskCompletedForDate(reminder, taskDate)
                                    },
                                    onClick = {
                                        editingReminder = reminder
                                        showReminderForm = true
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val emptyAssignedText = if (selectedDate == LocalDate.now()) {
                            "No assigned tasks for today"
                        } else {
                            "No assigned tasks for ${selectedDate.format(fullDateFormatter)}"
                        }
                        Text(
                            text = emptyAssignedText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        TaskDatePickerDialog(
            initialDate = selectedDate,
            allReminders = allReminders,
            onDateSelected = { chosenDate ->
                selectedDate = chosenDate
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}
