package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.fabxdi.dibadge.ui.components.DocumentTypeTile
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.ui.components.WaveformIcon
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.AudioPlayer
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.UserColorUtils
import kotlinx.coroutines.delay
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

/** True for an audio file: a voice note or an attached mp3, m4a, wav and so on. */
private fun isAudioAttachment(context: android.content.Context, uriString: String): Boolean {
    val uri = Uri.parse(uriString)
    val mime = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
    if (mime?.startsWith("audio/") == true) return true
    val ext = FilePickerUtils.getFileName(context, uri).substringBefore("?").substringAfterLast('.', "").lowercase()
    return ext in listOf("m4a", "mp3", "wav", "aac", "ogg", "oga", "opus", "amr", "flac")
}

/** True for a message that holds one recording made with the mic button (VOICE_...m4a). */
private fun isMicRecording(message: ChatMessage): Boolean {
    if (message.isDeleted || message.attachments.size != 1) return false
    val name = message.attachments[0].substringBefore("?").substringAfterLast('/')
    val decoded = try { java.net.URLDecoder.decode(name, "UTF-8") } catch (e: Exception) { name }
    return decoded.startsWith("VOICE_") && decoded.endsWith(".m4a", ignoreCase = true)
}

/** True while a bubble should not print its own time: the time is shown once, under the last bubble of a group. */
private val LocalGroupedTime = compositionLocalOf { false }

/** Opens a PDF inside the app. Provided by the chat screen; documents of other types open in an outside app. */
val LocalOpenPdf = staticCompositionLocalOf<(Uri) -> Unit> { {} }

private fun openDocument(context: android.content.Context, uri: Uri, fileName: String, openPdf: (Uri) -> Unit) {
    if (fileName.substringAfterLast('.', "").equals("pdf", ignoreCase = true)) openPdf(uri)
    else FilePickerUtils.openFileInExternalApp(context, uri, fileName)
}

