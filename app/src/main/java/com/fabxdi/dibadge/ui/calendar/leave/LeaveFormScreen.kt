package com.fabxdi.dibadge.ui.calendar.leave

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.fabxdi.dibadge.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.FilePickerUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveFormScreen(
    existingLeaves: List<LeaveEntity>,
    leaveToEdit: LeaveEntity? = null,
    onApply: (LocalDate, LocalDate?, String, String, List<String>, Int, String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var selectedLeaveType by remember { mutableStateOf<String?>(leaveToEdit?.leaveType) }
    var isLeaveTypeMenuExpanded by remember { mutableStateOf(false) }
    val leaveTypes = listOf(
        "Annual leave",
        "medical leave",
        "maternity leave",
        "paternity leave",
        "leave without pay",
        "Bereavement leave"
    )

    var selectedDateOption by remember { 
        mutableStateOf<String?>(
            if (leaveToEdit != null) {
                if (leaveToEdit.endDate == null) "A day" else "More days"
            } else null
        ) 
    }
    var isDateOptionMenuExpanded by remember { mutableStateOf(false) }
    val dateOptions = listOf("A day", "More days")

    var reason by remember { mutableStateOf(leaveToEdit?.reason ?: "") }
    var actionReason by remember { mutableStateOf("") }
    var attachedFiles by remember { 
        mutableStateOf<List<Uri>>(
            leaveToEdit?.attachments?.map { Uri.parse(it) } ?: emptyList()
        ) 
    }
    var actionAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var isEditMenuExpanded by remember { mutableStateOf(false) }
    var currentAction by remember { mutableStateOf(if (leaveToEdit != null) "Edit" else "Apply") }

    val isEditing = leaveToEdit != null
    val isPastLeave = leaveToEdit?.let { (it.endDate ?: it.startDate).isBefore(LocalDate.now()) } ?: false
    val isOngoing = leaveToEdit?.let {
        val today = LocalDate.now()
        val end = it.endDate ?: it.startDate
        !today.isBefore(it.startDate) && !today.isAfter(end)
    } ?: false
    
    val canEditDatesAndAttachments = !isEditing || (currentAction == "Cancel" || currentAction == "Extend")
    val isTypeAndReasonReadOnly = isEditing

    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (currentAction == "Extend" || currentAction == "Cancel") {
                val remainingSpace = 3 - actionAttachments.size
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
                    actionAttachments = actionAttachments + newUris
                }
            } else {
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
        }
    )

    var showDatePicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    var triedToApply by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showNoDateChangeDialog by remember { mutableStateOf(false) }
    var actionToConfirm by remember { mutableStateOf("") }
    
    fun isDateOccupied(date: LocalDate): Boolean {
        return existingLeaves.any { leave ->
            if (leaveToEdit != null && leave.id == leaveToEdit.id) return@any false
            val leaveEnd = leave.endDate ?: leave.startDate
            !date.isBefore(leave.startDate) && !date.isAfter(leaveEnd)
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = leaveToEdit?.startDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                val today = LocalDate.now()
                if (currentAction == "Cancel" && leaveToEdit != null) {
                    val leaveEnd = leaveToEdit.endDate ?: leaveToEdit.startDate
                    return !date.isBefore(leaveToEdit.startDate) && !date.isAfter(leaveEnd) && !date.isBefore(today)
                }
                if (currentAction == "Extend" && leaveToEdit != null) {
                    val leaveEnd = leaveToEdit.endDate ?: leaveToEdit.startDate
                    return date.isAfter(leaveEnd) && !date.isBefore(today) && !isDateOccupied(date)
                }
                return !date.isBefore(today) && !isDateOccupied(date)
            }
            override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
        }
    )

    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = leaveToEdit?.startDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = leaveToEdit?.endDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                if (currentAction == "Cancel" && leaveToEdit != null) {
                    val leaveEnd = leaveToEdit.endDate ?: leaveToEdit.startDate
                    return !date.isBefore(leaveToEdit.startDate) && !date.isAfter(leaveEnd) && !date.isBefore(LocalDate.now())
                }
                return !date.isBefore(LocalDate.now()) && !isDateOccupied(date)
            }
            override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
        }
    )

    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    val hasChanges = remember(
        selectedLeaveType, 
        datePickerState.selectedDateMillis, 
        dateRangePickerState.selectedStartDateMillis, 
        dateRangePickerState.selectedEndDateMillis,
        reason,
        actionReason,
        attachedFiles,
        actionAttachments,
        currentAction
    ) {
        if (!isEditing) {
            selectedLeaveType != null || 
            datePickerState.selectedDateMillis != null ||
            dateRangePickerState.selectedStartDateMillis != null ||
            reason.isNotEmpty() ||
            attachedFiles.isNotEmpty()
        } else {
            val originalStartDateMillis = leaveToEdit?.startDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
            val originalEndDateMillis = leaveToEdit?.endDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()

            when (currentAction) {
                "Extend" -> {
                    val currentEndDateMillis = if (selectedDateOption == "A day") datePickerState.selectedDateMillis else dateRangePickerState.selectedEndDateMillis
                    currentEndDateMillis != originalEndDateMillis || 
                    actionReason.isNotEmpty() || 
                    actionAttachments.isNotEmpty()
                }
                "Cancel" -> {
                    val currentStartDateMillis = if (selectedDateOption == "A day") datePickerState.selectedDateMillis else dateRangePickerState.selectedStartDateMillis
                    val currentEndDateMillis = if (selectedDateOption == "A day") null else dateRangePickerState.selectedEndDateMillis
                    currentStartDateMillis != originalStartDateMillis ||
                    currentEndDateMillis != originalEndDateMillis ||
                    actionReason.isNotEmpty() ||
                    actionAttachments.isNotEmpty()
                }
                else -> false
            }
        }
    }

    BackHandler {
        if (hasChanges && (currentAction == "Extend" || currentAction == "Cancel")) {
            showUnsavedChangesDialog = true
        } else {
            onClose()
        }
    }

    fun formatMillisToDate(millis: Long?): String {
        if (millis == null) return ""
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
            .format(formatter)
    }

    val isFormValid = if (currentAction == "Cancel" || currentAction == "Extend") {
        datePickerState.selectedDateMillis != null
    } else {
        selectedLeaveType != null && 
        (if (selectedDateOption == "More days") dateRangePickerState.selectedEndDateMillis != null else datePickerState.selectedDateMillis != null) &&
        reason.trim().isNotEmpty()
    }

    var hasSelectedActionDate by remember { mutableStateOf(false) }

    val actionMessage = remember(currentAction, datePickerState.selectedDateMillis, leaveToEdit, hasSelectedActionDate) {
        if (leaveToEdit != null && datePickerState.selectedDateMillis != null && hasSelectedActionDate) {
            val selectedDate = Instant.ofEpochMilli(datePickerState.selectedDateMillis!!).atZone(ZoneId.of("UTC")).toLocalDate()
            val originalEnd = leaveToEdit.endDate ?: leaveToEdit.startDate
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

            if (currentAction == "Cancel") {
                if (!selectedDate.isBefore(leaveToEdit.startDate) && !selectedDate.isAfter(originalEnd)) {
                    if (selectedDate == leaveToEdit.startDate) {
                        "Whole leave will be cancelled"
                    } else if (selectedDate == originalEnd) {
                        "Leave on ${selectedDate.format(formatter)} will be cancelled"
                    } else {
                        "Leaves from ${selectedDate.format(formatter)} to ${originalEnd.format(formatter)} will be cancelled"
                    }
                } else null
            } else if (currentAction == "Extend") {
                if (selectedDate.isAfter(originalEnd)) {
                    val extendStart = originalEnd.plusDays(1)
                    if (extendStart.isEqual(selectedDate)) {
                        "Leave will be extended to ${selectedDate.format(formatter)} (1 day)"
                    } else {
                        "Leave will be extended from ${extendStart.format(formatter)} to ${selectedDate.format(formatter)}"
                    }
                } else null
            } else null
        } else null
    }

    val errorColor = Color(0xFFBF2C34)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Leave",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (hasChanges && (currentAction == "Extend" || currentAction == "Cancel")) {
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
                if (leaveToEdit != null && isPastLeave) {
                    // No Edit option for past leaves
                } else {
                    Box {
                        TextButton(onClick = {
                            if (leaveToEdit != null && currentAction == "Edit") {
                                isEditMenuExpanded = true
                            } else {
                                triedToApply = true
                                if (isFormValid) {
                                    if ((currentAction == "Cancel" || currentAction == "Extend") && !hasSelectedActionDate) {
                                        showNoDateChangeDialog = true
                                    } else {
                                        actionToConfirm = currentAction
                                        showConfirmationDialog = true
                                    }
                                }
                            }
                        }) {
                            Text(
                                text = currentAction,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = isEditMenuExpanded,
                            onDismissRequest = { isEditMenuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Extend") },
                                onClick = {
                                    currentAction = "Extend"
                                    isEditMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cancel") },
                                onClick = {
                                    currentAction = "Cancel"
                                    isEditMenuExpanded = false
                                }
                            )
                            if (!(leaveToEdit?.status == "Approved" && isOngoing)) {
                                DropdownMenuItem(
                                    text = { Text("Cancel Whole Leave") },
                                    onClick = {
                                        actionToConfirm = "CancelAll"
                                        showConfirmationDialog = true
                                        isEditMenuExpanded = false
                                    }
                                )
                            }
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
            Spacer(modifier = Modifier.height(64.dp))

            // Leave Type and Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Leave Type (Left)
                Column(modifier = Modifier.weight(1.1f)) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = !isTypeAndReasonReadOnly) { isLeaveTypeMenuExpanded = true }
                        ) {
                            val iconColor = if (triedToApply && selectedLeaveType == null) errorColor else MaterialTheme.colorScheme.onBackground
                            Icon(
                                painter = painterResource(id = R.drawable.ic_leave),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = iconColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedLeaveType ?: "Type of leaves",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedLeaveType == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (!isTypeAndReasonReadOnly) {
                            DropdownMenu(
                                expanded = isLeaveTypeMenuExpanded,
                                onDismissRequest = { isLeaveTypeMenuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                leaveTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            selectedLeaveType = type
                                            isLeaveTypeMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Date Option (Right)
                val dimColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                val dateFieldText = remember(selectedDateOption, datePickerState.selectedDateMillis, dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis, dimColor) {
                    when (selectedDateOption) {
                        "A day" -> {
                            val d = formatMillisToDate(datePickerState.selectedDateMillis)
                            if (d.isEmpty()) buildAnnotatedString { append("Date") } else buildAnnotatedString { append(d) }
                        }
                        "More days" -> {
                            val start = formatMillisToDate(dateRangePickerState.selectedStartDateMillis)
                            val end = formatMillisToDate(dateRangePickerState.selectedEndDateMillis)
                            if (start.isNotEmpty() && end.isNotEmpty()) {
                                buildAnnotatedString {
                                    append(start)
                                    withStyle(SpanStyle(color = dimColor)) {
                                        append(" to ")
                                    }
                                    append(end)
                                }
                            } else if (start.isNotEmpty()) {
                                buildAnnotatedString { append(start) }
                            } else {
                                buildAnnotatedString { append("Date") }
                            }
                        }
                        else -> buildAnnotatedString { append("Date") }
                    }
                }

                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = canEditDatesAndAttachments) { 
                            if (currentAction == "Cancel" || currentAction == "Extend") {
                                showDatePicker = true
                            } else {
                                isDateOptionMenuExpanded = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (triedToApply && (if (selectedDateOption == "More days") dateRangePickerState.selectedEndDateMillis == null else datePickerState.selectedDateMillis == null)) errorColor else MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dateFieldText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (dateFieldText.text == "Date") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    DropdownMenu(
                        expanded = isDateOptionMenuExpanded,
                        onDismissRequest = { isDateOptionMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        dateOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedDateOption = option
                                    isDateOptionMenuExpanded = false
                                    if (option == "A day") showDatePicker = true else showDateRangePicker = true
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Reason Input Field
            TextField(
                value = reason,
                onValueChange = { reason = it },
                readOnly = isEditing,
                placeholder = { 
                    Text(
                        text = "Reason",
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

            if (currentAction == "Extend" || currentAction == "Cancel") {
                TextField(
                    value = actionReason,
                    onValueChange = { actionReason = it },
                    placeholder = { 
                        Text(
                            text = if (currentAction == "Extend") "Extension Reason (Optional)" else "Cancellation Reason (Optional)",
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

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 1.dp,
                color = if (triedToApply && reason.trim().isEmpty()) errorColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.Companion.height(DiBadgeTheme.spacing.large))

            // Attachments Section
            if (currentAction == "Extend" || currentAction == "Cancel") {
                if (attachedFiles.isNotEmpty()) {
                    Text(
                        text = "Previous Attachments",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    AttachmentList(
                        context = context,
                        attachedFiles = attachedFiles,
                        canRemove = false,
                        onRemove = {},
                        onAddClick = {}
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = if (currentAction == "Extend") "Extension Proof (Optional)" else "Cancellation Proof (Optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AttachmentList(
                    context = context,
                    attachedFiles = actionAttachments,
                    onRemove = { index ->
                        actionAttachments = actionAttachments.filterIndexed { i, _ -> i != index }
                    },
                    onAddClick = { attachmentLauncher.launch(arrayOf("*/*")) }
                )
            } else {
                AttachmentList(
                    context = context,
                    attachedFiles = attachedFiles,
                    onRemove = { index ->
                        attachedFiles = attachedFiles.filterIndexed { i, _ -> i != index }
                    },
                    onAddClick = { attachmentLauncher.launch(arrayOf("*/*")) }
                )
            }

            if (actionMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = actionMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }

    // Single Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { 
                    showDatePicker = false
                    hasSelectedActionDate = true
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Date Range Picker Dialog
    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text(
                        text = "Select Range",
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                headline = {
                    val startDateText = dateRangePickerState.selectedStartDateMillis?.let {
                        formatMillisToDate(it)
                    }
                    val endDateText = dateRangePickerState.selectedEndDateMillis?.let {
                        formatMillisToDate(it)
                    }

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
                        Text(
                            text = " - ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
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

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text(text = "Confirm Action") },
            text = {
                val message = when (actionToConfirm) {
                    "Cancel" -> "Are you sure you want to cancel the selected leave?"
                    "Extend" -> "Are you sure you want to extend this leave?"
                    "CancelAll" -> "Are you sure you want to cancel the whole leave?"
                    "Apply" -> "Are you sure you want to apply for this leave?"
                    else -> "Are you sure you want to proceed?"
                }
                Text(text = message)
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmationDialog = false
                    if (actionToConfirm == "CancelAll") {
                        onApply(
                            LocalDate.MIN,
                            null,
                            selectedLeaveType ?: "",
                            reason,
                            attachedFiles.map { it.toString() },
                            leaveToEdit?.id ?: 0,
                            "CancelAll"
                        )
                    } else if (actionToConfirm == "Apply") {
                        val startDate = if (selectedDateOption == "More days") {
                            Instant.ofEpochMilli(dateRangePickerState.selectedStartDateMillis!!).atZone(ZoneId.of("UTC")).toLocalDate()
                        } else {
                            Instant.ofEpochMilli(datePickerState.selectedDateMillis!!).atZone(ZoneId.of("UTC")).toLocalDate()
                        }
                        val endDate = if (selectedDateOption == "More days") {
                            Instant.ofEpochMilli(dateRangePickerState.selectedEndDateMillis!!).atZone(ZoneId.of("UTC")).toLocalDate()
                        } else null

                        onApply(
                            startDate,
                            endDate,
                            selectedLeaveType!!,
                            reason,
                            attachedFiles.map { it.toString() },
                            leaveToEdit?.id ?: 0,
                            actionToConfirm
                        )
                    } else {
                        val selectedDate = Instant.ofEpochMilli(datePickerState.selectedDateMillis!!).atZone(ZoneId.of("UTC")).toLocalDate()
                        onApply(
                            selectedDate,
                            null,
                            selectedLeaveType!!,
                            if (actionReason.isNotBlank()) actionReason else reason,
                            (if (actionAttachments.isNotEmpty()) actionAttachments else attachedFiles).map { it.toString() },
                            leaveToEdit?.id ?: 0,
                            actionToConfirm
                        )
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

    if (showNoDateChangeDialog) {
        AlertDialog(
            onDismissRequest = { showNoDateChangeDialog = false },
            title = { Text(text = "Date Not Selected") },
            text = { Text(text = "Please select a date to ${currentAction.lowercase()} the leave.") },
            confirmButton = {
                TextButton(onClick = { showNoDateChangeDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text(text = "Unsaved Changes") },
            text = { Text(text = "You have unsaved changes in your ${currentAction.lowercase()} request. Do you want to save them before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedChangesDialog = false
                    triedToApply = true
                    if (isFormValid) {
                        if ((currentAction == "Cancel" || currentAction == "Extend") && !hasSelectedActionDate) {
                            showNoDateChangeDialog = true
                        } else {
                            actionToConfirm = currentAction
                            showConfirmationDialog = true
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
}

@Composable
fun AttachmentList(
    context: Context,
    attachedFiles: List<Uri>,
    canRemove: Boolean = true,
    onRemove: (Int) -> Unit,
    onAddClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        attachedFiles.forEachIndexed { index, uri ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = FilePickerUtils.getFileName(context, uri),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (canRemove) {
                    IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        if (attachedFiles.size < 3 && canRemove) {
            TextButton(
                onClick = onAddClick,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Attachment")
                }
            }
        }
    }
}
