package com.fabxdi.dibadge.ui.calendar.claim

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
    var selectedCategory by remember { mutableStateOf(claimToEdit?.category ?: "") }
    var amountInput by remember { mutableStateOf(claimToEdit?.let { String.format(Locale.US, "%.2f", it.amount) } ?: "") }
    var descriptionInput by remember { mutableStateOf(claimToEdit?.description ?: "") }

    var attachedFiles by remember { mutableStateOf<List<AttachmentFile>>(emptyList()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val parsedAmount = amountInput.toDoubleOrNull() ?: 0.0
    val canSubmit = selectedCategory.isNotBlank() && parsedAmount > 0.0

    val hasUnsavedChanges = remember(selectedCategory, amountInput, descriptionInput, attachedFiles) {
        selectedCategory.isNotBlank() || amountInput.isNotBlank() || descriptionInput.isNotBlank() || attachedFiles.isNotEmpty()
    }

    val handleBackAttempt = {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler(enabled = true, onBack = { handleBackAttempt() })

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
                        onClick = { handleBackAttempt() },
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
                                selectedCategory,
                                parsedAmount,
                                descriptionInput.trim(),
                                claimToEdit?.id ?: 0
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
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

            // Main Card
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

                // Row 2: Date
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

                // Row 3: Category
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clickable { showCategorySheet = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Category", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = selectedCategory.ifBlank { "Select category" },
                            fontSize = 16.sp,
                            color = if (selectedCategory.isNotBlank()) primaryTextColor else tabInactiveColor
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select category",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Row 4: Amount
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
                            Text("S$ ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                            BasicTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Row 5: Description (Multi-line text, min height 96dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("Description (optional)", fontSize = 12.sp, color = secondaryTextColor)
                    Spacer(modifier = Modifier.height(6.dp))
                    BasicTextField(
                        value = descriptionInput,
                        onValueChange = { if (it.length <= 300) descriptionInput = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            color = primaryTextColor
                        ),
                        cursorBrush = SolidColor(tealAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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

    if (showCategorySheet) {
        val categories = listOf("Transport", "Meals", "Materials", "Accommodation", "Other")
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
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
                    text = "Category",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                selectedCategory = cat
                                showCategorySheet = false
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
                            text = cat,
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
