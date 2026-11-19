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

/**
 * The call screen (incoming, voice, video). It shows over the lock screen for an incoming call.
 * What it draws is in CallScreens.kt.
 */
class CallActivity : ComponentActivity() {

    private var acceptRequested by mutableStateOf(false)
    private var callBackId by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        handleIntent(intent)

        setContent {
            DiBadgeTheme {
                CallHost(
                    acceptRequested = acceptRequested,
                    onAcceptHandled = { acceptRequested = false },
                    callBackId = callBackId,
                    onCallBackHandled = { callBackId = "" },
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
        val back = intent?.getStringExtra(EXTRA_CALL_BACK_ID).orEmpty()
        if (back.isNotBlank()) {
            callBackId = back
            intent?.removeExtra(EXTRA_CALL_BACK_ID)
        }
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
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = CallEngine.get(context)
    val state by engine.state.collectAsState()

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
        val doc = runCatching { CallRepository().getCall(callBackId) }.getOrNull()
        val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (doc == null || doc.callerUid.isBlank() || doc.callerUid == me) {
            onClose()
            return@LaunchedEffect
        }
        callBackVideo = false // ring back as a voice call
        callBackTarget = doc
        val ask = missing(false).toList() + CallPermissions.bluetoothOnce(context)
        if (ask.isEmpty()) {
            callBackTarget = null
            startCallBack(doc)
        } else askToCallBack.launch(ask.toTypedArray())
    }

    // Accept was tapped on the notification
    LaunchedEffect(acceptRequested, state.phase) {
        if (acceptRequested && state.phase == CallPhase.RINGING_IN) {
            onAcceptHandled()
            answer()
        }
    }

    // After the call is over, show why for a moment, then leave
    LaunchedEffect(state.phase) {
        if (state.phase == CallPhase.ENDED) {
            delay(1500)
            engine.reset()
            onClose()
        } else if (state.phase == CallPhase.IDLE) {
            delay(400)
            if (engine.state.value.phase == CallPhase.IDLE) onClose()
        }
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