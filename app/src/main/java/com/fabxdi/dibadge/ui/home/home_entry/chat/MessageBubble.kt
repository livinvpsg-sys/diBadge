package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.util.AudioPlayer
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.util.FilePickerUtils
import java.time.format.DateTimeFormatter

private fun isEmoji(codePoint: Int): Boolean {
    return (codePoint in 0x1F600..0x1F64F) || // Emoticons
           (codePoint in 0x1F300..0x1F5FF) || // Misc Symbols and Pictographs
           (codePoint in 0x1F680..0x1F6FF) || // Transport and Map
           (codePoint in 0x2600..0x26FF)   || // Misc Symbols
           (codePoint in 0x2700..0x27BF)   || // Dingbats
           (codePoint in 0xFE00..0xFE0F)   || // Variation Selectors
           (codePoint in 0x1F900..0x1F9FF) || // Supplemental Symbols and Pictographs
           (codePoint in 0x1F1E6..0x1F1FF) || // Flags
           (codePoint in 0x1F004..0x1F0CF) || // Mahjong and Domino Tiles
           (codePoint in 0x1F01C..0x1F02B) || // Playing Cards
           (codePoint in 0x231A..0x231B)   || // Watch & Hourglass
           (codePoint in 0x23E9..0x23F3)   || // Multimedia
           (codePoint in 0x23F8..0x23FA)   || // Multimedia buttons
           (codePoint in 0x25AA..0x25AB)   || // Black/White small square
           (codePoint in 0x25FB..0x25FE)   || // Squares
           (codePoint in 0x2B05..0x2B07)   || // Arrows
           (codePoint in 0x2B1B..0x2B1C)   || // Squares
           (codePoint == 0x2B50)           || // Star
           (codePoint == 0x2B55)           || // Circle
           (codePoint == 0x3030)           || // Wavy dash
           (codePoint == 0x303D)           || // Part alternation mark
           (codePoint == 0x3297)           || // Registered designation
           (codePoint == 0x3299)              // Secret designation
}

