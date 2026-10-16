package com.fabxdi.dibadge.ui.my_tasks.to_do

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.ui.components.AttachmentList
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.components.DiBadgeDateRangePicker
import com.fabxdi.dibadge.ui.my_tasks.TaskDatePickerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val noteFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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

    var selectedRepeat by remember { mutableStateOf<String?>(reminderToEdit?.repeatType) }
    var selectedTime by remember { mutableStateOf<String?>(reminderToEdit?.time) }
    var selectedDueDate by remember { mutableStateOf<LocalDate?>(reminderToEdit?.date) }
    var isAlarmEnabled by remember { mutableStateOf(reminderToEdit?.isAlarmEnabled ?: false) }
    val isImportant = reminderToEdit?.isImportant ?: false

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var triedToSave by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showWeeklyDaysPicker by remember { mutableStateOf(false) }
    var showMonthlyDayPicker by remember { mutableStateOf(false) }
    var showYearlyDatePicker by remember { mutableStateOf(false) }
    var showRemindMePicker by remember { mutableStateOf(autoOpenTimePicker) }

    var isDueDateMenuExpanded by remember { mutableStateOf(false) }
    var isRepeatMenuExpanded by remember { mutableStateOf(false) }

    var attachedFiles by remember {
        mutableStateOf<List<Uri>>(
            reminderToEdit?.attachments?.map { Uri.parse(it) } ?: emptyList()
        )
    }

    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            val remainingSpace = 5 - attachedFiles.size
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

    fun handleSmartContentValueChange(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text

        if (newText.length == oldText.length + 1 && newText.endsWith(" ")) {
            val lines = oldText.split("\n")
            val lastLine = lines.lastOrNull() ?: ""

            val numMatch = Regex("^(\\d+)([.\\)])$").find(lastLine.trim())
            if (numMatch != null) {
                val (numStr, delimiter) = numMatch.destructured
                val base = oldText.dropLast(lastLine.length)
                val updatedText = "$base$numStr$delimiter "
                return TextFieldValue(text = updatedText, selection = TextRange(updatedText.length))
            }

            val bulletMatch = Regex("^([-*•])$").find(lastLine.trim())
            if (bulletMatch != null) {
                val bullet = bulletMatch.groupValues[1]
                val base = oldText.dropLast(lastLine.length)
                val updatedText = "$base$bullet "
                return TextFieldValue(text = updatedText, selection = TextRange(updatedText.length))
            }
        }

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
                    return TextFieldValue(text = updatedText, selection = TextRange(updatedText.length))
                } else {
                    val nextNum = (numStr.toIntOrNull() ?: 1) + 1
                    val updatedText = "$newText$nextNum$delimiter "
                    return TextFieldValue(text = updatedText, selection = TextRange(updatedText.length))
                }
            }

            val bulletMatch = Regex("^([-*•])\\s+(.*)$").find(lastLine)
            if (bulletMatch != null) {
                val (bullet, textAfter) = bulletMatch.destructured
                if (textAfter.isBlank()) {
                    val base = oldText.dropLast(lastLine.length)
                    val cleanedLastLine = lastLine.removePrefix(bullet).trimStart()
                    val updatedText = base + cleanedLastLine
                    return TextFieldValue(text = updatedText, selection = TextRange(updatedText.length))
                } else {
                    val updatedText = "$newText$bullet "
                    return TextFieldValue(text = updatedText, selection = TextRange(updatedText.length))
                }
            }
        }

        return newValue
    }

    val displayDateFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()) }

    val isTitleValid = title.trim().isNotEmpty()

    // --- Theme Color Tokens ---
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val pageBgColor = if (isDark) Color(0xFF000000) else Color(0xFFEBECF0)
    val cardBgColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val dividerColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE3E5EA)
    val primaryTextColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF111318)
    val secondaryTextColor = if (isDark) Color(0xFF8A8A8E) else Color(0xFF6B7078)
    val faintColor = if (isDark) Color(0xFF5A5A5E) else Color(0xFF9AA0A8)
    val tealAccent = if (isDark) Color(0xFF5CC9C0) else Color(0xFF17645E)
    val toggleOffColor = if (isDark) Color(0xFF48484A) else Color(0xFFC9CCD4)
    val errorColor = Color(0xFFFF8A80)

    val hasChanges = remember(
        title, content, selectedRepeat, selectedTime, isAlarmEnabled,
        selectedDueDate, attachedFiles
    ) {
        if (reminderToEdit == null) {
            title.trim().isNotEmpty() || content.trim().isNotEmpty() ||
                    selectedDueDate != null || selectedTime != null ||
                    (selectedRepeat != null && selectedRepeat != "once") || isAlarmEnabled || attachedFiles.isNotEmpty()
        } else {
            title.trim() != reminderToEdit.title.trim() ||
                    content.trim() != reminderToEdit.content.trim() ||
                    selectedDueDate != reminderToEdit.date ||
                    selectedTime != reminderToEdit.time ||
                    selectedRepeat != reminderToEdit.repeatType ||
                    isAlarmEnabled != reminderToEdit.isAlarmEnabled ||
                    attachedFiles.map { it.toString() } != reminderToEdit.attachments
        }
    }

    val isSaveEnabled = isTitleValid && hasChanges

    val handleBack = {
        if (hasChanges) {
            showUnsavedChangesDialog = true
        } else {
            onCancel()
        }
    }

    BackHandler(onBack = handleBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBgColor)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // 1. Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = handleBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryTextColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (reminderToEdit == null) "New task" else "Edit task",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            }

            TextButton(
                onClick = {
                    triedToSave = true
                    if (isSaveEnabled) {
                        onSave(
                            title.trim(),
                            content.trim(),
                            selectedDueDate,
                            selectedDueDate ?: LocalDate.now(),
                            selectedDueDate,
                            selectedRepeat ?: "once",
                            selectedTime,
                            isAlarmEnabled,
                            attachedFiles.map { it.toString() },
                            reminderToEdit?.id ?: 0
                        )
                    }
                },
                enabled = isSaveEnabled
            ) {
                Text(
                    text = "Save",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSaveEnabled) tealAccent else faintColor
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- CARD 1: TITLE & NOTE ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = cardBgColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Title Input (120 char limit, max 3 lines, cursor uses primaryTextColor)
                    BasicTextField(
                        value = title,
                        onValueChange = { if (it.length <= 120) title = it },
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        ),
                        cursorBrush = SolidColor(primaryTextColor),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { noteFocusRequester.requestFocus() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocusRequester),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (title.isEmpty()) {
                                    Text(
                                        text = "Title",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = faintColor
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = if (triedToSave && !isTitleValid) errorColor else dividerColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Note Input (2000 char limit, min 40dp height, grows up to 6 lines, cursor uses primaryTextColor)
                    BasicTextField(
                        value = contentValue,
                        onValueChange = { newValue ->
                            if (newValue.text.length <= 2000) {
                                contentValue = handleSmartContentValueChange(contentValue, newValue)
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor
                        ),
                        cursorBrush = SolidColor(primaryTextColor),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { keyboardController?.hide() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .focusRequester(noteFocusRequester),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.TopStart) {
                                if (content.isEmpty()) {
                                    Text(
                                        text = "Add note",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = faintColor
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            // --- CARD 2: OPTION BLOCK (Due date, Remind me, Repeat, Alarm) ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = cardBgColor
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. Due Date Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable {
                                keyboardController?.hide()
                                isDueDateMenuExpanded = true
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = "Due date",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Due date",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = secondaryTextColor
                            )
                        }
                        Text(
                            text = selectedDueDate?.format(displayDateFormatter) ?: "None",
                            fontSize = 15.sp,
                            fontWeight = if (selectedDueDate != null) FontWeight.Medium else FontWeight.Normal,
                            color = if (selectedDueDate != null) primaryTextColor else faintColor
                        )
                    }

                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                    // 2. Remind Me Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable {
                                keyboardController?.hide()
                                showRemindMePicker = true
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Remind me",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Remind me",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = secondaryTextColor
                            )
                        }
                        val remindText = selectedTime ?: "None"

                        Text(
                            text = remindText,
                            fontSize = 15.sp,
                            fontWeight = if (selectedTime != null) FontWeight.Medium else FontWeight.Normal,
                            color = if (selectedTime != null) primaryTextColor else faintColor
                        )
                    }

                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                    // 3. Repeat Row
                    val isRepeatDisabled = selectedDueDate == LocalDate.now()

                    LaunchedEffect(isRepeatDisabled) {
                        if (isRepeatDisabled) {
                            selectedRepeat = "once"
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .alpha(if (isRepeatDisabled) 0.45f else 1.0f)
                            .clickable(enabled = !isRepeatDisabled) {
                                keyboardController?.hide()
                                isRepeatMenuExpanded = true
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Repeat,
                                contentDescription = "Repeat",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Repeat",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = secondaryTextColor
                                )
                                if (isRepeatDisabled) {
                                    Text(
                                        text = "Not available for tasks due today",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = faintColor
                                    )
                                } else if (selectedRepeat != null && selectedRepeat != "once" && selectedRepeat != "Does not repeat") {
                                    val untilText = if (selectedDueDate != null) {
                                        "repeats until ${selectedDueDate!!.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
                                    } else {
                                        "repeats forever"
                                    }

                                    Text(
                                        text = untilText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = secondaryTextColor
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isRepeatDisabled) "None" else formatRepeatValue(selectedRepeat),
                            fontSize = 15.sp,
                            fontWeight = if (!isRepeatDisabled && selectedRepeat != null && selectedRepeat != "once" && selectedRepeat != "Does not repeat") FontWeight.Medium else FontWeight.Normal,
                            color = if (!isRepeatDisabled && selectedRepeat != null && selectedRepeat != "once" && selectedRepeat != "Does not repeat") primaryTextColor else faintColor
                        )
                    }

                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                    // 4. Alarm Row
                    val isAlarmDimmed = selectedTime == null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .alpha(if (isAlarmDimmed) 0.45f else 1.0f)
                            .clickable(enabled = !isAlarmDimmed) { isAlarmEnabled = !isAlarmEnabled }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Alarm",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Alarm",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = secondaryTextColor
                                )
                                if (isAlarmDimmed) {
                                    Text(
                                        text = "Set a remind time first",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = faintColor
                                    )
                                }
                            }
                        }

                        Switch(
                            checked = isAlarmEnabled && !isAlarmDimmed,
                            onCheckedChange = { if (!isAlarmDimmed) isAlarmEnabled = it },
                            enabled = !isAlarmDimmed,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = tealAccent,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = toggleOffColor
                            )
                        )
                    }
                }
            }

            // --- CARD 3: ADD ATTACHMENT ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = cardBgColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp)
                            .clickable { attachmentLauncher.launch(arrayOf("*/*")) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Add attachment",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Add attachment",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor
                        )
                    }

                    if (attachedFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AttachmentList(
                            context = context,
                            attachedFiles = attachedFiles,
                            onRemove = { index ->
                                attachedFiles = attachedFiles.filterIndexed { i, _ -> i != index }
                            },
                            onAddClick = { attachmentLauncher.launch(arrayOf("*/*")) },
                            isReadOnly = false
                        )
                    }
                }
            }

            // --- CARD 4: DELETE TASK (Only in Edit task mode) ---
            if (reminderToEdit != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { showDeleteConfirmation = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete task, button",
                            tint = errorColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Delete task",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = errorColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
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

        DiBadgeDatePicker(
            selectedDate = curDate,
            minDate = LocalDate.now(),
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
                .mapNotNull { it.trim().toIntOrNull() }.toSet().ifEmpty { setOf(LocalDate.now().dayOfMonth) }
        } else {
            setOf(LocalDate.now().dayOfMonth)
        }

        MonthlyDayPickerDialog(
            initialDayNumbers = initialDayNums,
            onDaysSelected = { chosenNums ->
                val dayStr = chosenNums.joinToString(",")
                selectedRepeat = "monthly:$dayStr"
                showMonthlyDayPicker = false
            },
            onDismiss = { showMonthlyDayPicker = false }
        )
    }

    if (showDateRangePicker) {
        DiBadgeDateRangePicker(
            initialStartDate = null,
            initialEndDate = null,
            minDate = LocalDate.now(),
            maxDate = selectedDueDate,
            onRangeSelected = { start, end ->
                showDateRangePicker = false
                val startStr = start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
                val endStr = end.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
                selectedRepeat = "custom:$startStr – $endStr"
                if (selectedDueDate == null) {
                    selectedDueDate = end
                }
            },
            onDismiss = { showDateRangePicker = false }
        )
    }

    if (showYearlyDatePicker) {
        val now = LocalDate.now()
        val initialMonth = if (selectedRepeat?.startsWith("yearly:") == true) {
            val parts = selectedRepeat!!.substringAfter(":").split("-")
            if (parts.size == 2) {
                try { Month.valueOf(parts[0].uppercase()) } catch (e: Exception) { now.month }
            } else now.month
        } else now.month

        val initialDay = if (selectedRepeat?.startsWith("yearly:") == true) {
            val parts = selectedRepeat!!.substringAfter(":").split("-")
            if (parts.size == 2) {
                parts[1].toIntOrNull() ?: now.dayOfMonth
            } else now.dayOfMonth
        } else now.dayOfMonth

        YearlyDatePickerDialog(
            initialMonth = initialMonth,
            initialDayNumber = initialDay,
            onDateSelected = { chosenMonth, chosenDay ->
                selectedRepeat = "yearly:${chosenMonth.name.lowercase()}-$chosenDay"
                showYearlyDatePicker = false
            },
            onDismiss = { showYearlyDatePicker = false }
        )
    }

    if (isDueDateMenuExpanded) {
        DueDateBottomSheet(
            selectedDueDate = selectedDueDate,
            onTodaySelect = { selectedDueDate = LocalDate.now() },
            onTomorrowSelect = { selectedDueDate = LocalDate.now().plusDays(1) },
            onPickDateSelect = { showDatePicker = true },
            onRemoveDueDateSelect = { selectedDueDate = null },
            onDismiss = { isDueDateMenuExpanded = false }
        )
    }

    if (isRepeatMenuExpanded) {
        RepeatOptionsBottomSheet(
            selectedOption = selectedRepeat,
            onOptionSelected = { option ->
                when (option) {
                    "Custom" -> showDateRangePicker = true
                    "Weekly" -> showWeeklyDaysPicker = true
                    "Monthly" -> showMonthlyDayPicker = true
                    "Yearly" -> showYearlyDatePicker = true
                    "Does not repeat" -> selectedRepeat = "once"
                    else -> selectedRepeat = option.lowercase()
                }
            },
            onDismiss = { isRepeatMenuExpanded = false }
        )
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = if (reminderToEdit == null) "New task not saved" else "Edit task not saved",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "If you go back now, this task will be lost.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onCancel()
                    }
                ) {
                    Text(
                        text = "Discard",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = errorColor
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text(
                        text = "Keep editing",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryTextColor
                    )
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp),
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
                    text = "It will be removed from all dates.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        if (reminderToEdit != null) {
                            onDelete(reminderToEdit)
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = errorColor
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryTextColor
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDateBottomSheet(
    selectedDueDate: LocalDate?,
    onTodaySelect: () -> Unit,
    onTomorrowSelect: () -> Unit,
    onPickDateSelect: () -> Unit,
    onRemoveDueDateSelect: () -> Unit,
    onDismiss: () -> Unit
) {
    val todayDate = remember { LocalDate.now() }
    val tomorrowDate = remember { todayDate.plusDays(1) }
    val dateDisplayFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()) }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val sheetBgColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val primaryTextColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF111318)
    val secondaryTextColor = if (isDark) Color(0xFF8A8A8E) else Color(0xFF6B7078)
    val errorColor = Color(0xFFFF8A80)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetBgColor,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = if (isDark) Color(0xFF5A5A5E) else Color(0xFFC9CCD4))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Row 1: Today
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        onTodaySelect()
                        onDismiss()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Today",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
                Text(
                    text = todayDate.format(dateDisplayFormatter),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            }

            // Row 2: Tomorrow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        onTomorrowSelect()
                        onDismiss()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tomorrow",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
                Text(
                    text = tomorrowDate.format(dateDisplayFormatter),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            }

            // Row 3: Pick a date
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        onDismiss()
                        onPickDateSelect()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pick a date",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            }

            // Row 4: Remove due date (if set)
            if (selectedDueDate != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            onRemoveDueDateSelect()
                            onDismiss()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Remove due date",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = errorColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatOptionsBottomSheet(
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "Does not repeat",
        "Daily",
        "Weekly",
        "Monthly",
        "Yearly",
        "Custom"
    )

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val sheetBgColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val primaryTextColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF111318)
    val checkColor = if (isDark) Color(0xFF5CC9C0) else Color(0xFF17645E)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetBgColor,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = if (isDark) Color(0xFF5A5A5E) else Color(0xFFC9CCD4))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            options.forEach { option ->
                val isSelected = (selectedOption == null && option == "Does not repeat") ||
                        (selectedOption?.equals(option, ignoreCase = true) == true) ||
                        (selectedOption == "once" && option == "Does not repeat")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            onOptionSelected(option)
                            onDismiss()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = option,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = primaryTextColor
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = checkColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun formatOrdinal(n: Int): String {
    val suffix = when {
        n in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

fun formatRepeatValue(selectedRepeat: String?): String {
    if (selectedRepeat == null || selectedRepeat == "once" || selectedRepeat == "Does not repeat") return "None"
    return when {
        selectedRepeat == "daily" -> "Every day"
        selectedRepeat.startsWith("weekly") -> {
            if (selectedRepeat.contains(":")) {
                val days = selectedRepeat.substringAfter(":").split(",")
                    .mapNotNull {
                        try { DayOfWeek.valueOf(it.trim().uppercase()) } catch (e: Exception) { null }
                    }
                if (days.size == 7) "Every day"
                else if (days.size > 3) {
                    val firstTwo = days.take(2).joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3) }
                    "$firstTwo +${days.size - 2} of week"
                } else if (days.isNotEmpty()) {
                    val dayStr = days.joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3) }
                    "$dayStr of week"
                } else "Weekly"
            } else "Weekly"
        }
        selectedRepeat.startsWith("monthly") -> {
            if (selectedRepeat.contains(":")) {
                val dayNums = selectedRepeat.substringAfter(":").split(",")
                    .mapNotNull { it.trim().toIntOrNull() }.sorted()
                if (dayNums.size > 3) {
                    val firstTwo = dayNums.take(2).joinToString(", ") { formatOrdinal(it) }
                    "$firstTwo +${dayNums.size - 2} of month"
                } else if (dayNums.isNotEmpty()) {
                    val numStr = dayNums.joinToString(", ") { formatOrdinal(it) }
                    "$numStr of month"
                } else "Monthly"
            } else "Monthly"
        }
        selectedRepeat.startsWith("yearly") -> {
            if (selectedRepeat.contains(":")) {
                val parts = selectedRepeat.substringAfter(":").split("-")
                if (parts.size == 2) {
                    val m = try { Month.valueOf(parts[0].uppercase()).name.lowercase().replaceFirstChar { it.uppercase() }.take(3) } catch (e: Exception) { "Oct" }
                    val d = parts[1].toIntOrNull() ?: 15
                    "${formatOrdinal(d)} $m, every year"
                } else "Yearly"
            } else "Yearly"
        }
        selectedRepeat.startsWith("custom:") -> {
            selectedRepeat.substringAfter("custom:")
        }
        selectedRepeat == "Custom" -> "Custom"
        else -> selectedRepeat.lowercase().replaceFirstChar { it.uppercase() }
    }
}
