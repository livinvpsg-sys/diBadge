package com.fabxdi.dibadge.ui.my_tasks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.data.ToDoEntity
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.my_tasks.to_do.ReminderFormScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeChip
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
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
    var selectedTaskFilter by remember { mutableStateOf("Today") }

    BackHandler(enabled = true) {
        if (selectedTaskFilter != "Today" || selectedDate != LocalDate.now()) {
            selectedDate = LocalDate.now()
            selectedTaskFilter = "Today"
        } else {
            onBack()
        }
    }

    val allReminders by reminderViewModel.allReminders.collectAsState(initial = emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showReminderForm by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var shouldOpenSnooze by remember { mutableStateOf(false) }
    var handledReminderId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(initialReminderIdToEdit, allReminders) {
        if (initialReminderIdToEdit != null && initialReminderIdToEdit != -1 && handledReminderId != initialReminderIdToEdit) {
            val targetReminder = allReminders.find { it.id == initialReminderIdToEdit }
            if (targetReminder != null) {
                handledReminderId = initialReminderIdToEdit
                editingReminder = targetReminder
                shouldOpenSnooze = openSnoozeTimePicker
                showReminderForm = true
                onSnoozeHandled()
            }
        }
    }

    LaunchedEffect(initialSnoozeReminder) {
        if (initialSnoozeReminder != null) {
            editingReminder = initialSnoozeReminder
            shouldOpenSnooze = true
            showReminderForm = true
        }
    }

    val sortedReminders = remember(allReminders, selectedDate) {
        val dateReminders = allReminders.filter { isReminderOnDate(it, selectedDate) }
        sortTasksByTime(dateReminders)
    }

    val uncompletedCount = remember(sortedReminders, selectedDate) {
        val dateStr = selectedDate.toString()
        sortedReminders.count { !it.completedDates.contains(dateStr) }
    }

    if (showReminderForm) {
        ReminderFormScreen(
            reminderToEdit = editingReminder,
            autoOpenTimePicker = shouldOpenSnooze,
            onSave = { title, content, date, start, end, repeat, time, isAlarm, attachments, id, remindDate ->
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
                    id,
                    remindDate
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

    val fullDateDisplayFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.getDefault()) }

    val todayDate = remember { LocalDate.now() }
    val overdueCount = remember(allReminders, todayDate) {
        val nowTime = LocalTime.now()
        allReminders.sumOf { reminder ->
            var count = 0
            val baseStart = reminder.startDate ?: reminder.date ?: todayDate
            val searchStart = if (baseStart.isAfter(todayDate.minusDays(60))) baseStart else todayDate.minusDays(60)

            var currDate = searchStart
            while (!currDate.isAfter(todayDate)) {
                val currDateStr = currDate.toString()
                val isCompleted = reminder.completedDates.contains(currDateStr)

                if (!isCompleted && isReminderOnDate(reminder, currDate)) {
                    if (currDate.isBefore(todayDate)) {
                        count++
                    } else if (currDate == todayDate && !reminder.time.isNullOrBlank()) {
                        val parsedTime = parseReminderTime(reminder.time)
                        if (parsedTime != LocalTime.MAX && parsedTime.isBefore(nowTime)) {
                            count++
                        }
                    }
                }
                currDate = currDate.plusDays(1)
            }
            count
        }
    }

    LaunchedEffect(overdueCount) {
        if (overdueCount == 0 && selectedTaskFilter == "Overdue") {
            selectedTaskFilter = "Today"
        }
    }

    val assignedCount = 0

    val todayFilterLabel = if (uncompletedCount > 0 && selectedDate == LocalDate.now()) "Today $uncompletedCount" else "Today"
    val assignedLabel = if (assignedCount > 0) "Assigned to me $assignedCount" else "Assigned to me"
    val overdueLabel = if (overdueCount > 0) "Overdue $overdueCount" else "Overdue"

    val taskFilters = remember(todayFilterLabel, assignedLabel, overdueLabel, overdueCount) {
        val list = mutableListOf(
            "Today" to todayFilterLabel,
            "Assigned to me" to assignedLabel
        )
        if (overdueCount > 0) {
            list.add("Overdue" to overdueLabel)
        }
        list
    }

    var showDatePicker by remember { mutableStateOf(false) }

    val taskListState = rememberLazyListState()
    var lastDayTransitionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(selectedDate) {
        taskListState.scrollToItem(0)
    }

    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBgColor = MaterialTheme.colorScheme.surface

    // Grouping tasks into 4 Time Groups
    val anytimeTasks = remember(sortedReminders) {
        sortedReminders.filter { reminder ->
            if (reminder.time.isNullOrBlank()) true else {
                val t = parseReminderTime(reminder.time)
                t == LocalTime.MAX
            }
        }
    }
    val morningTasks = remember(sortedReminders) {
        sortedReminders.filter { reminder ->
            if (reminder.time.isNullOrBlank()) false else {
                val t = parseReminderTime(reminder.time)
                t != LocalTime.MAX && t.hour in 0..11
            }
        }
    }
    val afternoonTasks = remember(sortedReminders) {
        sortedReminders.filter { reminder ->
            if (reminder.time.isNullOrBlank()) false else {
                val t = parseReminderTime(reminder.time)
                t != LocalTime.MAX && t.hour in 12..17
            }
        }
    }
    val eveningTasks = remember(sortedReminders) {
        sortedReminders.filter { reminder ->
            if (reminder.time.isNullOrBlank()) false else {
                val t = parseReminderTime(reminder.time)
                t != LocalTime.MAX && t.hour in 18..23
            }
        }
    }

    val timeGroups = remember(anytimeTasks, morningTasks, afternoonTasks, eveningTasks) {
        listOf(
            "Anytime" to anytimeTasks,
            "Morning" to morningTasks,
            "Afternoon" to afternoonTasks,
            "Evening" to eveningTasks
        ).filter { it.second.isNotEmpty() }
    }

    val totalTasksCount = sortedReminders.size

    val dayNestedScrollConnection = remember(selectedDate, selectedTaskFilter) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && selectedTaskFilter == "Today") {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastDayTransitionTime > 400L) {
                        // Swiping LEFT -> Next Day
                        if (available.x < -25f) {
                            lastDayTransitionTime = currentTime
                            selectedDate = selectedDate.plusDays(1)
                            return Offset(available.x, 0f)
                        }
                        // Swiping RIGHT -> Previous Day
                        if (available.x > 25f) {
                            lastDayTransitionTime = currentTime
                            selectedDate = selectedDate.minusDays(1)
                            return Offset(available.x, 0f)
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }

    val topBarDateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy, EEEE", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedDate.format(topBarDateFormatter),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    if (selectedTaskFilter == "Today") {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = "Pick Date",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (selectedTaskFilter == "Today") {
                Surface(
                    onClick = {
                        editingReminder = null
                        showReminderForm = true
                    },
                    modifier = Modifier
                        .padding(bottom = 16.dp, end = 4.dp)
                        .size(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Task",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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
            // 1. Task Filter Chips ("Today", "Assigned to me", "Overdue")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                taskFilters.forEach { (filterKey, filterLabel) ->
                    val isSelected = if (filterKey == "Today") {
                        selectedTaskFilter == "Today" && selectedDate == LocalDate.now()
                    } else {
                        selectedTaskFilter == filterKey
                    }
                    DiBadgeChip(
                        text = filterLabel,
                        selected = isSelected,
                        onClick = {
                            selectedTaskFilter = filterKey
                            if (filterKey == "Today") {
                                selectedDate = LocalDate.now()
                            }
                        }
                    )
                }
            }

            // 2. Dim Divider Line below Filter Chips
            if (selectedTaskFilter == "Today") {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Task Content List View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .pointerInput(selectedTaskFilter) {
                        if (selectedTaskFilter == "Today") {
                            detectHorizontalDragGestures { _, dragAmount ->
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastDayTransitionTime > 350L) {
                                    if (dragAmount < -30f) {
                                        lastDayTransitionTime = currentTime
                                        selectedDate = selectedDate.plusDays(1)
                                    } else if (dragAmount > 30f) {
                                        lastDayTransitionTime = currentTime
                                        selectedDate = selectedDate.minusDays(1)
                                    }
                                }
                            }
                        }
                    }
            ) {
                if (selectedTaskFilter == "Today") {
                    LazyColumn(
                        state = taskListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(dayNestedScrollConnection),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = if (sortedReminders.isEmpty()) Arrangement.Center else Arrangement.Top
                    ) {
                        if (sortedReminders.isEmpty()) {
                            item(key = "empty_state") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillParentMaxHeight(0.7f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_my_tasks),
                                        contentDescription = null,
                                        tint = secondaryTextColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val emptyTitle = "No tasks"
                                    Text(
                                        text = emptyTitle,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = secondaryTextColor
                                    )
                                }
                            }
                        } else {
                            timeGroups.forEach { (groupLabel, groupTasks) ->
                                item(key = "group_header_$groupLabel") {
                                    Text(
                                        text = groupLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = secondaryTextColor,
                                        modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 6.dp)
                                    )
                                }

                                item(key = "group_block_$groupLabel") {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            groupTasks.forEachIndexed { index, reminder ->
                                                val dateStr = selectedDate.toString()
                                                val isCompleted = reminder.completedDates.contains(dateStr)

                                                TaskCardItem(
                                                    reminder = reminder,
                                                    isCompleted = isCompleted,
                                                    viewDate = selectedDate,
                                                    showDivider = index < groupTasks.lastIndex,
                                                    onCheckedChange = { isDone ->
                                                        reminderViewModel.toggleTaskCompletedForDate(reminder, selectedDate)
                                                    },
                                                    onStarClick = {
                                                        reminderViewModel.toggleTaskImportant(reminder)
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
                            }
                        }
                    }
                } else if (selectedTaskFilter == "Overdue") {
                    OverdueTasksView(
                        allReminders = allReminders,
                        onToggleCompleted = { reminder, targetDate ->
                            reminderViewModel.toggleTaskCompletedForDate(reminder, targetDate)
                        },
                        onTaskClick = { reminder ->
                            editingReminder = reminder
                            showReminderForm = true
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_my_tasks),
                            contentDescription = null,
                            tint = secondaryTextColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No tasks",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        MyTasksDatePicker(
            selectedDate = selectedDate,
            allReminders = allReminders,
            onDateSelected = { chosenDate ->
                selectedDate = chosenDate
                selectedTaskFilter = "Today"
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

fun parseTimeInMinutes(timeStr: String?): Int {
    if (timeStr.isNullOrBlank()) return Int.MAX_VALUE
    val parsed = parseReminderTime(timeStr)
    return if (parsed != LocalTime.MAX) {
        parsed.hour * 60 + parsed.minute
    } else {
        Int.MAX_VALUE
    }
}

fun sortTasksByTime(reminders: List<ToDoEntity>): List<ToDoEntity> {
    return reminders.sortedWith(
        compareBy<ToDoEntity> { parseTimeInMinutes(it.time) }
            .thenBy { it.id }
    )
}
