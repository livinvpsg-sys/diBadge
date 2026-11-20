package com.fabxdi.dibadge.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The call screen (incoming, voice, video). It shows over the lock screen for an incoming call.
 * What it draws is in CallScreens.kt.
 */
class CallActivity : ComponentActivity() {

    private var acceptRequested by mutableStateOf(false)
    private var callBackId by mutableStateOf("")
    private var groupJoinId by mutableStateOf("")
    private var groupRing by mutableStateOf<GroupRingInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        handleIntent(intent)
        if (intent?.hasExtra(EXTRA_GROUP_DECLINE_ID) == true) {
            // "Decline" on the group ring: nothing to show
            finish()
            return
        }

        setContent {
            DiBadgeTheme {
                CallHost(
                    acceptRequested = acceptRequested,
                    onAcceptHandled = { acceptRequested = false },
                    callBackId = callBackId,
                    onCallBackHandled = { callBackId = "" },
                    groupJoinId = groupJoinId,
                    onGroupJoinHandled = { groupJoinId = "" },
                    ring = groupRing,
                    onRingJoin = { id -> groupJoinId = id; groupRing = null },
                    onRingDecline = { id -> declineGroupRing(id); groupRing = null },
                    onRingGone = { groupRing = null },
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val engine = CallEngine.get(this)
        if (callId.isNotBlank() && engine.state.value.callId != callId && engine.readyForIncoming()) {
            engine.showIncoming(callId)
        }
        if (intent?.getBooleanExtra(EXTRA_ACCEPT, false) == true) acceptRequested = true
        val gjoin = intent?.getStringExtra(EXTRA_GROUP_JOIN_ID).orEmpty()
        if (gjoin.isNotBlank()) {
            groupJoinId = gjoin
            intent?.removeExtra(EXTRA_GROUP_JOIN_ID)
        }
        val ringId = intent?.getStringExtra(EXTRA_GROUP_RING_ID).orEmpty()
        if (ringId.isNotBlank() && engine.readyForIncoming()) {
            groupRing = GroupRingInfo(
                callId = ringId,
                callerName = intent?.getStringExtra(EXTRA_GROUP_RING_NAME).orEmpty().ifBlank { "Someone" },
                video = intent?.getBooleanExtra(EXTRA_GROUP_RING_VIDEO, false) == true
            )
            intent?.removeExtra(EXTRA_GROUP_RING_ID)
        }
        val declineId = intent?.getStringExtra(EXTRA_GROUP_DECLINE_ID).orEmpty()
        if (declineId.isNotBlank()) {
            declineGroupRing(declineId)
            intent?.removeExtra(EXTRA_GROUP_DECLINE_ID)
        }
        val back = intent?.getStringExtra(EXTRA_CALL_BACK_ID).orEmpty()
        if (back.isNotBlank()) {
            callBackId = back
            intent?.removeExtra(EXTRA_CALL_BACK_ID)
        }
    }

    /** The ring was turned away: stop it, and do not count it as a missed call. */
    private fun declineGroupRing(callId: String) {
        GroupCallNotifier.cancel(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { GroupCallRepository().markDeclined(callId) }
        }
        if (CallEngine.get(this).state.value.phase == CallPhase.IDLE) finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_ACCEPT = "accept"

        /** The id of the missed call to ring back (the person who called is called). */
        const val EXTRA_CALL_BACK_ID = "call_back_id"

        /** The group call that is ringing: opens the full-screen ring. */
        const val EXTRA_GROUP_RING_ID = "group_ring_id"
        const val EXTRA_GROUP_RING_NAME = "group_ring_name"
        const val EXTRA_GROUP_RING_VIDEO = "group_ring_video"

        /** The group call whose ring was declined from the notification. */
        const val EXTRA_GROUP_DECLINE_ID = "group_decline_id"

        /** The id of a group call to join. */
        const val EXTRA_GROUP_JOIN_ID = "group_join_id"

        /** Opens the call screen and joins the group call [callId]. */
        fun joinGroup(context: android.content.Context, callId: String) {
            context.startActivity(
                Intent(context, CallActivity::class.java)
                    .putExtra(EXTRA_GROUP_JOIN_ID, callId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }

        /** Opens the call screen for the call that is going on now. */
        fun open(context: android.content.Context) {
            context.startActivity(
                Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}

@Composable
private fun CallHost(
    acceptRequested: Boolean,
    onAcceptHandled: () -> Unit,
    callBackId: String,
    onCallBackHandled: () -> Unit,
    groupJoinId: String,
    onGroupJoinHandled: () -> Unit,
    ring: GroupRingInfo?,
    onRingJoin: (String) -> Unit,
    onRingDecline: (String) -> Unit,
    onRingGone: () -> Unit,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = CallEngine.get(context)
    val state by engine.state.collectAsState()
    // True while a call back / group join is being set up, so the screen is not closed as "idle"
    var joining by androidx.compose.runtime.remember { mutableStateOf(false) }

    fun missing(video: Boolean): Array<String> {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (video) needed.add(Manifest.permission.CAMERA)
        return needed.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
    }

    val askToAnswer = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micOk) engine.accept() else engine.decline()
    }
    val askForCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) engine.setCameraOn(true)
    }

    fun answer() {
        val need = missing(state.isVideo)
        val ask = need.toList() + CallPermissions.bluetoothOnce(context)
        if (ask.isEmpty()) engine.accept() else askToAnswer.launch(ask.toTypedArray())
    }

    // "Call back" was tapped on a missed-call notification
    var callBackVideo by androidx.compose.runtime.remember { mutableStateOf(false) }
    var callBackTarget by androidx.compose.runtime.remember { mutableStateOf<CallDoc?>(null) }
    fun startCallBack(doc: CallDoc) {
        val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "Me"
        engine.startCall(
            groupId = doc.groupId,
            calleeUid = doc.callerUid,
            callerName = me,
            peerName = doc.callerName,
            video = callBackVideo
        )
    }
    val askToCallBack = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val doc = callBackTarget
        val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        callBackTarget = null
        joining = false
        if (doc != null && micOk && engine.state.value.phase == CallPhase.IDLE) startCallBack(doc)
        else if (doc != null && !micOk) {
            android.widget.Toast.makeText(context, "Microphone permission is needed for calls", android.widget.Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    LaunchedEffect(callBackId) {
        if (callBackId.isBlank()) return@LaunchedEffect
        onCallBackHandled()
        if (engine.state.value.phase != CallPhase.IDLE) return@LaunchedEffect
        joining = true
        val doc = runCatching { CallRepository().getCall(callBackId) }.getOrNull()
        val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (doc == null || doc.callerUid.isBlank() || doc.callerUid == me) {
            joining = false
            onClose()
            return@LaunchedEffect
        }
        callBackVideo = false // ring back as a voice call
        callBackTarget = doc
        val ask = missing(false).toList() + CallPermissions.bluetoothOnce(context)
        if (ask.isEmpty()) {
            callBackTarget = null
            startCallBack(doc)
            joining = false
        } else askToCallBack.launch(ask.toTypedArray())
    }

    // "Join" was tapped on a group call notification or banner
    var groupJoinTarget by androidx.compose.runtime.remember { mutableStateOf("") }
    fun startGroupJoin(callId: String) {
        val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "Me"
        engine.joinGroupCall(callId, me)
    }
    val askToJoinGroup = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val target = groupJoinTarget
        groupJoinTarget = ""
        joining = false
        val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (target.isNotBlank() && micOk && engine.state.value.phase == CallPhase.IDLE) startGroupJoin(target)
        else if (target.isNotBlank()) {
            android.widget.Toast.makeText(context, "Microphone permission is needed for calls", android.widget.Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    LaunchedEffect(groupJoinId) {
        if (groupJoinId.isBlank()) return@LaunchedEffect
        onGroupJoinHandled()
        if (engine.state.value.phase != CallPhase.IDLE) return@LaunchedEffect
        joining = true
        GroupCallNotifier.cancel(context)
        val doc = runCatching { GroupCallRepository().getCall(groupJoinId) }.getOrNull()
        if (doc == null || !doc.isActive) {
            android.widget.Toast.makeText(context, "That call has ended", android.widget.Toast.LENGTH_SHORT).show()
            joining = false
            onClose()
            return@LaunchedEffect
        }
        val need = (listOf(Manifest.permission.RECORD_AUDIO) + if (doc.isVideo) listOf(Manifest.permission.CAMERA) else emptyList())
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED } + CallPermissions.bluetoothOnce(context)
        if (need.isEmpty()) {
            startGroupJoin(groupJoinId)
            joining = false
        }
        else {
            groupJoinTarget = groupJoinId
            askToJoinGroup.launch(need.toTypedArray())
        }
    }

    // Accept was tapped on the notification
    LaunchedEffect(acceptRequested, state.phase) {
        if (acceptRequested && state.phase == CallPhase.RINGING_IN) {
            onAcceptHandled()
            answer()
        }
    }

    // After the call is over, show why for a moment, then leave
    LaunchedEffect(state.phase, joining, ring) {
        if (state.phase == CallPhase.ENDED) {
            delay(1500)
            engine.reset()
            onClose()
        } else if (state.phase == CallPhase.IDLE && !joining && ring == null) {
            delay(400)
            if (engine.state.value.phase == CallPhase.IDLE) onClose()
        }
    }

    if (ring != null && state.phase == CallPhase.IDLE) {
        GroupRingScreen(
            info = ring,
            onJoin = { onRingJoin(ring.callId) },
            onDecline = { onRingDecline(ring.callId) },
            onGone = { GroupCallNotifier.cancel(context); onRingGone() }
        )
        return
    }

    CallScreen(
        engine = engine,
        onAnswer = { answer() },
        onToggleCamera = { turnOn ->
            if (!turnOn) engine.setCameraOn(false)
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                engine.setCameraOn(true)
            } else askForCamera.launch(Manifest.permission.CAMERA)
        }
    )
}