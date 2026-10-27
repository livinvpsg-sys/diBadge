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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

fun buildMentionAnnotatedString(
    text: String,
    primaryTextColor: Color,
    tealAccent: Color,
    acceptedGroupNames: List<String> = emptyList()
): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Regex("""@([A-Za-z0-9_]+(?:\s+[A-Za-z0-9_]+)*)""")
        var lastIndex = 0

        pattern.findAll(text).forEach { matchResult ->
            val range = matchResult.range
            val mentionText = matchResult.value
            val nameWithoutAt = matchResult.groupValues[1]

            if (range.first > lastIndex) {
                append(text.substring(lastIndex, range.first))
            }

            val isGroupMentionName = nameWithoutAt.equals("All linked groups", ignoreCase = true) ||
                    acceptedGroupNames.any { it.equals(nameWithoutAt, ignoreCase = true) }

            val spanColor = if (isGroupMentionName) tealAccent else primaryTextColor

            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = spanColor)) {
                append(mentionText)
            }

            lastIndex = range.last + 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    player: AudioPlayer,
    isSelected: Boolean,
    isAnySelected: Boolean,
    isFirstInRun: Boolean = true,
    isHighlighted: Boolean = false,
    onLongClick: (String?) -> Unit,
    onClick: () -> Unit,
    onReplyClick: (String, String?) -> Unit,
    onMediaClick: (List<Uri>, Int) -> Unit,
    onCommentsClick: (ChatMessage) -> Unit = {},
    onSentToClick: (ChatMessage) -> Unit = {},
    onSwipeToReply: (String?) -> Unit = {}
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }
    val context = LocalContext.current
    val alignment = if (message.isSentByMe) Alignment.End else Alignment.Start

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val isDark = colors.onBackground.red > 0.5f

    // Theme tokens
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val cardFillColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val tealAccent = if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)

    // Bubble Colors:
    // Light Theme: Incoming #E8ECEF (chat bg), Own #FFFFFF (both raised with soft shadow)
    // Dark Theme: Incoming #181C21, Own #272F39
    val bubbleColor = if (message.isSentByMe) {
        if (isDark) Color(0xFF272F39) else Color(0xFFFFFFFF)
    } else {
        if (isDark) Color(0xFF181C21) else Color(0xFFE8ECEF)
    }

    // Corner radius: 14dp corners with 2dp corner at tail
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
                .padding(vertical = if (isFirstInRun) 4.dp else 1.dp, horizontal = 12.dp),
            horizontalAlignment = alignment
        ) {
            // Label above bubble
            if (message.isGroupMention && !message.isSentByMe) {
                // Incoming Cross-Posted Group-Mention Bubble: Group name in identity name color + 13dp link icon
                val groupDisplayName = message.sendingGroupName ?: message.senderName
                val groupTitleColor = remember(groupDisplayName, isDark) {
                    UserColorUtils.getIdentityColor(groupDisplayName, isDark)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 36.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = groupDisplayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = groupTitleColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Cross-posted link",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else if (!message.isSentByMe && isFirstInRun) {
                // Normal Incoming Sender Name (using per-identity color palette, never teal)
                val senderColor = remember(message.senderName, isDark) {
                    UserColorUtils.getColorForName(message.senderName, isDark)
                }
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = senderColor,
                    modifier = Modifier.padding(start = 36.dp, bottom = 2.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.isSentByMe) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (!message.isSentByMe) {
                    if (isFirstInRun || message.isGroupMention) {
                        val avatarShape = if (message.isGroupMention) RoundedCornerShape(10.dp) else CircleShape
                        val avatarText = (message.sendingGroupName ?: message.senderName).take(1).uppercase()

                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = avatarShape,
                            color = cardFillColor,
                            border = BorderStroke(1.dp, outlineColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = avatarText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryTextColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Spacer(modifier = Modifier.width(36.dp))
                    }
                }

                if (isEmojiOnly) {
                    EmojiMessage(message.text, message.timestamp.format(timeFormatter).lowercase(), alignment, secondaryTextColor, primaryTextColor)
                } else {
                    Surface(
                        color = bubbleColor,
                        shape = bubbleShape,
                        shadowElevation = if (!isDark) 1.5.dp else 1.dp,
                        tonalElevation = 0.dp,
                        modifier = Modifier.widthIn(min = if (message.isGroupMention) 180.dp else 40.dp, max = 272.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
                        ) {
                            if (message.replyingTo != null) {
                                ReplySection(message, onReplyClick, primaryTextColor, secondaryTextColor, tealAccent, isDark)
                            }

                            AttachmentAndTextSection(
                                message = message,
                                player = player,
                                isAnySelected = isAnySelected,
                                onLongClick = onLongClick,
                                onMediaClick = onMediaClick,
                                onCommentsClick = onCommentsClick,
                                onSentToClick = onSentToClick,
                                timeFormatter = timeFormatter,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                outlineColor = outlineColor,
                                tealAccent = tealAccent,
                                errorColor = errorColor,
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
            fontSize = 11.sp,
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
    tealAccent: Color,
    isDark: Boolean
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
                val replySenderColor = remember(replyingTo.senderName, isDark) {
                    UserColorUtils.getColorForName(replyingTo.senderName, isDark)
                }
                Text(
                    text = replyingTo.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = replySenderColor
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
    onCommentsClick: (ChatMessage) -> Unit,
    onSentToClick: (ChatMessage) -> Unit,
    timeFormatter: DateTimeFormatter,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    outlineColor: Color,
    tealAccent: Color,
    errorColor: Color,
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

            if (isImage) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 240.dp)
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
                        val typeDescription = when (ext) {
                            "doc", "docx" -> "Word"
                            "xls", "xlsx", "csv" -> "Excel"
                            "ppt", "pptx" -> "PowerPoint"
                            "pdf" -> "PDF"
                            else -> ext.uppercase()
                        }
                        Text(
                            text = "$typeDescription · Document",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }
        }

        // Message Text
        val rawTextToDisplay = if (!message.isSentByMe && message.isGroupMention) {
            stripGroupMention(message.text, message.sendingGroupName)
        } else {
            message.text
        }

        if (rawTextToDisplay.isNotBlank()) {
            val annotatedText = remember(rawTextToDisplay, primaryTextColor, tealAccent, message.mentionedGroupNames) {
                buildMentionAnnotatedString(
                    text = rawTextToDisplay,
                    primaryTextColor = primaryTextColor,
                    tealAccent = tealAccent,
                    acceptedGroupNames = message.mentionedGroupNames
                )
            }
            Text(
                text = annotatedText,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = primaryTextColor,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        // In-Bubble Footer Line: 5dp below text
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (message.isGroupMention) Arrangement.SpaceBetween else Arrangement.End
        ) {
            if (message.isGroupMention) {
                Row(
                    modifier = Modifier.clickable { onCommentsClick(message) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isCrossPostFailed) {
                        Text(
                            text = "Not delivered",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = errorColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Replies",
                            tint = tealAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))

                        val replyWord = if (message.commentCount > 1) "Replies" else "Reply"

                        if (message.unreadCommentCount > 0) {
                            Text(
                                text = replyWord,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tealAccent
                            )
                            Spacer(modifier = Modifier.width(4.dp))

                            val badgeText = if (message.commentCount > 99) "99+" else message.commentCount.toString()
                            val onTealColor = if (isDark) Color(0xFF0E1114) else Color.White

                            Surface(
                                shape = CircleShape,
                                color = tealAccent,
                                modifier = Modifier
                                    .height(18.dp)
                                    .widthIn(min = 18.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 5.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = onTealColor,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            val replyLabel = if (message.commentCount > 0) "$replyWord ${message.commentCount}" else replyWord
                            Text(
                                text = replyLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tealAccent
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.widthIn(min = 20.dp))
            }

            // Right end: Link or Warning icon (Sender side only) + Time + Read ticks
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.isSentByMe && message.isGroupMention) {
                    val linkIcon = if (message.isCrossPostFailed) Icons.Default.Warning else Icons.Default.Link
                    val linkTint = if (message.isCrossPostFailed) errorColor else secondaryTextColor

                    Icon(
                        imageVector = linkIcon,
                        contentDescription = "Sent to details",
                        tint = linkTint,
                        modifier = Modifier
                            .size(13.dp)
                            .clickable { onSentToClick(message) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = secondaryTextColor
                )
                if (message.isSentByMe) {
                    Spacer(modifier = Modifier.width(4.dp))
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
        else -> "FILE" to if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
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
