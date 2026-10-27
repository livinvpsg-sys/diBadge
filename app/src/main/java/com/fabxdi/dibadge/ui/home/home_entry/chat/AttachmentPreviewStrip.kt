package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.util.FilePickerUtils
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentPreviewStrip(
    attachedUris: List<Uri>,
    durationsMap: Map<String, Long>,
    selectedUris: Set<Uri> = emptySet(),
    isDark: Boolean,
    pageBgColor: Color,
    cardColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    errorColor: Color = Color(0xFFE8837A),
    onToggleSelect: (Uri) -> Unit = {},
    onExitSelectionMode: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onRemoveUri: (Uri) -> Unit = {},
    onAddMoreClick: (() -> Unit)? = null,
    onImageClick: (List<Uri>, Int) -> Unit = { _, _ -> },
    onFileClick: (Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (attachedUris.isEmpty()) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isSelectionMode = selectedUris.isNotEmpty()

    val activeCategoryName = remember(attachedUris) {
        if (attachedUris.isNotEmpty()) {
            val uri = attachedUris.first()
            val fileName = FilePickerUtils.getFileName(context, uri).lowercase()
            val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
            val ext = fileName.substringAfterLast('.', "")

            when {
                mimeType?.startsWith("image/") == true || ext in listOf("jpg", "jpeg", "png") -> "photo"
                mimeType?.startsWith("video/") == true || ext == "mp4" -> "video"
                mimeType?.startsWith("audio/") == true || ext in listOf("m4a", "mp3") -> "audio file"
                else -> "file"
            }
        } else "file"
    }

    val article = if (activeCategoryName.startsWith("a")) "an" else "a"
    val hintText = "Long press $article $activeCategoryName to remove it"

    val prefs = remember(context) { context.getSharedPreferences("dibadge_prefs", Context.MODE_PRIVATE) }
    var hasSeenHint by remember { mutableStateOf(prefs.getBoolean("has_seen_strip_longpress_hint", false)) }

    LaunchedEffect(attachedUris, isSelectionMode) {
        if (!hasSeenHint && attachedUris.isNotEmpty() && !isSelectionMode) {
            delay(4000)
            hasSeenHint = true
            prefs.edit().putBoolean("has_seen_strip_longpress_hint", true).apply()
        }
    }

    val markHintSeen = {
        if (!hasSeenHint) {
            hasSeenHint = true
            prefs.edit().putBoolean("has_seen_strip_longpress_hint", true).apply()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(pageBgColor)
    ) {
        // One-time hint line above strip (8dp above thumbnails)
        if (!hasSeenHint && !isSelectionMode) {
            Text(
                text = hintText,
                fontSize = 12.sp,
                color = secondaryTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
            )
        }

        // Selection Action Row above strip (Close icon & "{n} selected" only)
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close Icon (22dp)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onExitSelectionMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = primaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${selectedUris.size} selected",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )
            }
        }

        // Horizontal Preview Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(attachedUris) { index, uri ->
                val fileName = remember(uri) { FilePickerUtils.getFileName(context, uri) }
                val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
                val ext = fileName.substringAfterLast('.', "").lowercase()

                val isImage = mimeType?.startsWith("image/") == true || ext in listOf("jpg", "jpeg", "png")
                val isVideo = mimeType?.startsWith("video/") == true || ext == "mp4"
                val isAudio = mimeType?.startsWith("audio/") == true || ext in listOf("m4a", "mp3")

                val isSelected = selectedUris.contains(uri)
                val borderStroke = if (isSelected) BorderStroke(2.dp, primaryTextColor) else BorderStroke(1.dp, outlineColor)

                if (isImage || isVideo) {
                    val durationSec = durationsMap[uri.toString()] ?: 0L

                    Box(modifier = Modifier.size(72.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardColor,
                            border = borderStroke,
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleSelect(uri)
                                        } else {
                                            onImageClick(attachedUris, index)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleSelect(uri)
                                    }
                                )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Image preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                )

                                if (isVideo) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 12.dp),
                                        modifier = Modifier.align(Alignment.BottomStart)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Video",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            if (durationSec > 0) {
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = String.format(Locale.getDefault(), "%02d:%02d", durationSec / 60, durationSec % 60),
                                                    fontSize = 11.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                // 28% Black dim overlay & 20dp check circle when selected
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.28f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(20.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(20.dp),
                                            shape = CircleShape,
                                            color = primaryTextColor
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = pageBgColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (isAudio) {
                    // Audio Item (168dp x 72dp)
                    val durationSec = durationsMap[uri.toString()] ?: 0L
                    val durationStr = if (durationSec > 0) {
                        String.format(Locale.getDefault(), "%d:%02d", durationSec / 60, durationSec % 60)
                    } else "0:42"

                    Box(modifier = Modifier.size(width = 168.dp, height = 72.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardColor,
                            border = borderStroke,
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleSelect(uri)
                                        } else {
                                            onFileClick(uri)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleSelect(uri)
                                    }
                                )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape,
                                        color = secondaryTextColor.copy(alpha = 0.12f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play audio",
                                                tint = primaryTextColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fileName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = primaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = durationStr,
                                            fontSize = 11.sp,
                                            color = secondaryTextColor
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.28f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(20.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(20.dp),
                                            shape = CircleShape,
                                            color = primaryTextColor
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = pageBgColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Document Item (168dp x 72dp)
                    val fileSizeStr = remember(uri) {
                        val bytes = FilePickerUtils.getFileSizeBytes(context, uri)
                        FilePickerUtils.formatFileSize(bytes)
                    }

                    Box(modifier = Modifier.size(width = 168.dp, height = 72.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardColor,
                            border = borderStroke,
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleSelect(uri)
                                        } else {
                                            onFileClick(uri)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleSelect(uri)
                                    }
                                )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(width = 36.dp, height = 44.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = secondaryTextColor.copy(alpha = 0.12f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val tileText = when (ext) {
                                                "pdf" -> "PDF"
                                                "doc", "docx" -> "DOC"
                                                "xls", "xlsx", "csv" -> "XLS"
                                                "ppt", "pptx" -> "PPT"
                                                else -> "FILE"
                                            }
                                            Text(
                                                text = tileText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = secondaryTextColor
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fileName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = primaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = fileSizeStr,
                                            fontSize = 11.sp,
                                            color = secondaryTextColor
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.28f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(20.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(20.dp),
                                            shape = CircleShape,
                                            color = primaryTextColor
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = pageBgColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
