package com.fabxdi.dibadge.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.ClaimEntity
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import com.fabxdi.dibadge.ui.calendar.leave.LeaveFormScreen
import com.fabxdi.dibadge.ui.calendar.overtime.OvertimeFormScreen
import com.fabxdi.dibadge.ui.calendar.claim.ClaimFormScreen
import com.fabxdi.dibadge.ui.my_tasks.CalendarHeaderIcon
import com.fabxdi.dibadge.ui.my_tasks.MonthPickerSheet
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.LeaveOvertimeViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit,
    viewModel: LeaveOvertimeViewModel = viewModel(),
    initialSelectedDate: LocalDate? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDayDate by remember { mutableStateOf(initialSelectedDate ?: LocalDate.now()) }

    var showMonthPickerSheet by remember { mutableStateOf(false) }
    var showNewRequestSheet by remember { mutableStateOf(false) }
    var showDayPopupSheet by remember { mutableStateOf(false) }
    var showSummarySheet by remember { mutableStateOf(false) }

    var showLeaveForm by remember { mutableStateOf(false) }
    var showOvertimeForm by remember { mutableStateOf(false) }
    var showClaimForm by remember { mutableStateOf(false) }

    var editingLeave by remember { mutableStateOf<LeaveEntity?>(null) }
    var editingOvertime by remember { mutableStateOf<OvertimeEntity?>(null) }
    var editingClaim by remember { mutableStateOf<ClaimEntity?>(null) }
    var selectedRequestForDetails by remember { mutableStateOf<Any?>(null) }

    val allLeaves by viewModel.allLeaves.collectAsState(initial = emptyList())
    val allOvertime by viewModel.allOvertime.collectAsState(initial = emptyList())
    val allClaims by viewModel.allClaims.collectAsState(initial = emptyList())

    BackHandler(enabled = true) {
        onBackClick()
    }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant

    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    var lastSwipeTime by remember { mutableLongStateOf(0L) }

    // Summary Totals Calculation for Visible Month
    val monthLeaves = remember(allLeaves, currentYearMonth) {
        val monthStart = currentYearMonth.atDay(1)
        val monthEnd = currentYearMonth.atEndOfMonth()
        allLeaves.filter { leave ->
            val start = leave.startDate
            val end = leave.endDate ?: leave.startDate
            !(start.isAfter(monthEnd) || end.isBefore(monthStart))
        }
    }

    val monthOvertimes = remember(allOvertime, currentYearMonth) {
        allOvertime.filter { YearMonth.from(it.date) == currentYearMonth }
    }

    val monthClaims = remember(allClaims, currentYearMonth) {
        allClaims.filter { YearMonth.from(it.date) == currentYearMonth }
    }

    val approvedLeaveDays = monthLeaves.filter { it.status.equals("Approved", ignoreCase = true) }.sumOf { 1.0 }
    val pendingLeaveDays = monthLeaves.filter { it.status.equals("Pending", ignoreCase = true) }.sumOf { 1.0 }

    val approvedOtHours = monthOvertimes.filter { it.status.equals("Approved", ignoreCase = true) }.sumOf {
        val hrs = Duration.between(it.startTime, it.endTime).toMinutes() / 60.0
        if (hrs < 0) hrs + 24.0 else hrs
    }
    val pendingOtHours = monthOvertimes.filter { it.status.equals("Pending", ignoreCase = true) }.sumOf {
        val hrs = Duration.between(it.startTime, it.endTime).toMinutes() / 60.0
        if (hrs < 0) hrs + 24.0 else hrs
    }

    val approvedClaimAmount = monthClaims.filter { it.status.equals("Approved", ignoreCase = true) }.sumOf { it.amount }
    val pendingClaimAmount = monthClaims.filter { it.status.equals("Pending", ignoreCase = true) }.sumOf { it.amount }

    if (showLeaveForm) {
        LeaveFormScreen(
            existingLeaves = allLeaves,
            leaveToEdit = editingLeave,
            onApply = { startDate, endDate, type, reason, attachments, id, action ->
                viewModel.saveLeave(startDate, endDate, type, reason, attachments, id)
                showLeaveForm = false
                editingLeave = null
            },
            onClose = {
                showLeaveForm = false
                editingLeave = null
            }
        )
        return
    }

    if (showOvertimeForm) {
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
        return
    }

    if (showClaimForm) {
        ClaimFormScreen(
            claimToEdit = editingClaim,
            initialDate = selectedDayDate,
            onApply = { date, category, amount, description, id ->
                viewModel.saveClaim(date, category, amount, description, id)
                showClaimForm = false
                editingClaim = null
            },
            onClose = {
                showClaimForm = false
                editingClaim = null
            }
        )
        return
    }

    if (selectedRequestForDetails != null) {
        RequestDetailsScreen(
            requestItem = selectedRequestForDetails!!,
            onBack = { selectedRequestForDetails = null },
            onCancelRequestConfirmed = { item ->
                when (item) {
                    is LeaveEntity -> viewModel.deleteLeave(item)
                    is OvertimeEntity -> viewModel.deleteOvertime(item)
                    is ClaimEntity -> viewModel.deleteClaim(item)
                }
                selectedRequestForDetails = null
            }
        )
        return
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Back Arrow + Month Heading + Down Arrow
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = primaryTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showMonthPickerSheet = true }
                        ) {
                            Text(
                                text = currentYearMonth.format(monthYearFormatter),
                                fontSize = 20.sp,
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
                    }

                    // Right: 32dp Calendar outline icon with today's date
                    CalendarHeaderIcon(
                        todayDate = today.dayOfMonth,
                        primaryTextColor = primaryTextColor,
                        onClick = {
                            currentYearMonth = YearMonth.now()
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (!showNewRequestSheet && !showDayPopupSheet && !showSummarySheet) {
                Surface(
                    onClick = { showNewRequestSheet = true },
                    modifier = Modifier
                        .padding(bottom = 28.dp, end = 4.dp)
                        .size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New request",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Month Grid (Scoped Horizontal Swipe)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                        .pointerInput(currentYearMonth) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                val nowTime = System.currentTimeMillis()
                                if (nowTime - lastSwipeTime > 350L) {
                                    if (dragAmount < -30f) {
                                        lastSwipeTime = nowTime
                                        currentYearMonth = currentYearMonth.plusMonths(1)
                                    } else if (dragAmount > 30f) {
                                        lastSwipeTime = nowTime
                                        currentYearMonth = currentYearMonth.minusMonths(1)
                                    }
                                }
                            }
                        }
                ) {
                    CalendarGrid(
                        currentYearMonth = currentYearMonth,
                        leaves = allLeaves,
                        overtimes = allOvertime,
                        claims = allClaims,
                        onDateClick = { chosenDate ->
                            selectedDayDate = chosenDate
                            showDayPopupSheet = true
                        }
                    )
                }

                // Dedicated Bottom Handle Bar Touch Area (Swipe Up & Tap)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .navigationBarsPadding()
                        .clickable { showSummarySheet = true }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount < -15f) {
                                    showSummarySheet = true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(width = 40.dp, height = 4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = secondaryTextColor.copy(alpha = 0.50f)
                    ) {}
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

    if (showNewRequestSheet) {
        NewRequestSheet(
            onSelectLeave = {
                editingLeave = null
                showLeaveForm = true
            },
            onSelectOvertime = {
                editingOvertime = null
                showOvertimeForm = true
            },
            onSelectClaim = {
                editingClaim = null
                showClaimForm = true
            },
            onDismiss = { showNewRequestSheet = false }
        )
    }

    if (showDayPopupSheet) {
        val dayLeaves = remember(allLeaves, selectedDayDate) {
            allLeaves.filter { leave ->
                val end = leave.endDate ?: leave.startDate
                !(selectedDayDate.isBefore(leave.startDate) || selectedDayDate.isAfter(end))
            }
        }
        val dayOvertimes = remember(allOvertime, selectedDayDate) {
            allOvertime.filter { it.date == selectedDayDate }
        }
        val dayClaims = remember(allClaims, selectedDayDate) {
            allClaims.filter { it.date == selectedDayDate }
        }

        DayPopupSheet(
            date = selectedDayDate,
            leaves = dayLeaves,
            overtimes = dayOvertimes,
            claims = dayClaims,
            onLeaveClick = { leave ->
                selectedRequestForDetails = leave
            },
            onOvertimeClick = { overtime ->
                selectedRequestForDetails = overtime
            },
            onClaimClick = { claim ->
                selectedRequestForDetails = claim
            },
            onDismiss = { showDayPopupSheet = false }
        )
    }

    if (showSummarySheet) {
        MonthSummarySheet(
            monthTitle = currentYearMonth.format(monthYearFormatter),
            approvedLeaveDays = approvedLeaveDays,
            pendingLeaveDays = pendingLeaveDays,
            approvedOtHours = approvedOtHours,
            pendingOtHours = pendingOtHours,
            approvedClaimAmount = approvedClaimAmount,
            pendingClaimAmount = pendingClaimAmount,
            onDismiss = { showSummarySheet = false }
        )
    }
}
