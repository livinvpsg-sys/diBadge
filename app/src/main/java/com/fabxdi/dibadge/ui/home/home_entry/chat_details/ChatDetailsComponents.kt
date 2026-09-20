package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.viewmodel.GroupFolderFile
import com.fabxdi.dibadge.viewmodel.GroupMediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DetailActionItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
    }
}

fun formatTimestampToDateHeader(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) {
            date.format(DateTimeFormatter.ofPattern("MMMM d"))
        } else {
            date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        }
    }
}

fun getDocExtensionColor(fileName: String, url: String): Color {
    val name = fileName.ifBlank { url }.lowercase()
    return when {
        name.contains(".pdf") -> Color(0xFFE53935) // Red for PDF
        name.contains(".doc") || name.contains(".docx") -> Color(0xFF1E88E5) // Blue for Word
        name.contains(".xls") || name.contains(".xlsx") || name.contains(".csv") -> Color(0xFF43A047) // Green for Excel
        name.contains(".ppt") || name.contains(".pptx") -> Color(0xFFFB8C00) // Orange for PowerPoint
        name.contains(".zip") || name.contains(".rar") || name.contains(".7z") || name.contains(".tar") -> Color(0xFF8E24AA) // Purple for Archive
        name.contains(".txt") || name.contains(".json") || name.contains(".kt") || name.contains(".java") -> Color(0xFF00897B) // Teal for Text
        else -> Color(0xFF546E7A) // Default Blue-Grey
    }
}

fun shareMediaItem(context: Context, url: String, mediaType: String) {
    if (url.isBlank()) return
    try {
        val uri = Uri.parse(url)
        val isWebUrl = url.startsWith("http://") || url.startsWith("https://")

        val intent = Intent(Intent.ACTION_SEND).apply {
            if (isWebUrl) {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            } else {
                val mimeType = try {
                    context.contentResolver.getType(uri)
                } catch (e: Exception) {
                    null
                } ?: when (mediaType) {
                    "video" -> "video/*"
                    "document" -> "*/*"
                    else -> "image/*"
                }
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Unable to share media", Toast.LENGTH_SHORT).show()
    }
}

fun shareMultipleMediaItems(context: Context, items: List<GroupMediaItem>) {
    if (items.isEmpty()) return
    try {
        if (items.size == 1) {
            val item = items.first()
            shareMediaItem(context, item.url, item.mediaType)
        } else {
            val uris = ArrayList<Uri>()
            val textUrls = StringBuilder()

            for (item in items) {
                if (item.url.startsWith("http://") || item.url.startsWith("https://")) {
                    if (textUrls.isNotEmpty()) textUrls.append("\n")
                    textUrls.append(item.url)
                } else {
                    uris.add(Uri.parse(item.url))
                }
            }

            val intent = if (uris.size > 1) {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    if (textUrls.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, textUrls.toString())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    if (textUrls.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, textUrls.toString())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, textUrls.toString())
                }
            }

            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Unable to share media", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentTileItem(
    file: GroupFolderFile,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val docColor = remember(file.name, file.downloadUrl) {
        getDocExtensionColor(file.name, file.downloadUrl)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        Box(modifier = Modifier.size(72.dp)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = docColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = file.name,
                        tint = docColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(20.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = file.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
