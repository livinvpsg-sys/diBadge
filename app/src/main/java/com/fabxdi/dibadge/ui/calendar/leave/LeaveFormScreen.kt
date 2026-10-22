package com.fabxdi.dibadge.ui.calendar.leave

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.calendar.AttachmentFile
import com.fabxdi.dibadge.ui.calendar.AttachmentsSection
import com.fabxdi.dibadge.ui.settings.SettingsCard
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveFormScreen(
    existingLeaves: List<LeaveEntity>,
    leaveToEdit: LeaveEntity? = null,
    initialDate: LocalDate = LocalDate.now(),
    onApply: (startDate: LocalDate, endDate: LocalDate?, type: String, reason: String, attachments: List<String>, id: Int, action: String) -> Unit,
    onClose: () -> Unit
) {
    val isEditing = leaveToEdit != null

    var selectedOrg by remember { mutableStateOf("diBadge Inc.") }
    var selectedLeaveType by remember { mutableStateOf(leaveToEdit?.leaveType ?: "") }
    var fromDate by remember { mutableStateOf(leaveToEdit?.startDate ?: initialDate) }
    var toDate by remember { mutableStateOf(leaveToEdit?.endDate ?: leaveToEdit?.startDate ?: initialDate) }
    var reasonInput by remember { mutableStateOf(leaveToEdit?.reason ?: "") }

    var attachedFiles by remember { mutableStateOf<List<AttachmentFile>>(emptyList()) }

    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    var showLeaveTypeSheet by remember { mutableStateOf(false) }
    var showOrgSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Ensure To Date is not before From Date
    LaunchedEffect(fromDate) {
        if (toDate.isBefore(fromDate)) {
            toDate = fromDate
        }
    }

    // Leave Days Calculation (inclusive)
    val totalDays = remember(fromDate, toDate) {
        if (toDate.isBefore(fromDate)) 0L
        else ChronoUnit.DAYS.between(fromDate, toDate) + 1
    }

    // Overlap Check with existing leaves
    val hasOverlap = remember(fromDate, toDate, existingLeaves, leaveToEdit) {
        existingLeaves.any { leave ->
            if (leaveToEdit != null && leave.id == leaveToEdit.id) return@any false
            val leaveEnd = leave.endDate ?: leave.startDate
            !(toDate.isBefore(leave.startDate) || fromDate.isAfter(leaveEnd))
        }
    }

    val canSubmit = selectedLeaveType.isNotBlank() && !toDate.isBefore(fromDate) && !hasOverlap

    val hasUnsavedChanges = remember(selectedLeaveType, fromDate, toDate, reasonInput, attachedFiles) {
        if (!isEditing) selectedLeaveType.isNotBlank() || reasonInput.isNotBlank() || attachedFiles.isNotEmpty()
        else (selectedLeaveType != leaveToEdit?.leaveType || fromDate != leaveToEdit?.startDate || toDate != (leaveToEdit?.endDate ?: leaveToEdit?.startDate) || reasonInput != leaveToEdit?.reason)
    }

    val handleBack = {
        if (hasUnsavedChanges) {
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
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val tabInactiveColor = diBadgeColors.tabInactive
    val errorColor = colors.error

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()) }

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
                        text = if (isEditing) "Edit leave" else "New leave",
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
                            onApply(
                                fromDate,
                                if (fromDate.isEqual(toDate)) null else toDate,
                                selectedLeaveType,
                                reasonInput.trim(),
                                attachedFiles.map { it.uriString },
                                leaveToEdit?.id ?: 0,
                                if (isEditing) "Save" else "Apply"
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .semantics {
                            contentDescription = if (canSubmit) "Submit" else "Submit, unavailable"
                        }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Card 1: Organisation & Leave Type
            SettingsCard {
                // Row 1: Organisation
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

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Row 2: Leave type (Required)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clickable { showLeaveTypeSheet = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .semantics {
                            contentDescription = "Leave type, ${selectedLeaveType.ifBlank { "Select leave type" }}, opens list"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Leave type", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = selectedLeaveType.ifBlank { "Select leave type" },
                            fontSize = 16.sp,
                            color = if (selectedLeaveType.isNotBlank()) primaryTextColor else tabInactiveColor
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select leave type",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Section Label: Dates
            Text(
                text = "Dates",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor,
                modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
            )

            // Card 2: Dates Card
            SettingsCard {
                // From Date
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clickable { showFromDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("From", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(fromDate.format(dateFormatter), fontSize = 16.sp, color = primaryTextColor)
                    }
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "Select from date",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // To Date
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clickable { showToDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("To", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(toDate.format(dateFormatter), fontSize = 16.sp, color = primaryTextColor)
                    }
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "Select to date",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Overlap Error Message
                if (hasOverlap) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You already have leave on these dates",
                        fontSize = 13.sp,
                        color = errorColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                    Text("Total", fontSize = 14.sp, color = secondaryTextColor)
                    Text(
                        text = "$totalDays ${if (totalDays == 1L) "day" else "days"}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent
                    )
                }
            }

            // Section Label: Reason (optional)
            Text(
                text = "Reason (optional)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor,
                modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
            )

            // Card 3: Reason Text Card
            SettingsCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .padding(14.dp)
                ) {
                    if (reasonInput.isEmpty()) {
                        Text("Add a note", fontSize = 16.sp, color = tabInactiveColor)
                    }
                    BasicTextField(
                        value = reasonInput,
                        onValueChange = { if (it.length <= 300) reasonInput = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            color = primaryTextColor
                        ),
                        cursorBrush = SolidColor(tealAccent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Attachments Section
            AttachmentsSection(
                attachedFiles = attachedFiles,
                onAddFile = { attachedFiles = attachedFiles + it },
                onRemoveFile = { attachedFiles = attachedFiles - it },
                onShowSnackbar = { msg ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            )
        }
    }

    if (showFromDatePicker) {
        DiBadgeDatePicker(
            selectedDate = fromDate,
            onDateSelected = {
                fromDate = it
                showFromDatePicker = false
            },
            onDismiss = { showFromDatePicker = false }
        )
    }

    if (showToDatePicker) {
        DiBadgeDatePicker(
            selectedDate = toDate,
            onDateSelected = {
                toDate = it
                showToDatePicker = false
            },
            onDismiss = { showToDatePicker = false }
        )
    }

    if (showLeaveTypeSheet) {
        val leaveTypesList = listOf("Annual leave", "Sick leave", "Unpaid leave", "Other")
        ModalBottomSheet(
            onDismissRequest = { showLeaveTypeSheet = false },
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
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Leave type",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                leaveTypesList.forEach { typeOption ->
                    val isSelected = selectedLeaveType == typeOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                selectedLeaveType = typeOption
                                showLeaveTypeSheet = false
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, if (isSelected) tealAccent else tabInactiveColor)
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Surface(
                                        modifier = Modifier.size(10.dp),
                                        shape = CircleShape,
                                        color = tealAccent
                                    ) {}
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = typeOption,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = primaryTextColor
                        )
                    }
                }
            }
        }
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
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
