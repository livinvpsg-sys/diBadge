package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.GroupTaskEntity
import com.fabxdi.dibadge.data.TaskCheckItem
import com.fabxdi.dibadge.data.encodeTaskChecklist
import com.fabxdi.dibadge.data.parseTaskChecklist
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.Check
import java.time.temporal.ChronoUnit
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.GroupMember
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class TaskStatusCategory {
    OVERDUE,
    PENDING,
    DONE
}

fun calculateTaskStatusCategory(task: GroupTaskEntity, today: LocalDate = LocalDate.now()): TaskStatusCategory {
    return when {
        task.isCompleted -> TaskStatusCategory.DONE
        task.dueDate != null && task.dueDate.isBefore(today) -> TaskStatusCategory.OVERDUE
        else -> TaskStatusCategory.PENDING
    }
}

fun sortGroupTasks(tasks: List<GroupTaskEntity>, today: LocalDate = LocalDate.now()): List<GroupTaskEntity> {
    return tasks.sortedWith(Comparator { a, b ->
        val catA = calculateTaskStatusCategory(a, today)
        val catB = calculateTaskStatusCategory(b, today)

        if (catA != catB) {
            catA.ordinal.compareTo(catB.ordinal)
        } else {
            when (catA) {
                TaskStatusCategory.OVERDUE -> {
                    if (a.dueDate != null && b.dueDate != null) a.dueDate.compareTo(b.dueDate)
                    else b.createdAt.compareTo(a.createdAt)
                }
                TaskStatusCategory.PENDING -> {
                    if (a.dueDate != null && b.dueDate != null) a.dueDate.compareTo(b.dueDate)
                    else if (a.dueDate != null) -1
                    else if (b.dueDate != null) 1
                    else b.createdAt.compareTo(a.createdAt)
                }
                TaskStatusCategory.DONE -> {
                    b.createdAt.compareTo(a.createdAt)
                }
            }
        }
    })
}

fun resolveUserRole(
    members: List<GroupMember>,
    currentUserId: String = "me",
    isAdmin: Boolean = false,
    creatorId: String = "",
    ownerId: String = ""
): String {
    val uid = currentUserId.trim()
    val userMember = members.find {
        it.uid == uid || it.id == uid || it.isYou || it.displayName.contains("(You)", ignoreCase = true)
    }

    val rawRole = userMember?.role?.trim()?.lowercase() ?: ""

    if (rawRole == "owner" || rawRole == "admin") {
        return rawRole
    }

    val isCreatorOrOwner = (creatorId.isNotBlank() && (creatorId == uid || uid == "me")) ||
            (ownerId.isNotBlank() && (ownerId == uid || uid == "me")) ||
            isAdmin ||
            members.isEmpty() ||
            (members.size == 1 && (userMember != null || members.first().isYou || members.first().uid == uid))

    if (isCreatorOrOwner) {
        return "owner"
    }

    return if (rawRole.isNotBlank()) rawRole else "member"
}

fun canUserAssignTasks(userRole: String): Boolean {
    val role = userRole.lowercase().trim()
    return role == "owner" || role == "admin"
}

fun isTaskVisibleInAssignedToMe(task: GroupTaskEntity, currentUserId: String): Boolean {
    return task.assignedToUid == currentUserId || task.assignedTo.contains("(You)", ignoreCase = true)
}

