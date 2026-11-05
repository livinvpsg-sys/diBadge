package com.fabxdi.dibadge.ui.my_tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.GroupTaskEntity
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.ui.components.DiBadgeFab
import com.fabxdi.dibadge.ui.my_tasks.to_do.ReminderFormScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.GroupTaskViewModel
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
    reminderViewModel: ReminderViewModel = viewModel(),
    groupTaskViewModel: GroupTaskViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val now = LocalDate.now()
            if (now != today) today = now
        }
    }

    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedFilter by remember { mutableStateOf("All") } // "All" or "Assigned to me"
    var showMonthPickerSheet by remember { mutableStateOf(false) }

    val allReminders by reminderViewModel.allReminders.collectAsState(initial = emptyList())

    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val myUid = currentUser?.uid.orEmpty()
    val myName = currentUser?.displayName.orEmpty()
    val assignedTasks by remember(myUid, myName) { groupTaskViewModel.getTasksAssignedToMe(myUid, myName) }
        .collectAsState(initial = emptyList())

    val listState = rememberLazyListState()

    // On open or whenever the month changes: today's card for this month, otherwise the top
    LaunchedEffect(currentYearMonth) {
        val idx = if (currentYearMonth == YearMonth.now()) {
            (LocalDate.now().dayOfMonth - 1).coerceIn(0, currentYearMonth.lengthOfMonth() - 1)
        } else 0
        listState.scrollToItem(idx)
    }

    BackHandler(enabled = true) {
        if (currentYearMonth != YearMonth.now() || selectedFilter != "All") {
            currentYearMonth = YearMonth.now()
            selectedFilter = "All"
            coroutineScope.launch {
                val todayIndex = (today.dayOfMonth - 1).coerceIn(0, currentYearMonth.lengthOfMonth() - 1)
                listState.scrollToItem(todayIndex)
            }
        } else {
            onBack()
        }
    }

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

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tabInactiveColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error

    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    // Generate list of days for active month
    val monthDays = remember(currentYearMonth) {
        (1..currentYearMonth.lengthOfMonth()).map { day ->
            currentYearMonth.atDay(day)
        }
    }

    val showAssigned = selectedFilter == "Assigned to me"

    val dayTasksMap = remember(allReminders, monthDays, showAssigned) {
        monthDays.associateWith { date ->
            if (showAssigned) emptyList()
            else sortTasksByTime(allReminders.filter { isReminderOnDate(it, date) })
        }
    }

    // Group tasks assigned to me, shown on every day from their start to their end (or due) date
    val assignedMap = remember(assignedTasks, monthDays, showAssigned) {
        monthDays.associateWith { date ->
            if (!showAssigned) emptyList()
            else assignedTasks.filter { it.coversDate(date) }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBgColor)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Month and Year e.g. "October 2026 ▾"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showMonthPickerSheet = true }
                    ) {
                        Text(
                            text = currentYearMonth.format(monthYearFormatter),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Normal,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select month",
                            tint = primaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Right: 32dp Calendar outline icon with today's date number inside (e.g. 21)
                    CalendarHeaderIcon(
                        todayDate = today.dayOfMonth,
                        primaryTextColor = primaryTextColor,
                        onClick = {
                            currentYearMonth = YearMonth.now()
                            coroutineScope.launch {
                                val todayIndex = (today.dayOfMonth - 1).coerceIn(0, currentYearMonth.lengthOfMonth() - 1)
                                listState.scrollToItem(todayIndex)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Filter Chips Row ("All", "Assigned to me")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("All", "Assigned to me").forEach { filterOption ->
                        val isSelected = selectedFilter == filterOption
                        Surface(
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { selectedFilter = filterOption },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) primaryTextColor else outlineColor)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filterOption,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = primaryTextColor
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            DiBadgeFab(
                onClick = {
                    editingReminder = null
                    showReminderForm = true
                },
                contentDescription = "Add task",
                modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
                .pointerInput(currentYearMonth) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragCancel = { total = 0f },
                        onDragEnd = {
                            if (total < -120f) currentYearMonth = currentYearMonth.plusMonths(1)
                            else if (total > 120f) currentYearMonth = currentYearMonth.minusMonths(1)
                            total = 0f
                        },
                        onHorizontalDrag = { _, amount -> total += amount }
                    )
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(monthDays, key = { _, date -> date.toString() }) { index, date ->
                    val tasksForDate = dayTasksMap[date] ?: emptyList()
                    val assignedForDate = assignedMap[date] ?: emptyList()
                    val hasTasks = tasksForDate.isNotEmpty() || assignedForDate.isNotEmpty()
                    val isToday = date.isEqual(today)

                    val nextDate = monthDays.getOrNull(index + 1)
                    val nextHasTasks = nextDate != null &&
                        (dayTasksMap[nextDate]?.isNotEmpty() == true || assignedMap[nextDate]?.isNotEmpty() == true)
                    val showBottomDivider = !hasTasks && nextDate != null && !nextHasTasks

                    AgendaDayRow(
                        date = date,
                        isToday = isToday,
                        tasks = tasksForDate,
                        assignedTasks = assignedForDate,
                        onAssignedToggle = { task ->
                            groupTaskViewModel.updateTaskCompletion(
                                groupId = task.groupId,
                                taskId = task.id,
                                isCompleted = !task.isCompleted,
                                userName = myName.ifBlank { "Me" },
                                userUid = myUid,
                                onUpdated = {}
                            )
                        },
                        showBottomDivider = showBottomDivider,
                        cardBgColor = cardBgColor,
                        outlineColor = outlineColor,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        tabInactiveColor = tabInactiveColor,
                        tealAccent = tealAccent,
                        errorColor = errorColor,
                        onTaskToggleDone = { reminder ->
                            reminderViewModel.toggleTaskCompletedForDate(reminder, date)
                        },
                        onTaskClick = { reminder ->
                            editingReminder = reminder
                            showReminderForm = true
                        }
                    )
                }
            }
        }
    }

    if (showMonthPickerSheet) {
        MonthPickerSheet(
            selectedYearMonth = currentYearMonth,
            onMonthSelected = { newYM ->
                currentYearMonth = newYM
            },
            onDismiss = { showMonthPickerSheet = false }
        )
    }
}

@Composable
fun CalendarHeaderIcon(
    todayDate: Int,
    primaryTextColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = "Jump to today" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val strokeWidthPx = 1.5.dp.toPx()
            val cornerRadiusPx = 6.dp.toPx()
            val headerLineYPx = 10.dp.toPx()

            drawRoundRect(
                color = primaryTextColor,
                topLeft = Offset(1.dp.toPx(), 4.dp.toPx()),
                size = Size(30.dp.toPx(), 26.dp.toPx()),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = strokeWidthPx)
            )

            drawLine(
                color = primaryTextColor,
                start = Offset(1.dp.toPx(), headerLineYPx),
                end = Offset(31.dp.toPx(), headerLineYPx),
                strokeWidth = strokeWidthPx
            )

            drawLine(
                color = primaryTextColor,
                start = Offset(8.dp.toPx(), 1.dp.toPx()),
                end = Offset(8.dp.toPx(), 6.dp.toPx()),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )

            drawLine(
                color = primaryTextColor,
                start = Offset(24.dp.toPx(), 1.dp.toPx()),
                end = Offset(24.dp.toPx(), 6.dp.toPx()),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }

        Text(
            text = todayDate.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = primaryTextColor,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun AgendaDayRow(
    date: LocalDate,
    isToday: Boolean,
    tasks: List<ReminderEntity>,
    showBottomDivider: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tabInactiveColor: Color,
    tealAccent: Color,
    errorColor: Color,
    onTaskToggleDone: (ReminderEntity) -> Unit,
    onTaskClick: (ReminderEntity) -> Unit,
    assignedTasks: List<GroupTaskEntity> = emptyList(),
    onAssignedToggle: (GroupTaskEntity) -> Unit = {}
) {
    val hasTasks = tasks.isNotEmpty() || assignedTasks.isNotEmpty()
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val dayNameStr = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    val dateStr = date.toString()
    val isDarkTheme = DiBadgeTheme.colors.isDark

    Column(modifier = Modifier.fillMaxWidth()) {
        if (hasTasks) {
            // Day Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = cardBgColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .heightIn(min = 68.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Gutter + Tint Box (70dp wide)
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .fillMaxHeight()
                            .then(
                                if (isToday) {
                                    Modifier.background(
                                        color = tealAccent.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                                    )
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.width(44.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                fontSize = 18.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) tealAccent else primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dayNameStr,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = when {
                                    isToday -> tealAccent
                                    isWeekend -> tabInactiveColor
                                    else -> secondaryTextColor
                                }
                            )
                        }
                    }

                    // Vertical Dim Line or Space
                    if (!isToday) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .padding(vertical = 12.dp)
                                .background(outlineColor.copy(alpha = 0.45f))
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Tasks Stack
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp, top = 8.dp, bottom = 8.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        tasks.forEachIndexed { index, reminder ->
                            val isCompleted = reminder.completedDates.contains(dateStr)

                            AgendaTaskRow(
                                reminder = reminder,
                                isCompleted = isCompleted,
                                date = date,
                                tealAccent = tealAccent,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                errorColor = errorColor,
                                isDarkTheme = isDarkTheme,
                                onToggleDone = { onTaskToggleDone(reminder) },
                                onClick = { onTaskClick(reminder) }
                            )

                            if (index < tasks.lastIndex) {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = outlineColor.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }

                        assignedTasks.forEachIndexed { index, task ->
                            if (index > 0 || tasks.isNotEmpty()) {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = outlineColor.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            AssignedTaskRow(
                                task = task,
                                date = date,
                                tealAccent = tealAccent,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                errorColor = errorColor,
                                isDarkTheme = isDarkTheme,
                                onToggleDone = { onAssignedToggle(task) }
                            )
                        }
                    }
                }
            }
        } else {
            // Background-less Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Gutter + Tint Box (70dp wide)
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .fillMaxHeight()
                        .then(
                            if (isToday) {
                                Modifier.background(
                                    color = tealAccent.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                )
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.width(44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontSize = 18.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) tealAccent else primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dayNameStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = when {
                                isToday -> tealAccent
                                isWeekend -> tabInactiveColor
                                else -> secondaryTextColor
                            }
                        )
                    }
                }

                // Vertical Dim Line or Space
                if (!isToday) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(38.dp)
                            .background(outlineColor.copy(alpha = 0.45f))
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Spacer(modifier = Modifier.width(14.dp))
            }

            if (showBottomDivider) {
                if (isToday) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.width(70.dp))
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 1.dp,
                            color = outlineColor
                        )
                    }
                } else {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = outlineColor
                    )
                }
            }
        }
    }
}

