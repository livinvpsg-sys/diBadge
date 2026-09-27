package com.fabxdi.dibadge.ui.my_tasks.to_do

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.ui.components.AttachmentList
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderFormScreen(
    reminderToEdit: ReminderEntity? = null,
    onSave: (String, String, LocalDate?, LocalDate?, LocalDate?, String, String?, Boolean, List<String>, Int) -> Unit,
    onDelete: (ReminderEntity) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val titleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(100)
        titleFocusRequester.requestFocus()
        keyboardController?.show()
    }

    var title by remember { mutableStateOf(reminderToEdit?.title ?: "") }
    var content by remember { mutableStateOf(reminderToEdit?.content ?: "") }
    var isRepeatMenuExpanded by remember { mutableStateOf(false) }
    var selectedRepeat by remember { mutableStateOf<String?>(reminderToEdit?.repeatType ?: "once") }
    var pendingRepeatOption by remember { mutableStateOf<String?>(null) }
    val repeatOptions = listOf("once", "weekly", "monthly", "Yearly", "daily", "Custom")

    var selectedTime by remember { mutableStateOf<String?>(reminderToEdit?.time) }
    
    val initialTime = reminderToEdit?.time?.let {
        try {
            LocalTime.parse(it, DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
        } catch (e: Exception) {
            LocalTime.now()
        }
    } ?: LocalTime.now()
    
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute
    )
    var isAlarmEnabled by remember { mutableStateOf(reminderToEdit?.isAlarmEnabled ?: false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var triedToSave by remember { mutableStateOf(false) }

    val selectableDates = remember(selectedTime) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                val today = LocalDate.now()
                if (date.isBefore(today)) return false
                if (date.isEqual(today) && selectedTime != null) {
                    val selTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    if (selTime.isBefore(LocalTime.now())) return false
                }
                return true
            }
            override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = reminderToEdit?.date?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
        selectableDates = selectableDates
    )
    
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = reminderToEdit?.startDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = reminderToEdit?.endDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                return !date.isBefore(LocalDate.now())
            }
            override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
        }
    )

    var attachedFiles by remember { 
        mutableStateOf<List<Uri>>(
            reminderToEdit?.attachments?.map { Uri.parse(it) } ?: emptyList()
        ) 
    }
    
    val hasChanges = remember(
        title, content, selectedRepeat, selectedTime, isAlarmEnabled,
        datePickerState.selectedDateMillis,
        dateRangePickerState.selectedStartDateMillis,
        dateRangePickerState.selectedEndDateMillis,
        attachedFiles
    ) {
        reminderToEdit == null ||
        title != reminderToEdit.title ||
        content != reminderToEdit.content ||
        selectedRepeat != reminderToEdit.repeatType ||
        selectedTime != reminderToEdit.time ||
        isAlarmEnabled != reminderToEdit.isAlarmEnabled ||
        datePickerState.selectedDateMillis != reminderToEdit.date?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli() ||
        dateRangePickerState.selectedStartDateMillis != reminderToEdit.startDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli() ||
        dateRangePickerState.selectedEndDateMillis != reminderToEdit.endDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli() ||
        attachedFiles.map { it.toString() } != reminderToEdit.attachments
    }

    val handleBack = {
        if (hasChanges) {
            showUnsavedChangesDialog = true
        } else {
            onCancel()
        }
    }

    BackHandler(onBack = handleBack)

    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            val remainingSpace = 3 - attachedFiles.size
            if (remainingSpace > 0) {
                val newUris = uris.take(remainingSpace)
                newUris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {}
                }
                attachedFiles = attachedFiles + newUris
            }
        }
    )

    var showDatePicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    fun formatMillisToDate(millis: Long?): String {
        if (millis == null) return ""
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
            .format(formatter)
    }

    val pillDateFormatter = remember { DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.getDefault()) }

    val formattedDateText = remember(
        selectedRepeat,
        datePickerState.selectedDateMillis,
        dateRangePickerState.selectedStartDateMillis,
        dateRangePickerState.selectedEndDateMillis
    ) {
        val start = dateRangePickerState.selectedStartDateMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().format(pillDateFormatter)
        }
        val end = dateRangePickerState.selectedEndDateMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().format(pillDateFormatter)
        }

        if (start != null && end != null) {
            "$start - $end"
        } else if (start != null) {
            start
        } else {
            datePickerState.selectedDateMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().format(pillDateFormatter)
            }
        }
    }

    fun formatTime(hour: Int, minute: Int): String {
        val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
        return LocalTime.of(hour, minute).format(formatter)
    }

    val isDateSelected = (selectedRepeat == "Custom" && dateRangePickerState.selectedStartDateMillis != null) ||
            (selectedRepeat != "Custom")
    val isTimeSelected = selectedTime != null
    val isTextValid = title.trim().isNotEmpty() || content.trim().isNotEmpty()

    val errorColor = Color(0xFFBF2C34) 
    val iconColor = if (triedToSave && !isDateSelected) errorColor else MaterialTheme.colorScheme.onBackground
    val textColor = if (selectedRepeat == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = if (reminderToEdit != null) "Edit task" else "New task",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            },
            navigationIcon = {
                IconButton(onClick = handleBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                if (reminderToEdit != null && !hasChanges) {
                    TextButton(onClick = { showDeleteConfirmation = true }) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.bodyLarge,
                            color = errorColor
                        )
                    }
                } else {
                    TextButton(onClick = {
                        triedToSave = true
                        if (isDateSelected && isTimeSelected && isTextValid) {
                            val date = datePickerState.selectedDateMillis?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                            } ?: reminderToEdit?.date ?: LocalDate.now()

                            val startDate = if (selectedRepeat == "Custom") {
                                dateRangePickerState.selectedStartDateMillis?.let {
                                    Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                                } ?: date
                            } else {
                                date
                            }

                            val endDate = if (selectedRepeat == "Custom") {
                                dateRangePickerState.selectedEndDateMillis?.let {
                                    Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                                }
                            } else {
                                null
                            }

                            onSave(
                                title,
                                content,
                                date,
                                startDate,
                                endDate,
                                selectedRepeat!!,
                                selectedTime,
                                isAlarmEnabled,
                                attachedFiles.map { it.toString() },
                                reminderToEdit?.id ?: 0
                            )
                        }
                    }) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
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
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Title Input Field
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { 
                    Text(
                        text = "Title",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal)
                    ) 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester),
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
                color = if (triedToSave && !isTextValid) errorColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )

            // 2. Content Input Field
            TextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { 
                    Text(
                        text = "Content",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal)
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
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal)
            )

            Spacer(modifier = Modifier.height(20.dp))

            val DatePill: @Composable () -> Unit = {
                val isDateSelected = formattedDateText != null
                FilterChip(
                    selected = isDateSelected,
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "Date",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = formattedDateText ?: "Date",
                            fontSize = 13.sp,
                            fontWeight = if (isDateSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Transparent,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isDateSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        selectedBorderColor = MaterialTheme.colorScheme.onSurface,
                        selectedBorderWidth = 1.5.dp
                    )
                )
            }

            val RepeatPill: @Composable () -> Unit = {
                Box {
                    val isRepeatSelected = selectedRepeat != null
                    FilterChip(
                        selected = isRepeatSelected,
                        onClick = { isRepeatMenuExpanded = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Repeat",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = selectedRepeat ?: "once",
                                fontSize = 13.sp,
                                fontWeight = if (isRepeatSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.Transparent,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                            iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isRepeatSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            selectedBorderColor = MaterialTheme.colorScheme.onSurface,
                            selectedBorderWidth = 1.5.dp
                        )
                    )

                    DropdownMenu(
                        expanded = isRepeatMenuExpanded,
                        onDismissRequest = { isRepeatMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        repeatOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    isRepeatMenuExpanded = false
                                    if (option == "Custom") {
                                        showDateRangePicker = true
                                    } else {
                                        selectedRepeat = option
                                    }
                                }
                            )
                        }
                    }
                }
            }

            val TimePill: @Composable () -> Unit = {
                val isTimeSelected = selectedTime != null
                FilterChip(
                    selected = isTimeSelected,
                    onClick = { showTimePicker = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Time",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = selectedTime ?: "Time",
                            fontSize = 13.sp,
                            fontWeight = if (isTimeSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Transparent,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isTimeSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        selectedBorderColor = MaterialTheme.colorScheme.onSurface,
                        selectedBorderWidth = 1.5.dp
                    )
                )
            }

            val AlarmPill: @Composable () -> Unit = {
                FilterChip(
                    selected = isAlarmEnabled,
                    onClick = { isAlarmEnabled = !isAlarmEnabled },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isAlarmEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                            contentDescription = "Alarm",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (isAlarmEnabled) "ON" else "Alarm",
                            fontSize = 13.sp,
                            fontWeight = if (isAlarmEnabled) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Transparent,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isAlarmEnabled,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        selectedBorderColor = MaterialTheme.colorScheme.onSurface,
                        selectedBorderWidth = 1.5.dp
                    )
                )
            }

            val isCustomRange = selectedRepeat == "Custom" && formattedDateText != null && formattedDateText.contains("-")
            val isSingleDateSelected = formattedDateText != null && !isCustomRange

            if (isCustomRange) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DatePill()
                        RepeatPill()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimePill()
                        AlarmPill()
                    }
                }
            } else if (isSingleDateSelected) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DatePill()
                        RepeatPill()
                        TimePill()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlarmPill()
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DatePill()
                    RepeatPill()
                    TimePill()
                    AlarmPill()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AttachmentList(
                context = context,
                attachedFiles = attachedFiles,
                onRemove = { index ->
                    attachedFiles = attachedFiles.filterIndexed { i, _ -> i != index }
                },
                onAddClick = { attachmentLauncher.launch(arrayOf("*/*")) }
            )
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { 
                showDatePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        dateRangePickerState.setSelection(null, null)
                        selectedRepeat = "once"
                        showDatePicker = false 
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDatePicker = false
                }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { 
                showDateRangePicker = false
                if (dateRangePickerState.selectedStartDateMillis == null || dateRangePickerState.selectedEndDateMillis == null) {
                    if (selectedRepeat == "Custom") {
                        selectedRepeat = "once"
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        datePickerState.selectedDateMillis = null
                        selectedRepeat = "Custom"
                        showDateRangePicker = false 
                    },
                    enabled = dateRangePickerState.selectedStartDateMillis != null && 
                             dateRangePickerState.selectedEndDateMillis != null
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDateRangePicker = false
                    if (dateRangePickerState.selectedStartDateMillis == null || dateRangePickerState.selectedEndDateMillis == null) {
                        if (selectedRepeat == "Custom") {
                            selectedRepeat = "once"
                        }
                    }
                }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text(text = "Select Range", modifier = Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.labelMedium) },
                headline = {
                    val startDateText = dateRangePickerState.selectedStartDateMillis?.let { formatMillisToDate(it) }
                    val endDateText = dateRangePickerState.selectedEndDateMillis?.let { formatMillisToDate(it) }
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = startDateText ?: "Start Date",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            color = if (startDateText != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(text = " - ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Text(
                            text = endDateText ?: "End Date",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            color = if (endDateText != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }

    var isTimePickerAnalog by remember { mutableStateOf(true) }

    if (showTimePicker) {
        val selTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
        val isToday = if (selectedRepeat == "Custom") {
            dateRangePickerState.selectedStartDateMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
            } ?: true
        } else {
            datePickerState.selectedDateMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
            } ?: true
        }
        val isValid = !(isToday && selTime.isBefore(LocalTime.now()))

        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTime = formatTime(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    },
                    enabled = isValid
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isTimePickerAnalog = !isTimePickerAnalog }) {
                        Icon(
                            imageVector = if (isTimePickerAnalog) Icons.Default.Keyboard else Icons.Default.Schedule,
                            contentDescription = "Toggle Time Picker Mode"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showTimePicker = false }) { Text("Cancel", fontWeight = FontWeight.Bold) }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isTimePickerAnalog) {
                    TimePicker(state = timePickerState)
                } else {
                    TimeInput(state = timePickerState)
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete this To Do?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    reminderToEdit?.let { onDelete(it) }
                }) { Text("Delete", color = errorColor) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Do you want to save them before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedChangesDialog = false
                    triedToSave = true
                    if (isDateSelected && isTimeSelected && isTextValid) {
                        val date = datePickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }
                        val startDate = dateRangePickerState.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }
                        val endDate = dateRangePickerState.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }
                        onSave(title, content, date, startDate, endDate, selectedRepeat!!, selectedTime, isAlarmEnabled, attachedFiles.map { it.toString() }, reminderToEdit?.id ?: 0)
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedChangesDialog = false
                    onCancel()
                }) { Text("Discard") }
            }
        )
    }
}