fun createTasksForAssignees(
    title: String,
    groupId: String,
    assignees: List<GroupMember>,
    assignerName: String,
    assignerUid: String,
    dueDate: LocalDate?,
    dueTime: String?,
    note: String
): List<GroupTaskEntity> {
    return assignees.map { member ->
        GroupTaskEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            title = title.trim(),
            assignedTo = member.name,
            assignedToUid = member.uid,
            assignedBy = assignerName,
            assignedByUid = assignerUid,
            dueDate = dueDate,
            dueTime = dueTime,
            note = note.trim(),
            isCompleted = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksBottomSheet(
    groupId: String,
    tasks: List<GroupTaskEntity>,
    members: List<GroupMember>,
    currentRole: String = "admin", // "owner", "admin", "member"
    currentUserName: String = "",
    currentUserUid: String = "me",
    linkedGroups: List<LinkedGroupOption> = emptyList(),
    loadLinkedMembers: suspend (String) -> List<GroupMember> = { emptyList() },
    onAssignTaskConfirmed: (NewTaskInput) -> Unit,
    onToggleChecklistItem: (task: GroupTaskEntity, index: Int) -> Unit = { _, _ -> },
    onMarkDone: (task: GroupTaskEntity, done: Boolean) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    var showAssignTaskSheet by remember { mutableStateOf(false) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    // look the task up in the live list so ticks and status changes show at once
    val selectedTaskForDetails = tasks.firstOrNull { it.id == selectedTaskId }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val isDark = diBadgeColors.isDark

    val canAssign = canUserAssignTasks(currentRole)
    val today = remember { LocalDate.now() }

    val sortedTasks = remember(tasks, today) {
        sortGroupTasks(tasks, today)
    }

    if (showAssignTaskSheet) {
        AssignTaskSheet(
            members = members,
            linkedGroups = linkedGroups,
            loadLinkedMembers = loadLinkedMembers,
            onAssignConfirmed = { input ->
                showAssignTaskSheet = false
                onAssignTaskConfirmed(input)
                coroutineScope.launch {
                    val nameStr = if (input.assignees.size == 1) input.assignees.first().name else "${input.assignees.size} people"
                    snackbarHostState.showSnackbar("Task assigned to $nameStr")
                }
            },
            onDismiss = { showAssignTaskSheet = false }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
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
                .padding(bottom = 28.dp)
                .animateContentSize(animationSpec = tween(200))
        ) {
            // Header Row (48dp high)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (tasks.isEmpty()) "Tasks" else "Tasks (${tasks.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )

                if (canAssign) {
                    Row(
                        modifier = Modifier
                            .clickable { showAssignTaskSheet = true }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAdd,
                            contentDescription = "Assign task",
                            tint = primaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Assign task",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // List / Empty State
            if (sortedTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Text(
                            text = "No tasks yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (canAssign) "Tap Assign task to add one" else "Tasks assigned in this group appear here",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val maxListHeight = (minOf(sortedTasks.size, 8) * 68).dp
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight)
                ) {
                    items(sortedTasks, key = { it.id }) { task ->
                        TaskRowItem(
                            task = task,
                            today = today,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            outlineColor = outlineColor,
                            isDark = isDark,
                            onClick = { selectedTaskId = task.id }
                        )
                    }
                }
            }

            // "Task assigned to ..." shows here, inside the sheet.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (selectedTaskForDetails != null) {
        val task = selectedTaskForDetails
        val canComplete = canAssign ||
                task.assignedToUid == currentUserUid ||
                task.assignedByUid == currentUserUid
        TaskDetailsSheet(
            task = task,
            today = today,
            canComplete = canComplete,
            canTick = task.assignedToUid == currentUserUid || canAssign,
            onToggleChecklistItem = { index -> onToggleChecklistItem(task, index) },
            onMarkDone = { onMarkDone(task, !task.isCompleted) },
            onDismiss = { selectedTaskId = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailsSheet(
    task: GroupTaskEntity,
    today: LocalDate,
    canComplete: Boolean,
    canTick: Boolean,
    onToggleChecklistItem: (Int) -> Unit,
    onMarkDone: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val dimColor = diBadgeColors.tabInactive
    val isDark = diBadgeColors.isDark

    val checklist = remember(task.checklistJson) { parseTaskChecklist(task.checklistJson) }
    val doneCount = checklist.count { it.done }

    val statusLabel = when {
        task.isCompleted -> "Done"
        task.dueDate != null && task.dueDate.isBefore(today) -> "Overdue"
        else -> "Pending"
    }
    val overdueColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)
    val statusColor = if (statusLabel == "Overdue") overdueColor else secondaryTextColor

    val daysText = remember(task.dueDate, task.isCompleted) {
        val d = task.dueDate
        if (d == null || task.isCompleted) null
        else {
            val diff = ChronoUnit.DAYS.between(today, d)
            when {
                diff > 1L -> "$diff days left"
                diff == 1L -> "1 day left"
                diff == 0L -> "Due today"
                diff == -1L -> "1 day overdue"
                else -> "${-diff} days overdue"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
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
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = task.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Text(
                        text = "${priorityLabel(task.priority)} priority",
                        fontSize = 12.sp,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, if (statusLabel == "Overdue") overdueColor else outlineColor)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            DetailInfoRow(Icons.Outlined.Person, dimColor) {
                Column {
                    Text(task.assignedTo, fontSize = 16.sp, color = primaryTextColor)
                    Text("Assigned by ${task.assignedBy}", fontSize = 13.sp, color = secondaryTextColor)
                }
            }
            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            if (task.dueDate != null) {
                DetailInfoRow(Icons.Outlined.CalendarToday, dimColor) {
                    val timeStr = if (!task.dueTime.isNullOrBlank()) ", ${task.dueTime}" else ""
                    val dueText = task.dueDate.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()))
                    Column {
                        Text("$dueText$timeStr", fontSize = 16.sp, color = primaryTextColor)
                        if (daysText != null) {
                            Text(
                                daysText,
                                fontSize = 13.sp,
                                color = if (statusLabel == "Overdue") overdueColor else secondaryTextColor
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor)
            }

            if (task.dueDate != null && task.remindBeforeMinutes >= 0) {
                DetailInfoRow(Icons.Outlined.NotificationsNone, dimColor) {
                    Text(reminderLabel(task.remindBeforeMinutes), fontSize = 16.sp, color = primaryTextColor)
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor)
            }

            if (task.note.isNotBlank()) {
                DetailInfoRow(Icons.Outlined.Description, dimColor) {
                    Text(task.note, fontSize = 16.sp, color = primaryTextColor)
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor)
            }

            if (checklist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Checklist", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                    Text("$doneCount of ${checklist.size}", fontSize = 13.sp, color = secondaryTextColor)
                }
                Spacer(modifier = Modifier.height(4.dp))
                checklist.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable(enabled = canTick) { onToggleChecklistItem(index) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = RoundedCornerShape(5.dp),
                            color = if (item.done) tealAccent else Color.Transparent,
                            border = BorderStroke(1.5.dp, if (item.done) tealAccent else dimColor)
                        ) {
                            if (item.done) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Done",
                                        tint = if (isDark) Color(0xFF0E1114) else Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = item.text,
                            fontSize = 16.sp,
                            color = if (item.done) secondaryTextColor else primaryTextColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(thickness = 1.dp, color = outlineColor)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Close",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                if (canComplete) {
                    Spacer(modifier = Modifier.width(28.dp))
                    Text(
                        text = if (task.isCompleted) "Mark as not done" else "Mark as done",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable { onMarkDone() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        content()
    }
}

@Composable
fun TaskRowItem(
    task: GroupTaskEntity,
    today: LocalDate,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    outlineColor: Color,
    isDark: Boolean,
    onClick: () -> Unit = {}
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }

    val sublineText = remember(task.dueDate, task.dueTime, task.assignedTo) {
        if (task.dueDate != null) {
            val dStr = "Due ${task.dueDate.format(dateFormatter)}"
            val timeStr = if (!task.dueTime.isNullOrBlank()) ", ${task.dueTime}" else ""
            "$dStr$timeStr · ${task.assignedTo}"
        } else {
            task.assignedTo
        }
    }

    // Plain text status: only Overdue gets colour (a soft red); Done and Pending stay neutral.
    val statusLabel = when {
        task.isCompleted -> "Done"
        task.dueDate != null && task.dueDate.isBefore(today) -> "Overdue"
        else -> "Pending"
    }
    val statusFg = if (statusLabel == "Overdue") {
        if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)
    } else {
        secondaryTextColor
    }

    val accessibilityDesc = "${task.title}, $sublineText, $statusLabel"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDesc
            },
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title + Subline (LABEL ONLY: NO CIRCLE, NO CHECKBOX)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (task.isCompleted) secondaryTextColor else primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = sublineText,
                    fontSize = 13.sp,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = statusLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusFg
            )
        }

        HorizontalDivider(thickness = 1.dp, color = outlineColor)
    }
}

// ---------------------------------------------------------------------------
// PREVIEWS (412dp width, Light & Dark)
// ---------------------------------------------------------------------------

@Preview(name = "Tasks Sheet List - Light", widthDp = 412, showBackground = true)
@Composable
fun TasksBottomSheetListLightPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TasksBottomSheet(
                groupId = "g1",
                tasks = listOf(
                    GroupTaskEntity("1", "g1", "Prepare quarterly deck", "Anita Sharma", "u1", "Livin Varghese", "u0", LocalDate.now().minusDays(2), "5:00 pm", "", false),
                    GroupTaskEntity("2", "g1", "Review design specs", "Rahul Verma", "u2", "Livin Varghese", "u0", LocalDate.now().plusDays(2), "5:00 pm", "", false),
                    GroupTaskEntity("3", "g1", "Finalize budget report", "Livin Varghese", "u0", "Livin Varghese", "u0", LocalDate.now().minusDays(5), null, "", true)
                ),
                members = listOf(GroupMember("u1", "Anita Sharma"), GroupMember("u2", "Rahul Verma")),
                currentRole = "admin",
                onAssignTaskConfirmed = { },
                onDismiss = {}
            )
        }
    }
}

@Preview(
    name = "Tasks Sheet List - Dark",
    widthDp = 412,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun TasksBottomSheetListDarkPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.DARK) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TasksBottomSheet(
                groupId = "g1",
                tasks = listOf(
                    GroupTaskEntity("1", "g1", "Prepare quarterly deck", "Anita Sharma", "u1", "Livin Varghese", "u0", LocalDate.now().minusDays(2), "5:00 pm", "", false),
                    GroupTaskEntity("2", "g1", "Review design specs", "Rahul Verma", "u2", "Livin Varghese", "u0", LocalDate.now().plusDays(2), "5:00 pm", "", false),
                    GroupTaskEntity("3", "g1", "Finalize budget report", "Livin Varghese", "u0", "Livin Varghese", "u0", LocalDate.now().minusDays(5), null, "", true)
                ),
                members = listOf(GroupMember("u1", "Anita Sharma"), GroupMember("u2", "Rahul Verma")),
                currentRole = "admin",
                onAssignTaskConfirmed = { },
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "Tasks Sheet Member View - Light", widthDp = 412, showBackground = true)
@Composable
fun TasksBottomSheetMemberViewLightPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TasksBottomSheet(
                groupId = "g1",
                tasks = listOf(
                    GroupTaskEntity("1", "g1", "Review design specs", "Rahul Verma", "u2", "Livin Varghese", "u0", LocalDate.now().plusDays(2), "5:00 pm", "", false)
                ),
                members = listOf(GroupMember("u1", "Anita Sharma")),
                currentRole = "member",
                onAssignTaskConfirmed = { },
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "Tasks Sheet Empty - Light", widthDp = 412, showBackground = true)
@Composable
fun TasksBottomSheetEmptyLightPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TasksBottomSheet(
                groupId = "g1",
                tasks = emptyList(),
                members = listOf(GroupMember("u1", "Anita Sharma")),
                currentRole = "member",
                onAssignTaskConfirmed = { },
                onDismiss = {}
            )
        }
    }
}