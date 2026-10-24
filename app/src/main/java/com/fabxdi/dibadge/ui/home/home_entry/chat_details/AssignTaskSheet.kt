package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.components.DiBadgeTimePicker
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.GroupMember
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignTaskSheet(
    members: List<GroupMember>,
    onAssignConfirmed: (title: String, assignees: List<GroupMember>, dueDate: LocalDate?, dueTime: String?, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var taskTitle by remember { mutableStateOf("") }
    var selectedAssignees by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var dueTime by remember { mutableStateOf<LocalTime?>(null) }
    var noteText by remember { mutableStateOf("") }

    var showAssignToPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val isDark = primaryTextColor.red > 0.5f

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
                text = "Assign task",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Task Field (Label "Task", 20sp text with 1dp underline)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Task",
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )
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
                        cursorBrush = SolidColor(tealAccent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(thickness = 1.dp, color = primaryTextColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 1: Assign to
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clickable { showAssignToPicker = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = if (selectedAssignees.isNotEmpty()) secondaryTextColor else dimColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))

                if (selectedAssignees.isEmpty()) {
                    Text("Assign to", fontSize = 16.sp, color = dimColor)
                } else {
                    val firstMember = selectedAssignees.first()
                    val avatarColor = remember(firstMember.name) { UserColorUtils.getColorForName(firstMember.name) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = avatarColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = firstMember.name.take(1).uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val textDisplay = if (selectedAssignees.size == 1) firstMember.name else "${firstMember.name} +${selectedAssignees.size - 1}"
                        Text(textDisplay, fontSize = 16.sp, color = primaryTextColor)
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Row 2: Due date and time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clickable { showDatePicker = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = if (dueDateDisplay != null) secondaryTextColor else dimColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))

                if (dueDateDisplay == null) {
                    Text("Due date and time", fontSize = 16.sp, color = dimColor)
                } else {
                    Text(dueDateDisplay, fontSize = 16.sp, color = primaryTextColor)
                }
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Row 3: Note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clickable { showNoteDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = if (noteText.isNotBlank()) secondaryTextColor else dimColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))

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
                Text(
                    text = sublineNotice,
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: Cancel and Assign (Text only 15sp semibold, 28dp apart)
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
                            onDismiss()
                            val formattedTimeStr = dueTime?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
                            onAssignConfirmed(taskTitle, selectedAssignees, dueDate, formattedTimeStr, noteText)
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

    if (showNoteDialog) {
        var tempNote by remember { mutableStateOf(noteText) }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Note", color = primaryTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempNote,
                    onValueChange = { tempNote = it },
                    placeholder = { Text("Add note details...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    noteText = tempNote.trim()
                    showNoteDialog = false
                }) {
                    Text("Save", color = tealAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    tempNote = ""
                    noteText = ""
                    showNoteDialog = false
                }) {
                    Text("Clear", color = secondaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignToPickerSheet(
    members: List<GroupMember>,
    initialSelected: List<GroupMember>,
    onDone: (List<GroupMember>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedList by remember { mutableStateOf(initialSelected) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val isDark = primaryTextColor.red > 0.5f

    // Ensure "(You)" member appears first in list
    val sortedMembers = remember(members) {
        members.sortedByDescending { it.name.contains("(You)", ignoreCase = true) || it.isYou }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
            ) {
                items(sortedMembers, key = { it.id }) { member ->
                    val isSelected = selectedList.any { it.id == member.id }
                    val avatarColor = remember(member.name) { UserColorUtils.getColorForName(member.name) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable {
                                selectedList = if (isSelected) {
                                    selectedList.filter { it.id != member.id }
                                } else {
                                    selectedList + member
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = avatarColor
                            ) {
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

                        // 24dp outlined circle that becomes a ticked teal circle when chosen
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

                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Choose one or more people. Each person gets their own task.",
                fontSize = 13.sp,
                color = secondaryTextColor
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PREVIEWS (412dp width)
// ---------------------------------------------------------------------------

@Preview(name = "Assign Task Sheet - Not Filled", widthDp = 412, showBackground = true)
@Composable
fun AssignTaskSheetNotFilledPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AssignTaskSheet(
                members = listOf(
                    GroupMember("1", "Livin Varghese (You)", isYou = true),
                    GroupMember("2", "Anita Sharma"),
                    GroupMember("3", "Rahul Verma")
                ),
                onAssignConfirmed = { _, _, _, _, _ -> },
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "Assign To Picker Sheet", widthDp = 412, showBackground = true)
@Composable
fun AssignToPickerSheetPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AssignToPickerSheet(
                members = listOf(
                    GroupMember("1", "Livin Varghese (You)", isYou = true),
                    GroupMember("2", "Anita Sharma"),
                    GroupMember("3", "Rahul Verma")
                ),
                initialSelected = listOf(GroupMember("2", "Anita Sharma")),
                onDone = {},
                onDismiss = {}
            )
        }
    }
}
