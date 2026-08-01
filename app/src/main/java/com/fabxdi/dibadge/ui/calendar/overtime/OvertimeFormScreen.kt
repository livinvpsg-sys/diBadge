package com.fabxdi.dibadge.ui.calendar.overtime

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OvertimeFormScreen(
    existingLeaves: List<LeaveEntity>,
    overtimeToEdit: OvertimeEntity? = null,
    onApply: (LocalDate, LocalTime, LocalTime, String, Int) -> Unit,
    onDelete: (OvertimeEntity) -> Unit = {},
    onClose: () -> Unit
) {
    val isEditing = overtimeToEdit != null
    
    var selectedDate by remember { mutableStateOf<LocalDate?>(overtimeToEdit?.date) }
    var startTime by remember { mutableStateOf<LocalTime?>(overtimeToEdit?.startTime) }
    var endTime by remember { mutableStateOf<LocalTime?>(overtimeToEdit?.endTime) }
    var description by remember { mutableStateOf(overtimeToEdit?.description ?: "") }

    val hasChanges = remember(selectedDate, startTime, endTime, description) {
        if (!isEditing) return@remember false
        selectedDate != overtimeToEdit?.date ||
        startTime != overtimeToEdit?.startTime ||
        endTime != overtimeToEdit?.endTime ||
        description != (overtimeToEdit?.description ?: "")
    }

    val handleBack = {
        if (hasChanges) {
            // showUnsavedChangesDialog handled later in UI
        } else {
            onClose()
        }
    }
    
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    
    BackHandler {
        if (hasChanges) {
            showUnsavedChangesDialog = true
        } else {
            onClose()
        }
    }
    
    val canEdit = !isEditing || overtimeToEdit?.status == "Pending"
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showLeaveConflictDialog by remember { mutableStateOf(false) }
    var triedToApply by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = (selectedDate ?: LocalDate.now()).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                return !date.isAfter(LocalDate.now())
            }
            override fun isSelectableYear(year: Int): Boolean {
                return year <= LocalDate.now().year
            }
        }
    )

    val startTimePickerState = rememberTimePickerState(
        initialHour = startTime?.hour ?: 18,
        initialMinute = startTime?.minute ?: 0
    )

    val endTimePickerState = rememberTimePickerState(
        initialHour = endTime?.hour ?: 20,
        initialMinute = endTime?.minute ?: 0
    )

    val isFormValid by remember {
        derivedStateOf {
            selectedDate != null && startTime != null && endTime != null && description.trim().isNotEmpty()
        }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()) }

    val totalOvertimeText = remember(startTime, endTime) {
        if (startTime != null && endTime != null) {
            var duration = Duration.between(startTime, endTime)
            if (duration.isNegative) {
                duration = duration.plusDays(1)
            }
            val hours = duration.toHours()
            val minutes = duration.toMinutes() % 60
            String.format(Locale.getDefault(), "Total Overtime Hours : %02d hrs : %02d mts", hours, minutes)
        } else {
            "Total Overtime Hours : 00 hrs : 00 mts"
        }
    }

    val currentAction = when {
        !isEditing -> "Apply"
        hasChanges -> "Save"
        overtimeToEdit?.status == "Pending" -> "Delete"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Overtime",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (hasChanges) {
                        showUnsavedChangesDialog = true
                    } else {
                        onClose()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                if (isEditing && overtimeToEdit?.status == "Approved") {
                    // Approved status - totally cannot edit
                    Text(
                        text = "Approved",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                } else if (currentAction.isNotEmpty()) {
                    TextButton(onClick = {
                        if (currentAction == "Delete") {
                            showDeleteConfirmation = true
                        } else {
                            triedToApply = true
                            if (isFormValid) {
                                val hasConflict = existingLeaves.any { leave ->
                                    val leaveEnd = leave.endDate ?: leave.startDate
                                    !selectedDate!!.isBefore(leave.startDate) && !selectedDate!!.isAfter(leaveEnd)
                                }
                                
                                if (hasConflict) {
                                    showLeaveConflictDialog = true
                                } else {
                                    showConfirmationDialog = true
                                }
                            }
                        }
                    }) {
                        Text(
                            text = currentAction,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (currentAction == "Delete") Color(0xFFBF2C34) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .padding(horizontal = DiBadgeTheme.spacing.medium)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            // Date and Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date (Left)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = canEdit) { showDatePicker = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (triedToApply && selectedDate == null) Color(0xFFBF2C34) else MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedDate?.format(dateFormatter) ?: "Date",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedDate == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onBackground
                    )
                }

                // Time (Right)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = canEdit) { showStartTimePicker = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (triedToApply && (startTime == null || endTime == null)) Color(0xFFBF2C34) else MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (startTime != null && endTime != null) "${startTime?.format(timeFormatter)} - ${endTime?.format(timeFormatter)}" else "Time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (startTime == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Description Input Field
            TextField(
                value = description,
                onValueChange = { description = it },
                readOnly = !canEdit,
                placeholder = {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal)
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 1.dp,
                color = if (triedToApply && description.trim().isEmpty()) Color(0xFFBF2C34) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = totalOvertimeText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // Dialogs
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val isToday = selectedDate == LocalDate.now()
        val selectedStartTime = LocalTime.of(startTimePickerState.hour, startTimePickerState.minute)
        val isStartTimeValid = !isToday || !selectedStartTime.isAfter(LocalTime.now())

        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startTime = selectedStartTime
                        showStartTimePicker = false
                        showEndTimePicker = true
                    },
                    enabled = isStartTimeValid
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
            },
            text = {
                Column {
                    Text("Select Start Time", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 16.dp))
                    TimePicker(state = startTimePickerState)
                    if (!isStartTimeValid) {
                        Text(
                            text = "Cannot select future time for today",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        )
    }

    if (showEndTimePicker) {
        val isToday = selectedDate == LocalDate.now()
        val selectedEndTime = LocalTime.of(endTimePickerState.hour, endTimePickerState.minute)
        // For today, end time cannot be in the future, AND it cannot be before start time (as that would mean tomorrow)
        val isEndTimeInFuture = isToday && selectedEndTime.isAfter(LocalTime.now())
        val isEndTimeNextDay = isToday && startTime != null && selectedEndTime.isBefore(startTime!!)
        val isEndTimeValid = !isEndTimeInFuture && !isEndTimeNextDay

        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endTime = selectedEndTime
                        showEndTimePicker = false
                    },
                    enabled = isEndTimeValid
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
            },
            text = {
                Column {
                    Text("Select End Time", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 16.dp))
                    TimePicker(state = endTimePickerState)
                    if (!isEndTimeValid) {
                        Text(
                            text = if (isEndTimeNextDay) "End time cannot be before start time for today" else "Cannot select future time for today",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        )
    }

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text(text = if (isEditing) "Confirm Changes" else "Confirm Overtime") },
            text = { Text(text = if (isEditing) "Are you sure you want to save these changes?" else "Are you sure you want to apply for this overtime?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmationDialog = false
                    if (selectedDate != null && startTime != null && endTime != null) {
                        onApply(selectedDate!!, startTime!!, endTime!!, description, overtimeToEdit?.id ?: 0)
                    }
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(text = "Confirm Delete") },
            text = { Text(text = "Are you sure you want to delete this overtime?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    overtimeToEdit?.let { onDelete(it) }
                }) {
                    Text("Delete", color = Color(0xFFBF2C34))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text(text = "Unsaved Changes") },
            text = { Text(text = "You have unsaved changes. Do you want to save them before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedChangesDialog = false
                    triedToApply = true
                    if (isFormValid) {
                        val hasConflict = existingLeaves.any { leave ->
                            val leaveEnd = leave.endDate ?: leave.startDate
                            !selectedDate!!.isBefore(leave.startDate) && !selectedDate!!.isAfter(leaveEnd)
                        }
                        if (hasConflict) {
                            showLeaveConflictDialog = true
                        } else {
                            if (selectedDate != null && startTime != null && endTime != null) {
                                onApply(selectedDate!!, startTime!!, endTime!!, description, overtimeToEdit?.id ?: 0)
                            }
                        }
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedChangesDialog = false
                    onClose()
                }) {
                    Text("Discard")
                }
            }
        )
    }

    if (showLeaveConflictDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConflictDialog = false },
            title = { Text(text = "Leave Conflict") },
            text = { Text(text = "You cannot apply for overtime on a date when you have already applied for leave.") },
            confirmButton = {
                TextButton(onClick = { showLeaveConflictDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}
