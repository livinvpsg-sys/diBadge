package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.fabxdi.dibadge.ui.components.DocumentTypeTile
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
                        if (onClick != null) {
                            onClick()
                        }
                    },
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                val isDark = MaterialTheme.colorScheme.onBackground.red > 0.5f
                val audioColor = if (isDark) Color(0xFFE87DA3) else Color(0xFFB0446A)
                val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
                val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
                val pageBgColor = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF)
                val dimColor = secondaryTextColor.copy(alpha = 0.3f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = primaryTextColor,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
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
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = pageBgColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(28.dp)
                            .clickable {
                                // Basic seek placeholder for inline
                            }
                    ) {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            for (i in 0 until 15) {
                                val isPlayed = (i / 15f) <= progress
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(if (i % 2 == 0) 28.dp else 16.dp)
                                        .background(if (isPlayed) audioColor else dimColor, RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeStr = if (isPlaying || isPaused) {
                        String.format(Locale.getDefault(), "%d:%02d", currentPosition / 60, currentPosition % 60)
                    } else {
                        String.format(Locale.getDefault(), "%d:%02d", (duration ?: 0L) / 60, (duration ?: 0L) % 60)
                    }
                    Text(
                        text = "$timeStr · $fileName",
                        fontSize = 11.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (timestamp != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timestamp,
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }
        }
    } else {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val isDark = MaterialTheme.colorScheme.onBackground.red > 0.5f
        val contentColor = MaterialTheme.colorScheme.onSurface
        
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
                DocumentTypeTile(extension = ext, isDark = isDark)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
