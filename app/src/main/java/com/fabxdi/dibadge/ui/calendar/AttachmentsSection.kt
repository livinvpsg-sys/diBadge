package com.fabxdi.dibadge.ui.calendar

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.io.File

data class AttachmentFile(
    val uriString: String,
    val name: String,
    val sizeBytes: Long,
    val isPdf: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentsSection(
    attachedFiles: List<AttachmentFile>,
    onAddFile: (AttachmentFile) -> Unit,
    onRemoveFile: (AttachmentFile) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    var showAddSheet by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val pageBgColor = colors.background
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent

    // Pick Photo Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processSelectedUri(context, uri, false, onAddFile, onShowSnackbar)
        }
    }

    // Pick Document Launcher (PDF)
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processSelectedUri(context, uri, true, onAddFile, onShowSnackbar)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Attachments (optional)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = secondaryTextColor,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBgColor
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                attachedFiles.forEachIndexed { index, file ->
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
                                    imageVector = if (file.isPdf) Icons.Outlined.Description else Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sizeKb = file.sizeBytes / 1024
                            val sizeMbStr = if (sizeKb > 1024) "${String.format("%.1f", sizeKb / 1024.0)} MB" else "$sizeKb KB"
                            Text(
                                text = "${if (file.isPdf) "PDF" else "Photo"} · $sizeMbStr",
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                        }

                        IconButton(
                            onClick = { onRemoveFile(file) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove file",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                }

                if (attachedFiles.size < 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable { showAddSheet = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = tealAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Add attachment",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = tealAccent
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Up to 3 files, 5 MB each. Photos or PDF.",
            fontSize = 12.sp,
            color = secondaryTextColor,
            modifier = Modifier.padding(start = 4.dp)
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
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
                    text = "Add attachment",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 1: Choose photo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            showAddSheet = false
                            photoPickerLauncher.launch("image/*")
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Choose photo",
                        fontSize = 16.sp,
                        color = primaryTextColor
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Option 2: Choose file (PDF)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            showAddSheet = false
                            docPickerLauncher.launch("application/pdf")
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Choose file (PDF)",
                        fontSize = 16.sp,
                        color = primaryTextColor
                    )
                }
            }
        }
    }
}

private fun processSelectedUri(
    context: Context,
    uri: Uri,
    isPdf: Boolean,
    onAddFile: (AttachmentFile) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    var fileName = "File"
    var fileSize = 0L

    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: "File"
                if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
            }
        }
    } catch (e: Exception) {}

    // Check size limit: 5 MB = 5 * 1024 * 1024 bytes
    if (fileSize > 5 * 1024 * 1024) {
        onShowSnackbar("File is larger than 5 MB")
        return
    }

    onAddFile(
        AttachmentFile(
            uriString = uri.toString(),
            name = fileName,
            sizeBytes = fileSize,
            isPdf = isPdf
        )
    )
}
