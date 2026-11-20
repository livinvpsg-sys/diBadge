package com.fabxdi.dibadge.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One line of the Calls list. */
data class CallLogItem(
    val id: String,
    val otherUid: String,
    val otherName: String,
    val groupId: String,
    val video: Boolean,
    val outgoing: Boolean,
    val missed: Boolean,       // a call to you that you did not take
    val summary: String,      // "Missed · voice", "Outgoing · video · 4:12"...
    val at: Long,
    val isGroupCall: Boolean = false,
    val groupCallId: String = ""
)

/** A group call you were rung for and never joined or declined, once it has had time to be answered. */
private const val GROUP_RING_MS = 60_000L
private fun isMissedGroup(e: GroupCallLogEntry, now: Long): Boolean =
    !e.outgoing && !e.joined && !e.declined && e.createdAt > 0 && now - e.createdAt > GROUP_RING_MS

/** Remembers when the Calls list was last opened, so only newer missed calls get the red badge. */
object CallLogSeen {
    private const val PREFS = "call_log"
    private const val KEY = "seen_at"
    private val _seenAt = MutableStateFlow(-1L)
    val seenAt: StateFlow<Long> = _seenAt

    fun load(context: Context) {
        if (_seenAt.value >= 0) return
        _seenAt.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L)
    }

    fun markSeen(context: Context) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, now).apply()
        _seenAt.value = now
    }
}

private const val STALE_MS = 120_000L

/** True when the call was made to [me] and not taken. */
private fun isMissedFor(me: String, d: Map<String, Any?>, now: Long): Boolean {
    if (d["calleeUid"] != me) return false
    val status = d["status"] as? String ?: ""
    val created = (d["createdAt"] as? Number)?.toLong() ?: 0L
    return status == CallStatus.MISSED || status == CallStatus.CANCELLED ||
            (status == CallStatus.RINGING && now - created > STALE_MS)
}

/** Number of missed calls since the Calls list was last opened. Live. */
@Composable
fun rememberMissedCallCount(): Int {
    val context = LocalContext.current
    LaunchedEffect(Unit) { CallLogSeen.load(context) }
    val seen by CallLogSeen.seenAt.collectAsState()
    var times by remember { mutableStateOf<List<Long>>(emptyList()) }
    var groupLog by remember { mutableStateOf<List<GroupCallLogEntry>>(emptyList()) }
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { GroupCallRepository().observeMyLog().collect { groupLog = it } }
    // A ring turns into a missed call a minute later, so look again now and then
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(20_000); tick = System.currentTimeMillis() } }
    DisposableEffect(Unit) {
        val me = FirebaseAuth.getInstance().currentUser?.uid
        var reg: ListenerRegistration? = null
        if (me != null) {
            reg = FirebaseFirestore.getInstance().collection("calls").whereEqualTo("calleeUid", me)
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null) return@addSnapshotListener
                    val now = System.currentTimeMillis()
                    times = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        if (isMissedFor(me, d, now)) (d["createdAt"] as? Number)?.toLong() else null
                    }
                }
        }
        onDispose { reg?.remove() }
    }
    if (seen < 0) return 0
    val now = maxOf(tick, System.currentTimeMillis())
    return times.count { it > seen } + groupLog.count { isMissedGroup(it, now) && it.createdAt > seen }
}

