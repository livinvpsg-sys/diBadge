package com.fabxdi.dibadge.ui.calendar.claim

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.ClaimEntity
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.calendar.AttachmentFile
import com.fabxdi.dibadge.ui.calendar.AttachmentsSection
import com.fabxdi.dibadge.ui.settings.SettingsCard
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimFormScreen(
    claimToEdit: ClaimEntity? = null,
    initialDate: LocalDate = LocalDate.now(),
    onApply: (date: LocalDate, category: String, amount: Double, description: String, id: Int) -> Unit,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedOrg by remember { mutableStateOf("diBadge Inc.") }
    var selectedDate by remember { mutableStateOf(claimToEdit?.date ?: initialDate) }
    var amountInput by remember { mutableStateOf(claimToEdit?.let { String.format(Locale.US, "%.2f", it.amount) } ?: "") }
    var descriptionInput by remember { mutableStateOf(claimToEdit?.description ?: "") }

    var attachedFiles by remember { mutableStateOf<List<AttachmentFile>>(emptyList()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val parsedAmount = amountInput.toDoubleOrNull() ?: 0.0
    val canSubmit = parsedAmount > 0.0

    val hasUnsavedChanges = remember(amountInput, descriptionInput, attachedFiles) {
        amountInput.isNotBlank() || descriptionInput.isNotBlank() || attachedFiles.isNotEmpty()
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

    val formattedDateText = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
    }

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
                        text = if (claimToEdit != null) "Edit claim" else "New claim",
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
                                selectedDate,
                                "General",
                                parsedAmount,
                                descriptionInput.trim(),
                                claimToEdit?.id ?: 0
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

            // Section Label: Details
            Text(
                text = "Details",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
            )

            // Card 2: Details Card (Date, Amount, Description)
            SettingsCard {
                // Row 1: Date
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
                        Text(formattedDateText, fontSize = 16.sp, color = primaryTextColor)
                    }
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "Pick date",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Row 2: Amount
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Amount", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("S$ ", fontSize = 16.sp, fontWeight = FontWeight.Normal, color = primaryTextColor)
                            BasicTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = primaryTextColor
                                ),
                                cursorBrush = SolidColor(tealAccent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Row 3: Description (Multi-line text, min height 72dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .padding(14.dp)
                ) {
                    Text("Description (optional)", fontSize = 12.sp, color = secondaryTextColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (descriptionInput.isEmpty()) {
                            Text("Add a note", fontSize = 16.sp, color = tabInactiveColor)
                        }
                        BasicTextField(
                            value = descriptionInput,
                            onValueChange = { if (it.length <= 300) descriptionInput = it },
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
