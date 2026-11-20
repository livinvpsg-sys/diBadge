package com.fabxdi.dibadge.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/** What the ring screen needs to know about the call that is ringing. */
data class GroupRingInfo(val callId: String, val callerName: String, val video: Boolean)

/**
 * Full-screen "Name is calling the group", like an ordinary incoming call. Join answers it,
 * Decline turns the ring away. It closes by itself when the call ends or after 45 seconds.
 */
@Composable
fun GroupRingScreen(info: GroupRingInfo, onJoin: () -> Unit, onDecline: () -> Unit, onGone: () -> Unit) {
    val bg = Color(0xFF1A1B1E)
    val primary = Color(0xFFF2F2F4)
    val muted = Color(0xFFA3A3AA)
    val green = Color(0xFF2FA866)
    val red = Color(0xFFD6453B)
    val accent = DiBadgeTheme.colors.accent

    var groupTitle by remember { mutableStateOf("") }
    var onCall by remember { mutableStateOf(0) }

    LaunchedEffect(info.callId) {
        val repo = GroupCallRepository()
        val doc = runCatching { repo.getCall(info.callId) }.getOrNull()
        if (doc == null || !doc.isActive) { onGone(); return@LaunchedEffect }
        if (doc.groupId.isNotBlank()) {
            val snap = runCatching { FirebaseFirestore.getInstance().collection("personalGroups").document(doc.groupId).get().await() }.getOrNull()
            groupTitle = snap?.getString("name").orEmpty().ifBlank { snap?.getString("title").orEmpty() }
        }
    }
    LaunchedEffect(info.callId) {
        GroupCallRepository().observePeers(info.callId).collect { onCall = it.size }
    }
    LaunchedEffect(info.callId) {
        GroupCallRepository().observeCall(info.callId).collect { d -> if (d != null && !d.isActive) onGone() }
    }
    LaunchedEffect(info.callId) {
        delay(45_000)
        onGone()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))
        Text(
            if (info.video) "diBadge group video call" else "diBadge group voice call",
            fontSize = 14.sp, color = muted
        )
        Spacer(Modifier.height(32.dp))
        Box(modifier = Modifier.size(132.dp)) {
            Box(
                modifier = Modifier.size(132.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    info.callerName.trim().firstOrNull()?.uppercase() ?: "?",
                    fontSize = 54.sp, fontWeight = FontWeight.Medium, color = accent
                )
            }
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).size(40.dp).clip(CircleShape).background(bg),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Groups, null, tint = muted, modifier = Modifier.size(24.dp)) }
        }
        Spacer(Modifier.height(26.dp))
        Text(info.callerName, fontSize = 26.sp, color = primary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("is calling the group", fontSize = 15.sp, color = muted)
        if (groupTitle.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(groupTitle, fontSize = 17.sp, color = primary, textAlign = TextAlign.Center)
        }
        if (onCall > 0) {
            Spacer(Modifier.height(6.dp))
            Text(if (onCall == 1) "1 person on the call" else "$onCall people on the call", fontSize = 13.sp, color = muted)
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RingButton("Decline", red, muted, rotate = 135f, video = false, onClick = onDecline)
            RingButton("Join", green, muted, rotate = 0f, video = info.video, onClick = onJoin)
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun RingButton(label: String, color: Color, labelColor: Color, rotate: Float, video: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(68.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (video) Icons.Outlined.Videocam else Icons.Outlined.Call, label, tint = Color.White,
                modifier = Modifier.size(30.dp).rotate(rotate)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = 13.sp, color = labelColor)
    }
}