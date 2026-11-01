package com.fabxdi.dibadge.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.GroupTaskEntity
import com.fabxdi.dibadge.ui.components.DiBadgeFab
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.GroupMember
import com.fabxdi.dibadge.viewmodel.GroupTaskViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

enum class TaskStatus {
    ONGOING, UPCOMING, DELAYED, COMPLETED
}

fun getTaskStatus(task: GroupTaskEntity, today: LocalDate = LocalDate.now()): TaskStatus {
    if (task.isCompleted) return TaskStatus.COMPLETED
    val start = task.startDate ?: task.dueDate ?: today
    val end = task.endDate ?: task.dueDate ?: start
    return when {
        today.isBefore(start) -> TaskStatus.UPCOMING
        today.isAfter(end) -> TaskStatus.DELAYED
        else -> TaskStatus.ONGOING
    }
}

/** First day a task covers. A task with no dates sits on the day it was created. */
fun GroupTaskEntity.timelineStart(): LocalDate =
    startDate ?: dueDate ?: Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

/** Last day a task covers, never before its first day. */
fun GroupTaskEntity.timelineEnd(): LocalDate {
    val start = timelineStart()
    val end = endDate ?: dueDate ?: start
    return if (end.isBefore(start)) start else end
}

/** "17 Oct", "17–20 Oct" or "28 Oct – 3 Nov", so a range that crosses a month is never ambiguous. */
fun formatScheduleRange(start: LocalDate, end: LocalDate): String {
    val locale = Locale.getDefault()
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    return when {
        start == end -> start.format(dayMonth)
        start.year != end.year -> "${start.format(dayMonthYear)} – ${end.format(dayMonthYear)}"
        start.month != end.month -> "${start.format(dayMonth)} – ${end.format(dayMonth)}"
        else -> "${start.dayOfMonth}–${end.format(dayMonth)}"
    }
}

/** Every month from the earliest to the latest task, always including the current month. */
fun buildScheduleMonths(tasks: List<GroupTaskEntity>): List<YearMonth> {
    val current = YearMonth.now()
    val dates = tasks.flatMap { listOf(it.timelineStart(), it.timelineEnd()) }
    val first = dates.minOrNull()?.let { YearMonth.from(it) }?.takeIf { it.isBefore(current) } ?: current
    val last = dates.maxOrNull()?.let { YearMonth.from(it) }?.takeIf { it.isAfter(current) } ?: current
    val list = mutableListOf<YearMonth>()
    var m = first
    while (!m.isAfter(last)) {
        list.add(m)
        m = m.plusMonths(1)
    }
    return list
}

/**
 * Pager that opens on the current month and stays on the month you are viewing when
 * tasks load or change and the list of months grows.
 */