private fun isOnlyEmojis(text: String): Boolean {
    if (text.isBlank()) return false
    var i = 0
    while (i < text.length) {
        val codePoint = text.codePointAt(i)
        if (!isEmoji(codePoint) && !Character.isWhitespace(codePoint)) {
            return false
        }
        i += Character.charCount(codePoint)
    }
    return true
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    player: AudioPlayer,
    isSelected: Boolean,
    isAnySelected: Boolean,
    isHighlighted: Boolean = false,
    onLongClick: (String?) -> Unit,
    onClick: () -> Unit,
    onReplyClick: (String, String?) -> Unit,
    onMediaClick: (List<Uri>, Int) -> Unit,
    onSwipeToReply: (String?) -> Unit = {}
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a") }
    val context = LocalContext.current
    val alignment = if (message.isSentByMe) Alignment.End else Alignment.Start

    val isEmojiOnly = remember(message.text) {
        isOnlyEmojis(message.text) && message.attachments.isEmpty() && message.replyingTo == null
    }

    var offsetX by remember { mutableStateOf(0f) }
    val swipeThreshold = 150f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0 || offsetX > 0) {
                            offsetX = (offsetX + dragAmount).coerceIn(0f, swipeThreshold + 50f)
                        }
                    },
                    onDragEnd = {
                        if (offsetX >= swipeThreshold) {
                            onSwipeToReply(null)
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f }
                )
            }
    ) {
        if (offsetX > 20f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = (offsetX / swipeThreshold).coerceIn(0f, 1f)),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.toInt(), 0) }
                .background(
                    when {
                        isSelected -> Color(0xFF00A884).copy(alpha = 0.2f)
                        isHighlighted -> Color.White.copy(alpha = 0.15f)
                        else -> Color.Transparent
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { onLongClick(null) }
                )
                .padding(vertical = 4.dp, horizontal = 12.dp),
            horizontalAlignment = alignment
        ) {
            if (!message.isSentByMe) {
                val nameColor = UserColorUtils.getColorForName(message.senderName)
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold, 
                        fontSize = 11.sp
                    ),
                    color = nameColor,
                    modifier = Modifier.padding(start = 40.dp, bottom = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.isSentByMe) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (!message.isSentByMe) {
                    AvatarIcon(message.senderName)
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (isEmojiOnly) {
                    EmojiMessage(message.text, message.timestamp.format(timeFormatter).lowercase(), alignment)
                } else {
                    val bubbleColor = if (message.isSentByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    val contentColor = if (message.isSentByMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    
                    Surface(
                        color = bubbleColor,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isSentByMe) 16.dp else 4.dp,
                            bottomEnd = if (message.isSentByMe) 4.dp else 16.dp
                        ),
                        modifier = Modifier.widthIn(min = 40.dp, max = 280.dp)
                    ) {
                        Column {
                            if (message.replyingTo != null) {
                                ReplySection(message, onReplyClick, contentColor)
                            }

                            AttachmentAndTextSection(message, player, isAnySelected, onLongClick, onMediaClick, timeFormatter, contentColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarIcon(senderName: String) {
    val avatarColor = UserColorUtils.getColorForName(senderName)
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = avatarColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = senderName.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = Color.White
            )
        }
    }
}

@Composable
private fun EmojiMessage(text: String, time: String, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment) {
        val emojiFontSize = when (text.codePointCount(0, text.length)) {
            1 -> 48.sp
            2 -> 40.sp
            3 -> 32.sp
            else -> 24.sp
        }
        Text(text = text, fontSize = emojiFontSize, modifier = Modifier.padding(4.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ReplySection(message: ChatMessage, onReplyClick: (String, String?) -> Unit, contentColor: Color) {
    val context = LocalContext.current
    val replyingTo = message.replyingTo!!
    val replyColor = UserColorUtils.getColorForName(replyingTo.senderName)
    Surface(
        color = contentColor.copy(alpha = 0.1f),
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onReplyClick(replyingTo.id, message.replyingToAttachmentUri) },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).weight(1f)) {
                Text(
                    text = replyingTo.senderName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = replyColor
                )
                Text(
                    text = replyingTo.text.ifBlank {
                        val uri = message.replyingToAttachmentUri ?: replyingTo.attachments.firstOrNull()
                        if (uri != null) {
                            val fileName = FilePickerUtils.getFileName(context, Uri.parse(uri)).lowercase()
                            when {
                                fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".jpeg") -> "Photo"
                                fileName.endsWith(".mp4") -> "Video"
                                fileName.endsWith(".pdf") -> "PDF"
                                else -> "Attachment"
                            }
                        } else "Attachment"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = contentColor.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val replyUriString = message.replyingToAttachmentUri ?: replyingTo.attachments.firstOrNull()
            if (replyUriString != null) {
                val replyUri = Uri.parse(replyUriString)
                val mimeType = try { context.contentResolver.getType(replyUri) } catch (e: Exception) { null }
                if (mimeType?.startsWith("image/") == true || mimeType?.startsWith("video/") == true ||
                    replyUri.path?.lowercase()?.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".mp4") } == true) {
                    AsyncImage(
                        model = replyUri,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentAndTextSection(
    message: ChatMessage,
    player: AudioPlayer,
    isAnySelected: Boolean,
    onLongClick: (String?) -> Unit,
    onMediaClick: (List<Uri>, Int) -> Unit,
    timeFormatter: DateTimeFormatter,
    contentColor: Color
) {
    val context = LocalContext.current
    val formattedTime = message.timestamp.format(timeFormatter).lowercase()
    
    Column(
        modifier = Modifier.padding(
            horizontal = if (message.attachments.isNotEmpty()) 0.dp else 10.dp,
            vertical = if (message.attachments.isNotEmpty() && message.text.isBlank()) 0.dp else 6.dp
        ),
        horizontalAlignment = Alignment.Start
    ) {
        if (message.attachments.isNotEmpty()) {
            AttachmentsView(message, player, isAnySelected, onLongClick, onMediaClick)
            if (message.text.isNotBlank()) Spacer(modifier = Modifier.height(4.dp))
        }

        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal),
                color = contentColor,
                modifier = Modifier.padding(horizontal = if (message.attachments.isNotEmpty()) 10.dp else 0.dp)
            )
            TimestampRow(message.isEdited, formattedTime, true, contentColor, Modifier.align(Alignment.End))
        } else if (message.attachments.isNotEmpty()) {
            TimestampRow(message.isEdited, formattedTime, false, contentColor, Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun AttachmentsView(
    message: ChatMessage,
    player: AudioPlayer,
    isAnySelected: Boolean,
    onLongClick: (String?) -> Unit,
    onMediaClick: (List<Uri>, Int) -> Unit
) {
    val context = LocalContext.current
    val attachments = remember(message.attachments) { message.attachments.map { Uri.parse(it) } }
    
    val imageAttachments = attachments.filter { uri ->
        val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        mimeType?.startsWith("image/") == true || uri.path?.lowercase()?.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") } == true
    }
    val videoAttachments = attachments.filter { uri ->
        val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        mimeType?.startsWith("video/") == true || uri.path?.lowercase()?.endsWith(".mp4") == true
    }
    val audioAttachments = attachments.filter { uri ->
        val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        mimeType?.startsWith("audio/") == true || uri.path?.lowercase()?.endsWith(".m4a") == true
    }
    val otherAttachments = attachments.filter { !imageAttachments.contains(it) && !videoAttachments.contains(it) && !audioAttachments.contains(it) }
    
    val otherGroups = otherAttachments.groupBy { uri ->
        val fileName = FilePickerUtils.getFileName(context, uri).lowercase()
        when {
            fileName.endsWith(".pdf") -> "PDFs"
            fileName.endsWith(".xls") || fileName.endsWith(".xlsx") -> "Excel"
            fileName.endsWith(".doc") || fileName.endsWith(".docx") -> "Word"
            fileName.endsWith(".ppt") || fileName.endsWith(".pptx") -> "PowerPoint"
            fileName.endsWith(".zip") || fileName.endsWith(".rar") -> "Archives"
            else -> "Other Files"
        }
    }

    if (imageAttachments.isNotEmpty()) {
        ImageGrid(imageAttachments, message.isSentByMe, player, isAnySelected, onLongClick, onMediaClick)
    }
    if (videoAttachments.isNotEmpty()) {
        videoAttachments.forEachIndexed { index, uri ->
            ChatAttachmentView(context, uri, message.isSentByMe, player, Modifier.fillMaxWidth().height(200.dp).padding(vertical = 2.dp), null, null, { onLongClick(uri.toString()) }, { if (isAnySelected) Unit else onMediaClick(videoAttachments, index) })
        }
    }
    if (audioAttachments.isNotEmpty()) {
        audioAttachments.forEach { uri ->
            ChatAttachmentView(context, uri, message.isSentByMe, player, Modifier.fillMaxWidth().padding(vertical = 2.dp), null, message.attachmentDurations[uri.toString()], { onLongClick(uri.toString()) }, { if (isAnySelected) Unit else FilePickerUtils.openFile(context, uri) })
        }
    }
    otherGroups.forEach { (_, uris) ->
        uris.forEach { uri ->
            ChatAttachmentView(context, uri, message.isSentByMe, player, Modifier.fillMaxWidth().padding(vertical = 2.dp), null, null, { onLongClick(uri.toString()) }, { if (isAnySelected) Unit else FilePickerUtils.openFile(context, uri) })
        }
    }
}

@Composable
private fun ImageGrid(
    images: List<Uri>,
    isSentByMe: Boolean,
    player: AudioPlayer,
    isAnySelected: Boolean,
    onLongClick: (String?) -> Unit,
    onMediaClick: (List<Uri>, Int) -> Unit
) {
    val columns = 3
    val rows = (images.size + columns - 1) / columns
    Column(modifier = Modifier.width(if (images.size == 1) 220.dp else 260.dp).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until rows) {
            val startIndex = i * columns
            val endIndex = minOf(startIndex + columns, images.size)
            val itemsInRow = images.subList(startIndex, endIndex)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (indexInRow in itemsInRow.indices) {
                    val uri = itemsInRow[indexInRow]
                    val globalIndex = startIndex + indexInRow
                    ChatAttachmentView(
                        context = LocalContext.current,
                        uri = uri,
                        isSentByMe = isSentByMe,
                        player = player,
                        modifier = if (images.size == 1) Modifier.fillMaxWidth().height(200.dp) else Modifier.weight(1f).aspectRatio(1f),
                        timestamp = null,
                        onLongClick = { onLongClick(uri.toString()) },
                        onClick = { if (isAnySelected) Unit else onMediaClick(images, globalIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimestampRow(
    isEdited: Boolean, 
    time: String, 
    hasText: Boolean, 
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(
                top = if (hasText) 0.dp else 2.dp, 
                end = if (hasText) 2.dp else 6.dp, 
                bottom = if (hasText) 0.dp else 4.dp,
                start = 24.dp
            )
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEdited) {
            Text(
                text = "edited",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = contentColor.copy(alpha = 0.4f),
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = contentColor.copy(alpha = 0.5f)
        )
    }
}
