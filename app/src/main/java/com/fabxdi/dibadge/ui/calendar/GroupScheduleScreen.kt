package com.fabxdi.dibadge.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
    var selectedTaskForPreview by remember { mutableStateOf<GroupTaskEntity?>(null) }
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

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { monthsList.size })
    val currentYearMonth = monthsList.getOrNull(pagerState.currentPage) ?: YearMonth.now()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                // Top Bar: Back arrow and single heading "Schedule" (18sp semibold)
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

                    Text(
                        text = "Schedule",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Month Row: "October 2026" (15sp semibold, centered, no arrows)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
                    Text(
                        text = currentYearMonth.format(monthFormatter),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )
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
                    selectedTaskForPreview = task
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
                selectedTaskForPreview = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Task deleted")
                }
            },
            onDismiss = { selectedTaskForPreview = null }
        )
    }

    if (selectedDayForSheet != null) {
        val dayDate = selectedDayForSheet!!
        val dayTasks = tasks.filter { t ->
            val start = t.startDate ?: t.dueDate ?: t.createdAt.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            val end = t.endDate ?: t.dueDate ?: start
            !dayDate.isBefore(start) && !dayDate.isAfter(end)
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
                    Spacer(modifier = Modifier.height(12.dp))
                    if (canUserManage) {
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
                                .padding(vertical = 4.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dayTasks.forEach { task ->
                            val start = task.startDate ?: task.dueDate ?: LocalDate.now()
                            val end = task.endDate ?: task.dueDate ?: start
                            val dateRangeStr = if (start == end) {
                                start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
                            } else {
                                "${start.format(DateTimeFormatter.ofPattern("d", Locale.getDefault()))}–${end.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}"
                            }

                            val taskStatus = getTaskStatus(task)
                            val statusColor = getStatusTextColor(taskStatus)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedDayForSheet = null
                                        selectedTaskForPreview = task
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
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = dateRangeStr,
                                            fontSize = 11.sp,
                                            color = secondaryTextColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusIcon(status = taskStatus, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = taskStatus.name.lowercase().replaceFirstChar { it.uppercase() },
                                            fontSize = 11.sp,
                                            color = statusColor
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
            val start = t.startDate ?: t.dueDate ?: monthStart
            val end = t.endDate ?: t.dueDate ?: start
            !start.isAfter(monthEnd) && !end.isBefore(monthStart)
        }
    }

    val laneAssignments = remember(monthTasks, yearMonth) {
        if (monthTasks.isEmpty()) return@remember emptyList<TaskLaneAssignment>()

        val prep = monthTasks.map { task ->
            val rawStart = task.startDate ?: task.dueDate ?: monthStart
            val rawEnd = task.endDate ?: task.dueDate ?: rawStart
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
        val rowHeightPx = max(density.density * 16f, totalAvailableHeightPx / daysInMonth)
        val rowHeightDp = with(density) { rowHeightPx.toDp() }

        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column: Days (52dp wide)
            Column(
                modifier = Modifier
                    .width(52.dp)
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeightDp)
                            .background(dayBg)
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
                                        color = tealAccent
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${date.dayOfMonth}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "${date.dayOfMonth}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isWeekend) secondaryTextColor else primaryTextColor,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Text(
                                text = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 9.sp,
                                color = secondaryTextColor.copy(alpha = 0.7f),
                                modifier = Modifier.width(22.dp)
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

                        val isHorizontalSwipe = maxClusterLanes > 1
                        val fixedLaneWidth = 90.dp
                        val laneGap = 6.dp

                        val calculatedLaneWidth = if (isHorizontalSwipe) {
                            fixedLaneWidth
                        } else {
                            (containerWidthDp - 8.dp).coerceAtLeast(80.dp)
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
                                            (maxClusterLanes * (fixedLaneWidth.value + laneGap.value)).dp + 24.dp
                                        } else {
                                            containerWidthDp
                                        }
                                    )
                            ) {
                                laneAssignments.forEach { assign ->
                                    val status = getTaskStatus(assign.task, today)
                                    val topOffset = (assign.startRow * rowHeightPx).let { with(density) { it.toDp() } }
                                    val rawHeight = (assign.spanDays * rowHeightPx - 2f).let { with(density) { it.toDp() } }
                                    val barHeight = if (rawHeight > 16.dp) rawHeight else 16.dp

                                    val leftOffset = if (isHorizontalSwipe) {
                                        (assign.laneIndex * (fixedLaneWidth.value + laneGap.value)).dp
                                    } else {
                                        (assign.laneIndex * (calculatedLaneWidth.value + laneGap.value)).dp
                                    }

                                    val textLen = assign.task.title.length
                                    val dateStr = "${assign.effectiveStart.dayOfMonth}–${assign.effectiveEnd.dayOfMonth}"
                                    val estimatedContentWidth = (max(textLen * 6.5f, dateStr.length * 6f) + 30f).dp
                                    val cappedBarWidth = estimatedContentWidth.coerceIn(36.dp, calculatedLaneWidth)

                                    TaskBarItem(
                                        task = assign.task,
                                        status = status,
                                        width = cappedBarWidth,
                                        height = barHeight,
                                        hasTopChevron = assign.hasTopChevron,
                                        hasBottomChevron = assign.hasBottomChevron,
                                        isDark = isDark,
                                        tealAccent = tealAccent,
                                        onClick = { onTaskClick(assign.task) },
                                        modifier = Modifier.offset(x = leftOffset, y = topOffset)
                                    )
                                }
                            }
                        }

                        if (isHorizontalSwipe) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(bottom = 2.dp)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(outlineColor.copy(alpha = 0.3f), CircleShape)
                            )
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = remember(status, isDark) {
        when (status) {
            TaskStatus.ONGOING -> {
                if (isDark) Pair(Color(0xFF1F4A46), Color(0xFFBFE3DF))
                else Pair(Color(0xFFBFE3DF), Color(0xFF1F4A46))
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
                if (isDark) Pair(Color(0xFF2A3037), Color(0xFFD6DBE0))
                else Pair(Color(0xFFD6DBE0), Color(0xFF2A3037))
            }
        }
    }

    val tapTargetHeight = if (height > 32.dp) height else 32.dp

    Box(
        modifier = modifier
            .width(width)
            .height(tapTargetHeight)
            .clickable { onClick() },
        contentAlignment = Alignment.TopStart
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(7.dp)),
            shape = RoundedCornerShape(7.dp),
            color = bgColor,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatusIcon(status = status, isDark = isDark, tealAccent = tealAccent, modifier = Modifier.size(11.dp))

                        Text(
                            text = task.title,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            maxLines = if (height >= 40.dp) 3 else 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (status == TaskStatus.COMPLETED) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }

                    if (height >= 55.dp) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val start = task.startDate ?: task.dueDate ?: LocalDate.now()
                        val end = task.endDate ?: task.dueDate ?: start
                        Text(
                            text = "${start.dayOfMonth}–${end.dayOfMonth} ${start.month.name.take(3)}",
                            fontSize = 9.sp,
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
                            .size(12.dp)
                            .align(Alignment.TopEnd)
                    )
                }

                if (hasBottomChevron) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Ends later",
                        tint = textColor,
                        modifier = Modifier
                            .size(12.dp)
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
        TaskStatus.ONGOING -> tealAccent
        TaskStatus.UPCOMING -> if (isDark) Color(0xFF8FA9E6) else Color(0xFF3E5EA8)
    }
}

private data class TaskPrepData(val startRow: Int, val endRow: Int, val span: Int, val topChev: Boolean, val botChev: Boolean)
