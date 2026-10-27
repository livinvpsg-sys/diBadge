package com.fabxdi.dibadge.ui.home.home_entry.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChatComment(
    val id: String,
    val threadId: String,
    val text: String,
    val senderName: String,
    val senderUid: String,
    val groupName: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() {
            val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault())
            return dt.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        }

    val date: LocalDate
        get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault()).toLocalDate()
}

@Composable
private fun ReplyDateHeader(
    date: LocalDate,
    isDark: Boolean,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val sevenDaysAgo = today.minusDays(7)

    val text = when {
        date == today -> "Today"
        date == yesterday -> "Yesterday"
        date.isAfter(sevenDaysAgo) -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }

    val pillBgColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = pillBgColor,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = if (!isDark) 1.5.dp else 1.dp,
            tonalElevation = 0.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentThreadSheet(
    threadId: String,
    comments: List<ChatComment>,
    onPostComment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var commentInput by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val isDark = colors.onBackground.red > 0.5f

    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val cardFillColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)
    val tealAccent = if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)

    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.60f

    val groupedComments = remember(comments) {
        comments.groupBy { it.date }.toSortedMap()
    }

    val isScrolling = listState.isScrollInProgress
    var isFloatingDateVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            isFloatingDateVisible = true
        } else {
            delay(1500)
            isFloatingDateVisible = false
        }
    }

    val floatingAlpha by animateFloatAsState(
        targetValue = if (isFloatingDateVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300)
    )

    val topVisibleDate = remember(listState.firstVisibleItemIndex, comments) {
        if (comments.isNotEmpty()) {
            val firstIdx = listState.firstVisibleItemIndex
            val comment = comments.getOrNull(firstIdx.coerceIn(0, comments.size - 1))
            comment?.date ?: LocalDate.now()
        } else LocalDate.now()
    }

    val commonEmojis = remember {
        listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😋", "😛", "😝", "😜", "🤪", "🤨",
            "🧐", "🤓", "😎", "🤩", "👍", "👎", "👌", "🤝", "🙏", "👏",
            "🙌", "👐", "🤲", "💼", "📁", "📂", "📅", "📆", "📈", "📊"
        )
    }

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = cardColor,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.50f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = outlineColor
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Title: 16sp semibold
            Text(
                text = "Replies",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            // Visibility Helper Line
            Text(
                text = "Replies are visible to all groups this message was sent to",
                fontSize = 12.sp,
                color = secondaryTextColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Replies List (Oldest to Newest) - Scrollable inside max 60% height
            Box(modifier = Modifier.weight(1f, fill = false)) {
                if (comments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No replies yet",
                            fontSize = 14.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        groupedComments.forEach { (date, dateComments) ->
                            item {
                                ReplyDateHeader(
                                    date = date,
                                    isDark = isDark,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }
                            items(dateComments) { comment ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // 28dp Round Avatar
                                    Surface(
                                        modifier = Modifier.size(28.dp),
                                        shape = CircleShape,
                                        color = cardFillColor,
                                        border = BorderStroke(1.dp, outlineColor)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = comment.senderName.take(1).uppercase(),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = primaryTextColor
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        // Header Line: Name + Time + Group Name
                                        val isUser = comment.senderUid == currentUserId || comment.senderName == "Me"
                                        val nameText = if (isUser) "You" else comment.senderName
                                        val nameColor = if (isUser) secondaryTextColor else UserColorUtils.getColorForName(comment.senderName, isDark)
                                        val suffixText = "· ${comment.formattedTime}" + if (!comment.groupName.isNullOrBlank()) " · ${comment.groupName}" else ""

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = nameText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = nameColor
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = suffixText,
                                                fontSize = 11.sp,
                                                color = dimTextColor
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // Comment Text (15sp, 21sp line height)
                                        Text(
                                            text = comment.text,
                                            fontSize = 15.sp,
                                            lineHeight = 21.sp,
                                            color = primaryTextColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating date pill while scrolling in Replies sheet
                if (floatingAlpha > 0f && comments.isNotEmpty()) {
                    ReplyDateHeader(
                        date = topVisibleDate,
                        isDark = isDark,
                        secondaryTextColor = secondaryTextColor,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                            .graphicsLayer { alpha = floatingAlpha }
                    )
                }
            }

            // Fixed Composer at Bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp, bottom = 20.dp)
            ) {
                if (showEmojiPicker) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(bottom = 8.dp),
                        color = cardColor,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(40.dp),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(commonEmojis.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clickable { commentInput += commonEmojis[index] },
                                    contentAlignment = Alignment.Center
                                ) { Text(text = commonEmojis[index], fontSize = 24.sp) }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Field: 48dp high, fully rounded 24dp, raised fill
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 110.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = if (isDark) Color(0xFF222831) else Color(0xFFE8ECEF)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Emoji Icon (24dp, secondary)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { showEmojiPicker = !showEmojiPicker },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SentimentSatisfiedAlt,
                                    contentDescription = "Emoji",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (commentInput.isEmpty()) {
                                    Text(
                                        text = "Add a reply",
                                        fontSize = 16.sp,
                                        color = dimTextColor
                                    )
                                }
                                BasicTextField(
                                    value = commentInput,
                                    onValueChange = { commentInput = it },
                                    maxLines = 4,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        color = primaryTextColor
                                    ),
                                    cursorBrush = SolidColor(primaryTextColor),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Icon (24dp, teal when active, dim when empty) in 44dp touch area
                    val canSend = commentInput.trim().isNotEmpty()
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(enabled = canSend) {
                                val textToSend = commentInput.trim()
                                if (textToSend.isNotEmpty()) {
                                    onPostComment(textToSend)
                                    commentInput = ""
                                    showEmojiPicker = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Post reply",
                            tint = if (canSend) tealAccent else dimTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
