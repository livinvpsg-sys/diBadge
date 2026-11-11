package com.fabxdi.dibadge.ui.inbox

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.InboxEntity
import com.fabxdi.dibadge.data.InboxStamp
import com.fabxdi.dibadge.ui.profile.SignatureBottomSheet
import com.fabxdi.dibadge.ui.profile.parseSignatureImageBitmap
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.InboxViewModel
import com.fabxdi.dibadge.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    item: InboxEntity,
    onBack: () -> Unit,
    onViewInGroupChat: (String) -> Unit = {},
    inboxViewModel: InboxViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val profileData by profileViewModel.profileData.collectAsState()

    var showSignatureSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tealAccent = diBadgeColors.accent
    val isDark = diBadgeColors.isDark

    val isPending = item.actionStatus == "pending" && item.requiredAction != "none"
    val isComplete = !isPending && (item.signers.isEmpty() || item.signers.all { it.hasAnswered })

    val formattedHeaderTime = remember(item.timestamp) {
        val dt = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault())
        dt.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))
    }

    val isOrgMessage = item.type.startsWith("organisation")
    val avatarBgColor = if (isOrgMessage) cardBgColor else remember(item.senderName) { UserColorUtils.getColorForName(item.senderName) }

    // Real values from the profile only. No made-up name or badge number is ever stamped.
    val userFullName = remember(profileData.firstName, profileData.lastName) {
        "${profileData.firstName} ${profileData.lastName}".trim()
    }
    val userBadgeNumber = profileData.badgeNumber
    val hasUserSignature = profileData.signatureUrl.isNotBlank()
    val profileReady = userFullName.isNotBlank() && userBadgeNumber.isNotBlank()

    // File Name calculation for Export
    val exportFileName = remember(item) {
        val sanitizedSubject = item.subject.replace(Regex("[/\\\\:*?\"<>|]"), "-")
        val sanitizedGroupName = item.groupName?.replace(Regex("[/\\\\:*?\"<>|]"), "-") ?: "Group"
        val dateStr = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()

        when {
            isOrgMessage -> "$sanitizedSubject.pdf"
            item.permitNumber != null -> "${item.permitNumber}_${sanitizedGroupName}_$dateStr.pdf"
            else -> "${sanitizedSubject}_${sanitizedGroupName}_$dateStr.pdf"
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = pageBgColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Avatar 40dp
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = avatarBgColor,
                        border = if (isOrgMessage) BorderStroke(1.dp, outlineColor) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isOrgMessage) {
                                Icon(
                                    imageVector = Icons.Outlined.Business,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = item.senderName.take(1).uppercase(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Sender Name & Subline
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.senderName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor,
                            maxLines = 1
                        )
                        Text(
                            text = "${item.departmentOrRequester} · $formattedHeaderTime",
                            fontSize = 12.sp,
                            color = secondaryTextColor,
                            maxLines = 1
                        )
                    }

                    // Export Icon (only when complete)
                    if (isComplete) {
                        IconButton(
                            onClick = { showExportSheet = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Export as PDF",
                                tint = primaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, outlineColor),
                    modifier = Modifier.padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
                ) {
                    Text(
                        text = data.visuals.message,
                        fontSize = 14.sp,
                        color = primaryTextColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = if (isPending) 120.dp else 40.dp)
            ) {
                // Title / Subject
                Text(
                    text = item.subject,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Full Message Text
                Text(
                    text = item.body,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    color = primaryTextColor
                )

                // Group Request Link
                if (!isOrgMessage && item.groupId != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .clickable { onViewInGroupChat(item.groupId) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = tealAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "View in group chat",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tealAccent
                        )
                    }
                }

                // Stamps or Signers Section
                if (item.stamps.isNotEmpty() || item.signers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(22.dp))

                    if (item.signers.isNotEmpty()) {
                        val doneCount = item.signers.count { it.hasAnswered }
                        Text(
                            text = "Approvals $doneCount of ${item.signers.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Answered Stamps Cards
                    item.stamps.forEach { stamp ->
                        StampCard(
                            stamp = stamp,
                            cardBgColor = cardBgColor,
                            outlineColor = outlineColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            tealAccent = tealAccent,
                            isDark = isDark
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Waiting Signer Rows
                    item.signers.filter { !it.hasAnswered }.forEach { signer ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = cardBgColor,
                            border = BorderStroke(1.dp, outlineColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = remember(signer.name) { UserColorUtils.getColorForName(signer.name) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = signer.name.take(1).uppercase(),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = signer.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = primaryTextColor
                                    )
                                    Text(
                                        text = signer.statusText,
                                        fontSize = 13.sp,
                                        color = secondaryTextColor
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            // Pinned Action Buttons at Bottom (when pending)
            if (isPending) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(pageBgColor)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    if (!hasUserSignature) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Add your signature in your profile to respond.",
                                fontSize = 13.sp,
                                color = secondaryTextColor,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add signature",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tealAccent,
                                modifier = Modifier.clickable { showSignatureSheet = true }
                            )
                        }
                    }

                    if (hasUserSignature && !profileReady) {
                        Text(
                            text = "Your profile is still loading. Try again in a moment.",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    val canPress = hasUserSignature && profileReady

                    when (item.requiredAction) {
                        "acknowledge" -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .alpha(if (canPress) 1.0f else 0.38f)
                                    .clickable(enabled = canPress) {
                                        inboxViewModel.performAction(
                                            item = item,
                                            actionType = "acknowledge",
                                            userName = userFullName,
                                            userBadgeNumber = userBadgeNumber,
                                            userSignatureUrl = profileData.signatureUrl
                                        )
                                    },
                                shape = RoundedCornerShape(24.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, if (canPress) tealAccent else outlineColor)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Acknowledge",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (canPress) tealAccent else secondaryTextColor
                                    )
                                }
                            }
                        }
                        "verify", "accept_decline" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .alpha(if (canPress) 1.0f else 0.38f)
                                        .clickable(enabled = canPress) {
                                            inboxViewModel.performAction(
                                                item = item,
                                                actionType = "decline",
                                                userName = userFullName,
                                                userBadgeNumber = userBadgeNumber,
                                                userSignatureUrl = profileData.signatureUrl
                                            )
                                        },
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, outlineColor)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Decline",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = secondaryTextColor
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .alpha(if (canPress) 1.0f else 0.38f)
                                        .clickable(enabled = canPress) {
                                            inboxViewModel.performAction(
                                                item = item,
                                                actionType = "accepted",
                                                userName = userFullName,
                                                userBadgeNumber = userBadgeNumber,
                                                userSignatureUrl = profileData.signatureUrl
                                            )
                                        },
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, if (canPress) tealAccent else outlineColor)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Accept",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (canPress) tealAccent else secondaryTextColor
                                        )
                                    }
                                }
                            }
                        }
                        "approve" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .alpha(if (canPress) 1.0f else 0.38f)
                                        .clickable(enabled = canPress) {
                                            inboxViewModel.performAction(
                                                item = item,
                                                actionType = "rejected",
                                                userName = userFullName,
                                                userBadgeNumber = userBadgeNumber,
                                                userSignatureUrl = profileData.signatureUrl
                                            )
                                        },
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, outlineColor)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Reject",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = secondaryTextColor
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .alpha(if (canPress) 1.0f else 0.38f)
                                        .clickable(enabled = canPress) {
                                            inboxViewModel.performAction(
                                                item = item,
                                                actionType = "approved",
                                                userName = userFullName,
                                                userBadgeNumber = userBadgeNumber,
                                                userSignatureUrl = profileData.signatureUrl
                                            )
                                        },
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, if (canPress) tealAccent else outlineColor)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Approve",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (canPress) tealAccent else secondaryTextColor
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            // "sign"
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .alpha(if (canPress) 1.0f else 0.38f)
                                    .clickable(enabled = canPress) {
                                        inboxViewModel.performAction(
                                            item = item,
                                            actionType = "signed",
                                            userName = userFullName,
                                            userBadgeNumber = userBadgeNumber,
                                            userSignatureUrl = profileData.signatureUrl
                                        )
                                    },
                                shape = RoundedCornerShape(24.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, if (canPress) tealAccent else outlineColor)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Sign",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (canPress) tealAccent else secondaryTextColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSignatureSheet) {
        SignatureBottomSheet(
            existingSignatureUrl = profileData.signatureUrl,
            onDismiss = { showSignatureSheet = false },
            onSignatureSaved = { sigDataUrl, _, _, _ ->
                showSignatureSheet = false
                profileViewModel.updateSignatureUrl(sigDataUrl)
            }
        )
    }

    if (showExportSheet) {
        ExportPdfBottomSheet(
            fileName = exportFileName,
            item = item,
            context = context,
            onDismiss = { showExportSheet = false },
            onSavedToDownloads = {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Saved to Downloads")
                }
            },
            onSaveFailed = {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Couldn't save the PDF")
                }
            }
        )
    }
}

