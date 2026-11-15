package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.AnnouncementItem
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun pinnedDayText(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

/**
 * The pinned announcement at the top of a group chat. Same card as on the Announcements screen
 * (fixed height, one line of text). With several announcements the card can be swiped sideways.
 * Tapping the card opens that announcement on the Announcements screen, which marks it as read,
 * and the card then no longer shows for that person. The ✕ hides it from this chat for good;
 * the person who posted it sees it until they close it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedAnnouncementBar(
    items: List<AnnouncementItem>,
    myUid: String,
    canManage: Boolean,
    memberUids: Set<String>,
    onOpen: (AnnouncementItem) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pinned_announcements", Context.MODE_PRIVATE) }
    val prefKey = "dismissed_$myUid"
    var dismissed by remember {
        mutableStateOf<Set<String>>(prefs.getStringSet(prefKey, emptySet())?.toSet() ?: emptySet())
    }

    // So an announcement drops off the chat when its time runs out
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val visible = items.filter {
        it.isOngoing(now) &&
                it.id !in dismissed &&
                (it.authorUid == myUid || !it.readAt.containsKey(myUid))
    }
    if (visible.isEmpty()) return

    val dc = DiBadgeTheme.colors
    val pagerState = rememberPagerState(pageCount = { visible.size })

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 8.dp,
            key = { page -> visible.getOrNull(page)?.id ?: page }
        ) { page ->
            val item = visible[page]
            PinnedCard(
                item = item,
                position = page + 1,
                total = visible.size,
                myUid = myUid,
                canManage = canManage,
                memberUids = memberUids,
                onOpen = { onOpen(item) },
                onClose = {
                    val next = dismissed + item.id
                    dismissed = next
                    prefs.edit().putStringSet(prefKey, next).apply()
                }
            )
        }
        if (visible.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(visible.size) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.5.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (i == pagerState.currentPage) dc.accent else dc.tabInactive.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedCard(
    item: AnnouncementItem,
    position: Int,
    total: Int,
    myUid: String,
    canManage: Boolean,
    memberUids: Set<String>,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dc = DiBadgeTheme.colors

    val readCount = memberUids.count { item.readAt.containsKey(it) }
    val totalMembers = memberUids.size.coerceAtLeast(1)
    val roleLabel = when (item.authorRole.lowercase()) {
        "owner" -> " (Owner)"
        "admin" -> " (Admin)"
        else -> ""
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, dc.accent.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .clickable { onOpen() }
                .height(96.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = dc.accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (item.originGroupName.isNotBlank()) {
                        "From ${item.originGroupName} · ${item.authorName}$roleLabel · ${pinnedDayText(item.createdAt)}"
                    } else {
                        "${item.authorName}$roleLabel · ${pinnedDayText(item.createdAt)}"
                    },
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (total > 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$position of $total",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = dc.accent
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = item.text,
                fontSize = 14.sp,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    !item.mustAck -> {
                        Text(
                            text = "No confirmation needed",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                    canManage -> {
                        Text("Read $readCount of $totalMembers", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(10.dp))
                        LinearProgressIndicator(
                            progress = { readCount.toFloat() / totalMembers },
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = dc.accentFill,
                            trackColor = colors.outline.copy(alpha = 0.4f)
                        )
                    }
                    else -> {
                        Text(
                            text = "Tap to read",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}