@Composable
private fun OutsideTimeRow(
    message: ChatMessage,
    formattedTime: String,
    secondaryTextColor: Color,
    tealAccent: Color,
    errorColor: Color,
    onRetryFailedMessage: (ChatMessage) -> Unit
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = formattedTime, fontSize = 11.sp, color = secondaryTextColor)
        if (message.isSentByMe) {
            Spacer(modifier = Modifier.width(4.dp))
            when (message.sendStatus) {
                "SENDING" -> Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = "Sending",
                    tint = secondaryTextColor,
                    modifier = Modifier.size(13.dp)
                )
                "FAILED" -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onRetryFailedMessage(message) }
                ) {
                    Text(text = "Not sent", fontSize = 11.sp, color = errorColor)
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Failed",
                        tint = errorColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
                else -> Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Sent",
                    tint = tealAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
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
    isGroupedWithPrev: Boolean = false,
    isGroupedWithNext: Boolean = false,
    isHighlighted: Boolean = false,
    isOneToOne: Boolean = false,
    onLongClick: (String?) -> Unit,
    onClick: () -> Unit,
    onReplyClick: (String, String?) -> Unit,
    onMediaClick: (List<Uri>, Int) -> Unit,
    onCommentsClick: (ChatMessage) -> Unit = {},
    onSentToClick: (ChatMessage) -> Unit = {},
    onLocationClick: (ChatMessage) -> Unit = {},
    onStopLiveLocation: (String) -> Unit = {},
    onVoteOption: (messageId: String, optionId: String) -> Unit = { _, _ -> },
    onViewVotesClick: (ChatMessage) -> Unit = {},
    onSwipeToReply: (String?) -> Unit = {},
    onRetryFailedMessage: (ChatMessage) -> Unit = {}
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }
    val alignment = if (message.isSentByMe) Alignment.End else Alignment.Start

    val colors = MaterialTheme.colorScheme
    val isDark = DiBadgeTheme.colors.isDark

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
    // Bubbles sent together (same person, same minute) sit close, with tighter inner corners
    val innerTop = if (isGroupedWithPrev) 4.dp else 14.dp
    val innerBottom = if (isGroupedWithNext) 4.dp else 2.dp
    val bubbleShape = if (message.isSentByMe) {
        RoundedCornerShape(topStart = 14.dp, topEnd = innerTop, bottomStart = 14.dp, bottomEnd = innerBottom)
    } else {
        RoundedCornerShape(topStart = innerTop, topEnd = 14.dp, bottomStart = innerBottom, bottomEnd = 14.dp)
    }
    val groupedTime = !message.isDeleted && !message.isGroupMention
    val outsideTimeText = remember(message.timestamp, message.isEdited) {
        val t = message.timestamp.format(timeFormatter).lowercase()
        if (message.isEdited) "Edited $t" else t
    }

    val isEmojiOnly = remember(message.text) {
        isOnlyEmojis(message.text) && message.attachments.isEmpty() && message.replyingTo == null
    }

    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 150f

    CompositionLocalProvider(LocalGroupedTime provides groupedTime) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.toInt(), 0) }
                    .background(
                        when {
                            isSelected -> primaryTextColor.copy(alpha = 0.12f)
                            isHighlighted -> primaryTextColor.copy(alpha = 0.15f)
                            else -> Color.Transparent
                        }
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { onLongClick(null) }
                    )
                    .padding(vertical = if (isFirstInRun) 4.dp else 1.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAnySelected) {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Surface(
                                modifier = Modifier.size(22.dp),
                                shape = CircleShape,
                                color = tealAccent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (isDark) Color(0xFF0E1114) else Color(0xFFFFFFFF),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(Color.Transparent, CircleShape)
                                    .border(BorderStroke(1.5.dp, outlineColor), CircleShape)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
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
                    } else if (!isOneToOne && !message.isSentByMe && isFirstInRun) {
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
                        if (!isOneToOne && !message.isSentByMe) {
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

                        Column(horizontalAlignment = alignment) {
                            if (message.isDeleted) {
                                DeletedMessagePlaceholderBubble(
                                    message = message,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    outlineColor = outlineColor,
                                    timeFormatter = timeFormatter
                                )
                            } else if (isEmojiOnly) {
                                EmojiMessage(message.text, message.timestamp.format(timeFormatter).lowercase(), alignment, secondaryTextColor, primaryTextColor)
                            } else if (message.attachments.isNotEmpty()) {
                                Column(horizontalAlignment = alignment) {
                                    if (message.replyingTo != null) {
                                        ReplyPreviewAboveBubble(
                                            message = message,
                                            onReplyClick = onReplyClick,
                                            alignment = alignment,
                                            primaryTextColor = primaryTextColor,
                                            secondaryTextColor = secondaryTextColor,
                                            isDark = isDark
                                        )
                                    }

                                    val context = LocalContext.current
                                    val isMediaGrid = remember(message.attachments) {
                                        message.attachments.all { uriStr ->
                                            val cat = getAttachmentCategory(context, Uri.parse(uriStr))
                                            cat == AttachmentCategory.IMAGE || cat == AttachmentCategory.VIDEO
                                        }
                                    }

                                    // Voice notes and attached audio files (mp3, m4a ...) play right in the chat
                                    val isAudioOnly = remember(message.attachments) {
                                        message.attachments.isNotEmpty() && message.attachments.all { isAudioAttachment(context, it) }
                                    }

                                    val rawTimeStr = remember(message.timestamp) { message.timestamp.format(timeFormatter).lowercase() }
                                    val formattedTimeStr = remember(rawTimeStr, message.isEdited) {
                                        if (message.isEdited) "Edited $rawTimeStr" else rawTimeStr
                                    }

                                    Column(
                                        horizontalAlignment = alignment,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    ) {
                                        if (isMediaGrid) {
                                            MediaGridNoBubble(
                                                message = message,
                                                formattedTime = formattedTimeStr,
                                                onMediaClick = onMediaClick,
                                                primaryTextColor = primaryTextColor,
                                                secondaryTextColor = secondaryTextColor,
                                                tealAccent = tealAccent,
                                                isDark = isDark
                                            )
                                        } else if (isAudioOnly || message.isVoiceNote || isMicRecording(message)) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                message.attachments.forEachIndexed { index, audioUrl ->
                                                    val audioName = remember(audioUrl) { FilePickerUtils.getFileName(context, Uri.parse(audioUrl)) }
                                                    VoiceNoteBubble(
                                                        message = message,
                                                        url = audioUrl,
                                                        title = if (audioName.startsWith("VOICE_")) null else audioName,
                                                        player = player,
                                                        formattedTime = formattedTimeStr,
                                                        bubbleColor = bubbleColor,
                                                        bubbleShape = bubbleShape,
                                                        primaryTextColor = primaryTextColor,
                                                        secondaryTextColor = secondaryTextColor,
                                                        tealAccent = tealAccent,
                                                        isDark = isDark,
                                                        showFooter = message.text.isBlank() && index == message.attachments.lastIndex
                                                    )
                                                }
                                            }
                                        } else {
                                            DocumentRowsNoBubble(
                                                message = message,
                                                formattedTime = formattedTimeStr,
                                                primaryTextColor = primaryTextColor,
                                                secondaryTextColor = secondaryTextColor,
                                                cardFillColor = cardFillColor,
                                                tealAccent = tealAccent,
                                                isDark = isDark
                                            )
                                        }

                                        if (message.text.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                color = bubbleColor,
                                                shape = bubbleShape,
                                                shadowElevation = if (!isDark) 1.5.dp else 1.dp,
                                                modifier = Modifier.widthIn(min = 40.dp, max = 272.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                                                    Text(
                                                        text = buildMentionAnnotatedString(message.text, primaryTextColor, tealAccent, message.mentionedGroupNames),
                                                        fontSize = 15.sp,
                                                        lineHeight = 21.sp,
                                                        color = primaryTextColor
                                                    )
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp),
                                                        horizontalArrangement = Arrangement.End,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = formattedTimeStr,
                                                            fontSize = 11.sp,
                                                            color = secondaryTextColor
                                                        )
                                                        if (message.isSentByMe) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            when (message.sendStatus) {
                                                                "SENDING" -> {
                                                                    Icon(
                                                                        imageVector = Icons.Outlined.Schedule,
                                                                        contentDescription = "Sending",
                                                                        tint = secondaryTextColor,
                                                                        modifier = Modifier.size(13.dp)
                                                                    )
                                                                }
                                                                "FAILED" -> {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        modifier = Modifier.clickable { onRetryFailedMessage(message) }
                                                                    ) {
                                                                        Text(
                                                                            text = "Not sent",
                                                                            fontSize = 11.sp,
                                                                            color = errorColor
                                                                        )
                                                                        Spacer(modifier = Modifier.width(3.dp))
                                                                        Icon(
                                                                            imageVector = Icons.Default.ErrorOutline,
                                                                            contentDescription = "Failed",
                                                                            tint = errorColor,
                                                                            modifier = Modifier.size(13.dp)
                                                                        )
                                                                    }
                                                                }
                                                                else -> {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Check,
                                                                        contentDescription = "Sent",
                                                                        tint = secondaryTextColor,
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
                            } else {
                                Column(horizontalAlignment = alignment) {
                                    val topOverlapModifier = Modifier

                                    Surface(
                                        color = bubbleColor,
                                        shape = bubbleShape,
                                        shadowElevation = if (!isDark) 1.5.dp else 1.dp,
                                        tonalElevation = 0.dp,
                                        modifier = topOverlapModifier.widthIn(min = if (message.isGroupMention) 180.dp else 40.dp, max = 272.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.width(IntrinsicSize.Max)
                                        ) {
                                            if (message.replyingTo != null) {
                                                ReplyQuoteInBubble(
                                                    message = message,
                                                    onReplyClick = onReplyClick,
                                                    primaryTextColor = primaryTextColor,
                                                    secondaryTextColor = secondaryTextColor,
                                                    tealAccent = tealAccent,
                                                    isDark = isDark
                                                )
                                            }
                                            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 5.dp)) {
                                                AttachmentAndTextSection(
                                                    message = message,
                                                    player = player,
                                                    isAnySelected = isAnySelected,
                                                    onLongClick = onLongClick,
                                                    onMediaClick = onMediaClick,
                                                    onCommentsClick = onCommentsClick,
                                                    onSentToClick = onSentToClick,
                                                    onLocationClick = onLocationClick,
                                                    onStopLiveLocation = onStopLiveLocation,
                                                    onVoteOption = onVoteOption,
                                                    onViewVotesClick = onViewVotesClick,
                                                    timeFormatter = timeFormatter,
                                                    primaryTextColor = primaryTextColor,
                                                    secondaryTextColor = secondaryTextColor,
                                                    outlineColor = outlineColor,
                                                    tealAccent = tealAccent,
                                                    errorColor = errorColor,
                                                    isDark = isDark,
                                                    onRetryFailedMessage = onRetryFailedMessage
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (groupedTime && (!isGroupedWithNext || message.sendStatus != "SENT")) {
                                OutsideTimeRow(
                                    message = message,
                                    formattedTime = outsideTimeText,
                                    secondaryTextColor = secondaryTextColor,
                                    tealAccent = tealAccent,
                                    errorColor = errorColor,
                                    onRetryFailedMessage = onRetryFailedMessage
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeletedMessagePlaceholderBubble(
    message: ChatMessage,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    outlineColor: Color,
    timeFormatter: DateTimeFormatter
) {
    val formattedTime = remember(message.timestamp) { message.timestamp.format(timeFormatter).lowercase() }
    val placeholderText = if (message.deletedByRole == "admin") "Removed by an admin" else "This message was deleted"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.DoNotDisturbOn,
                contentDescription = null,
                tint = secondaryTextColor,
                modifier = Modifier.size(15.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = placeholderText,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = formattedTime,
                fontSize = 11.sp,
                color = secondaryTextColor
            )
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
        if (!LocalGroupedTime.current) {
            Text(
                text = time,
                fontSize = 11.sp,
                color = secondaryTextColor,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun ReplyPreviewAboveBubble(
    message: ChatMessage,
    onReplyClick: (String, String?) -> Unit,
    alignment: Alignment.Horizontal,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean
) {
    val replyingTo = message.replyingTo!!
    val isSenderYou = replyingTo.isSentByMe || replyingTo.senderName == "Me"
    val displayName = if (isSenderYou) "yourself" else replyingTo.senderName
    val captionText = "Replied to $displayName"
    val raisedSurfaceColor = if (isDark) Color(0xFF222831) else Color(0xFFE8ECEF)

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 3.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Replied to",
                tint = secondaryTextColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = captionText,
                fontSize = 12.sp,
                color = secondaryTextColor
            )
        }

        Surface(
            color = raisedSurfaceColor.copy(alpha = 0.90f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .widthIn(max = 200.dp)
                .clickable {
                    if (!replyingTo.isDeleted) {
                        onReplyClick(replyingTo.id, message.replyingToAttachmentUri)
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (replyingTo.isDeleted) {
                    Text(
                        text = "Message deleted",
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    val hasThumb = !message.replyingToAttachmentUri.isNullOrBlank() || replyingTo.attachments.isNotEmpty()
                    val thumbUri = message.replyingToAttachmentUri ?: replyingTo.attachments.firstOrNull()

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        val previewText = if (replyingTo.text.isNotBlank()) {
                            replyingTo.text.take(200)
                        } else if (replyingTo.isVoiceNote) {
                            "Voice message"
                        } else if (replyingTo.msgType == "location") {
                            "Location"
                        } else if (replyingTo.msgType == "poll") {
                            "Poll"
                        } else {
                            "Attachment"
                        }

                        Text(
                            text = previewText,
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (hasThumb && thumbUri != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            AsyncImage(
                                model = thumbUri,
                                contentDescription = "Quoted photo/video",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyQuoteInBubble(
    message: ChatMessage,
    onReplyClick: (String, String?) -> Unit,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color,
    isDark: Boolean
) {
    val replyingTo = message.replyingTo ?: return
    // The quoted message is yours if it says so, or if its sender is you (the quote only carries a name)
    val myName = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName.orEmpty() }
    val isSenderYou = replyingTo.isSentByMe || replyingTo.senderName == "Me" ||
            (message.isSentByMe && replyingTo.senderName == message.senderName) ||
            (myName.isNotBlank() && replyingTo.senderName == myName)
    val name = if (isSenderYou) "You" else replyingTo.senderName
    // Same per-member colour as the sender names in the chat
    val nameColor = if (isSenderYou) tealAccent else UserColorUtils.getColorForName(replyingTo.senderName, isDark)
    val stripBg = if (isDark) Color(0x66000000) else Color(0x1A000000)
    val thumbUri = message.replyingToAttachmentUri ?: replyingTo.attachments.firstOrNull()
    val preview = when {
        replyingTo.isDeleted -> "Message deleted"
        replyingTo.text.isNotBlank() -> replyingTo.text.take(200)
        replyingTo.isVoiceNote -> "Voice message"
        replyingTo.msgType == "location" -> "Location"
        replyingTo.msgType == "poll" -> "Poll"
        else -> "Attachment"
    }

    // The whole strip is shown at 60% so it reads quieter than the reply below it
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!replyingTo.isDeleted) onReplyClick(replyingTo.id, message.replyingToAttachmentUri) }
            .graphicsLayer(alpha = 0.6f)
            .background(stripBg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preview,
                fontSize = 12.sp,
                color = secondaryTextColor,
                fontStyle = if (replyingTo.isDeleted) FontStyle.Italic else FontStyle.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!replyingTo.isDeleted && thumbUri != null) {
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = thumbUri,
                contentDescription = "Quoted photo/video",
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Default.BrokenImage),
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(secondaryTextColor.copy(alpha = 0.15f))
            )
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
    onLocationClick: (ChatMessage) -> Unit,
    onStopLiveLocation: (String) -> Unit,
    onVoteOption: (messageId: String, optionId: String) -> Unit,
    onViewVotesClick: (ChatMessage) -> Unit,
    timeFormatter: DateTimeFormatter,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    outlineColor: Color,
    tealAccent: Color,
    errorColor: Color,
    isDark: Boolean,
    onRetryFailedMessage: (ChatMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val formattedTime = remember(message.timestamp) { message.timestamp.format(timeFormatter).lowercase() }

    Column {
        if (message.isForwarded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Forwarded",
                    tint = secondaryTextColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Forwarded",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = secondaryTextColor
                )
            }
        }
        if (message.isVoiceNote || isMicRecording(message)) {
            // Voice Message Bubble
            val audioColor = if (isDark) Color(0xFFE87DA3) else Color(0xFFB0446A)
            val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
            var isPlaying by remember { mutableStateOf(false) }
            var playSpeed by remember { mutableFloatStateOf(1.0f) }
            var progressFraction by remember { mutableFloatStateOf(0f) }
            var currentPosSec by remember { mutableLongStateOf(0L) }

            val totalSec = message.attachmentDurations.values.firstOrNull() ?: 21L
            val totalStr = String.format(Locale.getDefault(), "%d:%02d", totalSec / 60, totalSec % 60)
            val displayTimeStr = if (isPlaying && currentPosSec > 0) {
                String.format(Locale.getDefault(), "%d:%02d", currentPosSec / 60, currentPosSec % 60)
            } else {
                totalStr
            }

            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    while (isPlaying && player.isPlaying()) {
                        val total = player.getDuration()
                        val current = player.getCurrentPosition()
                        if (total > 0) {
                            progressFraction = current.toFloat() / total
                            currentPosSec = (current / 1000).toLong()
                        }
                        delay(50)
                    }
                }
            }

            val waveformBars = remember(message.waveformArray) {
                if (message.waveformArray.isNotEmpty()) {
                    message.waveformArray.take(25)
                } else {
                    listOf(6, 12, 18, 24, 14, 28, 20, 12, 22, 16, 8, 26, 18, 10, 22, 16, 12, 20, 14, 8, 16, 22, 12, 6, 10)
                }
            }

            Column(
                modifier = Modifier
                    .width(232.dp)
                    .padding(bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 40dp Round Play Button
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                isPlaying = !isPlaying
                                if (isPlaying && message.attachments.isNotEmpty()) {
                                    val uri = Uri.parse(message.attachments.first())
                                    player.playFile(uri) {
                                        isPlaying = false
                                        progressFraction = 0f
                                        currentPosSec = 0
                                    }
                                    player.setSpeed(playSpeed)
                                } else {
                                    player.stop()
                                    progressFraction = 0f
                                    currentPosSec = 0
                                }
                            },
                        shape = CircleShape,
                        color = primaryTextColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play voice message",
                                tint = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Waveform (150dp x 28dp)
                    Row(
                        modifier = Modifier
                            .width(150.dp)
                            .height(28.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        waveformBars.forEachIndexed { idx, barValue ->
                            val heightFrac = (barValue / 31f).coerceIn(0.15f, 1f)
                            val barFraction = idx.toFloat() / waveformBars.size
                            val isPlayed = isPlaying && barFraction <= progressFraction
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(heightFrac),
                                shape = RoundedCornerShape(1.dp),
                                color = if (isPlayed) audioColor else secondaryTextColor.copy(alpha = 0.35f)
                            ) {}
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer Line under waveform
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))

                        val isUnreadIncoming = !message.isSentByMe && !message.isListened
                        val timeColor = if (isUnreadIncoming) primaryTextColor else secondaryTextColor

                        Text(
                            text = displayTimeStr,
                            fontSize = 11.sp,
                            color = timeColor,
                            fontWeight = if (isUnreadIncoming) FontWeight.Medium else FontWeight.Normal
                        )

                        if (isUnreadIncoming) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                modifier = Modifier.size(6.dp),
                                shape = CircleShape,
                                color = primaryTextColor
                            ) {}
                        }

                        if (isPlaying) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                modifier = Modifier
                                    .clickable {
                                        playSpeed = when (playSpeed) {
                                            1.0f -> 1.5f
                                            1.5f -> 2.0f
                                            else -> 1.0f
                                        }
                                        player.setSpeed(playSpeed)
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = cardFillColor
                            ) {
                                val speedLabel = if (playSpeed == 1.5f) "1.5x" else if (playSpeed == 2.0f) "2x" else "1x"
                                Text(
                                    text = speedLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryTextColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (message.msgType == "location") {
            // Location Bubble
            val placeText = if (!message.locationPlaceName.isNullOrBlank()) {
                message.locationPlaceName
            } else {
                "${message.locationLat}° N, ${message.locationLng}° E"
            }
            val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val geoUri = Uri.parse("geo:${message.locationLat},${message.locationLng}?q=${message.locationLat},${message.locationLng}($placeText)")
                            val intent = Intent(Intent.ACTION_VIEW, geoUri)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            onLocationClick(message)
                        }
                    }
                    .padding(bottom = 6.dp)
            ) {
                // Map tile (232dp x 128dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = cardFillColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Map Pin",
                            tint = errorColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = placeText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = if (message.isLiveLocation) "Live location · " else "Location · ",
                        fontSize = 12.sp,
                        color = secondaryTextColor
                    )
                    Text(
                        text = "Open in Maps",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent
                    )
                }

                if (message.isLiveLocation && message.isSentByMe && !message.isLiveEnded) {
                    HorizontalDivider(
                        color = outlineColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Text(
                        text = "Stop sharing",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = errorColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStopLiveLocation(message.id) }
                    )
                }
            }
        } else if (message.msgType == "poll") {
            // Poll Bubble
            val totalVotes = message.pollOptions.sumOf { it.voteCount }
            val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Poll",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "POLL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryTextColor
                    )
                }

                Text(
                    text = message.pollQuestion,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Text(
                    text = if (message.isPollClosed) "Final result · Poll closed" else if (message.pollAllowMultiple) "Select one or more" else "Select one",
                    fontSize = 12.sp,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                message.pollOptions.forEach { option ->
                    val isSelected = message.userVotedOptionIds.contains(option.id)
                    val fraction = if (totalVotes > 0) option.voteCount / totalVotes.toFloat() else 0f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !message.isPollClosed) {
                                onVoteOption(message.id, option.id)
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    shape = CircleShape,
                                    color = tealAccent
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Voted",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(BorderStroke(1.8.dp, secondaryTextColor), CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = option.text,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected || message.isPollClosed) FontWeight.SemiBold else FontWeight.Normal,
                                color = primaryTextColor,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = option.voteCount.toString(),
                                fontSize = 13.sp,
                                color = secondaryTextColor
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            shape = RoundedCornerShape(2.dp),
                            color = cardFillColor
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .background(
                                            color = if (isSelected) primaryTextColor else secondaryTextColor,
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$totalVotes votes",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )

                    if (message.pollShowVoters) {
                        Text(
                            text = "View votes",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tealAccent,
                            modifier = Modifier.clickable { onViewVotesClick(message) }
                        )
                    }
                }
            }
        }
        // Attachments Rendering
        if (message.attachments.isNotEmpty()) {
            val mediaUris = remember(message.attachments) { message.attachments.map { Uri.parse(it) } }
            val n = mediaUris.size

            if (n == 1) {
                val firstUri = mediaUris.first()
                val firstUriStr = firstUri.toString()
                val firstFileName = FilePickerUtils.getFileName(context, firstUri)
                val firstExt = firstFileName.substringAfterLast('.', "").lowercase()
                val mimeType = try { context.contentResolver.getType(firstUri) } catch (e: Exception) { null }
                val isVideo = firstUriStr.contains("video") || firstExt == "mp4" || mimeType?.startsWith("video/") == true
                val isAudio = mimeType?.startsWith("audio/") == true || firstExt in listOf("m4a", "mp3")
                val isImage = mimeType?.startsWith("image/") == true || firstExt in listOf("jpg", "jpeg", "png")

                Column(
                    modifier = Modifier
                        .widthIn(min = 200.dp, max = 272.dp)
                        .padding(bottom = 6.dp)
                ) {
                    if (isImage || isVideo) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .clickable { onMediaClick(mediaUris, 0) }
                            ) {
                                AsyncImage(
                                    model = firstUri,
                                    contentDescription = if (isVideo) "Video" else "Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (isVideo) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.50f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .align(Alignment.Center)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                val durationSec = message.attachmentDurations[firstUriStr] ?: 0L
                                val durationStr = if (durationSec > 0) {
                                    String.format(Locale.getDefault(), "%d:%02d", durationSec / 60, durationSec % 60)
                                } else ""

                                if (durationStr.isNotEmpty()) Surface(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
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
                        }
                    } else if (isAudio) {
                        ChatAttachmentView(
                            context = context,
                            uri = firstUri,
                            isSentByMe = message.isSentByMe,
                            player = player,
                            duration = message.attachmentDurations[firstUriStr] ?: 0L,
                            onClick = { onMediaClick(mediaUris, 0) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
                        val openPdf = LocalOpenPdf.current
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cardFillColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openDocument(context, firstUri, firstFileName, openPdf) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DocumentTypeTile(extension = firstExt, isDark = isDark, widthDp = 28, heightDp = 34)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = firstFileName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val sizeBytes = FilePickerUtils.getFileSizeBytes(context, firstUri)
                                    val sizeStr = FilePickerUtils.formatFileSize(sizeBytes)
                                    val isFailed = message.sendStatus == "FAILED"
                                    if (isFailed && message.isSentByMe) {
                                        Text(
                                            text = "Not sent. Tap to retry",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable { onRetryFailedMessage(message) }
                                        )
                                    } else {
                                        Text(
                                            text = "${firstExt.uppercase()} · $sizeStr",
                                            fontSize = 12.sp,
                                            color = secondaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 2+ Items: Check if all items are images/videos to render as a grid OR use HorizontalPager
                val isMediaGrid = remember(message.attachments) {
                    message.attachments.all { uriStr ->
                        val ext = uriStr.substringAfterLast('.', "").lowercase()
                        uriStr.contains("jpg") || uriStr.contains("png") || uriStr.contains("jpeg") || uriStr.contains("video") || ext in listOf("jpg", "jpeg", "png", "mp4")
                    }
                }

                if (isMediaGrid) {
                    var isExpanded by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .widthIn(min = 200.dp, max = 272.dp)
                            .animateContentSize(animationSpec = tween(200))
                            .padding(bottom = 6.dp)
                    ) {
                        if (n == 2) {
                            // 2 Photos: Side by side 115dp squares
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                mediaUris.take(2).forEachIndexed { idx, uri ->
                                    Surface(
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clickable { onMediaClick(mediaUris, idx) }
                                    ) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = "Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        } else if (n == 3) {
                            // 3 Photos: 1 tall left (115x232), 2 stacked right
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(232.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { onMediaClick(mediaUris, 0) }
                                ) {
                                    AsyncImage(
                                        model = mediaUris[0],
                                        contentDescription = "Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clickable { onMediaClick(mediaUris, 1) }
                                    ) {
                                        AsyncImage(
                                            model = mediaUris[1],
                                            contentDescription = "Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clickable { onMediaClick(mediaUris, 2) }
                                    ) {
                                        AsyncImage(
                                            model = mediaUris[2],
                                            contentDescription = "Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        } else if (n == 4) {
                            // 4 Photos: 2x2 grid of 115dp squares
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    (0..1).forEach { idx ->
                                        Surface(
                                            shape = RoundedCornerShape(11.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clickable { onMediaClick(mediaUris, idx) }
                                        ) {
                                            AsyncImage(
                                                model = mediaUris[idx],
                                                contentDescription = "Photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    (2..3).forEach { idx ->
                                        Surface(
                                            shape = RoundedCornerShape(11.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clickable { onMediaClick(mediaUris, idx) }
                                        ) {
                                            AsyncImage(
                                                model = mediaUris[idx],
                                                contentDescription = "Photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (n >= 5) {
                            // 5+ Photos: Collapsed 2x2 grid with +n overlay (tapping expands inline) or Expanded 3-column grid
                            if (!isExpanded) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        (0..1).forEach { idx ->
                                            Surface(
                                                shape = RoundedCornerShape(11.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clickable { isExpanded = true }
                                            ) {
                                                AsyncImage(
                                                    model = mediaUris[idx],
                                                    contentDescription = "$n photos, tap to expand",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(11.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clickable { isExpanded = true }
                                        ) {
                                            AsyncImage(
                                                model = mediaUris[2],
                                                contentDescription = "$n photos, tap to expand",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(11.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clickable { isExpanded = true }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                AsyncImage(
                                                    model = mediaUris[3],
                                                    contentDescription = "$n photos, tap to expand",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.50f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "+${n - 4}",
                                                        fontSize = 22.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Expanded: 3 columns x N rows of 76dp square tiles
                                val rowCount = (n + 2) / 3
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    for (rowIndex in 0 until rowCount) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            for (colIndex in 0 until 3) {
                                                val idx = rowIndex * 3 + colIndex
                                                if (idx < n) {
                                                    val uri = mediaUris[idx]
                                                    val uriStr = uri.toString()
                                                    val ext = FilePickerUtils.getFileName(context, uri).substringAfterLast('.', "").lowercase()
                                                    val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
                                                    val isVideo = uriStr.contains("video") || ext == "mp4" || mimeType?.startsWith("video/") == true

                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .aspectRatio(1f)
                                                            .clickable { onMediaClick(mediaUris, idx) }
                                                    ) {
                                                        Box(modifier = Modifier.fillMaxSize()) {
                                                            AsyncImage(
                                                                model = uri,
                                                                contentDescription = "Photo/Video",
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                            if (isVideo) {
                                                                Surface(
                                                                    color = Color.Black.copy(alpha = 0.50f),
                                                                    shape = CircleShape,
                                                                    modifier = Modifier
                                                                        .size(24.dp)
                                                                        .align(Alignment.Center)
                                                                ) {
                                                                    Box(contentAlignment = Alignment.Center) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.PlayArrow,
                                                                            contentDescription = "Play Video",
                                                                            tint = Color.White,
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                    }
                                                                }
                                                                val durationSec = message.attachmentDurations[uriStr] ?: 0L
                                                                val durationStr = if (durationSec > 0) {
                                                                    String.format(Locale.getDefault(), "%d:%02d", durationSec / 60, durationSec % 60)
                                                                } else ""
                                                                if (durationStr.isNotEmpty()) Surface(
                                                                    color = Color.Black.copy(alpha = 0.55f),
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    modifier = Modifier
                                                                        .align(Alignment.BottomEnd)
                                                                        .padding(3.dp)
                                                                ) {
                                                                    Text(
                                                                        text = durationStr,
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = Color.White,
                                                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }

                                    // Show Less Button
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp, bottom = 2.dp)
                                            .clickable { isExpanded = false },
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Show less",
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Show less",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = secondaryTextColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 2+ Items Non-media / Mixed: Horizontal Pager
                    Column(
                        modifier = Modifier
                            .widthIn(max = 272.dp)
                            .padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
                        val audioColor = if (isDark) Color(0xFFE87DA3) else Color(0xFFB0446A)

                        val pageCount = (n + 1) / 2
                        val pagerState = rememberPagerState { pageCount }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val startIdx = page * 2
                                for (offset in 0..1) {
                                    val idx = startIdx + offset
                                    if (idx < n) {
                                        val uri = mediaUris[idx]
                                        val fileName = FilePickerUtils.getFileName(context, uri)
                                        val ext = fileName.substringAfterLast('.', "").lowercase()
                                        val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
                                        val isVideo = mimeType?.startsWith("video/") == true || ext == "mp4"
                                        val isAudio = mimeType?.startsWith("audio/") == true || ext in listOf("m4a", "mp3")
                                        val isImage = mimeType?.startsWith("image/") == true || ext in listOf("jpg", "jpeg", "png")
                                        val openPdf = LocalOpenPdf.current

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = cardFillColor,
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clickable {
                                                    if (!isImage && !isVideo && !isAudio) {
                                                        openDocument(context, uri, fileName, openPdf)
                                                    } else {
                                                        onMediaClick(mediaUris, idx)
                                                    }
                                                }
                                        ) {
                                            if (isImage || isVideo) {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                    AsyncImage(
                                                        model = uri,
                                                        contentDescription = "Photo/Video",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    if (isVideo) {
                                                        Surface(
                                                            color = Color.Black.copy(alpha = 0.50f),
                                                            shape = CircleShape,
                                                            modifier = Modifier.size(28.dp).align(Alignment.Center)
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
                                                        val durationSec = message.attachmentDurations[uri.toString()] ?: 0L
                                                        val durationStr = if (durationSec > 0) {
                                                            String.format(Locale.getDefault(), "%d:%02d", durationSec / 60, durationSec % 60)
                                                        } else ""
                                                        if (durationStr.isNotEmpty()) Surface(
                                                            color = Color.Black.copy(alpha = 0.55f),
                                                            shape = RoundedCornerShape(6.dp),
                                                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
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
                                                }
                                            } else if (isAudio) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
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
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = fileName,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = primaryTextColor,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            } else {
                                                Column(
                                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    DocumentTypeTile(extension = ext, isDark = isDark, widthDp = 40, heightDp = 48)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = fileName,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = primaryTextColor,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if (pageCount > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(18.dp).padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        repeat(pageCount) { page ->
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(
                                                        color = if (pagerState.currentPage == page) primaryTextColor else secondaryTextColor.copy(alpha = 0.3f),
                                                        shape = CircleShape
                                                    )
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
            if (!message.isGroupMention && !LocalGroupedTime.current) {
                TextWithInlineTime(text = annotatedText, textColor = primaryTextColor) {
                    InBubbleTime(message, formattedTime, secondaryTextColor, tealAccent, errorColor, onSentToClick)
                }
            } else {
                Text(
                    text = annotatedText,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = primaryTextColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }

        // In-Bubble Footer Line: 5dp below text (not needed when the time sits on the text's last line)
        if ((rawTextToDisplay.isBlank() && !LocalGroupedTime.current) || message.isGroupMention) Row(
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
            InBubbleTime(message, formattedTime, secondaryTextColor, tealAccent, errorColor, onSentToClick)
        }
    }
}

@Composable
private fun InBubbleTime(
    message: ChatMessage,
    formattedTime: String,
    secondaryTextColor: Color,
    tealAccent: Color,
    errorColor: Color,
    onSentToClick: (ChatMessage) -> Unit
) {
    if (LocalGroupedTime.current) return
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

/**
 * Message text with the time on the last line. If the last line has room, the time sits on that
 * line with a fixed gap; if not, the bubble grows (up to its maximum) or the time drops below.
 */
@Composable
private fun TextWithInlineTime(
    text: androidx.compose.ui.text.AnnotatedString,
    textColor: Color,
    time: @Composable () -> Unit
) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val baseStyle = androidx.compose.material3.LocalTextStyle.current.merge(
        androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    )
    Layout(
        content = {
            Text(
                text = text,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = textColor
            )
            Box { time() }
        }
    ) { measurables, constraints ->
        val gap = 10.dp.roundToPx()
        val timePlaceable = measurables[1].measure(androidx.compose.ui.unit.Constraints())
        val textPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        // Measured here, in the same pass, so the width of the last line is always up to date
        val result = measurer.measure(
            text = text,
            style = baseStyle,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = constraints.maxWidth)
        )
        val last = kotlin.math.ceil(result.getLineRight(result.lineCount - 1)).toInt()
        val inlineWidth = last + gap + timePlaceable.width
        val fitsInline = inlineWidth <= constraints.maxWidth
        val width: Int
        val height: Int
        if (fitsInline) {
            width = maxOf(textPlaceable.width, inlineWidth)
            height = maxOf(textPlaceable.height, timePlaceable.height)
        } else {
            width = maxOf(textPlaceable.width, timePlaceable.width)
            height = textPlaceable.height + timePlaceable.height
        }
        val w = width.coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(w, height) {
            textPlaceable.placeRelative(0, 0)
            timePlaceable.placeRelative(w - timePlaceable.width, height - timePlaceable.height)
        }
    }
}

@Composable
private fun AttachmentTimeFooterLine(
    formattedTime: String,
    isSentByMe: Boolean,
    secondaryTextColor: Color,
    tealAccent: Color,
    sendStatus: String = "SENT",
    errorColor: Color = Color.Red,
    onRetryFailedMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (LocalGroupedTime.current) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 3.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formattedTime,
            fontSize = 11.sp,
            color = secondaryTextColor
        )
        if (isSentByMe) {
            Spacer(modifier = Modifier.width(4.dp))
            when (sendStatus) {
                "SENDING" -> {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = "Sending",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
                "FAILED" -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onRetryFailedMessage() }
                    ) {
                        Text(
                            text = "Not sent",
                            fontSize = 11.sp,
                            color = errorColor
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Failed",
                            tint = errorColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Sent",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaGridNoBubble(
    message: ChatMessage,
    formattedTime: String,
    onMediaClick: (List<Uri>, Int) -> Unit,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color,
    isDark: Boolean
) {
    val context = LocalContext.current
    val mediaUris = remember(message.attachments) { message.attachments.map { Uri.parse(it) } }
    val n = mediaUris.size

    Column(modifier = Modifier.width(234.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (n == 1) {
                val uri = mediaUris.first()
                val uriStr = uri.toString()
                val fileName = FilePickerUtils.getFileName(context, uri)
                val ext = fileName.substringAfterLast('.', "").lowercase()
                val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
                val isVideo = uriStr.contains("video") || ext == "mp4" || mimeType?.startsWith("video/") == true

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clickable { onMediaClick(mediaUris, 0) }
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = if (isVideo) "Video" else "Photo",
                        contentScale = ContentScale.Crop,
                        error = rememberVectorPainter(Icons.Default.BrokenImage),
                        modifier = Modifier.fillMaxSize().background(secondaryTextColor.copy(alpha = 0.15f))
                    )

                    if (isVideo) {
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
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else if (n == 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    mediaUris.take(2).forEachIndexed { idx, uri ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable { onMediaClick(mediaUris, idx) }
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Photo",
                                contentScale = ContentScale.Crop,
                                error = rememberVectorPainter(Icons.Default.BrokenImage),
                                modifier = Modifier.fillMaxSize().background(secondaryTextColor.copy(alpha = 0.15f))
                            )
                        }
                    }
                }
            } else if (n == 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(234.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onMediaClick(mediaUris, 0) }
                    ) {
                        AsyncImage(
                            model = mediaUris[0],
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Default.BrokenImage),
                            modifier = Modifier.fillMaxSize().background(secondaryTextColor.copy(alpha = 0.15f))
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable { onMediaClick(mediaUris, 1) }
                        ) {
                            AsyncImage(
                                model = mediaUris[1],
                                contentDescription = "Photo",
                                contentScale = ContentScale.Crop,
                                error = rememberVectorPainter(Icons.Default.BrokenImage),
                                modifier = Modifier.fillMaxSize().background(secondaryTextColor.copy(alpha = 0.15f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable { onMediaClick(mediaUris, 2) }
                        ) {
                            AsyncImage(
                                model = mediaUris[2],
                                contentDescription = "Photo",
                                contentScale = ContentScale.Crop,
                                error = rememberVectorPainter(Icons.Default.BrokenImage),
                                modifier = Modifier.fillMaxSize().background(secondaryTextColor.copy(alpha = 0.15f))
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (0..1).forEach { idx ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clickable { onMediaClick(mediaUris, idx) }
                            ) {
                                AsyncImage(
                                    model = mediaUris[idx],
                                    contentDescription = "Photo",
                                    contentScale = ContentScale.Crop,
                                    error = rememberVectorPainter(Icons.Default.BrokenImage),
                                    modifier = Modifier.fillMaxSize().background(secondaryTextColor.copy(alpha = 0.15f))
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable { onMediaClick(mediaUris, 2) }
                        ) {
                            AsyncImage(
                                model = mediaUris[2],
                                contentDescription = "Photo",
                                contentScale = ContentScale.Crop,
                                error = rememberVectorPainter(Icons.Default.BrokenImage),
                                modifier = Modifier.fillMaxSize().background(secondaryTextColor.copy(alpha = 0.15f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable { onMediaClick(mediaUris, 3) }
                        ) {
                            AsyncImage(
                                model = mediaUris[3],
                                contentDescription = "Photo",
                                contentScale = ContentScale.Crop,
                                error = rememberVectorPainter(Icons.Default.BrokenImage),
                                modifier = Modifier.fillMaxSize().background(secondaryTextColor.copy(alpha = 0.15f))
                            )
                            if (n > 4) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.50f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${n - 4}",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Still uploading: dim the photos and show a spinning ring
            if (message.sendStatus == "SENDING" && message.attachments.any { !it.startsWith("http") }) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), modifier = Modifier.size(46.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 3.dp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        if (message.text.isBlank()) {
            AttachmentTimeFooterLine(
                formattedTime = formattedTime,
                isSentByMe = message.isSentByMe,
                secondaryTextColor = secondaryTextColor,
                tealAccent = tealAccent
            )
        }
    }
}

@Composable
private fun VoiceNoteBubble(
    message: ChatMessage,
    url: String,
    title: String?,
    player: AudioPlayer,
    formattedTime: String,
    bubbleColor: Color,
    bubbleShape: androidx.compose.ui.graphics.Shape,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color,
    isDark: Boolean,
    showFooter: Boolean
) {
    val uri = remember(url) { Uri.parse(url) }
    val totalSecKnown = message.attachmentDurations[url]
        ?: (if (message.attachments.size == 1) message.attachmentDurations.filterKeys { !it.startsWith("sz_") }.values.firstOrNull() else null)
        ?: 0L

    var active by remember(url) { mutableStateOf(false) }   // this note is the one loaded in the player
    var paused by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(false) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var positionSec by remember(url) { mutableLongStateOf(0L) }
    var durationSec by remember(url) { mutableLongStateOf(totalSecKnown) }
    var dragging by remember(url) { mutableStateOf(false) }
    var speed by remember(url) { mutableFloatStateOf(1f) }

    // Follow the player while this note is active. Starting another note makes this one reset.
    LaunchedEffect(active) {
        while (active) {
            if (player.currentSource() != url) {
                active = false
                paused = false
                loading = false
                progress = 0f
                positionSec = 0L
                break
            }
            val ready = player.isPrepared()
            loading = !ready
            if (ready) {
                paused = !player.isPlaying()
                val total = player.getDuration()
                val current = player.getCurrentPosition()
                if (total > 0) {
                    durationSec = (total / 1000).toLong()
                    if (!dragging) progress = (current.toFloat() / total).coerceIn(0f, 1f)
                    positionSec = (current / 1000).toLong()
                }
            }
            delay(80)
        }
    }

    // Stop playing when the bubble leaves the screen
    DisposableEffect(url) {
        onDispose { if (player.currentSource() == url) player.stop() }
    }

    fun fmt(sec: Long) = String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60)

    fun togglePlay() {
        if (active && player.currentSource() == url) {
            if (player.isPlaying()) {
                player.pause(); paused = true
            } else {
                player.resume(); paused = false
            }
        } else {
            player.playFile(uri) {
                active = false
                paused = false
                loading = false
                progress = 0f
                positionSec = 0L
            }
            player.setSpeed(speed)
            active = true
            paused = false
            loading = true
        }
    }

    val trackColor = secondaryTextColor.copy(alpha = 0.35f)
    val lineColor = tealAccent
    // Still being uploaded: the file is local and the message is not sent yet
    val sending = message.sendStatus == "SENDING" && !url.startsWith("http")

    @Composable
    fun ProgressLine(modifier: Modifier) {
        androidx.compose.foundation.Canvas(
            modifier = modifier
                .pointerInput(url) {
                    detectTapGestures { pos ->
                        if (active && player.isPrepared()) {
                            val frac = (pos.x / size.width).coerceIn(0f, 1f)
                            progress = frac
                            player.seekTo((frac * player.getDuration()).toInt())
                        }
                    }
                }
                .pointerInput(url) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            if (active && player.isPrepared()) {
                                player.seekTo((progress * player.getDuration()).toInt())
                            }
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            if (active) {
                                progress = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                    )
                }
        ) {
            val pad = 6.dp.toPx()
            val x0 = pad
            val x1 = size.width - pad
            val cy = size.height / 2f
            val stroke = 4.dp.toPx()
            val knobX = x0 + (x1 - x0) * progress
            drawLine(
                color = trackColor,
                start = androidx.compose.ui.geometry.Offset(x0, cy),
                end = androidx.compose.ui.geometry.Offset(x1, cy),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            if (progress > 0f) {
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(x0, cy),
                    end = androidx.compose.ui.geometry.Offset(knobX, cy),
                    strokeWidth = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            drawCircle(
                color = lineColor,
                radius = 6.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(knobX, cy)
            )
        }

    }

    Column(
        modifier = Modifier.width(234.dp),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            shadowElevation = if (!isDark) 1.5.dp else 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                if (!title.isNullOrBlank()) {
                    val ext = title.substringAfterLast('.', "").uppercase()
                    val sizeBytes = message.attachmentDurations["sz_" + url]
                        ?: (if (!url.startsWith("http")) FilePickerUtils.getFileSizeBytes(LocalContext.current, uri) else 0L)
                    val sizeStr = if (sizeBytes > 0L) FilePickerUtils.formatFileSize(sizeBytes) else ""
                    val sub = listOf(ext, if (durationSec > 0) fmt(durationSec) else "", sizeStr)
                        .filter { it.isNotBlank() }.joinToString(" \u00B7 ")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(tealAccent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = tealAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (sub.isNotBlank()) {
                                Text(text = sub, fontSize = 12.sp, color = secondaryTextColor, maxLines = 1)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(enabled = !sending) { togglePlay() },
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (loading || sending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = tealAccent
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (active && !paused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (active && !paused) "Pause audio" else "Play audio",
                                        tint = primaryTextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProgressLine(Modifier.weight(1f).height(22.dp))
                        Text(
                            text = if (active) fmt(positionSec) + (if (durationSec > 0) " / " + fmt(durationSec) else "") else fmt(durationSec),
                            fontSize = 11.sp,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                        if (active && !loading) {
                            val label = if (speed == 1.5f) "1.5x" else if (speed == 2f) "2x" else "1x"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = secondaryTextColor.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clickable {
                                        speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
                                        player.setSpeed(speed)
                                    }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryTextColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play / pause button
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(enabled = !sending) { togglePlay() },
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (loading || sending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = lineColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (active && !paused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (active && !paused) "Pause voice message" else "Play voice message",
                                        tint = primaryTextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Progress line: tap or drag to jump to a point
                            ProgressLine(Modifier.weight(1f).height(22.dp))

                            Row(
                                modifier = Modifier.padding(start = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (active) fmt(positionSec) else fmt(durationSec),
                                    fontSize = 11.sp,
                                    color = secondaryTextColor,
                                    modifier = Modifier.widthIn(min = 28.dp)
                                )
                                if (active && !loading) {
                                    val label = if (speed == 1.5f) "1.5x" else if (speed == 2f) "2x" else "1x"
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = secondaryTextColor.copy(alpha = 0.18f),
                                        modifier = Modifier.clickable {
                                            speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
                                            player.setSpeed(speed)
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFooter) {
            AttachmentTimeFooterLine(
                formattedTime = formattedTime,
                isSentByMe = message.isSentByMe,
                secondaryTextColor = secondaryTextColor,
                tealAccent = tealAccent,
                sendStatus = message.sendStatus
            )
        }
    }
}

@Composable
private fun DocumentRowsNoBubble(
    message: ChatMessage,
    formattedTime: String,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardFillColor: Color,
    tealAccent: Color,
    isDark: Boolean
) {
    val context = LocalContext.current
    val mediaUris = remember(message.attachments) { message.attachments.map { Uri.parse(it) } }

    Column(
        modifier = Modifier.width(234.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        mediaUris.forEach { uri ->
            val fileName = FilePickerUtils.getFileName(context, uri)
            val ext = fileName.substringAfterLast('.', "").uppercase()
            val sizeBytes = FilePickerUtils.getFileSizeBytes(context, uri)
            val sizeStr = FilePickerUtils.formatFileSize(sizeBytes)
            val openPdf = LocalOpenPdf.current

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = cardFillColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openDocument(context, uri, fileName, openPdf) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DocumentTypeTile(extension = ext.lowercase(), isDark = isDark, widthDp = 36, heightDp = 44)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$ext · $sizeStr",
                            fontSize = 12.sp,
                            color = secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (message.sendStatus == "SENDING" && !uri.toString().startsWith("http")) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = tealAccent
                        )
                    }
                }
            }
        }

        if (message.text.isBlank()) {
            AttachmentTimeFooterLine(
                formattedTime = formattedTime,
                isSentByMe = message.isSentByMe,
                secondaryTextColor = secondaryTextColor,
                tealAccent = tealAccent
            )
        }
    }
}