@Composable
fun StampCard(
    stamp: InboxStamp,
    cardBgColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = tealAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stamp.actionText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tealAccent
                )
            }

            if (stamp.signatureUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val sigBitmap = remember(stamp.signatureUrl) { parseSignatureImageBitmap(stamp.signatureUrl) }
                val sigTintColor = if (isDark) Color(0xFFFAF9F5) else Color(0xFF1C1B1F)

                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(66.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (sigBitmap != null) {
                        Image(
                            bitmap = sigBitmap,
                            contentDescription = "Signature",
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(sigTintColor, BlendMode.SrcIn),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stamp.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            if (stamp.badgeNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stamp.badgeNumber,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stamp.timestampText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportPdfBottomSheet(
    fileName: String,
    item: InboxEntity,
    context: Context,
    onDismiss: () -> Unit,
    onSavedToDownloads: () -> Unit,
    onSaveFailed: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline

    val dateStr = remember(item.timestamp) {
        val dt = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault())
        dt.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }

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
                text = "Export as PDF",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // File Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        fontSize = 14.sp,
                        color = primaryTextColor,
                        maxLines = 1
                    )
                    Text(
                        text = dateStr,
                        fontSize = 12.sp,
                        color = secondaryTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Save to device
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable {
                        onDismiss()
                        val pdfFile = generateInboxPdf(context, item, fileName)
                        if (pdfFile != null && saveToDownloads(context, pdfFile, fileName)) {
                            onSavedToDownloads()
                        } else {
                            onSaveFailed()
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Save to device",
                    fontSize = 16.sp,
                    color = primaryTextColor
                )
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable {
                        onDismiss()
                        val pdfFile = generateInboxPdf(context, item, fileName)
                        if (pdfFile != null) {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                            } catch (e: Exception) {}
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Share",
                    fontSize = 16.sp,
                    color = primaryTextColor
                )
            }
        }
    }
}

/**
 * Saves the PDF to the Downloads folder. On Android 10 and newer this goes through MediaStore,
 * which needs no storage permission. Older versions write the file directly and need
 * WRITE_EXTERNAL_STORAGE (maxSdkVersion 28) in the manifest.
 */
fun saveToDownloads(context: Context, source: File, fileName: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            val out = context.contentResolver.openOutputStream(uri) ?: return false
            out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
            true
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            source.copyTo(File(dir, fileName), overwrite = true)
            true
        }
    } catch (e: Exception) {
        false
    }
}

