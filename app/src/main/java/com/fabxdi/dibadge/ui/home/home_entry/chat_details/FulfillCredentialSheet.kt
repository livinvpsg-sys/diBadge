package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.DocEntity
import com.fabxdi.dibadge.ui.components.DocumentTypeTile
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.viewmodel.CredentialSubmitItem
import com.fabxdi.dibadge.viewmodel.GroupCredentialRequestItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class FulfillCertConfigState(
    val doc: DocEntity,
    var certName: String,
    var expiryDate: LocalDate? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FulfillCredentialSheet(
    request: GroupCredentialRequestItem,
    myDocsList: List<DocEntity>,
    isDark: Boolean,
    onFulfill: (certItems: List<CredentialSubmitItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) } // 1: Select files, 2: Configure details

    val selectedDocs = remember { mutableStateListOf<DocEntity>() }
    val certConfigs = remember { mutableStateListOf<FulfillCertConfigState>() }

    var datePickerTargetIndex by remember { mutableStateOf<Int?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                val fileName = FilePickerUtils.getFileName(context, uri).ifBlank { request.name }
                val newDoc = DocEntity(
                    id = UUID.randomUUID().toString(),
                    name = fileName,
                    type = "certificate",
                    files = listOf(uri.toString()),
                    fileNames = listOf(fileName)
                )
                if (selectedDocs.none { it.id == newDoc.id }) {
                    selectedDocs.add(newDoc)
                }
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
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
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = request.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
            Text(
                text = if (currentStep == 1) "Select one or more credentials" else "Fill details for selected credentials",
                fontSize = 13.sp,
                color = secondaryTextColor,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            if (currentStep == 1) {
                // STEP 1: Select files from My Docs / Upload
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { filePickerLauncher.launch("*/*") },
                    shape = RoundedCornerShape(10.dp),
                    color = cardFillColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = primaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Upload a file (photo or PDF)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Choose from My Docs",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (myDocsList.isEmpty() && selectedDocs.isEmpty()) {
                        item {
                            Text(
                                text = "No documents in My Docs",
                                fontSize = 13.sp,
                                color = secondaryTextColor,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        val availableDocs = (selectedDocs + myDocsList).distinctBy { it.id }
                        items(availableDocs, key = { it.id }) { doc ->
                            val isSelected = selectedDocs.any { it.id == doc.id }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            selectedDocs.removeAll { it.id == doc.id }
                                        } else {
                                            selectedDocs.add(doc)
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = cardFillColor,
                                border = if (isSelected) BorderStroke(1.5.dp, primaryTextColor) else BorderStroke(1.dp, outlineColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val ext = doc.name.substringAfterLast('.', "pdf").lowercase()
                                    DocumentTypeTile(extension = ext, isDark = isDark, widthDp = 24, heightDp = 30)

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = doc.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isSelected) {
                                        Surface(
                                            modifier = Modifier.size(20.dp),
                                            shape = CircleShape,
                                            color = tealAccent
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = if (isDark) Color(0xFF0E1114) else Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .border(BorderStroke(1.5.dp, outlineColor), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val canNext = selectedDocs.isNotEmpty()
                    TextButton(
                        enabled = canNext,
                        onClick = {
                            if (canNext) {
                                certConfigs.clear()
                                selectedDocs.forEach { doc ->
                                    certConfigs.add(
                                        FulfillCertConfigState(
                                            doc = doc,
                                            certName = if (selectedDocs.size == 1) request.name else doc.name,
                                            expiryDate = doc.expiryDate
                                        )
                                    )
                                }
                                currentStep = 2
                            }
                        }
                    ) {
                        Text(
                            text = if (canNext) "Next (${selectedDocs.size})" else "Next",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canNext) tealAccent else dimTextColor
                        )
                    }
                }
            } else {
                // STEP 2: Configure Name & Expiry for each selected credential
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(certConfigs.size) { idx ->
                        val config = certConfigs[idx]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardFillColor,
                            border = BorderStroke(1.dp, outlineColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val ext = config.doc.name.substringAfterLast('.', "pdf").lowercase()
                                    DocumentTypeTile(extension = ext, isDark = isDark, widthDp = 24, heightDp = 30)

                                    Spacer(modifier = Modifier.width(10.dp))

                                    BasicTextField(
                                        value = config.certName,
                                        onValueChange = { newName ->
                                            certConfigs[idx] = config.copy(certName = newName)
                                        },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryTextColor),
                                        cursorBrush = SolidColor(primaryTextColor),
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (certConfigs.size > 1) {
                                        IconButton(
                                            onClick = {
                                                certConfigs.removeAt(idx)
                                                if (certConfigs.isEmpty()) currentStep = 1
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = colors.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val expText = remember(config.expiryDate) {
                                    config.expiryDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) ?: "Expiry date (optional)"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            datePickerTargetIndex = idx
                                            showDatePickerDialog = true
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = expText,
                                        fontSize = 13.sp,
                                        color = if (config.expiryDate != null) primaryTextColor else dimTextColor
                                    )
                                    if (config.expiryDate != null) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear date",
                                            tint = secondaryTextColor,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { certConfigs[idx] = config.copy(expiryDate = null) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { currentStep = 1 }) {
                        Text("Back", fontSize = 15.sp, color = secondaryTextColor)
                    }

                    val canSubmit = certConfigs.all { it.certName.isNotBlank() }
                    TextButton(
                        enabled = canSubmit,
                        onClick = {
                            if (canSubmit) {
                                val submitItems = certConfigs.map { config ->
                                    val expiresAtMs = config.expiryDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                                    CredentialSubmitItem(
                                        myDocId = config.doc.id,
                                        fileUri = config.doc.files.firstOrNull() ?: "",
                                        name = config.certName.trim(),
                                        expiresAtMs = expiresAtMs
                                    )
                                }
                                onFulfill(submitItems)
                            }
                        }
                    ) {
                        Text(
                            text = "Submit",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canSubmit) tealAccent else dimTextColor
                        )
                    }
                }
            }
        }
    }

    if (showDatePickerDialog && datePickerTargetIndex != null) {
        val targetIdx = datePickerTargetIndex!!
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = {
                showDatePickerDialog = false
                datePickerTargetIndex = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selMs = datePickerState.selectedDateMillis
                        if (selMs != null && targetIdx < certConfigs.size) {
                            val newDate = Instant.ofEpochMilli(selMs).atZone(ZoneOffset.UTC).toLocalDate()
                            certConfigs[targetIdx] = certConfigs[targetIdx].copy(expiryDate = newDate)
                        }
                        showDatePickerDialog = false
                        datePickerTargetIndex = null
                    }
                ) {
                    Text("OK", color = tealAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDatePickerDialog = false
                        datePickerTargetIndex = null
                    }
                ) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
