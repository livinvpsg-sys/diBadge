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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
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
    onLocationClick: (ChatMessage) -> Unit = {},
    onStopLiveLocation: (String) -> Unit = {},
    onVoteOption: (messageId: String, optionId: String) -> Unit = { _, _ -> },
    onViewVotesClick: (ChatMessage) -> Unit = {},
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
    isDark: Boolean
) {
    val context = LocalContext.current
    val formattedTime = remember(message.timestamp) { message.timestamp.format(timeFormatter).lowercase() }

    Column {
        if (message.isVoiceNote) {
            // Voice Message Bubble
            val audioColor = if (isDark) Color(0xFFE87DA3) else Color(0xFFB0446A)
            val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
            var isPlaying by remember { mutableStateOf(false) }
            var playSpeed by remember { mutableFloatStateOf(1.0f) }

            val totalSec = message.attachmentDurations.values.firstOrNull() ?: 21L
            val totalStr = String.format(Locale.getDefault(), "%d:%02d", totalSec / 60, totalSec % 60)

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
                                    }
                                    player.setSpeed(playSpeed)
                                } else {
                                    player.stop()
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
                            val isPlayed = isPlaying && idx < 12
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
                        Text(
                            text = totalStr,
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )

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
            val isMediaGrid = remember(message.attachments) {
                message.attachments.all { uriStr ->
                    val ext = uriStr.substringAfterLast('.', "").lowercase()
                    uriStr.contains("jpg") || uriStr.contains("png") || uriStr.contains("jpeg") || uriStr.contains("video") || ext in listOf("jpg", "jpeg", "png", "mp4")
                }
            }

            if (isMediaGrid) {
                val n = mediaUris.size
                var isExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .width(232.dp)
                        .padding(bottom = 6.dp)
                ) {
                    if (n == 1) {
                        // 1 Photo/Video
                        val firstUri = mediaUris.first()
                        val firstUriStr = firstUri.toString()
                        val firstExt = FilePickerUtils.getFileName(context, firstUri).substringAfterLast('.', "").lowercase()
                        val isVideo = firstUriStr.contains("video") || firstExt == "mp4"

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
                                } else "0:42"

                                Surface(
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
                    } else if (n == 2) {
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
                    } else if (n == 4 || (n >= 5 && !isExpanded)) {
                        // 4 Photos (or 5+ collapsed 2x2 grid with +n overlay on 4th tile)
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
                                            .clickable {
                                                if (n >= 5 && !isExpanded) isExpanded = true else onMediaClick(mediaUris, idx)
                                            }
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
                                Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clickable {
                                            if (n >= 5 && !isExpanded) isExpanded = true else onMediaClick(mediaUris, 2)
                                        }
                                ) {
                                    AsyncImage(
                                        model = mediaUris[2],
                                        contentDescription = "Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clickable {
                                            if (n >= 5 && !isExpanded) isExpanded = true else onMediaClick(mediaUris, 3)
                                        }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = mediaUris[3],
                                            contentDescription = "Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (n >= 5) {
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
                    } else {
                        // Expanded Grid for 5+ items (3 columns, 76dp squares, 2dp gaps)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            mediaUris.chunked(3).forEach { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    chunk.forEachIndexed { colIdx, uri ->
                                        val actualIdx = mediaUris.indexOf(uri)
                                        Surface(
                                            shape = RoundedCornerShape(11.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clickable { onMediaClick(mediaUris, actualIdx) }
                                        ) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = "Photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    repeat(3 - chunk.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            // "Show less" row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isExpanded = false }
                                    .padding(top = 6.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
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
            } else {
                // Document / Audio Stacked Rows (Card layout: 232dp wide, 10dp corners, 3dp apart)
                val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)

                Column(
                    modifier = Modifier
                        .width(232.dp)
                        .padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    mediaUris.forEach { uri ->
                        val fileName = FilePickerUtils.getFileName(context, uri)
                        val ext = fileName.substringAfterLast('.', "").lowercase()

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cardFillColor,
                            border = BorderStroke(1.dp, outlineColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { FilePickerUtils.openFileInExternalApp(context, uri, fileName) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DocumentTypeTile(extension = ext, isDark = isDark)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val sizeBytes = FilePickerUtils.getFileSizeBytes(context, uri)
                                    val sizeStr = FilePickerUtils.formatFileSize(sizeBytes)
                                    Text(
                                        text = "${ext.uppercase()} · $sizeStr",
                                        fontSize = 12.sp,
                                        color = secondaryTextColor
                                    )
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
    isDark: Boolean,
    modifier: Modifier = Modifier,
    widthDp: Int = 28,
    heightDp: Int = 34
) {
    val ext = extension.lowercase()
    val (label, color, fontSize) = when (ext) {
        "doc", "docx" -> Triple("W", if (isDark) Color(0xFF5B8DEF) else Color(0xFF2F5FC0), 13.sp)
        "pdf" -> Triple("PDF", if (isDark) Color(0xFFE5534B) else Color(0xFFC4433A), 7.5.sp)
        "txt" -> Triple("TXT", if (isDark) Color(0xFF8A9199) else Color(0xFF65707A), 7.5.sp)
        "rtf" -> Triple("RTF", if (isDark) Color(0xFF8C8FE6) else Color(0xFF5558B8), 7.5.sp)
        "odt" -> Triple("ODT", if (isDark) Color(0xFF4FB0E6) else Color(0xFF1E7DB5), 7.5.sp)
        "xls", "xlsx" -> Triple("X", if (isDark) Color(0xFF3FB27F) else Color(0xFF1F7A4F), 13.sp)
        "csv" -> Triple("CSV", if (isDark) Color(0xFF9BCB4F) else Color(0xFF5E8F1E), 7.5.sp)
        "ppt", "pptx" -> Triple("P", if (isDark) Color(0xFFF0883E) else Color(0xFFC25A12), 13.sp)
        "html", "htm" -> Triple("</>", if (isDark) Color(0xFFB07CE8) else Color(0xFF7A47B8), 9.sp)
        "zip" -> Triple("ZIP", if (isDark) Color(0xFFE8B03A) else Color(0xFFA8780E), 7.5.sp)
        "rar" -> Triple("RAR", if (isDark) Color(0xFFD9835F) else Color(0xFFA84F2E), 7.5.sp)
        "pages" -> Triple("Pg", if (isDark) Color(0xFF82A8FF) else Color(0xFF3C6FD9), 12.sp)
        "ods" -> Triple("ODS", if (isDark) Color(0xFF5FD39A) else Color(0xFF23915E), 7.5.sp)
        "numbers" -> Triple("Nm", if (isDark) Color(0xFF58C26B) else Color(0xFF2E8B3F), 12.sp)
        "odp" -> Triple("ODP", if (isDark) Color(0xFFF6A25D) else Color(0xFFC76B1E), 7.5.sp)
        "key" -> Triple("Key", if (isDark) Color(0xFFF7B267) else Color(0xFFC98035), 9.sp)
        "xml" -> Triple("XML", if (isDark) Color(0xFF8F9BC9) else Color(0xFF505C8F), 7.5.sp)
        "json" -> Triple("{ }", if (isDark) Color(0xFFCDAA5A) else Color(0xFF8C6B15), 10.sp)
        "7z" -> Triple("7Z", if (isDark) Color(0xFFC9C14E) else Color(0xFF8A8410), 8.5.sp)
        "tar" -> Triple("TAR", if (isDark) Color(0xFFC9A27A) else Color(0xFF8A6A3E), 7.5.sp)
        "gz" -> Triple("GZ", if (isDark) Color(0xFFB58B6A) else Color(0xFF7A5436), 8.5.sp)
        "dwg" -> Triple("DWG", if (isDark) Color(0xFF7FA6D1) else Color(0xFF386C9F), 7.5.sp)
        "dxf" -> Triple("DXF", if (isDark) Color(0xFFA3B8CF) else Color(0xFF5A728C), 7.5.sp)
        "ifc" -> Triple("IFC", if (isDark) Color(0xFFC58BD1) else Color(0xFF8A3E99), 7.5.sp)
        "rvt" -> Triple("RVT", if (isDark) Color(0xFF5E86E0) else Color(0xFF2F55B0), 7.5.sp)
        "skp" -> Triple("SKP", if (isDark) Color(0xFFE0788F) else Color(0xFFB03F5A), 7.5.sp)
        else -> {
            val mark = ext.uppercase().take(3).ifBlank { "DOC" }
            val fSize = if (mark.length > 2) 7.5.sp else 12.sp
            Triple(mark, if (isDark) Color(0xFF8A9199) else Color(0xFF65707A), fSize)
        }
    }

    Surface(
        modifier = modifier.size(width = widthDp.dp, height = heightDp.dp),
        shape = RoundedCornerShape((heightDp * 0.16f).dp),
        color = color
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
