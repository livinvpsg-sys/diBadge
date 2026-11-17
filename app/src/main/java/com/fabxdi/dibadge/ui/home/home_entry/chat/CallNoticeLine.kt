package com.fabxdi.dibadge.ui.home.home_entry.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The small centred line the chat shows for a finished call, like "Voice call · 2:41" or "Video call · no answer". */
@Composable
fun CallNoticeLine(message: ChatMessage, secondaryTextColor: Color) {
    val isVideo = message.text.startsWith("Video")
    val time = runCatching {
        message.timestamp.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }.getOrDefault("")
    ChatNoticePill(
        text = if (time.isNotEmpty()) "${message.text} · $time" else message.text,
        textColor = secondaryTextColor,
        leading = {
            Icon(
                imageVector = if (isVideo) Icons.Outlined.Videocam else Icons.Outlined.Call,
                contentDescription = null,
                tint = secondaryTextColor,
                modifier = Modifier.size(14.dp)
            )
        }
    )
}