@Composable
fun CallHistoryScreen(
    nicknames: Map<String, String>,
    onBack: () -> Unit,
    onOpenChat: (otherUid: String, name: String) -> Unit
) {
    val context = LocalContext.current
    val isDark = DiBadgeTheme.colors.isDark
    val pageBg = if (isDark) Color(0xFF1F1F21) else Color(0xFFF5F6F8)
    val primary = if (isDark) Color(0xFFF2F2F4) else Color(0xFF15171C)
    val secondary = if (isDark) Color(0xFFA3A3AA) else Color(0xFF667085)
    val outline = if (isDark) Color(0xFF3A3A3F) else Color(0xFFE3E5EA)
    val error = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)
    val accent = DiBadgeTheme.colors.accent

    val me = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var asCaller by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var asCallee by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val names = remember { mutableStateMapOf<String, String>() }
    var onlyMissed by remember { mutableStateOf(false) }
    var groupLog by remember { mutableStateOf<List<GroupCallLogEntry>>(emptyList()) }
    val groupTitles = remember { mutableStateMapOf<String, String>() }
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    val uiScope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    // Opening the list clears the badge
    LaunchedEffect(Unit) { CallLogSeen.load(context); CallLogSeen.markSeen(context) }

    LaunchedEffect(Unit) { GroupCallRepository().observeMyLog().collect { groupLog = it } }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(15_000); tick = System.currentTimeMillis() } }
    // The names of the groups the group calls were in
    LaunchedEffect(groupLog) {
        groupLog.map { it.groupId }.filter { it.isNotBlank() && it !in groupTitles }.distinct().forEach { gid ->
            groupTitles[gid] = ""
            FirebaseFirestore.getInstance().collection("personalGroups").document(gid).get()
                .addOnSuccessListener { d -> groupTitles[gid] = d.getString("name").orEmpty().ifBlank { d.getString("title").orEmpty() } }
        }
    }

    DisposableEffect(me) {
        val regs = mutableListOf<ListenerRegistration>()
        if (me.isNotBlank()) {
            val col = FirebaseFirestore.getInstance().collection("calls")
            regs += col.whereEqualTo("callerUid", me).addSnapshotListener { snap, err ->
                if (err == null && snap != null) {
                    asCaller = snap.documents.mapNotNull { d -> d.data?.plus("id" to d.id) }
                    loaded = true
                }
            }
            regs += col.whereEqualTo("calleeUid", me).addSnapshotListener { snap, err ->
                if (err == null && snap != null) {
                    asCallee = snap.documents.mapNotNull { d -> d.data?.plus("id" to d.id) }
                    loaded = true
                }
            }
        }
        onDispose { regs.forEach { it.remove() } }
    }

    // Names of the people you called (a call to you already carries the caller's name)
    LaunchedEffect(asCaller) {
        asCaller.mapNotNull { it["calleeUid"] as? String }.distinct().filter { it !in names }.forEach { uid ->
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { names[uid] = it.getString("displayName").orEmpty() }
        }
    }

    val groupItems: List<CallLogItem> = remember(groupLog, groupTitles.toMap(), tick) {
        val now = System.currentTimeMillis()
        groupLog.mapNotNull { e ->
            val kind = if (e.video) "video" else "voice"
            val missed = isMissedGroup(e, now)
            val summary = when {
                missed -> "Missed group call · $kind"
                e.declined && !e.joined -> "Declined group call · $kind"
                e.joined && e.durationSec > 0 -> "Group $kind call · " + CallRepository.formatDuration(e.durationSec)
                e.joined -> "Group $kind call"
                e.outgoing -> "Group $kind call"
                else -> return@mapNotNull null // still ringing
            }
            val title = groupTitles[e.groupId].orEmpty().ifBlank { if (e.hostName.isBlank()) "Group call" else e.hostName + "'s group" }
            CallLogItem(
                id = "g_" + e.callId, otherUid = e.hostUid, otherName = title, groupId = e.groupId,
                video = e.video, outgoing = e.outgoing, missed = missed, summary = summary, at = e.createdAt,
                isGroupCall = true, groupCallId = e.callId
            )
        }
    }

    val items: List<CallLogItem> = remember(asCaller, asCallee, names.toMap(), nicknames, groupItems) {
        val now = System.currentTimeMillis()
        ((asCaller + asCallee).distinctBy { it["id"] }.mapNotNull { d ->
            val callerUid = d["callerUid"] as? String ?: return@mapNotNull null
            val calleeUid = d["calleeUid"] as? String ?: return@mapNotNull null
            val outgoing = callerUid == me
            val other = if (outgoing) calleeUid else callerUid
            val video = d["type"] == "video"
            val status = d["status"] as? String ?: ""
            val created = (d["createdAt"] as? Number)?.toLong() ?: 0L
            val started = (d["startedAt"] as? Number)?.toLong() ?: 0L
            val ended = (d["endedAt"] as? Number)?.toLong() ?: 0L
            val stale = status == CallStatus.RINGING && now - created > STALE_MS
            if ((status == CallStatus.RINGING && !stale) || (status == CallStatus.ACCEPTED && now - created < STALE_MS)) {
                return@mapNotNull null // still going on
            }
            val kind = if (video) "video" else "voice"
            val missed = !outgoing && (status == CallStatus.MISSED || status == CallStatus.CANCELLED || stale)
            val duration = if (started > 0 && ended > started) CallRepository.formatDuration((ended - started) / 1000) else ""
            val summary = when {
                missed -> "Missed · $kind"
                status == CallStatus.DECLINED -> "Declined · $kind"
                outgoing && (status == CallStatus.MISSED || status == CallStatus.CANCELLED || stale) -> "No answer"
                started > 0 -> (if (outgoing) "Outgoing" else "Incoming") + " · $kind" + if (duration.isNotBlank()) " · $duration" else ""
                else -> (if (outgoing) "Outgoing" else "Incoming") + " · $kind"
            }
            val raw = if (outgoing) names[other].orEmpty() else (d["callerName"] as? String).orEmpty()
            val name = nicknames[other]?.takeIf { it.isNotBlank() } ?: raw.ifBlank { "Someone" }
            CallLogItem(
                id = d["id"] as String, otherUid = other, otherName = name, groupId = d["groupId"] as? String ?: "",
                video = video, outgoing = outgoing, missed = missed, summary = summary, at = created
            )
        } + groupItems).sortedByDescending { it.at }.take(100)
    }
    val shown = if (onlyMissed) items.filter { it.missed } else items

    // Ringing someone back from a row
    var pendingBack by remember { mutableStateOf<CallLogItem?>(null) }
    fun begin(item: CallLogItem) {
        val meName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Me"
        val engine = CallEngine.get(context)
        if (engine.isBusy) { CallActivity.open(context); return }
        if (item.isGroupCall) {
            // Still going on: join it. Over: start a new call in the same group.
            uiScope.launch {
                val doc = runCatching { GroupCallRepository().getCall(item.groupCallId) }.getOrNull()
                if (doc != null && doc.isActive) {
                    CallActivity.joinGroup(context, item.groupCallId)
                } else {
                    engine.startGroupCall(item.groupId, item.otherName, meName, item.video)
                    CallActivity.open(context)
                }
            }
            return
        }
        engine.startCall(item.groupId, item.otherUid, meName, item.otherName, item.video)
        CallActivity.open(context)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val item = pendingBack
        pendingBack = null
        val micOk = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = item?.video != true || androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (item != null && micOk && camOk) begin(item)
        else if (item != null) android.widget.Toast.makeText(context, "Permission is needed for calls", android.widget.Toast.LENGTH_SHORT).show()
    }
    fun callBack(item: CallLogItem) {
        if (item.groupId.isBlank()) return
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (item.video) need.add(Manifest.permission.CAMERA)
        val missing = need.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        } + CallPermissions.bluetoothOnce(context)
        if (missing.isEmpty()) begin(item) else { pendingBack = item; permLauncher.launch(missing.toTypedArray()) }
    }

    Column(modifier = Modifier.fillMaxSize().background(pageBg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = primary, modifier = Modifier.size(22.dp)) }
            Text("Calls", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primary)
        }
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("All", !onlyMissed, accent, primary, outline) { onlyMissed = false }
            FilterChip("Missed", onlyMissed, accent, primary, outline) { onlyMissed = true }
        }

        if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        !loaded -> ""
                        onlyMissed -> "No missed calls"
                        else -> "No calls yet"
                    },
                    fontSize = 15.sp, color = secondary
                )
            }
        } else {
            val groups = shown.groupBy { dayLabel(it.at) }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                groups.forEach { (label, rows) ->
                    item(key = "h_$label") {
                        Text(
                            label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = secondary,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
                        )
                    }
                    items(rows, key = { it.id }) { row ->
                        CallRow(row, primary, secondary, error, accent, pageBg) {
                            if (it) callBack(row) else if (!row.isGroupCall) onOpenChat(row.otherUid, row.otherName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, accent: Color, primary: Color, outline: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier.clip(shape)
            .then(if (selected) Modifier.background(accent.copy(alpha = 0.16f)) else Modifier)
            .border(BorderStroke(1.dp, if (selected) accent else outline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) { Text(label, fontSize = 13.sp, color = if (selected) accent else primary) }
}

/** [onClick] gets true when the phone icon was tapped, false for the row. */
@Composable
private fun CallRow(row: CallLogItem, primary: Color, secondary: Color, error: Color, accent: Color, pageBg: Color, onClick: (Boolean) -> Unit) {
    val initial = row.otherName.trim().firstOrNull()?.uppercase() ?: "?"
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick(false) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Text(initial, fontSize = 17.sp, color = accent) }
            if (row.isGroupCall) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(pageBg),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Groups, null, tint = secondary, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.otherName, fontSize = 15.sp, color = if (row.missed) error else primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(row.summary, fontSize = 12.sp, color = if (row.missed) error else secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(timeLabel(row.at), fontSize = 12.sp, color = secondary)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onClick(true) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (row.isGroupCall && row.video) Icons.Outlined.Videocam else Icons.Outlined.Call,
                if (row.isGroupCall) "Join or start the call" else "Call back", tint = accent, modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun timeLabel(at: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))

private fun dayLabel(at: Long): String {
    val cal = Calendar.getInstance()
    fun day(c: Calendar) = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    val today = day(cal)
    val then = Calendar.getInstance().apply { timeInMillis = at }
    cal.add(Calendar.DAY_OF_YEAR, -1)
    return when (day(then)) {
        today -> "TODAY"
        day(cal) -> "YESTERDAY"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(at)).uppercase()
    }
}