package com.fabxdi.dibadge.ui.my_docs

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.DocEntity
import com.fabxdi.dibadge.ui.calendar.DetailValueRow
import com.fabxdi.dibadge.ui.settings.SettingsCard
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocDetailsScreen(
    doc: DocEntity,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error
    val isDark = primaryTextColor.red > 0.5f

    val today = remember { LocalDate.now() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }

    val typeLabelMap = remember {
        mapOf("id" to "ID", "certificate" to "Certificate", "licence" to "Licence", "other" to "Other")
    }

    val isShared = doc.sharedOrganisations.isNotEmpty()

    // Calculate Status
    val (statusText, statusBg, statusFg) = remember(doc.expiryDate, doc.noExpiry, today, isDark) {
        if (doc.noExpiry || doc.expiryDate == null) {
            Triple(
                "No expiry",
                if (isDark) Color(0xFF262D35) else Color(0xFFDDE3E8),
                if (isDark) Color(0xFFA3AEB8) else Color(0xFF4A5661)
            )
        } else {
            val daysDiff = ChronoUnit.DAYS.between(today, doc.expiryDate)
            val dateFormatted = doc.expiryDate.format(dateFormatter)

            when {
                daysDiff < 0 -> Triple(
                    "Expired $dateFormatted",
                    if (isDark) Color(0xFF3A2220) else Color(0xFFF6DCD9),
                    if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)
                )
                daysDiff <= 30 -> {
                    val label = when (daysDiff) {
                        0L -> "Expires Today"
                        1L -> "Expires Tomorrow"
                        else -> "Expires in $daysDiff days"
                    }
                    Triple(
                        label,
                        if (isDark) Color(0xFF262D35) else Color(0xFFDDE3E8),
                        if (isDark) Color(0xFFA3AEB8) else Color(0xFF4A5661)
                    )
                }
                else -> Triple(
                    "Valid until $dateFormatted",
                    if (isDark) Color(0xFF16403D) else Color(0xFFD3E9E6),
                    if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)
                )
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBgColor)
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = doc.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Three Icons: Share, Edit, Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = primaryTextColor, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = primaryTextColor, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = primaryTextColor, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Card 1: Details & Status Pill
            SettingsCard {
                // Status Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .semantics { contentDescription = "Status, $statusText" }
                ) {
                    Text("Status", fontSize = 12.sp, color = secondaryTextColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.height(24.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = statusBg
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(statusText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = statusFg)
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                DetailValueRow("Name", doc.name, primaryTextColor, secondaryTextColor)

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                DetailValueRow("Type", typeLabelMap[doc.type] ?: doc.type, primaryTextColor, secondaryTextColor)

                if (doc.number.isNotBlank()) {
                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                    DetailValueRow("Number", doc.number, primaryTextColor, secondaryTextColor)
                }

                if (!doc.noExpiry && doc.expiryDate != null) {
                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                    DetailValueRow("Expiry date", doc.expiryDate.format(dateFormatter), primaryTextColor, secondaryTextColor)
                }
            }

            // Card 2: Files
            if (doc.files.isNotEmpty()) {
                Text(
                    text = "Files",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
                )

                SettingsCard {
                    doc.files.forEachIndexed { index, uri ->
                        val fileName = doc.fileNames.getOrElse(index) { "Document" }
                        val fileSize = doc.fileSizes.getOrElse(index) { 500_000L }
                        val isPdf = doc.fileIsPdf.getOrElse(index) { uri.endsWith(".pdf", ignoreCase = true) }

                        val sizeKb = fileSize / 1024
                        val sizeMbStr = if (sizeKb > 1024) "${String.format(Locale.US, "%.1f", sizeKb / 1024.0)} MB" else "$sizeKb KB"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = pageBgColor
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPdf) Icons.Outlined.Description else Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileName,
                                    fontSize = 15.sp,
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${if (isPdf) "PDF" else "Photo"} · $sizeMbStr",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        if (index < doc.files.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = outlineColor)
                        }
                    }
                }
            }

            // Card 3: Shared with (only if shared at least once)
            if (isShared) {
                Text(
                    text = "Shared with",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
                )

                SettingsCard {
                    doc.sharedOrganisations.forEachIndexed { index, org ->
                        val dateInfo = doc.sharedDates.getOrElse(index) { "Shared" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Business,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = org,
                                    fontSize = 16.sp,
                                    color = primaryTextColor
                                )
                                Text(
                                    text = dateInfo,
                                    fontSize = 13.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        if (index < doc.sharedOrganisations.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = outlineColor)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete this document?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )
            },
            text = {
                Column {
                    Text(
                        text = "The document and its files will be removed from your account.",
                        fontSize = 14.sp,
                        color = secondaryTextColor
                    )
                    if (isShared) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Copies already shared with organisations stay with them.",
                            fontSize = 13.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteConfirmed()
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
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryTextColor
                    )
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
