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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.fabxdi.dibadge.viewmodel.GroupCredentialItem
import com.fabxdi.dibadge.viewmodel.GroupMember
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCredentialSheet(
    myDocsList: List<DocEntity>,
    members: List<GroupMember> = emptyList(),
    isAdmin: Boolean = false,
    isDark: Boolean,
    onAddCredential: (doc: DocEntity, certName: String, expiresAtMs: Long?, targetMember: GroupMember?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedDoc by remember { mutableStateOf<DocEntity?>(null) }
    var certNameInput by remember { mutableStateOf("") }
    var selectedExpiryDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val otherMembers = remember(members) { members.filter { !it.isYou } }
    var selectedTargetMember by remember { mutableStateOf<GroupMember?>(otherMembers.firstOrNull()) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)

    LaunchedEffect(selectedDoc) {
        if (selectedDoc != null) {
            certNameInput = selectedDoc!!.name
            selectedExpiryDate = selectedDoc!!.expiryDate
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                val fileName = FilePickerUtils.getFileName(context, uri).ifBlank { "Document" }
                val newDoc = DocEntity(
                    id = UUID.randomUUID().toString(),
                    name = fileName,
                    type = "certificate",
                    files = listOf(uri.toString()),
                    fileNames = listOf(fileName)
                )
                selectedDoc = newDoc
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
            // Header
            Text(
                text = "Add credential",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
            Text(
                text = if (isAdmin) "Manual upload for a member" else "From My Docs",
                fontSize = 13.sp,
                color = secondaryTextColor,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Member picker if Admin
            if (isAdmin && otherMembers.isNotEmpty()) {
                Text(
                    text = "Member",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(otherMembers) { member ->
                        val isSelected = selectedTargetMember?.uid == member.uid
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetMember = member },
                            shape = RoundedCornerShape(10.dp),
                            color = cardFillColor,
                            border = if (isSelected) BorderStroke(1.5.dp, tealAccent) else BorderStroke(1.dp, outlineColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(26.dp),
                                    shape = CircleShape,
                                    color = cardBgColor
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = member.displayName.take(1).uppercase(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryTextColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = member.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryTextColor,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = tealAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Upload new button
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

            // Docs Selection List
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
                    .heightIn(max = 160.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (myDocsList.isEmpty()) {
                    item {
                        Text(
                            text = "No files in My Docs yet",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(myDocsList) { doc ->
                        val isSelected = selectedDoc?.id == doc.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDoc = doc },
                            shape = RoundedCornerShape(10.dp),
                            color = cardFillColor,
                            border = if (isSelected) BorderStroke(1.5.dp, tealAccent) else BorderStroke(1.dp, outlineColor)
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

            // Field: Credential Name
            Text(
                text = "Credential name",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryTextColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicTextField(
                value = certNameInput,
                onValueChange = { certNameInput = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = primaryTextColor),
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                thickness = 1.dp,
                color = outlineColor
            )

            // Field: Expiry date (optional)
            Text(
                text = "Expiry date (optional)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryTextColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            val dateText = remember(selectedExpiryDate) {
                selectedExpiryDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) ?: "Select date"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePickerDialog = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateText,
                    fontSize = 15.sp,
                    color = if (selectedExpiryDate != null) primaryTextColor else dimTextColor
                )
                if (selectedExpiryDate != null) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear date",
                        tint = secondaryTextColor,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { selectedExpiryDate = null }
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                thickness = 1.dp,
                color = outlineColor
            )

            // Buttons: Cancel & Add
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }

                Spacer(modifier = Modifier.width(12.dp))

                val canAdd = selectedDoc != null && certNameInput.isNotBlank() && (!isAdmin || selectedTargetMember != null)
                TextButton(
                    enabled = canAdd,
                    onClick = {
                        if (canAdd) {
                            val doc = selectedDoc!!
                            val expiresAtMs = selectedExpiryDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                            onAddCredential(doc, certNameInput.trim(), expiresAtMs, selectedTargetMember)
                        }
                    }
                ) {
                    Text(
                        text = "Add",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canAdd) tealAccent else dimTextColor
                    )
                }
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selMs = datePickerState.selectedDateMillis
                        if (selMs != null) {
                            selectedExpiryDate = Instant.ofEpochMilli(selMs).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("OK", color = tealAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCredentialSheet(
    cert: GroupCredentialItem,
    isDark: Boolean,
    onSave: (newName: String, newExpiresAtMs: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf(cert.name) }
    var selectedExpiryDate by remember {
        mutableStateOf(
            cert.expiresAt?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        )
    }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp
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
                text = "Edit credential",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Field: Name
            Text(
                text = "Credential name",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryTextColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = primaryTextColor),
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                thickness = 1.dp,
                color = outlineColor
            )

            // Field: Expiry date
            Text(
                text = "Expiry date (optional)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryTextColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            val dateText = remember(selectedExpiryDate) {
                selectedExpiryDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) ?: "Select date"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePickerDialog = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateText,
                    fontSize = 15.sp,
                    color = if (selectedExpiryDate != null) primaryTextColor else dimTextColor
                )
                if (selectedExpiryDate != null) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear date",
                        tint = secondaryTextColor,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { selectedExpiryDate = null }
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                thickness = 1.dp,
                color = outlineColor
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    enabled = nameInput.isNotBlank(),
                    onClick = {
                        val expiresAtMs = selectedExpiryDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                        onSave(nameInput.trim(), expiresAtMs)
                    }
                ) {
                    Text("Save", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tealAccent)
                }
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selMs = datePickerState.selectedDateMillis
                        if (selMs != null) {
                            selectedExpiryDate = Instant.ofEpochMilli(selMs).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("OK", color = tealAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
