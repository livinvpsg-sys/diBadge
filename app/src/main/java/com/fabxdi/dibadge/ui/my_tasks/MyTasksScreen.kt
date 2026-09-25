package com.fabxdi.dibadge.ui.my_tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.ui.my_tasks.reminder.ReminderFormScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun isReminderOnDate(reminder: ReminderEntity, targetDate: LocalDate): Boolean {
    val repeat = reminder.repeatType.lowercase()
    return when {
        repeat == "once" || repeat.isBlank() -> {
            reminder.date == targetDate || reminder.startDate == targetDate
        }
        repeat == "daily" -> {
            val start = reminder.date ?: reminder.startDate ?: return false
            !targetDate.isBefore(start) && !targetDate.isAfter(start.plusMonths(12))
        }
        repeat == "weekly" -> {
            val start = reminder.date ?: reminder.startDate ?: return false
            !targetDate.isBefore(start) && targetDate.dayOfWeek == start.dayOfWeek && !targetDate.isAfter(start.plusYears(2))
        }
        repeat == "monthly" -> {
            val start = reminder.date ?: reminder.startDate ?: return false
            !targetDate.isBefore(start) && targetDate.dayOfMonth == start.dayOfMonth && !targetDate.isAfter(start.plusYears(3))
        }
        repeat == "yearly" -> {
            val start = reminder.date ?: reminder.startDate ?: return false
            !targetDate.isBefore(start) && targetDate.dayOfMonth == start.dayOfMonth && targetDate.month == start.month
        }
        repeat == "custom" -> {
            if (reminder.startDate != null && reminder.endDate != null) {
                !targetDate.isBefore(reminder.startDate) && !targetDate.isAfter(reminder.endDate)
            } else {
                reminder.date == targetDate || reminder.startDate == targetDate
            }
        }
        else -> reminder.date == targetDate || reminder.startDate == targetDate
    }
}

fun parseReminderTime(timeStr: String?): LocalTime {
    if (timeStr.isNullOrBlank()) return LocalTime.MAX
    return try {
        LocalTime.parse(timeStr.trim().uppercase(), DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
    } catch (e1: Exception) {
        try {
            LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        } catch (e2: Exception) {
            LocalTime.MAX
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    reminderViewModel: ReminderViewModel = viewModel()
) {
    BackHandler(onBack = onBack)

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTaskFilter by remember { mutableStateOf("To do") }
    val taskFilters = listOf("To do", "Assigned")
    var showDatePicker by remember { mutableStateOf(false) }
    var showReminderForm by remember { mutableStateOf(false) }

    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var completedTaskIds by remember { mutableStateOf(setOf<Int>()) }

    val allReminders by reminderViewModel.allReminders.collectAsState(initial = emptyList())

    // 1. Filter reminders for selectedDate according to repeat rules
    val dateReminders = remember(allReminders, selectedDate) {
        allReminders.filter { reminder ->
            isReminderOnDate(reminder, selectedDate)
        }
    }

    // 2. Sort chronologically (Morning -> Evening)
    val sortedReminders = remember(dateReminders) {
        dateReminders.sortedBy { parseReminderTime(it.time) }
    }

    // 3. Separate uncompleted (top) and completed (bottom)
    val uncompletedTasks = remember(sortedReminders, completedTaskIds) {
        sortedReminders.filter { !completedTaskIds.contains(it.id) }
    }
    val completedTasks = remember(sortedReminders, completedTaskIds) {
        sortedReminders.filter { completedTaskIds.contains(it.id) }
    }

    if (showReminderForm) {
        ReminderFormScreen(
            reminderToEdit = editingReminder,
            onSave = { title, content, date, start, end, repeat, time, isAlarm, attachments, id ->
                reminderViewModel.saveReminder(
                    title,
                    content,
                    date ?: selectedDate,
                    start ?: selectedDate,
                    end,
                    repeat,
                    time,
                    isAlarm,
                    attachments,
                    id
                )
                showReminderForm = false
                editingReminder = null
            },
            onDelete = { reminder ->
                reminderViewModel.deleteReminder(reminder)
                showReminderForm = false
                editingReminder = null
            },
            onCancel = {
                showReminderForm = false
                editingReminder = null
            }
        )
        return
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    val mainDateFormatter = remember { DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()) }
    val dayOfWeekFormatter = remember { DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showDatePicker = true
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = selectedDate.format(mainDateFormatter),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = selectedDate.format(dayOfWeekFormatter),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Reminder"
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
            // Task Filter Pills ("To do", "Assigned")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DiBadgeTheme.spacing.medium, vertical = 8.dp)
            ) {
                taskFilters.forEach { filter ->
                    val isSelected = selectedTaskFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTaskFilter = filter },
                        label = {
                            Text(
                                text = filter,
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

            Spacer(modifier = Modifier.height(8.dp))

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
                            Text(
                                text = "No to-do tasks for ${selectedDate.format(mainDateFormatter)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = DiBadgeTheme.spacing.medium, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            // 1. UNCOMPLETED TASKS (Sorted Morning -> Evening)
                            items(uncompletedTasks, key = { it.id }) { reminder ->
                                TaskCardItem(
                                    reminder = reminder,
                                    isCompleted = false,
                                    onCheckedChange = { checked ->
                                        completedTaskIds = if (checked) completedTaskIds + reminder.id else completedTaskIds - reminder.id
                                    },
                                    onClick = {
                                        editingReminder = reminder
                                        showReminderForm = true
                                    }
                                )
                            }

                            // 2. COMPLETED TASKS (Moved to Bottom)
                            if (completedTasks.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Completed (${completedTasks.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                items(completedTasks, key = { it.id }) { reminder ->
                                    TaskCardItem(
                                        reminder = reminder,
                                        isCompleted = true,
                                        onCheckedChange = { checked ->
                                            completedTaskIds = if (checked) completedTaskIds + reminder.id else completedTaskIds - reminder.id
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
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No assigned tasks for ${selectedDate.format(mainDateFormatter)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            selectedDate = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun TaskCardItem(
    reminder: ReminderEntity,
    isCompleted: Boolean,
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT SIDE: CHECKBOX
            Checkbox(
                checked = isCompleted,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // MIDDLE: TITLE & CONTENT PREVIEW
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Title
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.Medium,
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

                // 1st Few Words of Content in Small Font
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
            }

            Spacer(modifier = Modifier.width(12.dp))

            // RIGHT SIDE: DUE TIME
            if (!reminder.time.isNullOrBlank()) {
                Text(
                    text = reminder.time,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}