@Composable
fun AgendaTaskRow(
    reminder: ReminderEntity,
    isCompleted: Boolean,
    date: LocalDate,
    tealAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    errorColor: Color,
    isDarkTheme: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit
) {
    val headingText = reminder.title.ifBlank { reminder.content.ifBlank { "Task" } }

    val today = LocalDate.now()
    val nowTime = LocalTime.now()
    val parsedTime = remember(reminder.time) { parseReminderTime(reminder.time) }

    val isOverdue = remember(date, reminder.time, isCompleted, parsedTime) {
        if (isCompleted) false
        else if (date.isBefore(today)) true
        else if (date.isEqual(today) && parsedTime != LocalTime.MAX && parsedTime.isBefore(nowTime)) true
        else false
    }

    val timeDisplayColor = if (isOverdue) errorColor else secondaryTextColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Completion circle: 20dp visual, 48dp touch target
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Checkbox) { onToggleDone() }
                .semantics {
                    contentDescription = if (isCompleted) "Mark as not done" else "Mark as done"
                },
            contentAlignment = Alignment.Center
        ) {
          Box(modifier = Modifier.size(20.dp)) {
            if (isCompleted) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = tealAccent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isDarkTheme) Color(0xFF0E1114) else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, tealAccent)
                ) {}
            }
          }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Title and Time Subline
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headingText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = if (isCompleted) secondaryTextColor.copy(alpha = 0.5f) else primaryTextColor,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!reminder.time.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = timeDisplayColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reminder.time,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = timeDisplayColor
                    )
                }
            }
        }
    }
}

