package com.fabxdi.dibadge.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.GroupTaskEntity
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.GroupTaskViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskPreviewSheet(
    groupId: String,
    task: GroupTaskEntity,
    canUserManage: Boolean,
    userName: String,
    userUid: String,
    taskViewModel: GroupTaskViewModel,
    onTaskUpdated: () -> Unit,
    onTaskDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentTask by remember { mutableStateOf(task) }
    var notesText by remember { mutableStateOf(currentTask.note) }
    var isEditingNotes by remember { mutableStateOf(false) }

    var notesTextFieldValue by remember {
        mutableStateOf(TextFieldValue(text = notesText, selection = TextRange(notesText.length)))
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val errorColor = colors.error
    val tealAccent = diBadgeColors.accent
    val isDark = diBadgeColors.isDark
    val dimColor = secondaryTextColor.copy(alpha = 0.5f)

    val today = LocalDate.now()
    val status = getTaskStatus(currentTask, today)

    val start = currentTask.startDate ?: currentTask.dueDate ?: today
    val end = currentTask.endDate ?: currentTask.dueDate ?: start
    val daysCount = ChronoUnit.DAYS.between(start, end).toInt() + 1

    val createdDateStr = remember(currentTask.createdAt) {
        val dt = Instant.ofEpochMilli(currentTask.createdAt).atZone(ZoneId.systemDefault())
        dt.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }

    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Auto-save notes on focus loss or dismiss
    fun saveNotesIfNeeded() {
        val updatedNotes = notesTextFieldValue.text.trim()
        if (updatedNotes != currentTask.note) {
            taskViewModel.updateTaskNotes(groupId, currentTask.id, updatedNotes)
            currentTask = currentTask.copy(note = updatedNotes)
            notesText = updatedNotes
            onTaskUpdated()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            saveNotesIfNeeded()
            onDismiss()
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
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
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. TOP LINE: Status Label Badge (left-aligned, no "Task" label)
            StatusLabelBadge(status = status, isDark = isDark, tealAccent = tealAccent)

            Spacer(modifier = Modifier.height(12.dp))

            // 2. NAME ROW: Completion Circle (22dp) + Task Title + Trash Icon (22dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Completion Circle (22dp)
                val isCompleted = status == TaskStatus.COMPLETED
                val circleBorderColor = if (isCompleted) getStatusAccentColor(status, tealAccent) else secondaryTextColor.copy(alpha = 0.6f)
                val circleFillColor = if (isCompleted) getStatusAccentColor(status, tealAccent) else Color.Transparent

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(circleFillColor)
                        .border(
                            width = if (isCompleted) 0.dp else 1.7.dp,
                            color = circleBorderColor,
                            shape = CircleShape
                        )
                        .clickable(enabled = canUserManage) {
                            val nextCompleted = !isCompleted
                            taskViewModel.updateTaskCompletion(
                                groupId = groupId,
                                taskId = currentTask.id,
                                isCompleted = nextCompleted,
                                userName = userName,
                                userUid = userUid,
                                onUpdated = {
                                    currentTask = currentTask.copy(
                                        isCompleted = nextCompleted,
                                        completedBy = if (nextCompleted) userName else null,
                                        completedAt = if (nextCompleted) System.currentTimeMillis() else null
                                    )
                                    onTaskUpdated()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = cardBgColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Task Title (19sp semibold)
                Text(
                    text = currentTask.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Trash Icon (22dp error red)
                if (canUserManage) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete task",
                            tint = errorColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. INFO LINE: Directly under task name, left aligned with name text (offset by circle width 22dp + 10dp gap = 32dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp)
            ) {
                Text(
                    text = "$daysCount ${if (daysCount == 1) "day" else "days"} · Created by ${currentTask.assignedBy.ifBlank { "User" }}, $createdDateStr",
                    fontSize = 12.sp,
                    color = secondaryTextColor
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. DIVIDER: thin 1dp full-width line (18dp above dates)
            HorizontalDivider(
                thickness = 1.dp,
                color = outlineColor.copy(alpha = 0.50f)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 5. START DATE & END DATE (side by side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Start Date
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Start date", fontSize = 12.sp, color = secondaryTextColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    val isStartEditable = canUserManage && status == TaskStatus.UPCOMING
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = isStartEditable) {
                                if (isStartEditable) showStartDatePicker = true
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = dimColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = start.format(dateFormatter),
                            fontSize = 14.sp,
                            color = if (isStartEditable) primaryTextColor else dimColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // End Date
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "End date", fontSize = 12.sp, color = secondaryTextColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    val isEndEditable = canUserManage && status != TaskStatus.COMPLETED
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = isEndEditable) {
                                if (isEndEditable) showEndDatePicker = true
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = dimColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = end.format(dateFormatter),
                            fontSize = 14.sp,
                            color = if (isEndEditable) primaryTextColor else dimColor
                        )
                    }
                }
            }

            // Equal breathing space: 18dp between dates and Notes block
            if (canUserManage || notesText.isNotBlank()) {
                Spacer(modifier = Modifier.height(18.dp))

                // 6. NOTES BLOCK
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Notes", fontSize = 12.sp, color = secondaryTextColor)
                    Spacer(modifier = Modifier.height(5.dp))

                    if (canUserManage) {
                        if (isEditingNotes) {
                            BasicTextField(
                                value = notesTextFieldValue,
                                onValueChange = { notesTextFieldValue = it },
                                singleLine = false,
                                maxLines = 6,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    color = primaryTextColor
                                ),
                                cursorBrush = SolidColor(tealAccent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    isEditingNotes = false
                                    saveNotesIfNeeded()
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            isEditingNotes = false
                                            saveNotesIfNeeded()
                                        }
                                    }
                            )
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        } else {
                            Text(
                                text = notesText.ifBlank { "Add a note" },
                                fontSize = 14.sp,
                                color = if (notesText.isBlank()) dimColor else primaryTextColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        notesTextFieldValue = TextFieldValue(
                                            text = notesText,
                                            selection = TextRange(notesText.length)
                                        )
                                        isEditingNotes = true
                                    }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = notesText,
                            fontSize = 14.sp,
                            color = primaryTextColor,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showStartDatePicker) {
        DiBadgeDatePicker(
            selectedDate = start,
            minDate = LocalDate.now(),
            onDateSelected = { date ->
                val newStart = date
                val newEnd = if (newStart.isAfter(end)) newStart else end
                taskViewModel.updateTaskDates(groupId, currentTask.id, newStart, newEnd) {
                    currentTask = currentTask.copy(startDate = newStart, endDate = newEnd, dueDate = newEnd)
                    onTaskUpdated()
                }
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        DiBadgeDatePicker(
            selectedDate = end,
            minDate = start,
            onDateSelected = { date ->
                val newEnd = date
                taskViewModel.updateTaskDates(groupId, currentTask.id, start, newEnd) {
                    currentTask = currentTask.copy(endDate = newEnd, dueDate = newEnd)
                    onTaskUpdated()
                }
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }

    if (showDeleteDialog) {
        val yearStr = start.year.toString()
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete this task?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "${currentTask.title}, ${start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))} – ${end.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))} $yearStr",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        taskViewModel.deleteTask(groupId, currentTask.id) {
                            onTaskDeleted()
                        }
                    }
                ) {
                    Text("Delete", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = errorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

data class StatusLabelColors(
    val bgColor: Color,
    val accentColor: Color,
    val textColor: Color,
    val label: String
)

@Composable
fun StatusLabelBadge(status: TaskStatus, isDark: Boolean, tealAccent: Color) {
    val style = when (status) {
        TaskStatus.ONGOING -> {
            val bg = if (isDark) Color(0xFF1F4A46) else Color(0xFFBFE3DF)
            val textCol = if (isDark) Color(0xFFBFE3DF) else Color(0xFF1F4A46)
            StatusLabelColors(bg, tealAccent, textCol, "Ongoing")
        }
        TaskStatus.UPCOMING -> {
            StatusLabelColors(Color.Transparent, tealAccent, tealAccent, "Upcoming")
        }
        TaskStatus.DELAYED -> {
            val bg = if (isDark) Color(0xFF4A2623) else Color(0xFFF4CFCB)
            val textCol = if (isDark) Color(0xFFF4CFCB) else Color(0xFF4A2623)
            val redAccent = Color(0xFFD32F2F)
            StatusLabelColors(bg, redAccent, textCol, "Delayed")
        }
        TaskStatus.COMPLETED -> {
            val bg = if (isDark) Color(0xFF2A3037) else Color(0xFFD6DBE0)
            val textCol = if (isDark) Color(0xFFD6DBE0) else Color(0xFF2A3037)
            val greyAccent = Color(0xFF8A94A0)
            StatusLabelColors(bg, greyAccent, textCol, "Completed")
        }
    }

    Surface(
        shape = RoundedCornerShape(3.dp),
        color = style.bgColor,
        border = BorderStroke(1.dp, style.accentColor.copy(alpha = 0.40f))
    ) {
        Text(
            text = style.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = style.textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

fun getStatusAccentColor(status: TaskStatus, tealAccent: Color): Color {
    return when (status) {
        TaskStatus.ONGOING, TaskStatus.UPCOMING -> tealAccent
        TaskStatus.DELAYED -> Color(0xFFD32F2F)
        TaskStatus.COMPLETED -> Color(0xFF8A94A0)
    }
}
