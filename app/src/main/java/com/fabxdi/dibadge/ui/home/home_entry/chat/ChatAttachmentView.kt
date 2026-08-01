package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.fabxdi.dibadge.util.AudioPlayer
import com.fabxdi.dibadge.util.FilePickerUtils
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatAttachmentView(
    context: Context,
    uri: Uri,
    isSentByMe: Boolean,
    player: AudioPlayer,
    modifier: Modifier = Modifier,
    timestamp: String? = null,
    duration: Long? = null, // Duration in seconds
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val fileName = FilePickerUtils.getFileName(context, uri)
    val mimeType = try {
        context.contentResolver.getType(uri)
    } catch (e: Exception) {
        null
    }
    val isImage = mimeType?.startsWith("image/") == true ||
            fileName.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") }
    val isVideo = mimeType?.startsWith("video/") == true || fileName.lowercase().endsWith(".mp4")
    val isAudio = mimeType?.startsWith("audio/") == true || fileName.lowercase().endsWith(".m4a")

    if (isImage || isVideo) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = modifier
                .combinedClickable(
                    onClick = {
                        if (onClick != null) {
                            onClick()
                        } else {
                            FilePickerUtils.openFile(context, uri)
                        }
                    },
                    onLongClick = onLongClick
                ),
            color = Color.Black.copy(alpha = 0.05f)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isVideo) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                
                if (timestamp != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = timestamp,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    } else if (isAudio) {
        var isPlaying by remember { mutableStateOf(false) }
        var isPaused by remember { mutableStateOf(false) }
        var progress by remember { mutableFloatStateOf(0f) }
        var currentPosition by remember { mutableLongStateOf(0L) }

        val displayDuration = if (isPlaying || isPaused) currentPosition else (duration ?: 0L)

        LaunchedEffect(isPlaying, isPaused) {
            if (isPlaying && !isPaused) {
                while (isPlaying && !isPaused) {
                    if (player.isPlaying()) {
                        val total = player.getDuration()
                        val current = player.getCurrentPosition()
                        if (total > 0) {
                            progress = current.toFloat() / total
                            currentPosition = (current / 1000).toLong()
                        }
                    } else {
                        isPlaying = false
                        isPaused = false
                        progress = 0f
                        currentPosition = 0
                    }
                    delay(50)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            modifier = modifier
                .combinedClickable(
                    onClick = {
                        if (isPlaying) {
                            if (isPaused) {
                                player.resume()
                                isPaused = false
                            } else {
                                player.pause()
                                isPaused = true
                            }
                        } else {
                            player.playFile(uri) {
                                isPlaying = false
                                isPaused = false
                                progress = 0f
                                currentPosition = 0
                            }
                            isPlaying = true
                            isPaused = false
                        }
                    },
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                val contentColor = if (isSentByMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = contentColor.copy(alpha = 0.1f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Box(
                        modifier = Modifier.weight(1f).height(24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                            val centerY = size.height / 2
                            drawLine(
                                color = contentColor.copy(alpha = 0.2f),
                                start = Offset(0f, centerY),
                                end = Offset(size.width, centerY),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = contentColor,
                                start = Offset(0f, centerY),
                                end = Offset(size.width * progress, centerY),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawCircle(
                                color = contentColor,
                                radius = 5.dp.toPx(),
                                center = Offset(size.width * progress, centerY)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", displayDuration / 60, displayDuration % 60),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = contentColor
                    )
                }
                
                if (timestamp != null) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.End).padding(top = 0.dp, bottom = 2.dp)
                    )
                }
            }
        }
    } else {
        val icon = FilePickerUtils.getFileIcon(context, uri)
        val fileColor = remember(fileName) {
            val name = fileName.lowercase()
            when {
                name.endsWith(".pdf") -> Color(0xFFF44336) // Red
                name.endsWith(".xls") || name.endsWith(".xlsx") -> Color(0xFF4CAF50) // Green
                name.endsWith(".doc") || name.endsWith(".docx") -> Color(0xFF2196F3) // Blue
                name.endsWith(".ppt") || name.endsWith(".pptx") -> Color(0xFFFF9800) // Orange
                name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> Color(0xFF9C27B0) // Purple
                else -> Color(0xFF00A884) // Default Teallish
            }
        }
        
        val contentColor = if (isSentByMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        
        Box(
            modifier = modifier
                .combinedClickable(
                    onClick = {
                        if (onClick != null) {
                            onClick()
                        } else {
                            FilePickerUtils.openFile(context, uri)
                        }
                    },
                    onLongClick = onLongClick
                )
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = fileColor,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