/** A group task is shown from its start date to its end date, or on its due date if it has no range. */
fun GroupTaskEntity.coversDate(date: LocalDate): Boolean {
    val start = startDate ?: dueDate ?: return false
    val end = endDate ?: dueDate ?: start
    return !date.isBefore(start) && !date.isAfter(end)
}

@Composable
fun AssignedTaskRow(
    task: GroupTaskEntity,
    date: LocalDate,
    tealAccent: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    errorColor: Color,
    isDarkTheme: Boolean,
    onToggleDone: () -> Unit
) {
    val isOverdue = !task.isCompleted && date.isBefore(LocalDate.now())
    val subline = buildString {
        append("From ${task.assignedBy}")
        if (!task.dueTime.isNullOrBlank()) append(" · ${task.dueTime}")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Checkbox) { onToggleDone() }
                .semantics {
                    contentDescription = if (task.isCompleted) "Mark as not done" else "Mark as done"
                },
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(20.dp)) {
                if (task.isCompleted) {
                    Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = tealAccent) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isDarkTheme) Color(0xFF0E1114) else Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, tealAccent)
                    ) {}
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = if (task.isCompleted) secondaryTextColor.copy(alpha = 0.5f) else primaryTextColor,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subline,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = if (isOverdue) errorColor else secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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

fun sortTasksByTime(reminders: List<ReminderEntity>): List<ReminderEntity> {
    return reminders.sortedWith(
        compareBy<ReminderEntity> { parseTimeInMinutes(it.time) }
            .thenBy { it.id }
    )
}
