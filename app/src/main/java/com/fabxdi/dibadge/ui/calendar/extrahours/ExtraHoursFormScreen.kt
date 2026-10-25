package com.fabxdi.dibadge.ui.calendar.extrahours

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.components.DiBadgeTimePicker
import com.fabxdi.dibadge.ui.settings.SettingsCard
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraHoursFormScreen(
    existingLeaves: List<LeaveEntity>,
    overtimeToEdit: OvertimeEntity? = null,
    initialDate: LocalDate = LocalDate.now(),
    onApply: (LocalDate, LocalTime, LocalTime, String, Int) -> Unit,
    onDelete: (OvertimeEntity) -> Unit = {},
    onClose: () -> Unit
) {
    val isEditing = overtimeToEdit != null

    var selectedOrg by remember { mutableStateOf("diBadge Inc.") }
    var selectedDate by remember { mutableStateOf(overtimeToEdit?.date ?: initialDate) }
    var startTime by remember { mutableStateOf<LocalTime?>(overtimeToEdit?.startTime ?: LocalTime.of(18, 0)) }
    var endTime by remember { mutableStateOf<LocalTime?>(overtimeToEdit?.endTime ?: LocalTime.of(21, 0)) }
    var description by remember { mutableStateOf(overtimeToEdit?.description ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var showDiscardDialog by remember { mutableStateOf(false) }

    val hasChanges = remember(selectedDate, startTime, endTime, description) {
        if (!isEditing) description.isNotBlank()
        else (selectedDate != overtimeToEdit?.date || startTime != overtimeToEdit?.startTime || endTime != overtimeToEdit?.endTime || description != overtimeToEdit?.description)
    }

    val handleBack = {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler(enabled = true, onBack = { handleBack() })

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val tabInactiveColor = diBadgeColors.tabInactive

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }

    // Hours calculation
    val (calculatedHours, endsNextDay) = remember(startTime, endTime) {
        if (startTime != null && endTime != null) {
            var dur = Duration.between(startTime, endTime).toMinutes() / 60.0
            val nextDay = dur < 0
            if (nextDay) dur += 24.0
            Pair(dur, nextDay)
        } else Pair(0.0, false)
    }

    val canSubmit = startTime != null && endTime != null && calculatedHours > 0.0

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { handleBack() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isEditing) "Edit extra hours" else "New extra hours",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }

                Text(
                    text = "Submit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSubmit) tealAccent else tabInactiveColor,
                    modifier = Modifier
                        .clickable(enabled = canSubmit) {
                            if (startTime != null && endTime != null) {
                                onApply(
                                    selectedDate,
                                    startTime!!,
                                    endTime!!,
                                    description.trim(),
                                    overtimeToEdit?.id ?: 0
                                )
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .semantics {
                            contentDescription = if (canSubmit) "Submit" else "Submit, unavailable"
                        }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Card 1: Organisation
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Organisation", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(selectedOrg, fontSize = 16.sp, color = primaryTextColor)
                    }
                }
            }

            // Section Label: Date and time
            Text(
                text = "Date and time",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
            )

            // Card 2: Date and Time Card
            SettingsCard {
                // Date
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Date", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(selectedDate.format(dateFormatter), fontSize = 16.sp, color = primaryTextColor)
                    }
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "Select date",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Start time
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clickable { showStartTimePicker = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start time", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = startTime?.format(timeFormatter) ?: "Select time",
                            fontSize = 16.sp,
                            color = if (startTime != null) primaryTextColor else tabInactiveColor
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = "Select start time",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // End time
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clickable { showEndTimePicker = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("End time", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = endTime?.format(timeFormatter) ?: "Select time",
                            fontSize = 16.sp,
                            color = if (endTime != null) primaryTextColor else tabInactiveColor
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = "Select end time",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Total Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total", fontSize = 14.sp, color = secondaryTextColor)
                        if (endsNextDay) {
                            Text("Ends the next day", fontSize = 11.sp, color = secondaryTextColor)
                        }
                    }
                    Text(
                        text = if (calculatedHours > 0) "$calculatedHours hrs" else "0 hrs",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent
                    )
                }
            }

            // Section Label: Work done (optional)
            Text(
                text = "Work done (optional)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
            )

            // Card 3: Work done Text Card
            SettingsCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .padding(14.dp)
                ) {
                    if (description.isEmpty()) {
                        Text("Add a note", fontSize = 16.sp, color = tabInactiveColor)
                    }
                    BasicTextField(
                        value = description,
                        onValueChange = { if (it.length <= 300) description = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            color = primaryTextColor
                        ),
                        cursorBrush = SolidColor(tealAccent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DiBadgeDatePicker(
            selectedDate = selectedDate,
            onDateSelected = {
                selectedDate = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showStartTimePicker) {
        DiBadgeTimePicker(
            initialTime = startTime ?: LocalTime.of(18, 0),
            title = "Select Start Time",
            onTimeSelected = {
                startTime = it
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        DiBadgeTimePicker(
            initialTime = endTime ?: LocalTime.of(21, 0),
            title = "Select End Time",
            onTimeSelected = {
                endTime = it
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    text = "Discard this request?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onClose()
                    }
                ) {
                    Text(
                        text = "Discard",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
