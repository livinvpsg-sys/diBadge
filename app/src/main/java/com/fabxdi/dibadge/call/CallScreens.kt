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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val CallBg = Color(0xFF0E1114)
private val CallText = Color(0xFFF0F3F5)
private val CallMuted = Color(0xFF8A9199)
private val CallButton = Color(0xFF222831)
private val CallRed = Color(0xFFE8837A)

/**
 * The call screens: incoming, voice and video. They only show what [CallEngine] says and call its
 * functions. [onAnswer] and [onToggleCamera] are here because they may need a permission first.
 */
@Composable
fun CallScreen(
    engine: CallEngine,
    onAnswer: () -> Unit,
    onToggleCamera: (turnOn: Boolean) -> Unit
) {
    val state by engine.state.collectAsState()
    val localTrack by engine.localVideo.collectAsState()
    val remoteTrack by engine.remoteVideo.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(CallBg)) {
        when {
            state.phase == CallPhase.RINGING_IN -> IncomingLayout(state, onDecline = { engine.decline() }, onAccept = onAnswer)
            state.isVideo || state.cameraOn -> VideoLayout(state, engine, localTrack, remoteTrack, onToggleCamera)
            else -> VoiceLayout(state, engine, onToggleCamera)
        }
    }
}

// ---------------------------------------------------------------- incoming

@Composable
private fun IncomingLayout(state: CallState, onDecline: () -> Unit, onAccept: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))
        Text(
            if (state.isVideo) "diBadge video call" else "diBadge voice call",
            fontSize = 14.sp, color = CallMuted
        )
        Spacer(Modifier.height(28.dp))
        CallAvatar(state.peerName, 120)
        Spacer(Modifier.height(24.dp))
        Text(state.peerName.ifBlank { "Someone" }, fontSize = 26.sp, color = CallText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text("Incoming call…", fontSize = 15.sp, color = CallMuted)
        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 64.dp), horizontalArrangement = Arrangement.SpaceAround) {
            RoundAction(Icons.Outlined.CallEnd, "Decline", size = 68, container = CallRed, tint = CallBg, onClick = onDecline)
            RoundAction(Icons.Outlined.Call, "Accept", size = 68, container = DiBadgeTheme.colors.accentFill, tint = Color.White, onClick = onAccept)
        }
    }
}

// ---------------------------------------------------------------- voice

@Composable
private fun VoiceLayout(state: CallState, engine: CallEngine, onToggleCamera: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))
        Text("diBadge voice call", fontSize = 14.sp, color = CallMuted)
        Spacer(Modifier.height(28.dp))
        CallAvatar(state.peerName, 120)
        Spacer(Modifier.height(24.dp))
        Text(state.peerName.ifBlank { "Someone" }, fontSize = 26.sp, color = CallText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(statusText(state), fontSize = 15.sp, color = CallMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        ControlsRow(state, engine, onToggleCamera, showFlip = false)
        Spacer(Modifier.height(48.dp))
    }
}

// ---------------------------------------------------------------- video

@Composable
private fun VideoLayout(
    state: CallState,
    engine: CallEngine,
    localTrack: VideoTrack?,
    remoteTrack: VideoTrack?,
    onToggleCamera: (Boolean) -> Unit
) {
    var remoteHasVideo by remember(remoteTrack) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // The other person: their picture until their video starts
        if (!remoteHasVideo) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CallAvatar(state.peerName, 120)
            }
        }
        WebRtcVideo(
            track = remoteTrack,
            eglContext = engine.eglContext,
            mirror = false,
            overlay = false,
            onFirstFrame = { remoteHasVideo = true },
            modifier = Modifier.fillMaxSize().alpha(if (remoteHasVideo) 1f else 0f)
        )

        // Name and time over a soft shade
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
                .statusBarsPadding()
                .padding(top = 16.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(state.peerName.ifBlank { "Someone" }, fontSize = 18.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statusText(state), fontSize = 13.sp, color = Color(0xFFC3C9D0))
        }

        // You, small, top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 76.dp, end = 14.dp)
                .size(width = 96.dp, height = 132.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF3A3F52)),
            contentAlignment = Alignment.Center
        ) {
            if (state.cameraOn) {
                WebRtcVideo(
                    track = localTrack,
                    eglContext = engine.eglContext,
                    mirror = state.frontCamera,
                    overlay = true,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("Camera off", fontSize = 12.sp, color = Color(0xFFC3C9D0))
            }
        }

        // Controls over a soft shade
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                .navigationBarsPadding()
                .padding(top = 40.dp, bottom = 40.dp, start = 16.dp, end = 16.dp)
        ) {
            ControlsRow(state, engine, onToggleCamera, showFlip = true)
        }
    }
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun ControlsRow(state: CallState, engine: CallEngine, onToggleCamera: (Boolean) -> Unit, showFlip: Boolean) {
    val w = if (showFlip) 60 else 72
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundAction(
            if (state.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
            if (state.muted) "Unmute" else "Mute",
            active = state.muted,
            onClick = { engine.setMuted(!state.muted) },
            width = w
        )
        if (showFlip) {
            RoundAction(
                if (state.cameraOn) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                "Camera",
                active = !state.cameraOn,
                onClick = { onToggleCamera(!state.cameraOn) },
                width = w
            )
            RoundAction(Icons.Outlined.FlipCameraAndroid, "Flip", onClick = { engine.switchCamera() }, enabled = state.cameraOn, width = w)
            AudioRouteButton(state, engine, width = w)
        } else {
            AudioRouteButton(state, engine, width = w)
            RoundAction(Icons.Outlined.Videocam, "Video", onClick = { onToggleCamera(true) }, width = w)
        }
        RoundAction(Icons.Outlined.CallEnd, "End", container = CallRed, tint = CallBg, onClick = { engine.hangUp() }, width = w)
    }
}

