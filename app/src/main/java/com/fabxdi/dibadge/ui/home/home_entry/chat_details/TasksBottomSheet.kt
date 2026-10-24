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
    currentUserName: String = "Livin Varghese",
    currentUserUid: String = "me",
    onAssignTaskConfirmed: (title: String, assignees: List<GroupMember>, dueDate: LocalDate?, dueTime: String?, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAssignTaskSheet by remember { mutableStateOf(false) }
    var selectedTaskForDetails by remember { mutableStateOf<GroupTaskEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val isDark = primaryTextColor.red > 0.5f

    val canAssign = canUserAssignTasks(currentRole)
    val today = remember { LocalDate.now() }

    val sortedTasks = remember(tasks, today) {
        sortGroupTasks(tasks, today)
    }

    if (showAssignTaskSheet) {
        AssignTaskSheet(
            members = members,
            onAssignConfirmed = { title, assignees, dueDate, dueTime, note ->
                showAssignTaskSheet = false
                onAssignTaskConfirmed(title, assignees, dueDate, dueTime, note)
                coroutineScope.launch {
                    val nameStr = if (assignees.size == 1) assignees.first().name else "${assignees.size} people"
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
                            onClick = { selectedTaskForDetails = task }
                        )
                    }
                }
            }
        }
    }

    if (selectedTaskForDetails != null) {
        val task = selectedTaskForDetails!!
        AlertDialog(
            onDismissRequest = { selectedTaskForDetails = null },
            title = { Text(text = task.title, color = primaryTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Assigned to: ${task.assignedTo}", fontSize = 14.sp, color = secondaryTextColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Assigned by: ${task.assignedBy}", fontSize = 14.sp, color = secondaryTextColor)
                    if (task.dueDate != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val timeStr = if (!task.dueTime.isNullOrBlank()) ", ${task.dueTime}" else ""
                        Text("Due: ${task.dueDate}$timeStr", fontSize = 14.sp, color = secondaryTextColor)
                    }
                    if (task.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Note: ${task.note}", fontSize = 14.sp, color = primaryTextColor)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTaskForDetails = null }) {
                    Text("Close", color = tealAccent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
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

    val (statusLabel, statusBg, statusFg) = remember(task.isCompleted, task.dueDate, today, isDark) {
        when {
            task.isCompleted -> Triple(
                "Done",
                if (isDark) Color(0x2E58B5AC) else Color(0x241B6F69),
                if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)
            )
            task.dueDate != null && task.dueDate.isBefore(today) -> Triple(
                "Overdue",
                if (isDark) Color(0x29E8837A) else Color(0x1FC4433A),
                if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)
            )
            else -> Triple(
                "Pending",
                if (isDark) Color(0xFF262D35) else Color(0xFFDDE3E8),
                if (isDark) Color(0xFFA3AEB8) else Color(0xFF4A5661)
            )
        }
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

            // Status Label Pill (12sp semibold, 4x10dp padding, 12dp corners)
            Surface(
                modifier = Modifier.height(24.dp),
                shape = RoundedCornerShape(12.dp),
                color = statusBg
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusFg
                    )
                }
            }
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
                onAssignTaskConfirmed = { _, _, _, _, _ -> },
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
                onAssignTaskConfirmed = { _, _, _, _, _ -> },
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
                onAssignTaskConfirmed = { _, _, _, _, _ -> },
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
                onAssignTaskConfirmed = { _, _, _, _, _ -> },
                onDismiss = {}
            )
        }
    }
}
