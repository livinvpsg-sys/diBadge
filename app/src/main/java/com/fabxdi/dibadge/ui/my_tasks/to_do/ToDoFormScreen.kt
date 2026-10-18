package com.fabxdi.dibadge.ui.my_tasks.to_do

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.ui.components.AttachmentList
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.components.DiBadgeDateRangePicker
import com.fabxdi.dibadge.ui.my_tasks.TaskDatePickerDialog
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.FilePickerUtils
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
    val focusManager = LocalFocusManager.current

    val clearFocusAndHideKeyboard = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val isEditMode = reminderToEdit != null

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
    var showRemindMeActionSheet by remember { mutableStateOf(false) }

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val dividerColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val faintColor = colors.onSurfaceVariant.copy(alpha = 0.45f)
    val tealAccent = diBadgeColors.accent
    val toggleOffColor = colors.outline
    val errorColor = colors.error
    val tabInactiveColor = diBadgeColors.tabInactive
    val isDark = isSystemInDarkTheme()

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

    if (isEditMode) {
        // --- EDIT TASK SCREEN LAYOUT ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBgColor)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 1. Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSaveEnabled) tealAccent else tabInactiveColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Summary Bar
                TaskSummaryBar(
                    selectedDueDate = selectedDueDate,
                    selectedTime = selectedTime,
                    selectedRepeat = selectedRepeat,
                    isAlarmEnabled = isAlarmEnabled,
                    tabInactiveColor = tabInactiveColor,
                    cardBgColor = cardBgColor,
                    onClick = {
                        clearFocusAndHideKeyboard()
                        showBottomSheet = true
                    }
                )

                // 3. Attachment Chips Row
                if (attachedFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AttachmentChipsRow(
                        context = context,
                        attachedFiles = attachedFiles,
                        cardBgColor = cardBgColor,
                        dividerColor = dividerColor,
                        primaryTextColor = primaryTextColor,
                        tabInactiveColor = tabInactiveColor,
                        isDark = isDark
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Title & Note Tile
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 120.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = cardBgColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Title Input
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
                                autoCorrectEnabled = true,
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
                                            color = secondaryTextColor
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

                        // Note Input
                        BasicTextField(
                            value = contentValue,
                            onValueChange = { newValue ->
                                if (newValue.text.length <= 2000) {
                                    contentValue = handleSmartContentValueChange(contentValue, newValue)
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 17.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            ),
                            cursorBrush = SolidColor(primaryTextColor),
                            maxLines = Int.MAX_VALUE,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                autoCorrectEnabled = true,
                                imeAction = ImeAction.Default
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = null
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .padding(start = 4.dp)
                                .focusRequester(noteFocusRequester),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.TopStart) {
                                    if (content.isEmpty()) {
                                        Text(
                                            text = "Add note",
                                            fontSize = 17.sp,
                                            lineHeight = 25.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = secondaryTextColor
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    } else {
        // --- ADD TASK SCREEN LAYOUT ---
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
                        text = "Add task",
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
                                0
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
                                autoCorrectEnabled = true,
                                imeAction = ImeAction.Default
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = null
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

                        BasicTextField(
                            value = contentValue,
                            onValueChange = { newValue ->
                                if (newValue.text.length <= 2000) {
                                    contentValue = handleSmartContentValueChange(contentValue, newValue)
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 17.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Normal,
                                color = secondaryTextColor
                            ),
                            cursorBrush = SolidColor(primaryTextColor),
                            maxLines = Int.MAX_VALUE,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                autoCorrectEnabled = true,
                                imeAction = ImeAction.Default
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = null
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .padding(start = 4.dp)
                                .focusRequester(noteFocusRequester),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.TopStart) {
                                    if (content.isEmpty()) {
                                        Text(
                                            text = "Add note",
                                            fontSize = 17.sp,
                                            lineHeight = 25.sp,
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

                // --- CARD 2: OPTION BLOCK ---
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable {
                                    keyboardController?.hide()
                                    if (selectedTime != null) {
                                        showRemindMeActionSheet = true
                                    } else {
                                        showRemindMePicker = true
                                    }
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
                            Text(
                                text = selectedTime ?: "None",
                                fontSize = 15.sp,
                                fontWeight = if (selectedTime != null) FontWeight.Medium else FontWeight.Normal,
                                color = if (selectedTime != null) primaryTextColor else faintColor
                            )
                        }

                        HorizontalDivider(thickness = 1.dp, color = dividerColor)

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
                                            text = "Not available when due today",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = faintColor
                                        )
                                    } else {
                                        val endLine = getRepeatEndLine(selectedRepeat, selectedDueDate)
                                        if (endLine != null) {
                                            Text(
                                                text = endLine,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = secondaryTextColor
                                            )
                                        }
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

                        val isAlarmDimmed = selectedTime == null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .alpha(if (isAlarmDimmed) 0.45f else 1.0f)
                                .clickable(enabled = !isAlarmDimmed) {
                                    isAlarmEnabled = !isAlarmEnabled
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

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showRemindMeActionSheet) {
        RemindMeActionBottomSheet(
            selectedTime = selectedTime,
            onPickTimeSelect = {
                showRemindMeActionSheet = false
                showRemindMePicker = true
            },
            onRemoveTimeSelect = {
                selectedTime = null
                isAlarmEnabled = false
                showRemindMeActionSheet = false
            },
            onDismiss = { showRemindMeActionSheet = false }
        )
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
            selectedOption = if (selectedRepeat == "daily") "Every day" else selectedRepeat,
            onOptionSelected = { option ->
                when (option) {
                    "Custom" -> showDateRangePicker = true
                    "Weekly" -> showWeeklyDaysPicker = true
                    "Monthly" -> showMonthlyDayPicker = true
                    "Yearly" -> showYearlyDatePicker = true
                    "Does not repeat" -> selectedRepeat = "once"
                    "Every day" -> selectedRepeat = "daily"
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

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
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
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .background(
                                color = secondaryTextColor.copy(alpha = 0.40f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        ) {
            val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBgColor)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = navBottomPadding + 8.dp)
            ) {
                // 1. Due date row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable { isDueDateMenuExpanded = true }
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor
                        )
                    }
                    Text(
                        text = selectedDueDate?.format(displayDateFormatter) ?: "None",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = tabInactiveColor
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = dividerColor)

                // 2. Remind me row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            if (selectedTime != null) {
                                showRemindMeActionSheet = true
                            } else {
                                showRemindMePicker = true
                            }
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor
                        )
                    }
                    Text(
                        text = selectedTime ?: "None",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = tabInactiveColor
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = dividerColor)

                // 3. Repeat row
                val isRepeatDisabled = selectedDueDate == LocalDate.now()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .alpha(if (isRepeatDisabled) 0.45f else 1.0f)
                        .clickable(enabled = !isRepeatDisabled) {
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
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = secondaryTextColor
                            )
                            if (isRepeatDisabled) {
                                Text(
                                    text = "Not available when due today",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tabInactiveColor
                                )
                            } else {
                                val endLine = getRepeatEndLine(selectedRepeat, selectedDueDate)
                                if (endLine != null) {
                                    Text(
                                        text = endLine,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tabInactiveColor
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = if (isRepeatDisabled) "None" else formatRepeatValue(selectedRepeat),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = tabInactiveColor
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = dividerColor)

                // 4. Alarm row
                val isAlarmDimmed = selectedTime == null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .alpha(if (isAlarmDimmed) 0.45f else 1.0f)
                        .clickable(enabled = !isAlarmDimmed) {
                            isAlarmEnabled = !isAlarmEnabled
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
                            contentDescription = "Alarm",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Alarm",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = secondaryTextColor
                            )
                            if (isAlarmDimmed) {
                                Text(
                                    text = "Set a remind time first",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tabInactiveColor
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isAlarmEnabled && !isAlarmDimmed) "On" else "Off",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = tabInactiveColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
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

                // 5. Existing Attachments List (between Alarm and Add attachment)
                if (attachedFiles.isNotEmpty()) {
                    attachedFiles.forEachIndexed { index, uri ->
                        HorizontalDivider(thickness = 1.dp, color = dividerColor)
                        val fileName = remember(uri) { FilePickerUtils.getFileName(context, uri) }
                        val fileSize = remember(uri) { getFileSize(context, uri) }
                        val extension = remember(fileName) { fileName.substringAfterLast('.', "") }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                FileTypeTile(
                                    extension = extension,
                                    sizeDp = 28.dp,
                                    fontSizeSp = 10.sp,
                                    isDark = isDark
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = fileName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = secondaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = fileSize,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tabInactiveColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val removedUri = attachedFiles[index]
                                    val removedIndex = index
                                    attachedFiles = attachedFiles.filterIndexed { i, _ -> i != index }
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Attachment removed",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            attachedFiles = attachedFiles.toMutableList().apply {
                                                add(removedIndex.coerceAtMost(size), removedUri)
                                            }
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove attachment",
                                    tint = tabInactiveColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = dividerColor)

                // 6. Add attachment row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            attachmentLauncher.launch(arrayOf("*/*"))
                        }
                        .padding(horizontal = 16.dp),
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = dividerColor)

                // 7. Delete task row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            showBottomSheet = false
                            showDeleteConfirmation = true
                        }
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = errorColor
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Delete task?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "This task and its attachments will be removed. You can't undo this.",
                    fontSize = 15.sp,
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
                        fontWeight = FontWeight.SemiBold,
                        color = errorColor
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
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

    val colors = MaterialTheme.colorScheme
    val sheetBgColor = colors.surface
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val errorColor = colors.error

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetBgColor,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.outline)
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
        "Every day",
        "Weekly",
        "Monthly",
        "Yearly",
        "Custom"
    )

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val sheetBgColor = colors.surface
    val primaryTextColor = colors.onBackground
    val checkColor = diBadgeColors.accent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetBgColor,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.outline)
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
                    .sortedBy { if (it.value == 7) 0 else it.value } // Monday is 1, Sunday is 7; order Mon to Sun. Wait, the prompt says "week order starts on Monday". DayOfWeek value: Monday=1, Sunday=7. So default order is Monday to Sunday.
                    
                val orderedDays = days.sortedBy { it.value }
                
                if (orderedDays.size == 7) "Every day"
                else if (orderedDays.size == 5 && orderedDays.first() == DayOfWeek.MONDAY && orderedDays.last() == DayOfWeek.FRIDAY) "Weekdays"
                else if (orderedDays.size == 2 && orderedDays.contains(DayOfWeek.SATURDAY) && orderedDays.contains(DayOfWeek.SUNDAY)) "Weekends"
                else if (orderedDays.size == 1) {
                    val fullName = orderedDays.first().name.lowercase().replaceFirstChar { it.uppercase() }
                    "Every $fullName"
                }
                else if (orderedDays.size in 2..3) {
                    orderedDays.joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3) }
                }
                else if (orderedDays.size > 3) {
                    "${orderedDays.size} days a week"
                } else "Weekly"
            } else "Weekly"
        }
        selectedRepeat.startsWith("monthly") -> {
            if (selectedRepeat.contains(":")) {
                val dayNums = selectedRepeat.substringAfter(":").split(",")
                    .mapNotNull { it.trim().toIntOrNull() }.sorted()
                if (dayNums.size == 1) {
                    "Monthly on ${formatOrdinal(dayNums.first())}"
                } else if (dayNums.size in 2..3) {
                    val numStr = dayNums.joinToString(", ") { formatOrdinal(it) }
                    "$numStr monthly"
                } else if (dayNums.size > 3) {
                    "${dayNums.size} dates monthly"
                } else "Monthly"
            } else "Monthly"
        }
        selectedRepeat.startsWith("yearly") -> {
            if (selectedRepeat.contains(":")) {
                val parts = selectedRepeat.substringAfter(":").split("-")
                if (parts.size == 2) {
                    val m = try { Month.valueOf(parts[0].uppercase()).name.lowercase().replaceFirstChar { it.uppercase() }.take(3) } catch (e: Exception) { "Oct" }
                    val d = parts[1].toIntOrNull() ?: 15
                    "Every $d $m"
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

fun getRepeatEndLine(selectedRepeat: String?, dueDate: LocalDate?): String? {
    if (selectedRepeat == null || selectedRepeat == "once" || selectedRepeat == "Does not repeat") {
        return null
    }
    
    if (dueDate != null && dueDate.isEqual(LocalDate.now())) {
        return "Not available when due today"
    }
    
    if (selectedRepeat.startsWith("custom:")) {
        val rangeStr = selectedRepeat.substringAfter("custom:")
        if (rangeStr.contains(" – ")) {
            val endStr = rangeStr.split(" – ").last()
            return "Until $endStr"
        }
        return "Until $rangeStr"
    }
    
    if (dueDate != null) {
        return "Until due date"
    }
    
    return "No end date"
}

private fun getFileSize(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex != -1 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                val bytes = cursor.getLong(sizeIndex)
                when {
                    bytes < 1024 -> "$bytes B"
                    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
                }
            } else "File"
        } ?: "File"
    } catch (e: Exception) {
        "File"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskSummaryBar(
    selectedDueDate: LocalDate?,
    selectedTime: String?,
    selectedRepeat: String?,
    isAlarmEnabled: Boolean,
    tabInactiveColor: Color,
    cardBgColor: Color,
    onClick: () -> Unit
) {
    val currentYear = LocalDate.now().year
    val dateText = remember(selectedDueDate) {
        if (selectedDueDate != null) {
            val pattern = if (selectedDueDate.year == currentYear) "EEE, d MMM" else "EEE, d MMM yyyy"
            selectedDueDate.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        } else null
    }

    val isRepeatSet = selectedDueDate != LocalDate.now() &&
            selectedRepeat != null && selectedRepeat != "once" && selectedRepeat != "Does not repeat"
    val repeatText = if (isRepeatSet) formatRepeatValue(selectedRepeat) else null
    val isAlarmOn = isAlarmEnabled && selectedTime != null

    val hasAnyItem = dateText != null || selectedTime != null || repeatText != null

    val summaryAccessibilityText = buildString {
        if (!hasAnyItem) {
            append("Add date, reminder…")
        } else {
            val parts = mutableListOf<String>()
            if (dateText != null) parts.add("Due $dateText")
            if (selectedTime != null) parts.add("Remind at $selectedTime")
            if (repeatText != null) parts.add("Repeat $repeatText")
            if (isAlarmOn) parts.add("Alarm on")
            append(parts.joinToString(", "))
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Task options. $summaryAccessibilityText"
            },
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (!hasAnyItem) {
                    Text(
                        text = "Add date, reminder…",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = tabInactiveColor
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (dateText != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = tabInactiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = dateText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tabInactiveColor
                                )
                            }
                        }

                        if (selectedTime != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = tabInactiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = selectedTime,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tabInactiveColor
                                )
                            }
                        }

                        if (repeatText != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Repeat,
                                    contentDescription = null,
                                    tint = tabInactiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = repeatText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tabInactiveColor
                                )
                            }
                        }

                        if (isAlarmOn) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                    tint = tabInactiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Alarm",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tabInactiveColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = tabInactiveColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FileTypeTile(
    extension: String,
    sizeDp: Dp,
    fontSizeSp: TextUnit,
    isDark: Boolean
) {
    val ext = extension.lowercase()
    val (typeColor, label) = when {
        ext == "pdf" -> (if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)) to "PDF"
        ext in listOf("doc", "docx", "rtf", "odt") -> (if (isDark) Color(0xFF6FA3E0) else Color(0xFF2F6DB5)) to "DOC"
        ext in listOf("xls", "xlsx", "csv", "ods") -> (if (isDark) Color(0xFF6DBB8A) else Color(0xFF2E7D4F)) to "XLS"
        ext in listOf("ppt", "pptx", "odp") -> (if (isDark) Color(0xFFE0A35F) else Color(0xFFB8681A)) to "PPT"
        ext in listOf("jpg", "jpeg", "png", "heic", "webp", "gif") -> {
            val labelStr = if (ext.length <= 4) ext.uppercase() else "IMG"
            (if (isDark) Color(0xFFB496E0) else Color(0xFF7B4FB3)) to labelStr
        }
        else -> {
            val labelStr = if (ext.length in 1..4) ext.uppercase() else "FILE"
            (if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)) to labelStr
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp)
            .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = fontSizeSp,
            fontWeight = FontWeight.Bold,
            color = typeColor,
            letterSpacing = 0.02.sp
        )
    }
}

@Composable
private fun AttachmentChipsRow(
    context: Context,
    attachedFiles: List<Uri>,
    cardBgColor: Color,
    dividerColor: Color,
    primaryTextColor: Color,
    tabInactiveColor: Color,
    isDark: Boolean
) {
    if (attachedFiles.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(attachedFiles) { uri ->
            val fileName = remember(uri) { FilePickerUtils.getFileName(context, uri) }
            val fileSize = remember(uri) { getFileSize(context, uri) }
            val extension = remember(fileName) { fileName.substringAfterLast('.', "") }

            Surface(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { FilePickerUtils.openFile(context, uri) },
                shape = RoundedCornerShape(12.dp),
                color = cardBgColor,
                border = BorderStroke(1.dp, dividerColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileTypeTile(
                        extension = extension,
                        sizeDp = 24.dp,
                        fontSizeSp = 9.sp,
                        isDark = isDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.widthIn(max = 160.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = fileName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = primaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = fileSize,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = tabInactiveColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


