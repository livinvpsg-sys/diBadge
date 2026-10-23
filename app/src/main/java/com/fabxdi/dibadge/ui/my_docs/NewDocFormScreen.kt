package com.fabxdi.dibadge.ui.my_docs

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.DocEntity
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.calendar.AttachmentFile
import com.fabxdi.dibadge.ui.calendar.AttachmentsSection
import com.fabxdi.dibadge.ui.settings.SettingsCard
import com.fabxdi.dibadge.ui.settings.SettingsSwitch
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDocFormScreen(
    docToEdit: DocEntity? = null,
    initialFile: AttachmentFile? = null,
    onSave: (DocEntity) -> Unit,
    onClose: () -> Unit
) {
    val isEditing = docToEdit != null
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var nameInput by remember { mutableStateOf(docToEdit?.name ?: "") }
    var selectedType by remember { mutableStateOf(docToEdit?.type ?: "") }
    var numberInput by remember { mutableStateOf(docToEdit?.number ?: "") }
    var expiryDate by remember { mutableStateOf<LocalDate?>(docToEdit?.expiryDate ?: LocalDate.now().plusYears(1)) }
    var noExpiry by remember { mutableStateOf(docToEdit?.noExpiry ?: false) }

    var attachedFiles by remember {
        mutableStateOf<List<AttachmentFile>>(
            if (docToEdit != null) {
                docToEdit.files.mapIndexed { idx, uri ->
                    AttachmentFile(
                        uriString = uri,
                        name = docToEdit.fileNames.getOrElse(idx) { "Document" },
                        sizeBytes = docToEdit.fileSizes.getOrElse(idx) { 500_000L },
                        isPdf = docToEdit.fileIsPdf.getOrElse(idx) { uri.endsWith(".pdf", ignoreCase = true) }
                    )
                }
            } else if (initialFile != null) {
                listOf(initialFile)
            } else {
                emptyList()
            }
        )
    }

    var showTypeSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val canSave = nameInput.isNotBlank() && selectedType.isNotBlank() && attachedFiles.isNotEmpty() && (noExpiry || expiryDate != null)

    val hasUnsavedChanges = remember(nameInput, selectedType, numberInput, expiryDate, noExpiry, attachedFiles) {
        if (!isEditing) nameInput.isNotBlank() || selectedType.isNotBlank() || numberInput.isNotBlank() || attachedFiles.isNotEmpty()
        else (nameInput != docToEdit?.name || selectedType != docToEdit?.type || numberInput != docToEdit?.number || expiryDate != docToEdit?.expiryDate || noExpiry != docToEdit?.noExpiry)
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

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()) }

    val typeLabelMap = remember {
        mapOf("id" to "ID", "certificate" to "Certificate", "licence" to "Licence", "other" to "Other")
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
                        text = if (isEditing) "Edit document" else "New document",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }

                Text(
                    text = "Save",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSave) tealAccent else tabInactiveColor,
                    modifier = Modifier
                        .clickable(enabled = canSave) {
                            val savedDoc = DocEntity(
                                id = docToEdit?.id ?: "",
                                name = nameInput.trim(),
                                type = selectedType,
                                number = numberInput.trim(),
                                expiryDate = if (noExpiry) null else expiryDate,
                                noExpiry = noExpiry,
                                files = attachedFiles.map { it.uriString },
                                fileNames = attachedFiles.map { it.name },
                                fileSizes = attachedFiles.map { it.sizeBytes },
                                fileIsPdf = attachedFiles.map { it.isPdf },
                                sharedOrganisations = docToEdit?.sharedOrganisations ?: emptyList(),
                                sharedDates = docToEdit?.sharedDates ?: emptyList(),
                                createdAt = docToEdit?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(savedDoc)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .semantics {
                            contentDescription = if (canSave) "Save" else "Save, unavailable"
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

            // Main Form Card
            SettingsCard {
                // Row 1: Name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Name", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (nameInput.isEmpty()) {
                                Text("Document name", fontSize = 16.sp, color = tabInactiveColor)
                            }
                            BasicTextField(
                                value = nameInput,
                                onValueChange = { if (it.length <= 60) nameInput = it },
                                singleLine = true,
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

                // Row 2: Type
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clickable { showTypeSheet = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Type", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = typeLabelMap[selectedType] ?: "Select type",
                            fontSize = 16.sp,
                            color = if (selectedType.isNotBlank()) primaryTextColor else tabInactiveColor
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select type",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Row 3: Number (optional)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Number (optional)", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (numberInput.isEmpty()) {
                                Text("Document number", fontSize = 16.sp, color = tabInactiveColor)
                            }
                            BasicTextField(
                                value = numberInput,
                                onValueChange = { if (it.length <= 40) numberInput = it },
                                singleLine = true,
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

                // Row 4: Expiry date
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .alpha(if (!noExpiry) 1.0f else 0.40f)
                        .clickable(enabled = !noExpiry) { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Expiry date", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (noExpiry) "" else expiryDate?.format(dateFormatter) ?: "Select date",
                            fontSize = 16.sp,
                            color = if (!noExpiry && expiryDate != null) primaryTextColor else tabInactiveColor
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "Select expiry date",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Row 5: No expiry switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("No expiry", fontSize = 16.sp, color = primaryTextColor)
                    SettingsSwitch(
                        checked = noExpiry,
                        onCheckedChange = { noExpiry = it }
                    )
                }
            }

            // Files Section
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
            selectedDate = expiryDate ?: LocalDate.now(),
            onDateSelected = {
                expiryDate = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTypeSheet) {
        val typesList = listOf("id" to "ID", "certificate" to "Certificate", "licence" to "Licence", "other" to "Other")
        ModalBottomSheet(
            onDismissRequest = { showTypeSheet = false },
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
                    text = "Type",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                typesList.forEach { (typeKey, typeLabel) ->
                    val isSelected = selectedType == typeKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                selectedType = typeKey
                                if (nameInput.isBlank()) {
                                    nameInput = typeLabel
                                }
                                showTypeSheet = false
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
                            text = typeLabel,
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
                    text = "Discard this document?",
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