@Composable
fun rememberMonthPager(months: List<YearMonth>): PagerState {
    var shown by remember { mutableStateOf(YearMonth.now()) }
    val pagerState = rememberPagerState(
        initialPage = months.indexOf(shown).coerceAtLeast(0),
        pageCount = { months.size }
    )
    LaunchedEffect(months) {
        val target = months.indexOf(shown)
        if (target >= 0 && target != pagerState.currentPage) pagerState.scrollToPage(target)
    }
    LaunchedEffect(pagerState.settledPage) {
        months.getOrNull(pagerState.settledPage)?.let { shown = it }
    }
    return pagerState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScheduleScreen(
    groupId: String,
    groupName: String,
    canUserManage: Boolean,
    members: List<GroupMember> = emptyList(),
    onBack: () -> Unit,
    taskViewModel: GroupTaskViewModel = viewModel()
) {
    BackHandler { onBack() }

    val tasks by taskViewModel.getTasksForGroup(groupId).collectAsState(initial = emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showNewScheduleSheet by remember { mutableStateOf(false) }
    var initialDateForNewTask by remember { mutableStateOf(LocalDate.now()) }
    // Keep only the id so the sheet always shows the latest copy of the task
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    val selectedTaskForPreview = tasks.firstOrNull { it.id == selectedTaskId }
    var selectedDayForSheet by remember { mutableStateOf<LocalDate?>(null) }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUserName = currentUser?.displayName ?: "User"
    val currentUserUid = currentUser?.uid ?: "me"

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val isDark = diBadgeColors.isDark

    val monthsList = remember(tasks) { buildScheduleMonths(tasks) }
    val pagerState = rememberMonthPager(monthsList)
    val currentYearMonth = monthsList.getOrNull(pagerState.currentPage) ?: YearMonth.now()
    val thisMonthIndex = monthsList.indexOf(YearMonth.now())

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                // Top bar: back arrow, "Schedule" and the group name under it
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Schedule",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Normal,
                            color = primaryTextColor
                        )
                        if (groupName.isNotBlank()) {
                            Text(
                                text = groupName,
                                fontSize = 13.sp,
                                color = secondaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (thisMonthIndex >= 0 && pagerState.currentPage != thisMonthIndex) {
                        Text(
                            text = "Today",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tealAccent,
                            modifier = Modifier
                                .clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(thisMonthIndex) }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Month row: previous, "October 2026", next
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val canGoBack = pagerState.currentPage > 0
                    val canGoForward = pagerState.currentPage < monthsList.size - 1
                    IconButton(
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        enabled = canGoBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous month",
                            tint = if (canGoBack) secondaryTextColor else secondaryTextColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
                    Text(
                        text = currentYearMonth.format(monthFormatter),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 140.dp)
                    )

                    IconButton(
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        enabled = canGoForward,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next month",
                            tint = if (canGoForward) secondaryTextColor else secondaryTextColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (canUserManage) {
                DiBadgeFab(
                    onClick = {
                        initialDateForNewTask = LocalDate.now()
                        showNewScheduleSheet = true
                    },
                    contentDescription = "Add task",
                    modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
                )
            }
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
                canUserManage = canUserManage,
                selectedDay = selectedDayForSheet,
                isDark = isDark,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                outlineColor = outlineColor,
                tealAccent = tealAccent,
                onDayClick = { date ->
                    selectedDayForSheet = if (selectedDayForSheet == date) null else date
                },
                onTaskClick = { task ->
                    selectedTaskId = task.id
                }
            )
        }
    }

    if (showNewScheduleSheet) {
        NewScheduleSheet(
            groupId = groupId,
            initialDate = initialDateForNewTask,
            userName = currentUserName,
            userUid = currentUserUid,
            members = members,
            taskViewModel = taskViewModel,
            onTaskAdded = { startDate ->
                showNewScheduleSheet = false
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
            onDismiss = { showNewScheduleSheet = false }
        )
    }

    if (selectedTaskForPreview != null) {
        val taskToPreview = selectedTaskForPreview!!
        TaskPreviewSheet(
            groupId = groupId,
            task = taskToPreview,
            canUserManage = canUserManage,
            userName = currentUserName,
            userUid = currentUserUid,
            taskViewModel = taskViewModel,
            onTaskUpdated = {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Task updated")
                }
            },
            onTaskDeleted = {
                selectedTaskId = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Task deleted")
                }
            },
            onDismiss = { selectedTaskId = null }
        )
    }

    if (selectedDayForSheet != null) {
        val dayDate = selectedDayForSheet!!
        val dayTasks = tasks.filter { t ->
            !dayDate.isBefore(t.timelineStart()) && !dayDate.isAfter(t.timelineEnd())
        }

        val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

        ModalBottomSheet(
            onDismissRequest = { selectedDayForSheet = null },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = cardBgColor,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.40f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(width = 36.dp, height = 4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = secondaryTextColor.copy(alpha = 0.40f)
                    ) {}
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Title: "Sat, 17 Oct · 3 tasks" (15sp semibold)
                Text(
                    text = "${dayDate.format(dayFormatter)} · ${dayTasks.size} ${if (dayTasks.size == 1) "task" else "tasks"}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (dayTasks.isEmpty()) {
                    Text(
                        text = "No tasks on this day",
                        fontSize = 14.sp,
                        color = secondaryTextColor
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dayTasks.forEach { task ->
                            val dateRangeStr = formatScheduleRange(task.timelineStart(), task.timelineEnd())

                            val taskStatus = getTaskStatus(task)
                            val statusColor = getStatusTextColor(taskStatus, isDark, tealAccent)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedDayForSheet = null
                                        selectedTaskId = task.id
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = colors.background,
                                border = BorderStroke(1.dp, outlineColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = dateRangeStr,
                                            fontSize = 12.sp,
                                            color = secondaryTextColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusIcon(status = taskStatus, isDark = isDark, tealAccent = tealAccent, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = taskStatus.name.lowercase().replaceFirstChar { it.uppercase() },
                                            fontSize = 12.sp,
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (canUserManage) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Add task",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable {
                                initialDateForNewTask = dayDate
                                selectedDayForSheet = null
                                showNewScheduleSheet = true
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

data class TaskLaneAssignment(
    val task: GroupTaskEntity,
    val effectiveStart: LocalDate,
    val effectiveEnd: LocalDate,
    val startRow: Int,
    val endRow: Int,
    val spanDays: Int,
    val laneIndex: Int,
    val clusterLaneCount: Int,
    val hasTopChevron: Boolean,
    val hasBottomChevron: Boolean
)

@Composable
fun MonthTimelineView(
    yearMonth: YearMonth,
    tasks: List<GroupTaskEntity>,
    canUserManage: Boolean,
    selectedDay: LocalDate?,
    isDark: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    outlineColor: Color,
    tealAccent: Color,
    onDayClick: (LocalDate) -> Unit,
    onTaskClick: (GroupTaskEntity) -> Unit
) {
    val today = LocalDate.now()
    val daysInMonth = yearMonth.lengthOfMonth()
    val days = remember(yearMonth) { (1..daysInMonth).map { yearMonth.atDay(it) } }

    val monthStart = yearMonth.atDay(1)
    val monthEnd = yearMonth.atEndOfMonth()

    val monthTasks = remember(tasks, yearMonth) {
        tasks.filter { t ->
            !t.timelineStart().isAfter(monthEnd) && !t.timelineEnd().isBefore(monthStart)
        }
    }

    val laneAssignments = remember(monthTasks, yearMonth) {
        if (monthTasks.isEmpty()) return@remember emptyList<TaskLaneAssignment>()

        val prep = monthTasks.map { task ->
            val rawStart = task.timelineStart()
            val rawEnd = task.timelineEnd()
            val effStart = if (rawStart.isBefore(monthStart)) monthStart else rawStart
            val effEnd = if (rawEnd.isAfter(monthEnd)) monthEnd else rawEnd

            val startRow = effStart.dayOfMonth - 1
            val endRow = effEnd.dayOfMonth - 1
            val span = endRow - startRow + 1

            val topChev = rawStart.isBefore(monthStart)
            val botChev = rawEnd.isAfter(monthEnd)

            Triple(task, effStart, effEnd) to TaskPrepData(startRow, endRow, span, topChev, botChev)
        }

        val n = prep.size
        val parent = IntArray(n) { it }
        fun find(i: Int): Int {
            var root = i
            while (root != parent[root]) root = parent[root]
            var curr = i
            while (curr != root) {
                val nxt = parent[curr]
                parent[curr] = root
                curr = nxt
            }
            return root
        }
        fun union(i: Int, j: Int) {
            val ri = find(i)
            val rj = find(j)
            if (ri != rj) parent[ri] = rj
        }

        for (i in 0 until n) {
            val (_, s1, e1) = prep[i].first
            for (j in i + 1 until n) {
                val (_, s2, e2) = prep[j].first
                if (!s1.isAfter(e2) && !s2.isAfter(e1)) {
                    union(i, j)
                }
            }
        }

        val clusters = (0 until n).groupBy { find(it) }
        val result = mutableListOf<TaskLaneAssignment>()

        clusters.values.forEach { clusterIndices ->
            val clusterItems = clusterIndices.map { prep[it] }.sortedWith(
                compareBy({ it.first.second }, { it.first.third }, { it.first.first.id })
            )

            val laneLastEnd = mutableListOf<LocalDate>()
            val laneMap = mutableMapOf<String, Int>()

            clusterItems.forEach { item ->
                val (t, effStart, effEnd) = item.first
                var assignedLane = -1
                for (l in laneLastEnd.indices) {
                    if (laneLastEnd[l].isBefore(effStart)) {
                        assignedLane = l
                        laneLastEnd[l] = effEnd
                        break
                    }
                }
                if (assignedLane == -1) {
                    assignedLane = laneLastEnd.size
                    laneLastEnd.add(effEnd)
                }
                laneMap[t.id] = assignedLane
            }

            val clusterCount = laneLastEnd.size

            clusterItems.forEach { item ->
                val (t, effStart, effEnd) = item.first
                val prepData = item.second
                val laneIdx = laneMap[t.id] ?: 0
                result.add(
                    TaskLaneAssignment(
                        task = t,
                        effectiveStart = effStart,
                        effectiveEnd = effEnd,
                        startRow = prepData.startRow,
                        endRow = prepData.endRow,
                        spanDays = prepData.span,
                        laneIndex = laneIdx,
                        clusterLaneCount = clusterCount,
                        hasTopChevron = prepData.topChev,
                        hasBottomChevron = prepData.botChev
                    )
                )
            }
        }

        result
    }

    val maxClusterLanes = remember(laneAssignments) {
        if (laneAssignments.isEmpty()) 1 else laneAssignments.maxOf { it.clusterLaneCount }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val density = LocalDensity.current
        val totalAvailableHeightPx = with(density) { this@BoxWithConstraints.maxHeight.toPx() }
        // Rows fill the screen, but never get smaller than 22dp. When a month doesn't fit
        // (small screen, landscape, large fonts) the page scrolls so every day stays reachable.
        val rowHeightPx = max(density.density * 22f, totalAvailableHeightPx / daysInMonth)
        val rowHeightDp = with(density) { rowHeightPx.toDp() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeightDp * daysInMonth)
            ) {
                // Left Column: Days (56dp wide)
                Column(
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                ) {
                    days.forEach { date ->
                        val isWeekend = date.dayOfWeek.value >= 6
                        val isToday = date == today
                        val isSelected = date == selectedDay
                        val dayBg = when {
                            isSelected -> if (isDark) Color(0xFF2C323A) else Color(0xFFDDE2E8)
                            isWeekend -> if (isDark) Color(0xFF1E242B) else Color(0xFFEFEFF2)
                            else -> Color.Transparent
                        }

                        val dayDescription = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())) +
                                if (isToday) ", today" else ""
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeightDp)
                                .background(dayBg)
                                .semantics(mergeDescendants = true) { contentDescription = dayDescription }
                                .clickable { onDayClick(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(width = 20.dp, height = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isToday) {
                                        Surface(
                                            modifier = Modifier.size(20.dp),
                                            shape = CircleShape,
                                            color = primaryTextColor
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${date.dayOfMonth}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) Color(0xFF0E1114) else Color.White,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "${date.dayOfMonth}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isWeekend) secondaryTextColor else primaryTextColor,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Text(
                                    text = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                                    fontSize = 11.sp,
                                    color = secondaryTextColor,
                                    modifier = Modifier.width(28.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Right Area: Task Timeline & Bars
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (monthTasks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No tasks this month",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryTextColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (canUserManage) "Tap + to add a task" else "The owner or an admin adds the tasks",
                                    fontSize = 13.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val containerWidthDp = this.maxWidth
                            val containerWidthPx = with(density) { containerWidthDp.toPx() }

                            val lineRows = remember(laneAssignments) {
                                val set = mutableSetOf<Int>()
                                laneAssignments.forEach { assign ->
                                    set.add(assign.startRow)
                                    set.add(assign.endRow + 1)
                                }
                                set
                            }

                            // Horizontal lines ONLY at task dates
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                lineRows.forEach { r ->
                                    val y = r * rowHeightPx
                                    drawLine(
                                        color = outlineColor.copy(alpha = 0.45f),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }

                            val isHorizontalSwipe = maxClusterLanes > 2
                            val fixedLaneWidth = 76.dp
                            val laneGap = 1.dp

                            val calculatedLaneWidth = if (isHorizontalSwipe) {
                                fixedLaneWidth
                            } else {
                                val avail = (containerWidthDp - 4.dp).coerceAtLeast(40.dp)
                                (avail / max(1, maxClusterLanes)).coerceIn(40.dp, 200.dp)
                            }

                            val scrollState = rememberScrollState()

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isHorizontalSwipe) Modifier.horizontalScroll(scrollState)
                                        else Modifier
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(
                                            if (isHorizontalSwipe) {
                                                (maxClusterLanes * (fixedLaneWidth.value + laneGap.value)).dp + 16.dp
                                            } else {
                                                containerWidthDp
                                            }
                                        )
                                ) {
                                    laneAssignments.forEach { assign ->
                                        val status = getTaskStatus(assign.task, today)
                                        val topOffset = (assign.startRow * rowHeightPx).let { with(density) { it.toDp() } }
                                        val rawHeight = (assign.spanDays * rowHeightPx - 2f).let { with(density) { it.toDp() } }
                                        val barHeight = if (rawHeight > 20.dp) rawHeight else 20.dp

                                        val leftOffset = (assign.laneIndex * (calculatedLaneWidth.value + laneGap.value)).dp

                                        TaskBarItem(
                                            task = assign.task,
                                            status = status,
                                            width = calculatedLaneWidth,
                                            height = barHeight,
                                            hasTopChevron = assign.hasTopChevron,
                                            hasBottomChevron = assign.hasBottomChevron,
                                            isDark = isDark,
                                            tealAccent = tealAccent,
                                            dateRange = formatScheduleRange(assign.task.timelineStart(), assign.task.timelineEnd()),
                                            onClick = { onTaskClick(assign.task) },
                                            modifier = Modifier.offset(x = leftOffset, y = topOffset)
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
}

@Composable
fun TaskBarItem(
    task: GroupTaskEntity,
    status: TaskStatus,
    width: Dp,
    height: Dp,
    hasTopChevron: Boolean,
    hasBottomChevron: Boolean,
    isDark: Boolean,
    tealAccent: Color,
    dateRange: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = remember(status, isDark) {
        when (status) {
            TaskStatus.ONGOING -> {
                // Neutral and strongest, so what is happening now stands out without colour
                if (isDark) Pair(Color(0xFF3A424C), Color(0xFFF0F3F5))
                else Pair(Color(0xFFC9D0D8), Color(0xFF11161A))
            }
            TaskStatus.UPCOMING -> {
                if (isDark) Pair(Color(0xFF243049), Color(0xFFDCE6FB))
                else Pair(Color(0xFFD6E0F4), Color(0xFF1D3266))
            }
            TaskStatus.DELAYED -> {
                if (isDark) Pair(Color(0xFF4A2623), Color(0xFFF4CFCB))
                else Pair(Color(0xFFF4CFCB), Color(0xFF4A2623))
            }
            TaskStatus.COMPLETED -> {
                if (isDark) Pair(Color(0xFF1E242B), Color(0xFF8A9199))
                else Pair(Color(0xFFE6E9ED), Color(0xFF65707A))
            }
        }
    }

    val statusLabel = status.name.lowercase().replaceFirstChar { it.uppercase() }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .semantics(mergeDescendants = true) {
                contentDescription = "${task.title}, $statusLabel, $dateRange"
            }
            .clickable(onClickLabel = "Open task") { onClick() },
        contentAlignment = Alignment.TopStart
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(5.dp)),
            shape = RoundedCornerShape(5.dp),
            color = bgColor,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        StatusIcon(status = status, isDark = isDark, tealAccent = tealAccent, modifier = Modifier.size(9.dp))

                        Text(
                            text = task.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            maxLines = if (height >= 38.dp) 3 else 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (status == TaskStatus.COMPLETED) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }

                    if (height >= 48.dp) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = dateRange,
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (hasTopChevron) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Starts earlier",
                        tint = textColor,
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                    )
                }

                if (hasBottomChevron) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Ends later",
                        tint = textColor,
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusIcon(status: TaskStatus, isDark: Boolean = true, tealAccent: Color = Color(0xFF1F8A70), modifier: Modifier = Modifier) {
    val color = getStatusTextColor(status, isDark, tealAccent)
    when (status) {
        TaskStatus.ONGOING -> {
            Surface(
                modifier = modifier,
                shape = CircleShape,
                color = color
            ) {}
        }
        TaskStatus.UPCOMING -> {
            Canvas(modifier = modifier) {
                drawCircle(
                    color = color,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                )
            }
        }
        TaskStatus.DELAYED -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = Color(0xFFD32F2F)
                ) {}
                Text(
                    text = "!",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        TaskStatus.COMPLETED -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = color,
                modifier = modifier
            )
        }
    }
}

fun getStatusTextColor(status: TaskStatus, isDark: Boolean = true, tealAccent: Color = Color(0xFF1F8A70)): Color {
    return when (status) {
        TaskStatus.COMPLETED -> Color(0xFF8A94A0)
        TaskStatus.DELAYED -> Color(0xFFD32F2F)
        TaskStatus.ONGOING -> if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
        TaskStatus.UPCOMING -> if (isDark) Color(0xFF8FA9E6) else Color(0xFF3E5EA8)
    }
}

private data class TaskPrepData(val startRow: Int, val endRow: Int, val span: Int, val topChev: Boolean, val botChev: Boolean)
