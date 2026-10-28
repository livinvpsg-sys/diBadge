package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.util.FilePickerUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppDocInfoScreen(
    fileUri: Uri,
    fileName: String,
    isPreSend: Boolean = false,
    isDark: Boolean,
    onClose: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)

    val pageBgColor = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF)
    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)

    val ext = remember(fileName) { fileName.substringAfterLast('.', "").uppercase() }
    val sizeBytes = remember(fileUri) { FilePickerUtils.getFileSizeBytes(context, fileUri) }
    val sizeStr = remember(sizeBytes) { FilePickerUtils.formatFileSize(sizeBytes) }

    // SAF Document Creator for Save to device
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        if (destUri != null) {
            try {
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(context, "Saved to device", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not save file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = pageBgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(cardColor)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Arrow
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // File Name
                Text(
                    text = fileName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Save to device Icon
                    IconButton(
                        onClick = { createDocLauncher.launch(fileName) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save to device",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Delete Icon (sender pre-send view)
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = errorColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Center Info Card
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // File Tile
                Surface(
                    modifier = Modifier.size(width = 56.dp, height = 72.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = cardFillColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (ext.length <= 4) ext else "FILE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = fileName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$ext · $sizeStr",
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isPreSend) "Preview is ready after sending" else "Preview not available",
                    fontSize = 14.sp,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Save to device Button
                Surface(
                    modifier = Modifier
                        .clickable { createDocLauncher.launch(fileName) },
                    shape = RoundedCornerShape(20.dp),
                    color = cardFillColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = primaryTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save to device",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                    }
                }
            }
        }
    }
}