private sealed class PdfItem(val height: Float) {
    class Line(val text: String, val size: Float, val bold: Boolean, val color: Int) : PdfItem(size * 1.4f)
    class Gap(h: Float) : PdfItem(h)
    class Img(val bitmap: android.graphics.Bitmap, val w: Float, val h: Float) : PdfItem(h + 4f)
}

/** A4 PDF with wrapped text, page breaks, "Page n of N" and the signature images. */
fun generateInboxPdf(context: Context, item: InboxEntity, fileName: String): File? {
    return try {
        val pageW = 595
        val pageH = 842
        val margin = 40f
        val top = 50f
        val bottomLimit = pageH - 60f
        val contentW = (pageW - 2 * margin).toInt()

        val grey = android.graphics.Color.GRAY
        val black = android.graphics.Color.BLACK
        val teal = android.graphics.Color.parseColor("#3A4694")

        val items = mutableListOf<PdfItem>()

        fun addText(text: String, size: Float, bold: Boolean, color: Int) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            text.split("\n").forEach { paragraph ->
                if (paragraph.isBlank()) {
                    items.add(PdfItem.Gap(size * 1.4f))
                } else {
                    val layout = StaticLayout.Builder.obtain(paragraph, 0, paragraph.length, tp, contentW).build()
                    for (i in 0 until layout.lineCount) {
                        val line = paragraph.substring(layout.getLineStart(i), layout.getLineEnd(i)).trimEnd()
                        items.add(PdfItem.Line(line, size, bold, color))
                    }
                }
            }
        }

        val dateText = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))

        addText(item.senderName, 12f, true, black)
        addText("${item.departmentOrRequester} \u00B7 $dateText", 10f, false, grey)
        items.add(PdfItem.Gap(10f))
        addText(item.subject, 14f, true, black)
        items.add(PdfItem.Gap(8f))
        addText(item.body, 10f, false, black)
        items.add(PdfItem.Gap(20f))

        if (item.stamps.isNotEmpty()) {
            addText("Approvals ${item.stamps.size}", 11f, true, black)
            items.add(PdfItem.Gap(6f))
            for (stamp in item.stamps) {
                addText(stamp.actionText, 10f, true, teal)
                if (stamp.signatureUrl.isNotBlank()) {
                    val bmp = try { parseSignatureImageBitmap(stamp.signatureUrl)?.asAndroidBitmap() } catch (e: Exception) { null }
                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                        val scale = minOf(130f / bmp.width, 66f / bmp.height)
                        items.add(PdfItem.Img(bmp, bmp.width * scale, bmp.height * scale))
                    }
                }
                addText(stamp.name, 10f, false, black)
                if (stamp.badgeNumber.isNotBlank()) addText(stamp.badgeNumber, 10f, false, black)
                addText(stamp.timestampText, 10f, false, grey)
                items.add(PdfItem.Gap(14f))
            }
        }

        // Split the items into pages
        val pages = mutableListOf<MutableList<PdfItem>>(mutableListOf())
        var y = top
        for (it in items) {
            val current = pages.last()
            if (y + it.height > bottomLimit && current.isNotEmpty()) {
                pages.add(mutableListOf())
                y = top
            }
            // a gap at the top of a fresh page would only waste space
            if (it is PdfItem.Gap && pages.last().isEmpty()) continue
            pages.last().add(it)
            y += it.height
        }

        val pdfDocument = PdfDocument()
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(black, android.graphics.PorterDuff.Mode.SRC_IN)
        }

        pages.forEachIndexed { index, pageItems ->
            val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create())
            val canvas: Canvas = page.canvas
            var py = top
            for (pi in pageItems) {
                when (pi) {
                    is PdfItem.Line -> {
                        linePaint.textSize = pi.size
                        linePaint.typeface = Typeface.create(Typeface.DEFAULT, if (pi.bold) Typeface.BOLD else Typeface.NORMAL)
                        linePaint.color = pi.color
                        canvas.drawText(pi.text, margin, py + pi.size, linePaint)
                    }
                    is PdfItem.Img -> {
                        canvas.drawBitmap(
                            pi.bitmap, null,
                            android.graphics.RectF(margin, py, margin + pi.w, py + pi.h), imgPaint
                        )
                    }
                    is PdfItem.Gap -> {}
                }
                py += pi.height
            }

            linePaint.color = grey
            linePaint.textSize = 9f
            linePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("diBadge", margin, 810f, linePaint)
            canvas.drawText("Page ${index + 1} of ${pages.size}", 500f, 810f, linePaint)
            pdfDocument.finishPage(page)
        }

        val tempFile = File(context.cacheDir, fileName)
        FileOutputStream(tempFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        tempFile
    } catch (e: Exception) {
        null
    }
}