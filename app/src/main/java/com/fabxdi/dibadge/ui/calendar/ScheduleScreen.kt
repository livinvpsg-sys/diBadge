package com.fabxdi.dibadge.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.GroupTaskEntity
import com.fabxdi.dibadge.ui.components.DiBadgeFab
import com.fabxdi.dibadge.viewmodel.GroupTaskViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    taskViewModel: GroupTaskViewModel = viewModel()
) {
    BackHandler { onBack() }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUserId = currentUser?.uid ?: "me"
    val currentUserName = currentUser?.displayName ?: "User"

    val tasks by taskViewModel.getTasksForGroup("").collectAsState(initial = emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showNewTaskSheet by remember { mutableStateOf(false) }
    var initialDateForNewTask by remember { mutableStateOf(LocalDate.now()) }
    var selectedTaskForPreview by remember { mutableStateOf<GroupTaskEntity?>(null) }

    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = Color(0xFF1F8A70)
    val isDark = primaryTextColor.red > 0.5f

    val monthsList = remember(tasks) {
        val dates = tasks.mapNotNull { it.startDate ?: it.dueDate } + tasks.mapNotNull { it.endDate ?: it.dueDate }
        if (dates.isEmpty()) {
            listOf(YearMonth.now())
        } else {
            val minDate = dates.minOrNull() ?: LocalDate.now()
            val maxDate = dates.maxOrNull() ?: LocalDate.now()
            val startYM = YearMonth.from(minDate)
            val endYM = YearMonth.from(maxDate)
            val list = mutableListOf<YearMonth>()
            var curr = startYM
            while (!curr.isAfter(endYM)) {
                list.add(curr)
                curr = curr.plusMonths(1)
            }
            list.ifEmpty { listOf(YearMonth.now()) }
        }
    }

    val pagerState = rememberPagerState(pageCount = { monthsList.size })
    val currentYearMonth = monthsList.getOrNull(pagerState.currentPage) ?: YearMonth.now()

    var selectedDayForSheet by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor
                        )
                    }
                    Text(
                        text = "Schedule",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.size(44.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Month row: "‹ October 2026 ›"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage > 0) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous month",
                            tint = if (pagerState.currentPage > 0) secondaryTextColor else secondaryTextColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
                    Text(
                        text = currentYearMonth.format(monthTitleFormatter),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = {
                            if (pagerState.currentPage < monthsList.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage < monthsList.size - 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next month",
                            tint = if (pagerState.currentPage < monthsList.size - 1) secondaryTextColor else secondaryTextColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            DiBadgeFab(
                onClick = {
                    initialDateForNewTask = LocalDate.now()
                    showNewTaskSheet = true
                },
                contentDescription = "Add task",
                modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
            )
        }
    ) { innerPadding ->
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) { page ->
            val ym = monthsList[page]
            MonthTimelineView(
                yearMonth = ym,
                tasks = tasks,
                canUserManage = true,
                selectedDay = selectedDayForSheet,
                isDark = isDark,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                outlineColor = outlineColor,
                tealAccent = tealAccent,
                onDayClick = { date ->
                    selectedDayForSheet = date
                },
                onTaskClick = { task ->
                    selectedTaskForPreview = task
                }
            )
        }
    }

    if (showNewTaskSheet) {
        NewScheduleSheet(
            groupId = "",
            initialDate = initialDateForNewTask,
            userName = currentUserName,
            userUid = currentUserId,
            taskViewModel = taskViewModel,
            onTaskAdded = { startDate ->
                showNewTaskSheet = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Task added")
                }
                val targetYM = YearMonth.from(startDate)
                val targetIndex = monthsList.indexOf(targetYM)
                if (targetIndex != -1) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(targetIndex)
                    }
                }
            },
            onDismiss = { showNewTaskSheet = false }
        )
    }

    if (selectedTaskForPreview != null) {
        val taskToPreview = selectedTaskForPreview!!
        TaskPreviewSheet(
            groupId = taskToPreview.groupId.ifBlank { "" },
            task = taskToPreview,
            canUserManage = true,
            userName = currentUserName,
            userUid = currentUserId,
            taskViewModel = taskViewModel,
            onTaskUpdated = {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Task updated")
                }
            },
            onTaskDeleted = {
                selectedTaskForPreview = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Task deleted")
                }
            },
            onDismiss = { selectedTaskForPreview = null }
        )
    }
}
