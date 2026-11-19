package com.fabxdi.dibadge.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.util.capitalizeFirst
import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

fun buildSearchAnnotatedString(
    text: String,
    query: String,
    primaryTextColor: Color,
    amberHighlightColor: Color
): AnnotatedString {
    if (query.isBlank() || text.isBlank()) {
        return AnnotatedString(text)
    }

    val normalizedText = text.lowercase()
    val normalizedQuery = query.lowercase().trim()

    return buildAnnotatedString {
        var startIdx = 0
        while (startIdx < text.length) {
            val matchIdx = normalizedText.indexOf(normalizedQuery, startIdx)
            if (matchIdx == -1) {
                append(text.substring(startIdx))
                break
            } else {
                if (matchIdx > startIdx) {
                    append(text.substring(startIdx, matchIdx))
                }
                val endIdx = (matchIdx + normalizedQuery.length).coerceAtMost(text.length)
                withStyle(
                    style = SpanStyle(
                        background = amberHighlightColor,
                        color = primaryTextColor
                    )
                ) {
                    append(text.substring(matchIdx, endIdx))
                }
                startIdx = endIdx
            }
        }
    }
}

@Composable
fun HomeSearchRow(
    title: String,
    subtitle: String,
    isGroup: Boolean,
    photoUrl: String = "",
    idForColor: String,
    query: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.onBackground.red > 0.5f

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val outlineColor = colors.outline
    val amberHighlightColor = if (isDark) Color(0xFFFFB300).copy(alpha = 0.35f) else Color(0xFFFFF59D)

    val avatarShape = if (isGroup) RoundedCornerShape(12.dp) else CircleShape
    val avatarColor = remember(idForColor) { UserColorUtils.getColorForName(idForColor) }

    val annotatedTitle = remember(title, query, primaryTextColor, amberHighlightColor) {
        buildSearchAnnotatedString(title, query, primaryTextColor, amberHighlightColor)
    }

    val annotatedSubtitle = remember(subtitle, query, primaryTextColor, amberHighlightColor) {
        buildSearchAnnotatedString(subtitle, query, primaryTextColor, amberHighlightColor)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar (44dp)
        Surface(
            modifier = Modifier.size(44.dp),
            shape = avatarShape,
            color = avatarColor,
            border = BorderStroke(1.dp, outlineColor)
        ) {
            if (photoUrl.isNotBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = title.take(1).uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Text Column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = annotatedTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = annotatedSubtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A chat is a group unless it is person to person. */
fun isGroupEntry(entry: HomeEntryEntity, directIds: Set<String> = emptySet()): Boolean {
    // Same rule as the chat screen: only a chat marked direct is person to person
    val sub = entry.subtitle.trim().lowercase()
    if (sub in listOf("direct", "chat", "one-to-one")) return false
    return entry.id.toString() !in directIds
}

/** Right-hand time: today as a clock time, yesterday, this week as a weekday, older as a date. */
private fun homeTimeLabel(epochMs: Long, timeFormatter: DateTimeFormatter): String {
    if (epochMs <= 0L) return ""
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = LocalDate.now(zone)
    val day = dt.toLocalDate()
    val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo <= 0L -> dt.format(timeFormatter)
        daysAgo == 1L -> "Yesterday"
        daysAgo < 7L -> dt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        day.year == today.year -> dt.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        else -> dt.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeEntryCard(
    entry: HomeEntryEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    preview: ChatPreview? = null,
    isGroup: Boolean = isGroupEntry(entry),
    photoUrl: String = "",
    draft: String = ""
) {
    val colors = MaterialTheme.colorScheme
    val dc = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant

    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }

    // Stable color hash for group ID
    val avatarColor = remember(entry.id) {
        UserColorUtils.getColorForName(entry.id.toString())
    }

    val unread = preview?.unread ?: 0
    val hasUnread = unread > 0
    val timeText = remember(preview?.at, entry.timestamp) {
        val ms = preview?.at?.takeIf { it > 0L }
            ?: entry.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        homeTimeLabel(ms, timeFormatter)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isSelected) primaryTextColor.copy(alpha = 0.10f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: 44dp Avatar Circle, with a small group badge at the bottom right for groups
            Box(modifier = Modifier.size(44.dp)) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = avatarColor
                ) {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = entry.title.take(1).uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                if (isGroup) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(20.dp)
                            .background(colors.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Groups,
                            contentDescription = "Group",
                            tint = dc.accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Middle: name and the last message
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = capitalizeFirst(entry.title),
                        fontSize = 16.sp,
                        fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (draft.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = dc.accent, fontWeight = FontWeight.SemiBold)) { append("Draft: ") }
                            append(draft.replace(Regex("\\s+"), " ").trim())
                        },
                        fontSize = 13.5.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (preview != null) {
                    val prefix = when {
                        preview.isCall -> ""
                        preview.senderUid == myUid -> "You: "
                        isGroup && preview.senderName.isNotBlank() -> preview.senderName.trim().substringBefore(" ") + ": "
                        else -> ""
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = prefix + preview.text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (hasUnread) primaryTextColor.copy(alpha = 0.85f) else secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: time and unread count, or the selection tick
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = primaryTextColor,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = timeText,
                        fontSize = 12.sp,
                        fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (hasUnread) dc.accent else secondaryTextColor
                    )
                    if (hasUnread) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                .background(dc.accentFill, RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unread > 99) "99+" else unread.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeBackupCard(
    onSetUp: () -> Unit,
    onNotNow: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val tealAccent = diBadgeColors.accent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Back up your media to Google Drive",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Keep a private copy of your photos and videos in your Google storage.",
                fontSize = 13.sp,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Not now",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryTextColor,
                    modifier = Modifier
                        .clickable { onNotNow() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Set up",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tealAccent,
                    modifier = Modifier
                        .clickable { onSetUp() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}