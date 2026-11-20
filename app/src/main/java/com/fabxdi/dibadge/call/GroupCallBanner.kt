package com.fabxdi.dibadge.call

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

/**
 * "Group voice call in progress · Join": shown at the top of a group chat while a call is running there.
 * Anyone in the group can join (or go back to the call they are already on).
 */
@Composable
fun GroupCallBanner(groupId: String, textColor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var call by remember(groupId) { mutableStateOf<GroupCallDoc?>(null) }
    LaunchedEffect(groupId) {
        GroupCallRepository().observeActive(groupId).collect { call = it }
    }
    val engineState by CallEngine.get(context).state.collectAsState()
    val c = call ?: return
    val onThis = engineState.isGroup && engineState.callId == c.id &&
            engineState.phase != CallPhase.IDLE && engineState.phase != CallPhase.ENDED
    val accent = DiBadgeTheme.colors.accent
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(shape)
            .border(1.dp, accent, shape)
            .clickable {
                if (onThis) CallActivity.open(context) else CallActivity.joinGroup(context, c.id)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(if (c.isVideo) Icons.Outlined.Videocam else Icons.Outlined.Call, null, tint = accent, modifier = Modifier.size(18.dp))
            Text(
                text = (if (c.isVideo) "Group video call" else "Group voice call") +
                        if (c.participants.isNotEmpty()) " · ${c.participants.size} in the call" else " · starting",
                fontSize = 13.sp, color = textColor, modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(if (onThis) "Return" else "Join", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}