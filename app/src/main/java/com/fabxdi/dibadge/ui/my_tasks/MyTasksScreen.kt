package com.fabxdi.dibadge.ui.my_tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import android.view.ViewGroup
import android.view.Gravity
import androidx.compose.animation.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.ui.my_tasks.to_do.ReminderFormScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun hasTasksOnDate(allReminders: List<ReminderEntity>, targetDate: LocalDate): Boolean {
    return allReminders.any { isReminderOnDate(it, targetDate) }
}

fun isReminderOnDate(reminder: ReminderEntity, targetDate: LocalDate): Boolean {
    val repeat = reminder.repeatType.lowercase()
    return when {
        repeat == "once" || repeat.isBlank() -> {
            (reminder.date ?: reminder.startDate) == targetDate
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

data class WeekPageData(
    val yearMonth: YearMonth,
    val sundayDate: LocalDate
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    reminderViewModel: ReminderViewModel = viewModel(),
    initialReminderIdToEdit: Int? = null,
    openSnoozeTimePicker: Boolean = false,
    onSnoozeHandled: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTaskFilter by remember { mutableStateOf("To do") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showReminderForm by remember { mutableStateOf(false) }
    var shouldOpenSnooze by remember { mutableStateOf(openSnoozeTimePicker) }

    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var completedTaskIds by remember { mutableStateOf(setOf<Int>()) }

    val allReminders by reminderViewModel.allReminders.collectAsState(initial = emptyList())

    LaunchedEffect(initialReminderIdToEdit, allReminders) {
        if (initialReminderIdToEdit != null && initialReminderIdToEdit != -1 && allReminders.isNotEmpty()) {
            val found = allReminders.find { it.id == initialReminderIdToEdit }
            if (found != null) {
                editingReminder = found
                showReminderForm = true
            }
        }
    }

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
    val uncompletedTasks = remember(sortedReminders, selectedDate) {
        sortedReminders.filter { !it.completedDates.contains(selectedDate.toString()) }
    }
    val completedTasks = remember(sortedReminders, selectedDate) {
        sortedReminders.filter { it.completedDates.contains(selectedDate.toString()) }
    }

    if (showReminderForm) {
        ReminderFormScreen(
            reminderToEdit = editingReminder,
            autoOpenTimePicker = shouldOpenSnooze,
            onSave = { title, content, date, start, end, repeat, time, isAlarm, attachments, id ->
                val finalDate = date ?: selectedDate
                val finalStart = start ?: finalDate
                reminderViewModel.saveReminder(
                    title,
                    content,
                    finalDate,
                    finalStart,
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

            // Task Filter Pills ("To do · N", "Assigned to me · N")
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
                            // 1. UNCOMPLETED TASKS (Sorted Morning -> Evening)
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

                            // 2. COMPLETED TASKS (Moved to Bottom)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDatePickerDialog(
    initialDate: LocalDate,
    allReminders: List<ReminderEntity>,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var pickerMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var tempSelectedDate by remember { mutableStateOf(initialDate) }
    var showYearPicker by remember { mutableStateOf(false) }

    val daysInMonth = remember(pickerMonth) { pickerMonth.lengthOfMonth() }
    val firstDayOfMonth = remember(pickerMonth) { pickerMonth.atDay(1).dayOfWeek.value % 7 }

    val calendarDays = remember(daysInMonth, firstDayOfMonth) {
        val days = mutableListOf<LocalDate?>()
        repeat(firstDayOfMonth) { days.add(null) }
        for (i in 1..daysInMonth) { days.add(pickerMonth.atDay(i)) }
        while (days.size % 7 != 0) { days.add(null) }
        days.chunked(7)
    }

    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val headlineFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                win.setGravity(Gravity.CENTER)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .wrapContentHeight()
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Top Headline ("Fri, 2 Oct")
                    Text(
                        text = tempSelectedDate.format(headlineFormatter),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Month Year Dropdown & Navigation Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showYearPicker = !showYearPicker }
                        ) {
                            Text(
                                text = pickerMonth.format(monthYearFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Year",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        if (!showYearPicker) {
                            Row {
                                IconButton(onClick = { pickerMonth = pickerMonth.minusMonths(1) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev Month")
                                }
                                IconButton(onClick = { pickerMonth = pickerMonth.plusMonths(1) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (showYearPicker) {
                        val currentYear = pickerMonth.year
                        val years = remember { (currentYear - 30..currentYear + 30).toList() }
                        val listState = rememberLazyListState(initialFirstVisibleItemIndex = 28)

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(years) { yearVal ->
                                val isSelectedYear = yearVal == currentYear
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pickerMonth = pickerMonth.withYear(yearVal)
                                            showYearPicker = false
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = yearVal.toString(),
                                        style = if (isSelectedYear) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelectedYear) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelectedYear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        // Day names header
                        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        Row(modifier = Modifier.fillMaxWidth()) {
                            daysOfWeek.forEach { day ->
                                Text(
                                    text = day,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Date cells grid with task dots
                        calendarDays.forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                week.forEach { date ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .padding(1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (date != null) {
                                            val isSelected = date == tempSelectedDate
                                            val isToday = date == LocalDate.now()
                                            val hasTask = remember(allReminders, date) {
                                                hasTasksOnDate(allReminders, date)
                                            }

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable {
                                                        tempSelectedDate = date
                                                        onDateSelected(date)
                                                    },
                                                shape = CircleShape,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else -> Color.Transparent
                                                }
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = date.dayOfMonth.toString(),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                        color = when {
                                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                                            isToday -> MaterialTheme.colorScheme.primary
                                                            else -> MaterialTheme.colorScheme.onSurface
                                                        }
                                                    )

                                                    if (hasTask) {
                                                        Spacer(modifier = Modifier.height(1.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .background(
                                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                                    shape = CircleShape
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Action Buttons ("Today", "Cancel")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onDateSelected(LocalDate.now())
                            }
                        ) {
                            Text(
                                text = "Today",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        TextButton(onClick = onDismiss) {
                            Text(
                                text = "Cancel",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
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
            }

            Spacer(modifier = Modifier.width(10.dp))

            // RIGHT SIDE: DUE TIME
            if (!reminder.time.isNullOrBlank()) {
                Text(
                    text = reminder.time.lowercase(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = if (isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    }
                )
            }
        }
    }
}
