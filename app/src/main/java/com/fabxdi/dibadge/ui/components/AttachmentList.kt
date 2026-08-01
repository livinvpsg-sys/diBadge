package com.fabxdi.dibadge.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.AudioPlayer

@Composable
fun AttachmentList(
    context: Context,
    attachedFiles: List<Uri>,
    onRemove: (Int) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false,
    showAddButton: Boolean = true,
    player: AudioPlayer? = null
) {
    Column(modifier = modifier) {
        attachedFiles.forEachIndexed { index, uri ->
            val fileName = FilePickerUtils.getFileName(context, uri).lowercase()
            val fileColor = when {
                fileName.endsWith(".pdf") -> Color(0xFFF44336)
                fileName.endsWith(".xls") || fileName.endsWith(".xlsx") -> Color(0xFF4CAF50)
                fileName.endsWith(".doc") || fileName.endsWith(".docx") -> Color(0xFF2196F3)
                fileName.endsWith(".ppt") || fileName.endsWith(".pptx") -> Color(0xFFFF9800)
                fileName.endsWith(".zip") || fileName.endsWith(".rar") || fileName.endsWith(".7z") -> Color(0xFF9C27B0)
                else -> Color(0xFF00A884)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable {
                        val isAudio = context.contentResolver.getType(uri)?.startsWith("audio/") == true || 
                                     uri.path?.lowercase()?.endsWith(".m4a") == true
                        if (isAudio && player != null) {
                            player.playFile(uri) {}
                        } else {
                            FilePickerUtils.openFile(context, uri)
                        }
                    }
            ) {
                Icon(
                    imageVector = FilePickerUtils.getFileIcon(context, uri),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = fileColor
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = FilePickerUtils.getFileName(context, uri),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                
                if (!isReadOnly) {
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { onRemove(index) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        if (!isReadOnly && showAddButton && attachedFiles.size < 3) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .clickable { onAddClick() }
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Attachment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
