package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
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
import com.fabxdi.dibadge.ui.components.DocumentTypeTile
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.FilePickerUtils

data class UploadFileConfigState(
    val uri: Uri,
    var name: String,
    var categoryId: String? = null,
    var categoryName: String? = null,
    val id: Long = System.nanoTime()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadToLibrarySheet(
    initialCategoryId: String? = null,
    initialCategoryName: String? = null,
    isDark: Boolean,
    onUploadFiles: (configs: List<UploadFileConfigState>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uploadConfigs = remember { mutableStateListOf<UploadFileConfigState>() }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)

    fun handleSelectedUri(uri: Uri) {
        val mimeType = try { context.contentResolver.getType(uri) ?: "" } catch (e: Exception) { "" }
        val ext = FilePickerUtils.getFileName(context, uri).substringAfterLast('.', "").lowercase()
        val isVideo = mimeType.startsWith("video/") || ext in listOf("mp4", "mov", "avi", "mkv", "3gp", "webm")

        if (isVideo) {
            Toast.makeText(context, "Videos cannot be uploaded to Library", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = FilePickerUtils.getFileName(context, uri).ifBlank { "Document" }
        uploadConfigs.add(
            UploadFileConfigState(
                uri = uri,
                name = fileName,
                categoryId = initialCategoryId,
                categoryName = initialCategoryName
            )
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                handleSelectedUri(uri)
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
                text = "Upload",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Source Option 1: Upload a file
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
                        text = "Upload a file (PDF, images, Office, DWG)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Source Option 2: Upload a photo from the gallery
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { filePickerLauncher.launch("image/*") },
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
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = primaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Upload a photo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selected Files Configuration List
            if (uploadConfigs.isNotEmpty()) {
                Text(
                    text = "Selected files (${uploadConfigs.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uploadConfigs.size, key = { uploadConfigs[it].id }) { idx ->
                        val config = uploadConfigs[idx]
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
                                    val ext = config.name.substringAfterLast('.', "pdf").lowercase()
                                    DocumentTypeTile(extension = ext, isDark = isDark, widthDp = 24, heightDp = 30)

                                    Spacer(modifier = Modifier.width(10.dp))

                                    BasicTextField(
                                        value = config.name,
                                        onValueChange = { newName ->
                                            uploadConfigs[idx] = config.copy(name = newName)
                                        },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryTextColor),
                                        cursorBrush = SolidColor(primaryTextColor),
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { uploadConfigs.removeAt(idx) },
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
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons: Cancel & Upload
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }

                Spacer(modifier = Modifier.width(12.dp))

                val canUpload = uploadConfigs.isNotEmpty() && uploadConfigs.all { it.name.isNotBlank() }
                TextButton(
                    enabled = canUpload,
                    onClick = {
                        if (canUpload) {
                            onUploadFiles(uploadConfigs.toList())
                        }
                    }
                ) {
                    Text(
                        text = "Upload",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canUpload) tealAccent else dimTextColor
                    )
                }
            }
        }
    }
}
