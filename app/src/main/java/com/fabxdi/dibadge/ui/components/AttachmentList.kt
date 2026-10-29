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
        val isDark = MaterialTheme.colorScheme.onBackground.red > 0.5f

        attachedFiles.forEachIndexed { index, uri ->
            val fileName = FilePickerUtils.getFileName(context, uri).lowercase()
            val ext = fileName.substringAfterLast('.', "")

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
                DocumentTypeTile(extension = ext, isDark = isDark, widthDp = 24, heightDp = 29)
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = FilePickerUtils.getFileName(context, uri),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
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
