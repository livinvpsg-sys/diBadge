package com.fabxdi.dibadge.ui.my_tasks.to_do

import com.fabxdi.dibadge.ui.my_tasks.TaskDatePickerDialog
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.style.TextAlign
import java.time.YearMonth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderFormScreen(
    reminderToEdit: ReminderEntity? = null,
    onSave: (String, String, LocalDate?, LocalDate?, LocalDate?, String, String?, Boolean, List<String>, Int) -> Unit,
    onDelete: (ReminderEntity) -> Unit,
    onCancel: () -> Unit,
    autoOpenTimePicker: Boolean = false
) {
    val context = LocalContext.current
    val titleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var isEditMode by remember { mutableStateOf(reminderToEdit == null) }

    LaunchedEffect(Unit) {
        if (reminderToEdit == null) {
            delay(100)
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    var title by remember { mutableStateOf(reminderToEdit?.title ?: "") }
    var contentValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = reminderToEdit?.content ?: "",
                selection = TextRange(reminderToEdit?.content?.length ?: 0)
            )
        )
    }
    val content = contentValue.text
    var isRepeatMenuExpanded by remember { mutableStateOf(false) }
    var selectedRepeat by remember { mutableStateOf<String?>(reminderToEdit?.repeatType) }
    var pendingRepeatOption by remember { mutableStateOf<String?>(null) }
    val repeatOptions = listOf("weekly", "monthly", "Yearly", "daily", "Custom")

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
    
    var selectedDueDate by remember { mutableStateOf<LocalDate?>(reminderToEdit?.date) }

    val customRangeSelectableDates = remember(selectedDueDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                val today = LocalDate.now()
                if (date.isBefore(today)) return false
                if (selectedDueDate != null && date.isAfter(selectedDueDate)) return false
                return true
            }

            override fun isSelectableYear(year: Int): Boolean {
                val todayYear = LocalDate.now().year
                val dueYear = selectedDueDate?.year ?: (todayYear + 50)
                return year in todayYear..dueYear
            }
        }
    }

    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = reminderToEdit?.startDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = reminderToEdit?.endDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
        selectableDates = customRangeSelectableDates
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
        attachedFiles, reminderToEdit
    ) {
        if (reminderToEdit == null) {
            title.trim().isNotEmpty() ||
            content.trim().isNotEmpty() ||
            selectedTime != null ||
            isAlarmEnabled ||
            attachedFiles.isNotEmpty() ||
            datePickerState.selectedDateMillis != null ||
            dateRangePickerState.selectedStartDateMillis != null ||
            (selectedRepeat != null && selectedRepeat != "once")
        } else {
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
    var showWeeklyDaysPicker by remember { mutableStateOf(false) }
    var showRemindMePicker by remember { mutableStateOf(autoOpenTimePicker) }
    var isDateMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(autoOpenTimePicker) {
        if (autoOpenTimePicker) {
            isEditMode = true
        }
    }

fun handleSmartContentValueChange(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    val oldText = oldValue.text
    val newText = newValue.text

    // 1. SPACEBAR PRESSED (User typed e.g. "1." or "-" then Space)
    if (newText.length == oldText.length + 1 && newText.endsWith(" ")) {
        val lines = oldText.split("\n")
        val lastLine = lines.lastOrNull() ?: ""

        val numMatch = Regex("^(\\d+)([.\\)])$").find(lastLine.trim())
        if (numMatch != null) {
            val (numStr, delimiter) = numMatch.destructured
            val base = oldText.dropLast(lastLine.length)
            val updatedText = "$base$numStr$delimiter "
            return TextFieldValue(
                text = updatedText,
                selection = TextRange(updatedText.length)
            )
        }

        val bulletMatch = Regex("^([-*•])$").find(lastLine.trim())
        if (bulletMatch != null) {
            val bullet = bulletMatch.groupValues[1]
            val base = oldText.dropLast(lastLine.length)
            val updatedText = "$base$bullet "
            return TextFieldValue(
                text = updatedText,
                selection = TextRange(updatedText.length)
            )
        }
    }

    // 2. ENTER PRESSED (User pressed Enter on an existing list line)
    if (newText.length == oldText.length + 1 && newText.endsWith("\n")) {
        val lines = oldText.split("\n")
        val lastLine = lines.lastOrNull() ?: ""

        val numberMatch = Regex("^(\\d+)([.\\)])\\s+(.*)$").find(lastLine)
        if (numberMatch != null) {
            val (numStr, delimiter, textAfter) = numberMatch.destructured
            if (textAfter.isBlank()) {
                val prefixToRemove = "$numStr$delimiter"
                val base = oldText.dropLast(lastLine.length)
                val cleanedLastLine = lastLine.removePrefix(prefixToRemove).trimStart()
                val updatedText = base + cleanedLastLine
                return TextFieldValue(
                    text = updatedText,
                    selection = TextRange(updatedText.length)
                )
            } else {
                val nextNum = (numStr.toIntOrNull() ?: 1) + 1
                val updatedText = "$newText$nextNum$delimiter "
                return TextFieldValue(
                    text = updatedText,
                    selection = TextRange(updatedText.length)
                )
            }
        }

        val bulletMatch = Regex("^([-*•])\\s+(.*)$").find(lastLine)
        if (bulletMatch != null) {
            val (bullet, textAfter) = bulletMatch.destructured
            if (textAfter.isBlank()) {
                val base = oldText.dropLast(lastLine.length)
                val cleanedLastLine = lastLine.removePrefix(bullet).trimStart()
                val updatedText = base + cleanedLastLine
                return TextFieldValue(
                    text = updatedText,
                    selection = TextRange(updatedText.length)
                )
            } else {
                val updatedText = "$newText$bullet "
                return TextFieldValue(
                    text = updatedText,
                    selection = TextRange(updatedText.length)
                )
            }
        }
    }

    return newValue
}

    fun formatMillisToDate(millis: Long?): String {
        if (millis == null) return ""
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
            .format(formatter)
    }

    val pillDateFormatter = remember { DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.getDefault()) }

    val formattedDateText = remember(selectedDueDate) {
        selectedDueDate?.format(pillDateFormatter)
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
                    text = if (reminderToEdit != null) {
                        if (isEditMode) "Edit task" else "Task Details"
                    } else "New task",
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
                if (reminderToEdit != null && !isEditMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isEditMode = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Task",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        TextButton(onClick = { showDeleteConfirmation = true }) {
                            Text(
                                text = "Delete",
                                style = MaterialTheme.typography.bodyLarge,
                                color = errorColor
                            )
                        }
                    }
                } else {
                    TextButton(onClick = {
                        triedToSave = true
                        if (isTextValid) {
                            val dueDate = selectedDueDate

                            val startDate = if (selectedRepeat == "Custom") {
                                dateRangePickerState.selectedStartDateMillis?.let {
                                    Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                                } ?: dueDate ?: LocalDate.now()
                            } else {
                                LocalDate.now()
                            }

                            val endDate = if (selectedRepeat == "Custom") {
                                dateRangePickerState.selectedEndDateMillis?.let {
                                    Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                                } ?: dueDate
                            } else {
                                dueDate
                            }

                            onSave(
                                title,
                                content,
                                dueDate,
                                startDate,
                                endDate,
                                selectedRepeat ?: "once",
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
                readOnly = !isEditMode,
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
                value = contentValue,
                onValueChange = { newValue ->
                    if (isEditMode) {
                        contentValue = handleSmartContentValueChange(contentValue, newValue)
                    }
                },
                readOnly = !isEditMode,
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

            val displayDateText = remember(selectedDueDate, pillDateFormatter) {
                if (selectedDueDate == null) {
                    "Set due date"
                } else if (selectedDueDate == LocalDate.now()) {
                    "Due today"
                } else {
                    "Due on " + selectedDueDate!!.format(pillDateFormatter)
                }
            }
            val hasDateValue = selectedDueDate != null

            val isDueDateToday = selectedDueDate == LocalDate.now()

            LaunchedEffect(isDueDateToday) {
                if (isDueDateToday) {
                    selectedRepeat = "once"
                }
            }

            val dueGapDays = remember(selectedDueDate) {
                if (selectedDueDate != null) {
                    ChronoUnit.DAYS.between(LocalDate.now(), selectedDueDate!!)
                } else {
                    0L
                }
            }

            val isLessSevenDaysGap = dueGapDays in 1L..6L
            val isNoDueDate = selectedDueDate == null

            LaunchedEffect(isLessSevenDaysGap) {
                if (isLessSevenDaysGap && selectedRepeat != "once" && selectedRepeat != "daily" && selectedRepeat != "Custom") {
                    selectedRepeat = "once"
                }
            }

            val DatePill: @Composable () -> Unit = {
                Box {
                    FilterChip(
                        selected = hasDateValue,
                        onClick = { if (isEditMode) isDateMenuExpanded = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = "Date",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = displayDateText,
                                fontSize = 13.sp,
                                fontWeight = if (hasDateValue) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        trailingIcon = if (hasDateValue && isEditMode) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Date",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            selectedDueDate = null
                                        },
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        } else null,
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
                            selected = hasDateValue,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            selectedBorderColor = MaterialTheme.colorScheme.onSurface,
                            selectedBorderWidth = 1.5.dp
                        )
                    )

                    DropdownMenu(
                        expanded = isDateMenuExpanded,
                        onDismissRequest = { isDateMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        val todayDate = LocalDate.now()
                        val todayFormatted = todayDate.format(pillDateFormatter)
                        DropdownMenuItem(
                            text = { Text("Today ($todayFormatted)") },
                            onClick = {
                                isDateMenuExpanded = false
                                selectedDueDate = todayDate
                            }
                        )
                        val tomorrow = todayDate.plusDays(1)
                        val tomorrowFormatted = tomorrow.format(pillDateFormatter)
                        DropdownMenuItem(
                            text = { Text("Tomorrow ($tomorrowFormatted)") },
                            onClick = {
                                isDateMenuExpanded = false
                                selectedDueDate = tomorrow
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pick a date") },
                            onClick = {
                                isDateMenuExpanded = false
                                showDatePicker = true
                            }
                        )
                    }
                }
            }

            val customRangeText = remember(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis) {
                val start = dateRangePickerState.selectedStartDateMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().format(pillDateFormatter)
                }
                val end = dateRangePickerState.selectedEndDateMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().format(pillDateFormatter)
                }
                if (start != null && end != null) {
                    "$start - $end"
                } else {
                    start ?: "Custom"
                }
            }

            val repeatPillText = remember(selectedRepeat, customRangeText) {
                when {
                    selectedRepeat?.startsWith("weekly") == true -> {
                        val dayPart = if (selectedRepeat!!.contains(":")) {
                            selectedRepeat!!.substringAfter(":").lowercase().replaceFirstChar { it.uppercase() }.take(3)
                        } else {
                            LocalDate.now().dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
                        }
                        "Weekly ($dayPart)"
                    }
                    selectedRepeat == "Custom" -> customRangeText
                    selectedRepeat == null || selectedRepeat == "once" -> "Repeat"
                    else -> selectedRepeat!!
                }
            }

            val RepeatPill: @Composable () -> Unit = {
                Box {
                    val isRepeatSelected = selectedRepeat != null && selectedRepeat != "once"
                    FilterChip(
                        selected = isRepeatSelected,
                        onClick = { if (isEditMode && !isDueDateToday) isRepeatMenuExpanded = true },
                        enabled = isEditMode && !isDueDateToday,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Repeat",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = repeatPillText,
                                fontSize = 13.sp,
                                fontWeight = if (isRepeatSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        trailingIcon = if (isRepeatSelected && isEditMode && !isDueDateToday) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Repeat",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            selectedRepeat = "once"
                                        },
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        } else null,
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.Transparent,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDueDateToday) 0.3f else 0.5f),
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                            iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDueDateToday) 0.3f else 0.5f),
                            disabledContainerColor = Color.Transparent,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = !isDueDateToday,
                            selected = isRepeatSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            selectedBorderColor = MaterialTheme.colorScheme.onSurface,
                            selectedBorderWidth = 1.5.dp,
                            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    )

                    DropdownMenu(
                        expanded = isRepeatMenuExpanded,
                        onDismissRequest = { isRepeatMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        repeatOptions.forEach { option ->
                            val isOptionEnabled = when {
                                isNoDueDate -> {
                                    option != "once"
                                }
                                isLessSevenDaysGap -> {
                                    option == "once" || option == "daily" || option == "Custom"
                                }
                                else -> true
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option,
                                        color = if (isOptionEnabled) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        }
                                    )
                                },
                                enabled = isOptionEnabled,
                                onClick = {
                                    if (isOptionEnabled) {
                                        isRepeatMenuExpanded = false
                                        if (option == "Custom") {
                                            showDateRangePicker = true
                                        } else if (option == "weekly") {
                                            showWeeklyDaysPicker = true
                                        } else {
                                            selectedRepeat = option
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            val RemindMePill: @Composable () -> Unit = {
                val isTimeSelected = selectedTime != null
                FilterChip(
                    selected = isTimeSelected,
                    onClick = {
                        if (isEditMode) {
                            showRemindMePicker = true
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Remind me",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (isTimeSelected) "Reminder at ${selectedTime!!.lowercase()}" else "Remind me",
                            fontSize = 13.sp,
                            fontWeight = if (isTimeSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    trailingIcon = if (isTimeSelected && isEditMode) {
                        {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Time",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        selectedTime = null
                                    },
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else null,
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
                    onClick = { if (isEditMode) isAlarmEnabled = !isAlarmEnabled },
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

            if (isEditMode) {
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DatePill()
                    RemindMePill()
                    RepeatPill()
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
                onAddClick = { attachmentLauncher.launch(arrayOf("*/*")) },
                isReadOnly = !isEditMode
            )
        }
    }

    if (showRemindMePicker) {
        RemindMePickerDialog(
            dueDate = selectedDueDate,
            initialTime = selectedTime,
            onConfirm = { timeStr ->
                selectedTime = timeStr
                showRemindMePicker = false
            },
            onDismiss = { showRemindMePicker = false }
        )
    }

    if (showDatePicker) {
        val curDate = selectedDueDate ?: LocalDate.now()

        TaskDatePickerDialog(
            initialDate = curDate,
            allReminders = emptyList(),
            onDateSelected = { chosenDate ->
                selectedDueDate = chosenDate
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showWeeklyDaysPicker) {
        val initialDay = if (selectedRepeat?.startsWith("weekly:") == true) {
            try {
                DayOfWeek.valueOf(selectedRepeat!!.substringAfter(":").uppercase())
            } catch (e: Exception) {
                LocalDate.now().dayOfWeek
            }
        } else {
            LocalDate.now().dayOfWeek
        }

        WeeklyDaysPickerDialog(
            initialDay = initialDay,
            onDaySelected = { chosenDay ->
                selectedRepeat = "weekly:${chosenDay.name}"
                showWeeklyDaysPicker = false
            },
            onDismiss = { showWeeklyDaysPicker = false }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindMePickerDialog(
    dueDate: LocalDate?,
    initialTime: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parsedTime = initialTime?.let {
        try {
            LocalTime.parse(it, DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
        } catch (e: Exception) {
            LocalTime.now()
        }
    } ?: LocalTime.now()

    val timePickerState = rememberTimePickerState(
        initialHour = parsedTime.hour,
        initialMinute = parsedTime.minute
    )
    var isAnalogTime by remember { mutableStateOf(true) }

    fun formatTime(hour: Int, minute: Int): String {
        val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
        return LocalTime.of(hour, minute).format(formatter)
    }

    val selTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
    val isDueDateToday = dueDate == LocalDate.now()
    val isTimeValid = if (isDueDateToday) {
        !selTime.isBefore(LocalTime.now())
    } else {
        true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .wrapContentHeight()
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // TIME PICKER VIEW
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CompositionLocalProvider(
                            LocalDensity provides Density(
                                density = LocalDensity.current.density,
                                fontScale = 1.0f
                            )
                        ) {
                            if (isAnalogTime) {
                                TimePicker(
                                    state = timePickerState,
                                    colors = TimePickerDefaults.colors(
                                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                                        selectorColor = MaterialTheme.colorScheme.primary,
                                        clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            } else {
                                TimeInput(
                                    state = timePickerState,
                                    colors = TimePickerDefaults.colors(
                                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isAnalogTime = !isAnalogTime }) {
                            Icon(
                                imageVector = if (isAnalogTime) Icons.Default.Keyboard else Icons.Default.Schedule,
                                contentDescription = "Toggle Time Picker Mode"
                            )
                        }

                        Row {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    val timeStr = formatTime(timePickerState.hour, timePickerState.minute)
                                    onConfirm(timeStr)
                                },
                                enabled = isTimeValid
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyDaysPickerDialog(
    initialDay: DayOfWeek,
    onDaySelected: (DayOfWeek) -> Unit,
    onDismiss: () -> Unit
) {
    val daysOfWeek = listOf(
        DayOfWeek.SUNDAY to "Sunday",
        DayOfWeek.MONDAY to "Monday",
        DayOfWeek.TUESDAY to "Tuesday",
        DayOfWeek.WEDNESDAY to "Wednesday",
        DayOfWeek.THURSDAY to "Thursday",
        DayOfWeek.FRIDAY to "Friday",
        DayOfWeek.SATURDAY to "Saturday"
    )
    var selectedDay by remember { mutableStateOf(initialDay) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .wrapContentHeight()
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Repeat Weekly On",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    daysOfWeek.forEach { (dayOfWeek, dayLabel) ->
                        val isSelected = selectedDay == dayOfWeek
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { selectedDay = dayOfWeek },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                onDaySelected(selectedDay)
                            }
                        ) {
                            Text("OK", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
