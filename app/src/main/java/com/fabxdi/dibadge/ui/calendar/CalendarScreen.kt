package com.fabxdi.dibadge.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.ui.home.HomeTab
import java.time.LocalDate
import com.fabxdi.dibadge.ui.my_tasks.reminder.ReminderFormScreen
import com.fabxdi.dibadge.ui.calendar.overtime.OvertimeFormScreen
import com.fabxdi.dibadge.ui.calendar.leave.LeaveFormScreen
import com.fabxdi.dibadge.ui.my_activity.MyActivityScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import androidx.compose.foundation.shape.CircleShape

import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import com.fabxdi.dibadge.data.LeaveEntity
import kotlin.collections.find
import kotlin.collections.forEach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onTabClick: (HomeTab) -> Unit,
    onBackClick: () -> Unit,
    viewModel: ReminderViewModel = viewModel(),
    initialReminderId: Int? = null,
    onReminderOpened: () -> Unit = {},
    initialSelectedDate: LocalDate? = null
) {
    // Handle device back button
    BackHandler {
        onBackClick()
    }

    var isFabExpanded by remember { mutableStateOf(false) }
    var showReminderForm by remember { mutableStateOf(false) }
    var showOvertimeForm by remember { mutableStateOf(false) }
    var showLeaveForm by remember { mutableStateOf(false) }
    var showLogbook by remember { mutableStateOf(false) }
    var showDayScreen by remember { mutableStateOf(initialSelectedDate != null) }
    var selectedDayDate by remember { mutableStateOf(initialSelectedDate ?: LocalDate.now()) }
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var editingLeave by remember { mutableStateOf<LeaveEntity?>(null) }
    var editingOvertime by remember { mutableStateOf<OvertimeEntity?>(null) }

    val allReminders by viewModel.allReminders.collectAsState(initial = emptyList())
    val allLeaves by viewModel.allLeaves.collectAsState(initial = emptyList())
    val allOvertime by viewModel.allOvertime.collectAsState(initial = emptyList())

    // Handle initial reminder opening from notification
    LaunchedEffect(initialReminderId, allReminders) {
        if (initialReminderId != null && allReminders.isNotEmpty()) {
            val reminder = allReminders.find { it.id == initialReminderId }
            if (reminder != null) {
                editingReminder = reminder
                showReminderForm = true
                onReminderOpened()
            }
        }
    }
    
    val calendarData = remember(allReminders, allLeaves, allOvertime) {
        val itemsMap = mutableMapOf<LocalDate, MutableList<Any>>()
        val highlightSet = mutableSetOf<LocalDate>()
        val combinedStatusMap = mutableMapOf<LocalDate, String>()
        
        allReminders.forEach { reminder ->
            // ... (rest of reminder logic remains same)
            when (reminder.repeatType) {
                "once" -> {
                    reminder.date?.let {
                        itemsMap.getOrPut(it) { mutableListOf() }.add(reminder)
                        highlightSet.add(it)
                    }
                }
                "weekly" -> {
                    reminder.date?.let { start ->
                        var current = start
                        val endLimit = start.plusYears(2)
                        while (current.isBefore(endLimit)) {
                            itemsMap.getOrPut(current) { mutableListOf() }.add(reminder)
                            highlightSet.add(current)
                            current = current.plusWeeks(1)
                        }
                    }
                }
                "monthly" -> {
                    reminder.date?.let { start ->
                        var current = start
                        val endLimit = start.plusYears(2)
                        while (current.isBefore(endLimit)) {
                            itemsMap.getOrPut(current) { mutableListOf() }.add(reminder)
                            highlightSet.add(current)
                            current = current.plusMonths(1)
                        }
                    }
                }
                "Yearly" -> {
                    reminder.date?.let { start ->
                        var current = start
                        val endLimit = start.plusYears(5)
                        while (current.isBefore(endLimit)) {
                            itemsMap.getOrPut(current) { mutableListOf() }.add(reminder)
                            highlightSet.add(current)
                            current = current.plusYears(1)
                        }
                    }
                }
                "daily" -> {
                    reminder.date?.let { start ->
                        var current = start
                        val endLimit = start.plusMonths(6)
                        highlightSet.add(start)
                        while (current.isBefore(endLimit)) {
                            itemsMap.getOrPut(current) { mutableListOf() }.add(reminder)
                            current = current.plusDays(1)
                        }
                    }
                }
                "Custom" -> {
                    if (reminder.startDate != null && reminder.endDate != null) {
                        var current: LocalDate = reminder.startDate
                        while (!current.isAfter(reminder.endDate)) {
                            itemsMap.getOrPut(current) { mutableListOf() }.add(reminder)
                            highlightSet.add(current)
                            current = current.plusDays(1)
                        }
                    }
                }
            }
        }

        allLeaves.forEach { leave ->
            var current = leave.startDate
            val end = leave.endDate ?: leave.startDate
            while (!current.isAfter(end)) {
                itemsMap.getOrPut(current) { mutableListOf() }.add(leave)
                combinedStatusMap[current] = leave.status
                current = current.plusDays(1)
            }
        }

        allOvertime.forEach { overtime ->
            itemsMap.getOrPut(overtime.date) { mutableListOf() }.add(overtime)
            // If there's already a status from leave, we keep it (as per rule: no overtime on leave day)
            // but for existing data, we could prioritize Approved
            if (!combinedStatusMap.containsKey(overtime.date) || overtime.status == "Approved") {
                combinedStatusMap[overtime.date] = overtime.status
            }
        }

        Triple(itemsMap, highlightSet, combinedStatusMap)
    }

    val savedItemsMap = calendarData.first
    val highlightDates = calendarData.second
    val statusMap = calendarData.third

    when {
        showReminderForm -> {
            ReminderFormScreen(
                reminderToEdit = editingReminder,
                onSave = { title, content, date, start, end, repeat, time, isAlarm, attachments, id ->
                    viewModel.saveReminder(
                        title,
                        content,
                        date,
                        start,
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
                    viewModel.deleteReminder(reminder)
                    showReminderForm = false
                    editingReminder = null
                },
                onCancel = {
                    showReminderForm = false
                    editingReminder = null
                }
            )
        }
        showLeaveForm -> {
            LeaveFormScreen(
                existingLeaves = allLeaves,
                leaveToEdit = editingLeave,
                onApply = { startDate, endDate, type, reason, attachments, id, action ->
                    if (action == "CancelAll") {
                        editingLeave?.let { viewModel.deleteLeave(it) }
                    } else if (action == "Extend" && editingLeave != null) {
                        val extensionEndDate = startDate
                        val currentEndDate = editingLeave!!.endDate ?: editingLeave!!.startDate

                        // Always save extended part as Pending
                        viewModel.saveLeave(
                            startDate = currentEndDate.plusDays(1),
                            endDate = extensionEndDate,
                            leaveType = type,
                            reason = reason,
                            attachments = attachments,
                            status = "Pending"
                        )
                    } else if (action == "Cancel" && editingLeave != null) {
                        val selectedDate = startDate
                        if (selectedDate == editingLeave!!.startDate) {
                            // If cancelling from the start, delete the whole thing or change status
                            if (editingLeave!!.status == "Pending") {
                                viewModel.deleteLeave(editingLeave!!)
                            } else {
                                // Approved -> Entire thing becomes Pending
                                viewModel.updateLeave(editingLeave!!.copy(status = "Pending"))
                            }
                        } else {
                            val newEndDate = selectedDate.minusDays(1)
                            if (editingLeave!!.status == "Pending") {
                                // Trim to end at the day before selectedDate
                                val updatedLeave = editingLeave!!.copy(
                                    endDate = if (newEndDate == editingLeave!!.startDate) null else newEndDate
                                )
                                viewModel.updateLeave(updatedLeave)
                            } else {
                                // Split Approved leave
                                val remainingStartDate = selectedDate
                                val remainingEndDate = editingLeave!!.endDate

                                // Update first part (Approved)
                                val firstPart = editingLeave!!.copy(
                                    endDate = if (newEndDate == editingLeave!!.startDate) null else newEndDate
                                )
                                viewModel.updateLeave(firstPart)

                                // Insert second part as Pending
                                viewModel.saveLeave(
                                    startDate = remainingStartDate,
                                    endDate = remainingEndDate,
                                    leaveType = type,
                                    reason = reason,
                                    attachments = attachments,
                                    status = "Pending"
                                )
                            }
                        }
                    } else {
                        viewModel.saveLeave(startDate, endDate, type, reason, attachments, id)
                    }
                    showLeaveForm = false
                    editingLeave = null
                },
                onClose = {
                    showLeaveForm = false
                    editingLeave = null
                }
            )
        }
        showOvertimeForm -> {
            OvertimeFormScreen(
                existingLeaves = allLeaves,
                overtimeToEdit = editingOvertime,
                onApply = { date, start, end, description, id ->
                    viewModel.saveOvertime(date, start, end, description, id)
                    showOvertimeForm = false
                    editingOvertime = null
                },
                onDelete = { overtime ->
                    viewModel.deleteOvertime(overtime)
                    showOvertimeForm = false
                    editingOvertime = null
                },
                onClose = {
                    showOvertimeForm = false
                    editingOvertime = null
                }
            )
        }
        showLogbook -> {
            MyActivityScreen(
                leaves = allLeaves,
                overtimes = allOvertime,
                onBack = { showLogbook = false }
            )
        }
        showDayScreen -> {
            DayScreen(
                date = selectedDayDate,
                items = savedItemsMap[selectedDayDate] ?: emptyList(),
                onItemClick = { item ->
                    if (item is ReminderEntity) {
                        editingReminder = item
                        showReminderForm = true
                    } else if (item is LeaveEntity) {
                        editingLeave = item
                        showLeaveForm = true
                    } else if (item is OvertimeEntity) {
                        editingOvertime = item
                        showOvertimeForm = true
                    }
                },
                onBack = { showDayScreen = false }
            )
        }
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
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
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = NavigationBarDefaults.Elevation
                    ) {
                        HomeTab.entries.forEach { tab ->
                            val isSelected = false
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    onTabClick(tab)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                ),
                                alwaysShowLabel = true,
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                icon = {
                                    val iconTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

                                    when (tab) {
                                        HomeTab.Home -> {
                                            Icon(
                                                painter = painterResource(id = tab.iconRes),
                                                contentDescription = tab.label,
                                                tint = iconTint
                                            )
                                        }
                                        HomeTab.Connections -> {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_connections),
                                                contentDescription = tab.label,
                                                tint = iconTint
                                            )
                                        }
                                        HomeTab.MyTasks -> {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_my_tasks),
                                                contentDescription = tab.label,
                                                tint = iconTint
                                            )
                                        }
                                        HomeTab.Note -> {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_inbox),
                                                contentDescription = tab.label,
                                                tint = iconTint
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                },
                floatingActionButton = {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp) // Breathing space from bottom bar
                    ) {
                        if (isFabExpanded) {
                            // Overtime Pill
                            CalendarFabPill(
                                label = "Overtime",
                                onClick = {
                                    showOvertimeForm = true
                                    isFabExpanded = false
                                }
                            )
                            // Leave Pill
                            CalendarFabPill(
                                label = "Leave",
                                onClick = {
                                    showLeaveForm = true
                                    isFabExpanded = false
                                }
                            )
                        }

                        FloatingActionButton(
                            onClick = { isFabExpanded = !isFabExpanded },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = "Expand Menu"
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(DiBadgeTheme.spacing.medium),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        CalendarGrid(
                            highlightDates = highlightDates,
                            leaveStatusMap = statusMap,
                            onDateClick = { date ->
                                val items = savedItemsMap[date]
                                if (!items.isNullOrEmpty()) {
                                    selectedDayDate = date
                                    showDayScreen = true
                                }
                            }
                        )
                    }

                    // Overlay to detect clicks and close the FAB menu
                    if (isFabExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isFabExpanded = false
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarFabPill(
    label: String,
    onClick: () -> Unit
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier = Modifier.height(40.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
