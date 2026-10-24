package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
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
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.AudioPlayer
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.UserColorUtils
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun isEmoji(codePoint: Int): Boolean {
    return (codePoint in 0x1F600..0x1F64F) ||
           (codePoint in 0x1F300..0x1F5FF) ||
           (codePoint in 0x1F680..0x1F6FF) ||
           (codePoint in 0x2600..0x26FF)   ||
           (codePoint in 0x2700..0x27BF)   ||
           (codePoint in 0xFE00..0xFE0F)   ||
           (codePoint in 0x1F900..0x1F9FF) ||
           (codePoint in 0x1F1E6..0x1F1FF) ||
           (codePoint in 0x1F004..0x1F0CF) ||
           (codePoint in 0x1F01C..0x1F02B) ||
           (codePoint in 0x231A..0x231B)   ||
           (codePoint in 0x23E9..0x23F3)   ||
           (codePoint in 0x23F8..0x23FA)   ||
           (codePoint in 0x25AA..0x25AB)   ||
           (codePoint in 0x25FB..0x25FE)   ||
           (codePoint in 0x2B05..0x2B07)   ||
           (codePoint in 0x2B1B..0x2B1C)   ||
           (codePoint == 0x2B50)           ||
           (codePoint == 0x2B55)           ||
           (codePoint == 0x3030)           ||
           (codePoint == 0x303D)           ||
           (codePoint == 0x3297)           ||
           (codePoint == 0x3299)
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
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }
    val context = LocalContext.current
    val alignment = if (message.isSentByMe) Alignment.End else Alignment.Start

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val isDark = primaryTextColor.red > 0.5f

    // Bubble Background Colors:
    // Light Theme: Incoming #E8ECEF, Own #FFFFFF (both raised with soft shadow)
    // Dark Theme: Incoming #181C21, Own #272F39
    val bubbleColor = if (message.isSentByMe) {
        if (isDark) Color(0xFF272F39) else Color(0xFFFFFFFF)
    } else {
        if (isDark) Color(0xFF181C21) else Color(0xFFE8ECEF)
    }

    val bubbleShape = if (message.isSentByMe) {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 2.dp, bottomEnd = 14.dp)
    }

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
                    tint = secondaryTextColor.copy(alpha = (offsetX / swipeThreshold).coerceIn(0f, 1f)),
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
                        isSelected -> tealAccent.copy(alpha = 0.18f)
                        isHighlighted -> primaryTextColor.copy(alpha = 0.15f)
                        else -> Color.Transparent
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { onLongClick(null) }
                )
                .padding(vertical = 3.dp, horizontal = 12.dp),
            horizontalAlignment = alignment
        ) {
            // Sender Name (12sp SemiBold teal) sits ABOVE the bubble outside it for incoming messages
            if (!message.isSentByMe) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tealAccent,
                    modifier = Modifier.padding(start = 36.dp, bottom = 2.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.isSentByMe) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (!message.isSentByMe) {
                    // 28dp Round Avatar
                    Surface(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = UserColorUtils.getColorForName(message.senderName),
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = message.senderName.take(1).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (isEmojiOnly) {
                    EmojiMessage(message.text, message.timestamp.format(timeFormatter).lowercase(), alignment, secondaryTextColor, primaryTextColor)
                } else {
                    Surface(
                        color = bubbleColor,
                        shape = bubbleShape,
                        shadowElevation = if (!isDark) 2.dp else 0.dp,
                        modifier = Modifier.widthIn(min = 40.dp, max = 272.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (message.replyingTo != null) {
                                ReplySection(message, onReplyClick, primaryTextColor, secondaryTextColor, tealAccent)
                            }

                            AttachmentAndTextSection(
                                message = message,
                                player = player,
                                isAnySelected = isAnySelected,
                                onLongClick = onLongClick,
                                onMediaClick = onMediaClick,
                                timeFormatter = timeFormatter,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                tealAccent = tealAccent,
                                isDark = isDark
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiMessage(
    text: String,
    time: String,
    alignment: Alignment.Horizontal,
    secondaryTextColor: Color,
    primaryTextColor: Color
) {
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
            fontSize = 10.sp,
            color = secondaryTextColor,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ReplySection(
    message: ChatMessage,
    onReplyClick: (String, String?) -> Unit,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color
) {
    val replyingTo = message.replyingTo!!
    Surface(
        color = secondaryTextColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReplyClick(replyingTo.id, message.replyingToAttachmentUri) }
            .padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(tealAccent, shape = RoundedCornerShape(1.5.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = replyingTo.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealAccent
                )
                Text(
                    text = replyingTo.text.ifBlank { "Attachment" },
                    fontSize = 12.sp,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color,
    isDark: Boolean
) {
    val context = LocalContext.current
    val formattedTime = remember(message.timestamp) { message.timestamp.format(timeFormatter).lowercase() }

    Column {
        // Attachments
        message.attachments.forEachIndexed { index, uriStr ->
            val uri = Uri.parse(uriStr)
            val fileName = FilePickerUtils.getFileName(context, uri)
            val ext = fileName.substringAfterLast('.', "").lowercase()

            val isImage = uriStr.contains("jpg") || uriStr.contains("png") || uriStr.contains("jpeg") || ext in listOf("jpg", "png", "jpeg")
            val isPdf = ext == "pdf"
            val isDoc = ext in listOf("doc", "docx")
            val isXls = ext in listOf("xls", "xlsx", "csv")
            val isPpt = ext in listOf("ppt", "pptx")

            if (isImage) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clickable {
                            val mediaUris = message.attachments.map { Uri.parse(it) }
                            onMediaClick(mediaUris, index)
                        }
                        .padding(bottom = 6.dp)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // Document Tile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { FilePickerUtils.openFile(context, uri) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DocumentTypeTile(extension = ext, isDark = isDark)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = primaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${ext.uppercase()} · Document",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }
        }

        // Message Text
        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = primaryTextColor,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        // Timestamp & Read Ticks at Bottom Right
        Row(
            modifier = Modifier.align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedTime,
                fontSize = 11.sp,
                color = secondaryTextColor
            )
            if (message.isSentByMe) {
                Spacer(modifier = Modifier.width(4.dp))
                // Two Teal Ticks for Read
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Read",
                    tint = tealAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun DocumentTypeTile(
    extension: String,
    isDark: Boolean
) {
    val (label, color) = when (extension.lowercase()) {
        "pdf" -> "PDF" to if (isDark) Color(0xFFE5584F) else Color(0xFFC4433A)
        "doc", "docx" -> "DOC" to if (isDark) Color(0xFF4A8FE0) else Color(0xFF2F6FBF)
        "xls", "xlsx", "csv" -> "XLS" to if (isDark) Color(0xFF3FA66B) else Color(0xFF2C8A55)
        "ppt", "pptx" -> "PPT" to if (isDark) Color(0xFFE08A3C) else Color(0xFFC2681E)
        else -> "FILE" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.16f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = color
            )
        }
    }
}