private fun routeIcon(r: AudioRoute): ImageVector = when (r) {
    AudioRoute.EARPIECE -> Icons.Outlined.PhoneInTalk
    AudioRoute.SPEAKER -> Icons.Outlined.VolumeUp
    AudioRoute.BLUETOOTH -> Icons.Outlined.Bluetooth
    AudioRoute.WIRED -> Icons.Outlined.Headphones
}

private fun routeLabel(r: AudioRoute): String = when (r) {
    AudioRoute.EARPIECE -> "Phone"
    AudioRoute.SPEAKER -> "Speaker"
    AudioRoute.BLUETOOTH -> "Bluetooth"
    AudioRoute.WIRED -> "Headset"
}

/**
 * Shows where the sound comes out. With only the phone and the speaker, one tap switches between
 * them. With a headset connected it opens a short list to choose from.
 */
@Composable
private fun AudioRouteButton(state: CallState, engine: CallEngine, width: Int) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        RoundAction(
            routeIcon(state.audioRoute),
            routeLabel(state.audioRoute),
            // lit whenever the sound is NOT on the phone's own earpiece (speaker, Bluetooth, headset)
            active = state.audioRoute != AudioRoute.EARPIECE,
            onClick = {
                if (state.routes.size <= 2) {
                    engine.setAudioRoute(if (state.audioRoute == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER)
                } else {
                    menuOpen = true
                }
            },
            width = width
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            state.routes.forEach { r ->
                val selected = r == state.audioRoute
                DropdownMenuItem(
                    modifier = Modifier.background(
                        if (selected) DiBadgeTheme.colors.accent.copy(alpha = 0.22f) else Color.Transparent
                    ),
                    text = {
                        Text(
                            routeLabel(r),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) DiBadgeTheme.colors.accent else CallText
                        )
                    },
                    leadingIcon = {
                        Icon(
                            routeIcon(r), null,
                            tint = if (selected) DiBadgeTheme.colors.accent else CallText
                        )
                    },
                    trailingIcon = {
                        if (selected) Icon(Icons.Outlined.Check, null, tint = DiBadgeTheme.colors.accent)
                    },
                    onClick = { menuOpen = false; engine.setAudioRoute(r) }
                )
            }
        }
    }
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Int = 56,
    container: Color = CallButton,
    tint: Color = CallText,
    active: Boolean = false,
    enabled: Boolean = true,
    width: Int = 72
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(width.dp)) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(if (active) CallText else container)
                .alpha(if (enabled) 1f else 0.4f)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (active) CallBg else tint, modifier = Modifier.size((size * 0.43f).dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = CallMuted, maxLines = 1)
    }
}

@Composable
private fun CallAvatar(name: String, size: Int) {
    val palette = listOf(0xFFE39A6B, 0xFF6BA8E3, 0xFF9B7BD6, 0xFF5FB88C, 0xFFD66B8A, 0xFFC9A93F)
    val color = Color(palette[(name.hashCode() and 0x7FFFFFFF) % palette.size])
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            fontSize = (size * 0.4f).sp, fontWeight = FontWeight.SemiBold, color = Color.White
        )
    }
}

private fun statusText(s: CallState): String = when (s.phase) {
    CallPhase.STARTING -> "Starting…"
    CallPhase.RINGING_OUT -> "Ringing…"
    CallPhase.RINGING_IN -> "Incoming call…"
    CallPhase.CONNECTING -> "Connecting…"
    CallPhase.CONNECTED -> CallRepository.formatDuration(s.seconds)
    CallPhase.ENDED -> when (s.endReason) {
        CallEndReason.DECLINED -> "Declined"
        CallEndReason.NO_ANSWER -> "No answer"
        CallEndReason.CANCELLED -> "Call cancelled"
        CallEndReason.FAILED -> "Call failed"
        else -> "Call ended"
    }
    CallPhase.IDLE -> ""
}

/** Draws one WebRTC video track. */
@Composable
private fun WebRtcVideo(
    track: VideoTrack?,
    eglContext: EglBase.Context,
    mirror: Boolean,
    overlay: Boolean,
    modifier: Modifier = Modifier,
    onFirstFrame: () -> Unit = {}
) {
    val renderer = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val firstFrame by rememberUpdatedState(onFirstFrame)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, object : RendererCommon.RendererEvents {
                    override fun onFirstFrameRendered() { post { firstFrame() } }
                    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {}
                })
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                if (overlay) setZOrderMediaOverlay(true)
                renderer.value = this
            }
        },
        update = { it.setMirror(mirror) },
        onRelease = { it.release() }
    )

    val r = renderer.value
    DisposableEffect(track, r) {
        if (track != null && r != null) track.addSink(r)
        onDispose { if (track != null && r != null) runCatching { track.removeSink(r) } }
    }
}