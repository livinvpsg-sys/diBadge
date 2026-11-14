package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.components.DiBadgeTimePicker
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.GroupMember
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the Assign task sheet collects. */
data class NewTaskInput(
    val title: String,
    val assignees: List<GroupMember>,
    val dueDate: LocalDate?,
    val dueTime: String?,
    val note: String,
    val priority: String,              // "low" | "medium" | "high"
    val remindBeforeMinutes: Int,      // -1 none, 0 at due time, else minutes before
    val checklist: List<String>
)

/** A group linked to the current group; all of its members can be picked at once. */
data class LinkedGroupOption(val id: String, val name: String)

fun reminderLabel(minutes: Int): String = when {
    minutes < 0 -> "None"
    minutes == 0 -> "At due time"
    minutes % 1440 == 0 -> if (minutes / 1440 == 1) "1 day before" else "${minutes / 1440} days before"
    minutes % 60 == 0 -> if (minutes / 60 == 1) "1 hour before" else "${minutes / 60} hours before"
    else -> "$minutes min before"
}

fun priorityLabel(p: String): String = when (p) {
    "low" -> "Low"
    "high" -> "High"
    else -> "Medium"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignTaskSheet(
    members: List<GroupMember>,
    linkedGroups: List<LinkedGroupOption> = emptyList(),
    loadLinkedMembers: suspend (String) -> List<GroupMember> = { emptyList() },
    onAssignConfirmed: (NewTaskInput) -> Unit,
    onDismiss: () -> Unit
) {
    var taskTitle by remember { mutableStateOf("") }
    var selectedAssignees by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var dueTime by remember { mutableStateOf<LocalTime?>(null) }
    var noteText by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
    var remindMinutes by remember { mutableStateOf(-1) }
    val checklist = remember { mutableStateListOf<String>() }

    var showAssignToPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent

    val canAssign = taskTitle.isNotBlank() && selectedAssignees.isNotEmpty()

    val dueDateDisplay = remember(dueDate, dueTime) {
        if (dueDate == null) null
        else {
            val dStr = dueDate!!.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            if (dueTime != null) "$dStr, ${dueTime!!.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}" else dStr
        }
    }

    val sublineNotice = remember(selectedAssignees) {
        if (selectedAssignees.isEmpty()) null
        else if (selectedAssignees.size == 1) "${selectedAssignees.first().name} sees it in My Tasks, Assigned to me"
        else "They see it in My Tasks, Assigned to me"
    }

    if (showAssignToPicker) {
        AssignToPickerSheet(
            members = members,
            linkedGroups = linkedGroups,
            loadLinkedMembers = loadLinkedMembers,
            initialSelected = selectedAssignees,
            onDone = {
                selectedAssignees = it
                showAssignToPicker = false
            },
            onDismiss = { showAssignToPicker = false }
        )
        return
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
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Assign task",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Task field with underline
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Task", fontSize = 13.sp, color = secondaryTextColor)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (taskTitle.isEmpty()) {
                        Text("What needs to be done", fontSize = 20.sp, color = dimColor)
                    }
                    BasicTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 20.sp,
                            color = primaryTextColor
                        ),
                        cursorBrush = SolidColor(primaryTextColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(thickness = 1.dp, color = primaryTextColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Assign to
            TaskFieldRow(
                icon = Icons.Outlined.Person,
                filled = selectedAssignees.isNotEmpty(),
                dimColor = dimColor,
                secondaryTextColor = secondaryTextColor,
                onClick = { showAssignToPicker = true }
            ) {
                if (selectedAssignees.isEmpty()) {
                    Text("Assign to", fontSize = 16.sp, color = dimColor)
                } else {
                    val first = selectedAssignees.first()
                    val avatarColor = remember(first.name) { UserColorUtils.getColorForName(first.name) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(28.dp), shape = CircleShape, color = avatarColor) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = first.name.take(1).uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val textDisplay = if (selectedAssignees.size == 1) first.name else "${first.name} +${selectedAssignees.size - 1}"
                        Text(textDisplay, fontSize = 16.sp, color = primaryTextColor)
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Due date and time
            TaskFieldRow(
                icon = Icons.Outlined.CalendarToday,
                filled = dueDateDisplay != null,
                dimColor = dimColor,
                secondaryTextColor = secondaryTextColor,
                onClick = { showDatePicker = true }
            ) {
                if (dueDateDisplay == null) Text("Due date and time", fontSize = 16.sp, color = dimColor)
                else Text(dueDateDisplay, fontSize = 16.sp, color = primaryTextColor)
            }
            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Reminder
            TaskFieldRow(
                icon = Icons.Outlined.NotificationsNone,
                filled = remindMinutes >= 0,
                dimColor = dimColor,
                secondaryTextColor = secondaryTextColor,
                onClick = { showReminderDialog = true }
            ) {
                if (remindMinutes < 0) Text("Remind", fontSize = 16.sp, color = dimColor)
                else Text(reminderLabel(remindMinutes), fontSize = 16.sp, color = primaryTextColor)
            }
            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Priority chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                listOf("low", "medium", "high").forEach { p ->
                    val selected = priority == p
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) tealAccent.copy(alpha = 0.14f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (selected) tealAccent else outlineColor),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { priority = p }
                    ) {
                        Text(
                            text = priorityLabel(p),
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) tealAccent else secondaryTextColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Checklist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Checklist,
                    contentDescription = null,
                    tint = if (checklist.isNotEmpty()) secondaryTextColor else dimColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (checklist.isEmpty()) {
                        Text(
                            "Checklist",
                            fontSize = 16.sp,
                            color = dimColor,
                            modifier = Modifier
                                .clickable { checklist.add("") }
                                .padding(bottom = 18.dp)
                        )
                    } else {
                        checklist.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.size(18.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(1.5.dp, dimColor)
                                    ) {}
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (item.isEmpty()) {
                                        Text("Item", fontSize = 16.sp, color = dimColor)
                                    }
                                    BasicTextField(
                                        value = item,
                                        onValueChange = { checklist[index] = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 16.sp,
                                            color = primaryTextColor
                                        ),
                                        cursorBrush = SolidColor(primaryTextColor),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                IconButton(onClick = { checklist.removeAt(index) }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Remove item",
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .clickable { checklist.add("") }
                                .padding(top = 6.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = tealAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add item", fontSize = 15.sp, color = tealAccent, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Note
            TaskFieldRow(
                icon = Icons.Outlined.Description,
                filled = noteText.isNotBlank(),
                dimColor = dimColor,
                secondaryTextColor = secondaryTextColor,
                onClick = { showNoteDialog = true }
            ) {
                if (noteText.isBlank()) {
                    Text("Note", fontSize = 16.sp, color = dimColor)
                } else {
                    Text(
                        text = noteText.lineSequence().firstOrNull() ?: "",
                        fontSize = 16.sp,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (sublineNotice != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = sublineNotice, fontSize = 13.sp, color = secondaryTextColor)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.width(28.dp))
                Text(
                    text = "Assign",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canAssign) tealAccent else dimColor,
                    modifier = Modifier
                        .clickable(enabled = canAssign) {
                            val formattedTimeStr = dueTime?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
                            val input = NewTaskInput(
                                title = taskTitle,
                                assignees = selectedAssignees,
                                dueDate = dueDate,
                                dueTime = formattedTimeStr,
                                note = noteText,
                                priority = priority,
                                // a reminder only makes sense when there is a due date
                                remindBeforeMinutes = if (dueDate == null) -1 else remindMinutes,
                                checklist = checklist.map { it.trim() }.filter { it.isNotEmpty() }
                            )
                            onDismiss()
                            onAssignConfirmed(input)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showDatePicker) {
        DiBadgeDatePicker(
            selectedDate = dueDate ?: LocalDate.now(),
            onDateSelected = {
                dueDate = it
                showDatePicker = false
                showTimePicker = true
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        DiBadgeTimePicker(
            initialTime = dueTime ?: LocalTime.of(17, 0),
            title = "Select Due Time",
            onTimeSelected = {
                dueTime = it
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showReminderDialog) {
        ReminderDialog(
            current = remindMinutes,
            hasDueDate = dueDate != null,
            onSelect = {
                remindMinutes = it
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false }
        )
    }

    if (showNoteDialog) {
        var tempNote by remember { mutableStateOf(noteText) }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Note", color = primaryTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempNote,
                    onValueChange = { tempNote = it },
                    placeholder = { Text("Add note details...", color = secondaryTextColor) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryTextColor,
                        unfocusedTextColor = primaryTextColor,
                        cursorColor = primaryTextColor,
                        focusedBorderColor = primaryTextColor,
                        unfocusedBorderColor = outlineColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    noteText = tempNote.trim()
                    showNoteDialog = false
                }) { Text("Save", color = tealAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    tempNote = ""
                    noteText = ""
                    showNoteDialog = false
                }) { Text("Clear", color = secondaryTextColor) }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun TaskFieldRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    dimColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (filled) secondaryTextColor else dimColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        content()
    }
}

@Composable
private fun ReminderDialog(
    current: Int,
    hasDueDate: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tealAccent = DiBadgeTheme.colors.accent
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val presets = listOf(-1 to "None", 0 to "At due time", 60 to "1 hour before", 1440 to "1 day before", 2880 to "2 days before")
    var customHours by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind", color = primaryTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (!hasDueDate) {
                    Text(
                        "Set a due date first. The reminder is sent before it.",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                presets.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable { onSelect(value) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = current == value,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = tealAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 16.sp, color = primaryTextColor)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable { showCustom = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = current > 0 && presets.none { it.first == current },
                        onClick = { showCustom = true },
                        colors = RadioButtonDefaults.colors(selectedColor = tealAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Custom", fontSize = 16.sp, color = primaryTextColor)
                }
                if (showCustom) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 48.dp)) {
                        OutlinedTextField(
                            value = customHours,
                            onValueChange = { customHours = it.filter { c -> c.isDigit() }.take(3) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("hours before", fontSize = 15.sp, color = secondaryTextColor)
                    }
                }
            }
        },
        confirmButton = {
            if (showCustom) {
                TextButton(
                    enabled = (customHours.toIntOrNull() ?: 0) > 0,
                    onClick = { onSelect((customHours.toIntOrNull() ?: 1) * 60) }
                ) { Text("Save", color = tealAccent, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = secondaryTextColor) }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignToPickerSheet(
    members: List<GroupMember>,
    linkedGroups: List<LinkedGroupOption> = emptyList(),
    loadLinkedMembers: suspend (String) -> List<GroupMember> = { emptyList() },
    initialSelected: List<GroupMember>,
    onDone: (List<GroupMember>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedList by remember { mutableStateOf(initialSelected) }
    // group id -> the members it contributed, so unticking the group removes exactly those
    var groupMembers by remember { mutableStateOf<Map<String, List<GroupMember>>>(emptyMap()) }
    var loadingGroupId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val isDark = diBadgeColors.isDark

    val sortedMembers = remember(members) {
        members.sortedByDescending { it.name.contains("(You)", ignoreCase = true) || it.isYou }
    }

    @Composable
    fun CheckCircle(isSelected: Boolean) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = if (isSelected) tealAccent else Color.Transparent,
            border = BorderStroke(1.5.dp, if (isSelected) tealAccent else dimColor)
        ) {
            if (isSelected) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (isDark) Color(0xFF0E1114) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Assign to",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )
                Text(
                    text = "Done",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealAccent,
                    modifier = Modifier
                        .clickable { onDone(selectedList) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Members", fontSize = 13.sp, color = secondaryTextColor, modifier = Modifier.padding(vertical = 6.dp))

                sortedMembers.forEach { member ->
                    val isSelected = selectedList.any { it.id == member.id }
                    val avatarColor = remember(member.name) { UserColorUtils.getColorForName(member.name) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable {
                                selectedList = if (isSelected) selectedList.filter { it.id != member.id }
                                else selectedList + member
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = avatarColor) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = member.name.take(1).uppercase(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = member.name,
                                fontSize = 16.sp,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        CheckCircle(isSelected)
                    }
                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                }

                if (linkedGroups.isNotEmpty()) {
                    Text(
                        "Linked groups",
                        fontSize = 13.sp,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                    )

                    linkedGroups.forEach { group ->
                        val groupSelected = groupMembers.containsKey(group.id)
                        val count = groupMembers[group.id]?.size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable(enabled = loadingGroupId == null) {
                                    if (groupSelected) {
                                        val remove = groupMembers[group.id].orEmpty().map { it.id }.toSet()
                                        // keep anyone another selected group still covers
                                        val stillCovered = groupMembers
                                            .filterKeys { it != group.id }
                                            .values.flatten().map { it.id }.toSet()
                                        selectedList = selectedList.filter { it.id !in remove || it.id in stillCovered }
                                        groupMembers = groupMembers - group.id
                                    } else {
                                        loadingGroupId = group.id
                                        scope.launch {
                                            val loaded = loadLinkedMembers(group.id)
                                            val existing = selectedList.map { it.id }.toSet()
                                            selectedList = selectedList + loaded.filter { it.id !in existing }
                                            groupMembers = groupMembers + (group.id to loaded)
                                            loadingGroupId = null
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Group,
                                        contentDescription = null,
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = group.name,
                                        fontSize = 16.sp,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (count != null) {
                                        Text("$count people", fontSize = 12.sp, color = secondaryTextColor)
                                    }
                                }
                            }
                            if (loadingGroupId == group.id) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = tealAccent
                                )
                            } else {
                                CheckCircle(groupSelected)
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Choose people or whole groups. Each person gets their own task.",
                fontSize = 13.sp,
                color = secondaryTextColor
            )
        }
    }
}