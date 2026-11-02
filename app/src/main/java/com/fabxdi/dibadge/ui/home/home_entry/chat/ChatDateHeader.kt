package com.fabxdi.dibadge.ui.home.home_entry.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.fabxdi.dibadge.ui.home.ChatAvatar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DateHeader(
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

@Composable
fun GroupCreatedHeader(
    createdDateText: String,
    secondaryTextColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Group created $createdDateText",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = secondaryTextColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChatStartedHeader(
    createdDateText: String,
    secondaryTextColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Chat started $createdDateText",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = secondaryTextColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Shown in the middle of the chat while there are no messages yet:
 * avatar, name, when it started, and a friendly nudge to send the first message.
 */
@Composable
fun EmptyChatState(
    title: String,
    idForColor: String,
    photoUrl: String,
    isOneToOne: Boolean,
    createdDateText: String,
    memberCount: Int,
    firstName: String,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val pillBgColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChatAvatar(
            title = title,
            idForColor = idForColor,
            size = 88.dp,
            photoUrl = photoUrl
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = primaryTextColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!isOneToOne && memberCount > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (memberCount == 1) "1 member" else "$memberCount members",
                fontSize = 14.sp,
                color = secondaryTextColor,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            color = pillBgColor,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 0.dp
        ) {
            Text(
                text = if (isOneToOne) "Chat started $createdDateText" else "Group created $createdDateText",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isOneToOne && firstName.isNotBlank()) {
                "Say hello to $firstName.\nYour messages will appear here."
            } else {
                "Send the first message to get everyone talking.\nYour messages will appear here."
            },
            fontSize = 14.sp,
            color = secondaryTextColor,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
