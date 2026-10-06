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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
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
import java.time.Month
import java.time.Year
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

    var isEditMode by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (reminderToEdit == null) {
            delay(100)
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
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
    val repeatOptions = listOf("daily", "weekly", "monthly", "Yearly", "Custom")

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
        selectedDueDate,
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
            selectedDueDate != null ||
            dateRangePickerState.selectedStartDateMillis != null ||
            (selectedRepeat != null && selectedRepeat != "once")
        } else {
            title != reminderToEdit.title ||
            content != reminderToEdit.content ||
            selectedRepeat != reminderToEdit.repeatType ||
            selectedTime != reminderToEdit.time ||
            isAlarmEnabled != reminderToEdit.isAlarmEnabled ||
            selectedDueDate != reminderToEdit.date ||
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
    var showMonthlyDayPicker by remember { mutableStateOf(false) }
    var showYearlyDatePicker by remember { mutableStateOf(false) }
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
                if (reminderToEdit == null) {
                    Text(
                        text = "New task",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (reminderToEdit != null) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Task",
                                tint = errorColor
                            )
                        }
                    }

                    val showSaveButton = reminderToEdit == null || hasChanges

                    if (showSaveButton) {
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

            // Title & Details Input Container Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        thickness = 0.5.dp,
                        color = if (triedToSave && !isTextValid) errorColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    // 2. Details Input Field
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
                                text = "Details",
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
                }
            }

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

            val dueGapMonths = remember(selectedDueDate) {
                if (selectedDueDate != null) {
                    ChronoUnit.MONTHS.between(YearMonth.from(LocalDate.now()), YearMonth.from(selectedDueDate!!))
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
                        if (selectedRepeat!!.contains(":")) {
                            val dayNames = selectedRepeat!!.substringAfter(":").split(",")
                                .mapNotNull {
                                    try {
                                        DayOfWeek.valueOf(it.trim().uppercase()).name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)
                                    } catch (e: Exception) { null }
                                }
                            if (dayNames.isNotEmpty()) "Weekly (${dayNames.joinToString(", ")})" else "Weekly"
                        } else {
                            val defaultDay = LocalDate.now().dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
                            "Weekly ($defaultDay)"
                        }
                    }
                    selectedRepeat?.startsWith("monthly") == true -> {
                        if (selectedRepeat!!.contains(":")) {
                            val dayNums = selectedRepeat!!.substringAfter(":").split(",")
                                .mapNotNull { it.trim().toIntOrNull() }
                                .sorted()
                            val formatted = dayNums.map { num ->
                                val suffix = when (num) {
                                    1, 21, 31 -> "st"
                                    2, 22 -> "nd"
                                    3, 23 -> "rd"
                                    else -> "th"
                                }
                                "$num$suffix"
                            }
                            if (formatted.isNotEmpty()) "Monthly (${formatted.joinToString(", ")})" else "Monthly"
                        } else {
                            val defaultNum = LocalDate.now().dayOfMonth
                            "Monthly (${defaultNum}th)"
                        }
                    }
                    selectedRepeat?.lowercase()?.startsWith("yearly") == true -> {
                        val monthDayPart = if (selectedRepeat!!.contains(":")) {
                            val parts = selectedRepeat!!.substringAfter(":").split("-")
                            if (parts.size == 2) {
                                val m = try { Month.valueOf(parts[0].uppercase()).name.lowercase().replaceFirstChar { it.uppercase() }.take(3) } catch (e: Exception) { "Oct" }
                                "${parts[1]} $m"
                            } else {
                                "15 Oct"
                            }
                        } else {
                            val now = LocalDate.now()
                            "${now.dayOfMonth} ${now.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
                        }
                        "Yearly ($monthDayPart)"
                    }
                    selectedRepeat == "Custom" -> customRangeText
                    selectedRepeat == null || selectedRepeat == "once" -> "Repeat"
                    else -> selectedRepeat!!
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Task Properties (Due Date, Remind Me, Repeat, Alarm) rendered line-by-line
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Due Date Row
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (isEditMode) isDateMenuExpanded = true }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "Due Date",
                            modifier = Modifier.size(20.dp),
                            tint = if (hasDateValue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = displayDateText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = if (hasDateValue) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (hasDateValue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        )
                        if (hasDateValue && isEditMode) {
                            IconButton(
                                onClick = { selectedDueDate = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Date",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

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

                // 2. Remind Me Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (isEditMode) showRemindMePicker = true }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Remind Me",
                        modifier = Modifier.size(20.dp),
                        tint = if (selectedTime != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (selectedTime != null) "Reminder at ${selectedTime!!.lowercase()}" else "Remind me",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = if (selectedTime != null) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (selectedTime != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedTime != null && isEditMode) {
                        IconButton(
                            onClick = { selectedTime = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Time",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // 3. Repeat Row
                Box {
                    val isRepeatSelected = selectedRepeat != null && selectedRepeat != "once"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (isEditMode && !isDueDateToday) isRepeatMenuExpanded = true }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            modifier = Modifier.size(20.dp),
                            tint = if (isRepeatSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDueDateToday) 0.3f else 0.5f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = repeatPillText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = if (isRepeatSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isRepeatSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDueDateToday) 0.3f else 0.6f),
                            modifier = Modifier.weight(1f)
                        )
                        if (isRepeatSelected && isEditMode && !isDueDateToday) {
                            IconButton(
                                onClick = { selectedRepeat = "once" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Repeat",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

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
                                dueGapMonths < 1L -> {
                                    option != "monthly" && option != "Yearly"
                                }
                                dueGapMonths < 12L -> {
                                    option != "Yearly"
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
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
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
                                        } else if (option == "monthly") {
                                            showMonthlyDayPicker = true
                                        } else if (option.equals("Yearly", ignoreCase = true)) {
                                            showYearlyDatePicker = true
                                        } else {
                                            selectedRepeat = option
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // 4. Alarm Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (isEditMode) isAlarmEnabled = !isAlarmEnabled }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAlarmEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                        contentDescription = "Alarm",
                        modifier = Modifier.size(20.dp),
                        tint = if (isAlarmEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isAlarmEnabled) "Alarm ON" else "Alarm",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = if (isAlarmEnabled) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isAlarmEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
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
        val initialDays = if (selectedRepeat?.startsWith("weekly:") == true) {
            selectedRepeat!!.substringAfter(":").split(",")
                .mapNotNull {
                    try { DayOfWeek.valueOf(it.trim().uppercase()) } catch (e: Exception) { null }
                }.toSet().ifEmpty { setOf(LocalDate.now().dayOfWeek) }
        } else {
            setOf(LocalDate.now().dayOfWeek)
        }

        WeeklyDaysPickerDialog(
            initialDays = initialDays,
            onDaysSelected = { chosenDays ->
                val dayNames = chosenDays.joinToString(",") { it.name }
                selectedRepeat = "weekly:$dayNames"
                showWeeklyDaysPicker = false
            },
            onDismiss = { showWeeklyDaysPicker = false }
        )
    }

    if (showMonthlyDayPicker) {
        val initialDayNums = if (selectedRepeat?.startsWith("monthly:") == true) {
            selectedRepeat!!.substringAfter(":").split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet().ifEmpty { setOf(LocalDate.now().dayOfMonth) }
        } else {
            setOf(LocalDate.now().dayOfMonth)
        }

        MonthlyDayPickerDialog(
            initialDayNumbers = initialDayNums,
            onDaysSelected = { chosenDays ->
                val dayNumsStr = chosenDays.sorted().joinToString(",")
                selectedRepeat = "monthly:$dayNumsStr"
                showMonthlyDayPicker = false
            },
            onDismiss = { showMonthlyDayPicker = false }
        )
    }

    if (showYearlyDatePicker) {
        val (initialMonth, initialDayNum) = if (selectedRepeat?.lowercase()?.startsWith("yearly:") == true) {
            val parts = selectedRepeat!!.substringAfter(":").split("-")
            if (parts.size == 2) {
                val m = try { Month.valueOf(parts[0].uppercase()) } catch (e: Exception) { LocalDate.now().month }
                val d = parts[1].toIntOrNull() ?: LocalDate.now().dayOfMonth
                m to d
            } else {
                LocalDate.now().month to LocalDate.now().dayOfMonth
            }
        } else {
            LocalDate.now().month to LocalDate.now().dayOfMonth
        }

        YearlyDatePickerDialog(
            initialMonth = initialMonth,
            initialDayNumber = initialDayNum,
            onDateSelected = { chosenMonth, chosenDayNum ->
                selectedRepeat = "yearly:${chosenMonth.name}-$chosenDayNum"
                showYearlyDatePicker = false
            },
            onDismiss = { showYearlyDatePicker = false }
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
