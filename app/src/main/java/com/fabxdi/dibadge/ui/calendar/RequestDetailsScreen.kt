package com.fabxdi.dibadge.ui.calendar

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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.ClaimEntity
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import com.fabxdi.dibadge.ui.settings.SettingsCard
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailsScreen(
    requestItem: Any,
    onBack: () -> Unit,
    onCancelRequestConfirmed: (Any) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val errorColor = colors.error

    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }

    // Title based on request type
    val titleText = when (requestItem) {
        is LeaveEntity -> "Leave request"
        is OvertimeEntity -> "Extra hours request"
        is ClaimEntity -> "Claim"
        else -> "Request details"
    }

    // Status and Date
    val statusText = when (requestItem) {
        is LeaveEntity -> requestItem.status
        is OvertimeEntity -> requestItem.status
        is ClaimEntity -> requestItem.status
        else -> "Pending"
    }

    val appliedDateStr = when (requestItem) {
        is LeaveEntity -> requestItem.appliedDateTime.format(dateTimeFormatter)
        is OvertimeEntity -> requestItem.appliedDateTime.format(dateTimeFormatter)
        else -> "22 Oct 2026, 9:42 AM"
    }

    val isPending = statusText.equals("Pending", ignoreCase = true)
    val isApproved = statusText.equals("Approved", ignoreCase = true)
    val isRejected = statusText.equals("Rejected", ignoreCase = true)

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBgColor)
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
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
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = titleText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Card 1: Details
            SettingsCard {
                // Row 1: Status
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .semantics { contentDescription = "Status, $statusText" }
                ) {
                    Text(
                        text = "Status",
                        fontSize = 12.sp,
                        color = secondaryTextColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    RequestStatusPill(status = statusText)
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Row 2: Submitted
                DetailValueRow(
                    label = "Submitted",
                    value = appliedDateStr,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor
                )

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Row 3+: Form specific fields
                when (requestItem) {
                    is LeaveEntity -> {
                        DetailValueRow("Organisation", "diBadge Inc.", primaryTextColor, secondaryTextColor)
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        DetailValueRow("Leave type", requestItem.leaveType, primaryTextColor, secondaryTextColor)
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        DetailValueRow("From", requestItem.startDate.format(dateFormatter), primaryTextColor, secondaryTextColor)
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        val endDate = requestItem.endDate ?: requestItem.startDate
                        DetailValueRow("To", endDate.format(dateFormatter), primaryTextColor, secondaryTextColor)
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        val daysCount = ChronoUnit.DAYS.between(requestItem.startDate, endDate) + 1
                        DetailValueRow("Days", "$daysCount ${if (daysCount == 1L) "day" else "days"}", secondaryTextColor, secondaryTextColor)

                        if (requestItem.reason.isNotBlank()) {
                            HorizontalDivider(thickness = 1.dp, color = outlineColor)
                            DetailValueRow("Reason", requestItem.reason, primaryTextColor, secondaryTextColor)
                        }
                    }

                    is OvertimeEntity -> {
                        DetailValueRow("Organisation", "diBadge Inc.", primaryTextColor, secondaryTextColor)
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        DetailValueRow("Date", requestItem.date.format(dateFormatter), primaryTextColor, secondaryTextColor)
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        DetailValueRow("Start time", requestItem.startTime.format(timeFormatter), primaryTextColor, secondaryTextColor)
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        DetailValueRow("End time", requestItem.endTime.format(timeFormatter), primaryTextColor, secondaryTextColor)
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        val hrs = Duration.between(requestItem.startTime, requestItem.endTime).toMinutes() / 60.0
                        val finalHrs = if (hrs < 0) hrs + 24.0 else hrs
                        DetailValueRow("Hours", "$finalHrs hrs", secondaryTextColor, secondaryTextColor)

                        if (requestItem.description.isNotBlank()) {
                            HorizontalDivider(thickness = 1.dp, color = outlineColor)
                            DetailValueRow("Work done", requestItem.description, primaryTextColor, secondaryTextColor)
                        }
                    }

                    is ClaimEntity -> {
                        DetailValueRow("Organisation", "diBadge Inc.", primaryTextColor, secondaryTextColor)
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        DetailValueRow("Date", requestItem.date.format(dateFormatter), primaryTextColor, secondaryTextColor)
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        DetailValueRow("Amount", "S$${String.format(Locale.US, "%.2f", requestItem.amount)}", primaryTextColor, secondaryTextColor)

                        if (requestItem.description.isNotBlank()) {
                            HorizontalDivider(thickness = 1.dp, color = outlineColor)
                            DetailValueRow("Description", requestItem.description, primaryTextColor, secondaryTextColor)
                        }
                    }
                }
            }

            // Card 2: Decision (Only for Approved or Rejected)
            if (isApproved || isRejected) {
                Text(
                    text = "Decision",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                )

                SettingsCard {
                    val decisionBy = if (isApproved) "Approved by" else "Rejected by"
                    DetailValueRow(decisionBy, "HR Manager", primaryTextColor, secondaryTextColor)

                    HorizontalDivider(thickness = 1.dp, color = outlineColor)

                    val decisionTimeStr = when (requestItem) {
                        is LeaveEntity -> requestItem.approvedDate?.format(dateFormatter) ?: "22 Oct 2026, 10:15 AM"
                        is OvertimeEntity -> requestItem.approvedDate?.format(dateFormatter) ?: "22 Oct 2026, 10:15 AM"
                        else -> "22 Oct 2026, 10:15 AM"
                    }
                    DetailValueRow("On", decisionTimeStr, primaryTextColor, secondaryTextColor)
                }
            }

            // Card 3: Attachments (Leave and Claim only, if non-empty)
            val attachmentsList = when (requestItem) {
                is LeaveEntity -> requestItem.attachments
                else -> emptyList()
            }

            if (attachmentsList.isNotEmpty()) {
                Text(
                    text = "Attachments",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                )

                SettingsCard {
                    attachmentsList.forEachIndexed { index, fileUri ->
                        val isPdf = fileUri.endsWith(".pdf", ignoreCase = true)
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
                                    text = "Attachment_${index + 1}${if (isPdf) ".pdf" else ".jpg"}",
                                    fontSize = 15.sp,
                                    color = primaryTextColor
                                )
                                Text(
                                    text = if (isPdf) "PDF · 240 KB" else "Photo · 1.2 MB",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        if (index < attachmentsList.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = outlineColor)
                        }
                    }
                }
            }

            // Card 4: Cancel Request (Only while Pending)
            if (isPending) {
                Spacer(modifier = Modifier.height(24.dp))

                SettingsCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable { showCancelConfirmDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel request",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = errorColor
                        )
                    }
                }
            }
        }
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = {
                Text(
                    text = "Cancel this request?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "The approver will no longer see it.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmDialog = false
                        onCancelRequestConfirmed(requestItem)
                    }
                ) {
                    Text(
                        text = "Cancel request",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = errorColor
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text(
                        text = "Keep",
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

@Composable
fun DetailValueRow(
    label: String,
    value: String,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, $value"
            },
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = secondaryTextColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            color = primaryTextColor
        )
    }
}

@Composable
fun RequestStatusPill(
    status: String
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val (bgColor, textColor, labelText) = when {
        status.equals("Approved", ignoreCase = true) -> Triple(
            if (isDark) Color(0xFF16403D) else Color(0xFFD3E9E6),
            if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69),
            "Approved"
        )
        status.equals("Rejected", ignoreCase = true) -> Triple(
            if (isDark) Color(0xFF3A2220) else Color(0xFFF6DCD9),
            if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A),
            "Rejected"
        )
        status.equals("Cancelled", ignoreCase = true) -> Triple(
            if (isDark) Color(0xFF262D35) else Color(0xFFDDE3E8),
            if (isDark) Color(0xFFA3AEB8) else Color(0xFF4A5661),
            "Cancelled"
        )
        else -> Triple(
            if (isDark) Color(0xFF262D35) else Color(0xFFDDE3E8),
            if (isDark) Color(0xFFA3AEB8) else Color(0xFF4A5661),
            "Pending"
        )
    }

    Surface(
        modifier = Modifier
            .height(24.dp)
            .wrapContentWidth(),
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = labelText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}
