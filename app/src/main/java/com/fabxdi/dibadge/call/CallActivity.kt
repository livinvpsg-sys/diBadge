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

        /** Opens the call screen for the call that is going on now. */
        fun open(context: android.content.Context) {
            context.startActivity(
                Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}

@Composable
private fun CallHost(acceptRequested: Boolean, onAcceptHandled: () -> Unit, onClose: () -> Unit) {
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
        if (need.isEmpty()) engine.accept() else askToAnswer.launch(need)
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