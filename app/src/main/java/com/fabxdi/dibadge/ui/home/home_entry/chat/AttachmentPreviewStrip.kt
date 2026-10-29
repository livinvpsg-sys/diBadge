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
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.fabxdi.dibadge.ui.components.DocumentTypeTile
import com.fabxdi.dibadge.ui.components.WaveformIcon
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
    onAudioClick: (List<Uri>, Int) -> Unit = { _, _ -> },
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

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode && !hasSeenHint) {
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
                                    // Centred 28dp round play badge
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.50f),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .align(Alignment.Center)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play Video",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    // Duration chip at bottom right (4dp from edges)
                                    val durationStr = if (durationSec > 0) {
                                        String.format(Locale.getDefault(), "%d:%02d", durationSec / 60, durationSec % 60)
                                    } else "0:42"

                                    Surface(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = durationStr,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
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
                    // Audio Item (72dp x 72dp Square)
                    val durationSec = durationsMap[uri.toString()] ?: 0L
                    val durationStr = if (durationSec > 0) {
                        String.format(Locale.getDefault(), "%d:%02d", durationSec / 60, durationSec % 60)
                    } else ""
                    val audioColor = if (isDark) Color(0xFFE87DA3) else Color(0xFFB0446A)

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
                                            onAudioClick(attachedUris, index)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleSelect(uri)
                                    }
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 7.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    modifier = Modifier.size(30.dp),
                                    shape = CircleShape,
                                    color = audioColor.copy(alpha = 0.18f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        WaveformIcon(
                                            color = audioColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = fileName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = durationStr.ifBlank { "0:00" },
                                        fontSize = 10.sp,
                                        color = secondaryTextColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.28f))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(18.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(18.dp),
                                    shape = CircleShape,
                                    color = primaryTextColor
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = pageBgColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Document Item (72dp x 72dp Square) - Centered 28x34dp Page Icon & 1-line Name
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
                                            onAudioClick(attachedUris, index)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleSelect(uri)
                                    }
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 7.dp, bottom = 6.dp, start = 5.dp, end = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                DocumentTypeTile(
                                    extension = ext,
                                    isDark = isDark,
                                    widthDp = 28,
                                    heightDp = 34
                                )

                                Text(
                                    text = fileName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.28f))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(18.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(18.dp),
                                    shape = CircleShape,
                                    color = primaryTextColor
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = pageBgColor,
                                            modifier = Modifier.size(12.dp)
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
