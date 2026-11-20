package com.fabxdi.dibadge.call

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where the call is, for the call screens. */
enum class CallPhase {
    IDLE,
    STARTING,     // outgoing: setting the call up
    RINGING_OUT,  // outgoing: the other phone is ringing
    RINGING_IN,   // incoming: waiting for you to accept or decline
    CONNECTING,   // answered, the two phones are linking up
    CONNECTED,    // you are talking
    ENDED         // over: the screen shows why for a moment, then calls reset()
}

/** Why a call ended (for the ENDED screen). */
enum class CallEndReason { NONE, COMPLETED, DECLINED, NO_ANSWER, CANCELLED, FAILED, MIC_BUSY }

/** Where the sound of the call comes out. */
enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED }

/** One person on a group call, for the call screen. */
data class GroupMemberState(val uid: String, val name: String, val cameraOn: Boolean, val muted: Boolean, val isMe: Boolean)

data class CallState(
    val phase: CallPhase = CallPhase.IDLE,
    val callId: String = "",
    val peerName: String = "",
    val isOutgoing: Boolean = false,
    val isVideo: Boolean = false,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    val audioRoute: AudioRoute = AudioRoute.EARPIECE,
    val routes: List<AudioRoute> = listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
    val cameraOn: Boolean = false,
    val frontCamera: Boolean = true,
    val seconds: Long = 0,          // time since you were connected
    val weakSignal: Boolean = false, // the connection is poor right now
    val reconnecting: Boolean = false, // a group call is linking up again after a drop
    val endReason: CallEndReason = CallEndReason.NONE,
    val isGroup: Boolean = false,
    val groupId: String = "",
    val participants: List<GroupMemberState> = emptyList()
)

/**
 * One call at a time. Holds the microphone, camera and the connection to the other phone.
 * The screens only watch [state], [localVideo] and [remoteVideo] and call the functions below.
 * The people on the call set it up through Firestore (see [CallRepository]); the Worker rings the phone
 * and hands out the relay servers (see [CallApi]).
 *
 * The caller must already have the microphone permission (and camera for video) before calling
 * [startCall] or [accept]; asking for it is the screen's job.
 */
class CallEngine private constructor(private val appContext: Context) {

    private val repo = CallRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val myUid get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()

    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()

    private val eglBase: EglBase = EglBase.create()
    /** The screens need this to draw video (SurfaceViewRenderer.init). */
    val eglContext: EglBase.Context get() = eglBase.eglBaseContext

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule())
            .createPeerConnectionFactory()
    }

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ---- one call's things; all null when idle
    private var call: CallDoc? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var capturing = false
    private var remoteSet = false
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var finished = false
    private var connectedAtMs = 0L
    private var savedAudioMode = AudioManager.MODE_NORMAL
    private var savedSpeaker = false

    private var observeJob: Job? = null
    private var candidateJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var tickerJob: Job? = null
    private var dropJob: Job? = null
    private var qualityJob: Job? = null
    private var badStreak = 0
    private var goodStreak = 0
    private var lastLost = -1L
    private var lastRecv = -1L

    // ---- the "ringing..." tone the caller hears while waiting for the other person to answer
    private var ringback: android.media.ToneGenerator? = null

    init {
        scope.launch {
            _state.collect { st ->
                val waiting = st.isOutgoing && (st.phase == CallPhase.STARTING || st.phase == CallPhase.RINGING_OUT)
                if (waiting) startRingback() else stopRingback()
            }
        }
    }

    private fun startRingback() {
        if (ringback != null) return
        try {
            ringback = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80).also {
                it.startTone(android.media.ToneGenerator.TONE_SUP_RINGTONE)
            }
        } catch (e: Exception) {
            Log.w("CallEngine", "ringback failed: ${e.message}")
            ringback = null
        }
    }

    private fun stopRingback() {
        val tone = ringback ?: return
        ringback = null
        try {
            tone.stopTone()
            tone.release()
        } catch (e: Exception) {
        }
    }

    // ================================================================ outgoing

    /** Calls [calleeUid] from the 1:1 chat [groupId]. */
    fun startCall(groupId: String, calleeUid: String, callerName: String, peerName: String, video: Boolean) {
        if (_state.value.phase != CallPhase.IDLE) return
        resetInternals()
        _state.value = CallState(
            phase = CallPhase.STARTING, peerName = peerName, isOutgoing = true, isVideo = video,
            cameraOn = video, speakerOn = video
        )
        CallService.start(appContext)
        scope.launch {
            try {
                val callId = repo.startCall(groupId, calleeUid, callerName, video)
                _state.update { it.copy(callId = callId) }
                call = repo.getCall(callId)

                val ice = CallApi.iceServers(callId)
                if (ice.isEmpty()) Log.w(TAG, "no relay servers, trying a direct connection only")
                setUpConnection(ice, video)
                observeCallDoc(callId)

                // Offer, then ring: the other phone will find the offer when it answers
                val offer = createOffer()
                setLocal(offer)
                repo.setOffer(callId, SessionDesc(offer.type.canonicalForm(), offer.description))
                CallApi.ring(callId)

                observeCandidates(callId, fromCaller = false)
                _state.update { it.copy(phase = CallPhase.RINGING_OUT) }
                startAudioRouting()

                ringTimeoutJob = scope.launch {
                    delay(RING_SECONDS * 1000L)
                    if (_state.value.phase == CallPhase.RINGING_OUT) {
                        runCatching { repo.markMissed(callId) }
                        CallApi.cancel(callId)
                        finish(CallEndReason.NO_ANSWER, writeLine = CallStatus.MISSED)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "startCall failed: ${e.message}")
                failAndCleanup()
            }
        }
    }

    // ================================================================ incoming

    /** A call is ringing on this phone. Shows the incoming screen state and watches for the caller hanging up. */
    fun showIncoming(callId: String) {
        if (_state.value.phase != CallPhase.IDLE) return
        resetInternals()
        _state.value = CallState(phase = CallPhase.RINGING_IN, callId = callId, isOutgoing = false)
        scope.launch {
            try {
                val doc = repo.getCall(callId)
                if (doc == null || CallStatus.isOver(doc.status)) {
                    finish(CallEndReason.CANCELLED)
                    return@launch
                }
                call = doc
                _state.update {
                    it.copy(peerName = doc.callerName, isVideo = doc.isVideo, cameraOn = doc.isVideo, speakerOn = doc.isVideo)
                }
                observeCallDoc(callId)
            } catch (e: Exception) {
                Log.w(TAG, "showIncoming failed: ${e.message}")
                failAndCleanup()
            }
        }
    }

    /** True while a call is going on (so a second incoming call is refused). */
    val isBusy: Boolean get() = _state.value.phase != CallPhase.IDLE && _state.value.phase != CallPhase.ENDED

    /** Makes the engine ready for an incoming call. False when you are already on a call. */
    fun readyForIncoming(): Boolean {
        if (_state.value.phase == CallPhase.ENDED) reset()
        return _state.value.phase == CallPhase.IDLE
    }

    /** A push said the caller hung up before you answered. */
    fun remoteCancelled(callId: String) {
        IncomingCallNotifier.cancel(appContext)
        if (_state.value.phase == CallPhase.RINGING_IN && _state.value.callId == callId) {
            finish(CallEndReason.CANCELLED)
        }
    }

    /** The person pressed Accept. */
    fun accept() {
        val callId = _state.value.callId
        if (_state.value.phase != CallPhase.RINGING_IN || callId.isBlank()) return
        _state.update { it.copy(phase = CallPhase.CONNECTING) }
        IncomingCallNotifier.cancel(appContext)
        CallService.start(appContext)
        scope.launch {
            try {
                // The offer may arrive a moment after the ringing push
                val doc = withTimeoutOrNull(15_000) { repo.observeCall(callId).first { it?.offer != null } }
                    ?: throw IllegalStateException("no offer")
                call = doc
                val ice = CallApi.iceServers(callId)
                setUpConnection(ice, doc.isVideo)
                setRemote(SessionDescription(SessionDescription.Type.fromCanonicalForm(doc.offer!!.type), doc.offer.sdp))
                val answer = createAnswer()
                setLocal(answer)
                observeCandidates(callId, fromCaller = true)
                startAudioRouting()
                repo.accept(callId, SessionDesc(answer.type.canonicalForm(), answer.description))
                startConnectTimeout()
            } catch (e: Exception) {
                Log.w(TAG, "accept failed: ${e.message}")
                runCatching { repo.decline(callId) }
                failAndCleanup()
            }
        }
    }

    /** The person pressed Decline. */
    fun decline() {
        val callId = _state.value.callId
        if (_state.value.phase != CallPhase.RINGING_IN) return
        IncomingCallNotifier.cancel(appContext)
        scope.launch {
            runCatching { repo.decline(callId) }
            finish(CallEndReason.DECLINED)
        }
    }

    /** Declines the call and sends [text] to the caller in your chat with them. */
    fun declineWithMessage(text: String) {
        if (_state.value.phase != CallPhase.RINGING_IN) return
        val doc = call
        val message = text.trim()
        decline()
        if (message.isBlank() || doc == null || doc.groupId.isBlank()) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        scope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val name = try {
                    db.collection("users").document(user.uid).get().await().getString("displayName")
                } catch (e: Exception) {
                    null
                } ?: user.displayName ?: "Me"
                val msgId = java.util.UUID.randomUUID().toString()
                db.collection("personalGroups").document(doc.groupId).collection("messages").document(msgId).set(
                    mapOf(
                        "id" to msgId,
                        "text" to message,
                        "senderName" to com.fabxdi.dibadge.util.capitalizeName(name),
                        "senderUid" to user.uid,
                        "attachments" to emptyList<String>(),
                        "attachmentDurations" to emptyMap<String, Any>(),
                        "attachmentThumbs" to emptyList<String>(),
                        "timestamp" to System.currentTimeMillis(),
                        "isEdited" to false
                    )
                ).await()
                com.fabxdi.dibadge.util.PushApi.messageSent(doc.groupId, msgId)
            } catch (e: Exception) {
                Log.w(TAG, "declineWithMessage: ${e.message}")
            }
        }
    }

    // ================================================================ during the call

    /** The red button. Works at every stage. */
    fun hangUp() {
        val s = _state.value
        if (s.isGroup && s.phase != CallPhase.IDLE && s.phase != CallPhase.ENDED) {
            leaveGroupCall()
            return
        }
        val callId = s.callId
        when (s.phase) {
            CallPhase.IDLE, CallPhase.ENDED -> return
            CallPhase.RINGING_IN -> decline()
            CallPhase.STARTING, CallPhase.RINGING_OUT -> scope.launch {
                if (callId.isNotBlank()) {
                    runCatching { repo.cancel(callId) }
                    CallApi.cancel(callId)
                }
                finish(CallEndReason.CANCELLED, writeLine = CallStatus.CANCELLED)
            }
            CallPhase.CONNECTING, CallPhase.CONNECTED -> scope.launch {
                runCatching { repo.end(callId) }
                finish(CallEndReason.COMPLETED, writeLine = CallStatus.ENDED)
            }
        }
    }

    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
        _state.update { it.copy(muted = muted) }
        if (_state.value.isGroup && gCallId.isNotBlank()) {
            scope.launch { runCatching { gRepo.updatePeer(gCallId, "muted", muted) } }
        }
    }

    /** Kept for the old Speaker button: on = speaker, off = the best other route. */
    fun setSpeaker(on: Boolean) {
        setAudioRoute(if (on) AudioRoute.SPEAKER else bestNonSpeaker())
    }

    /** Sends the sound to the earpiece, the speaker, a Bluetooth headset or a wired headset. */
    fun setAudioRoute(route: AudioRoute) {
        val available = availableRoutes()
        if (route !in available) return
        applyRoute(route)
        _state.update { it.copy(audioRoute = route, speakerOn = route == AudioRoute.SPEAKER, routes = available) }
    }

    /** Turns your camera on or off. Works in a voice call too: the other person then sees your video. */
    fun setCameraOn(on: Boolean) {
        val track = videoTrack ?: return
        if (on) {
            startCapture()
            track.setEnabled(true)
        } else {
            track.setEnabled(false)
            stopCapture()
        }
        _state.update { it.copy(cameraOn = on) }
        if (_state.value.isGroup && gCallId.isNotBlank()) {
            scope.launch { runCatching { gRepo.updatePeer(gCallId, "cameraOn", on) } }
        }
    }

    fun switchCamera() {
        capturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                _state.update { it.copy(frontCamera = isFront) }
            }

            override fun onCameraSwitchError(error: String?) {
                Log.w(TAG, "switch camera: $error")
            }
        })
    }

    /** The call screen closed after the ENDED state. Ready for the next call. */
    fun reset() {
        if (_state.value.phase == CallPhase.ENDED || _state.value.phase == CallPhase.IDLE) {
            resetInternals()
            _state.value = CallState()
        }
    }

    // ================================================================ group calls (Cloudflare SFU)
    //
    // Everyone sends their microphone and camera ONCE to the Cloudflare SFU (the "push" connection) and
    // pulls everybody else's tracks from it (the "pull" connection). Who is on the call, and each person's
    // SFU session, is kept in Firestore (see GroupCallRepository); the Worker holds the SFU secret.

    private val gRepo = GroupCallRepository()
    private var gCallId = ""
    private var gDoc: GroupCallDoc? = null
    private var pushPc: PeerConnection? = null
    private var pullPc: PeerConnection? = null
    private var pushSession = ""
    private var pullSession = ""
    private val pullMutex = Mutex()
    private val midOwner = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>() // mid -> (uid, trackName)
    private val pulledSessions = mutableMapOf<String, String>() // uid -> the session of theirs that is pulled
    private val pulling = mutableSetOf<String>()
    private var peersJob: Job? = null
    private var groupCallJob: Job? = null
    private var othersSeen = false
    private var groupVideo = false
    private var gMyName = ""
    private var iceCache: List<PeerConnection.IceServer>? = null
    private var reconnectJob: Job? = null
    private var dropWatchJob: Job? = null
    private var reconnectTries = 0
    private var joinedAtMs = 0L

    private val _remoteVideos = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    /** The video of each other person on a group call, by uid. */
    val remoteVideos: StateFlow<Map<String, VideoTrack>> = _remoteVideos.asStateFlow()

    private open class BaseObserver : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(p0: IceCandidate?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    /** Starts a group call in the chat [groupId] and rings every member. */
    fun startGroupCall(groupId: String, title: String, hostName: String, video: Boolean) {
        if (_state.value.phase != CallPhase.IDLE) return
        resetInternals()
        _state.value = CallState(
            phase = CallPhase.STARTING, peerName = title.ifBlank { "Group call" }, isVideo = video,
            cameraOn = video, speakerOn = video, isGroup = true, groupId = groupId
        )
        CallService.start(appContext)
        scope.launch {
            try {
                val id = gRepo.create(groupId, hostName, video)
                gCallId = id
                gDoc = gRepo.getCall(id)
                _state.update { it.copy(callId = id) }
                joinSfu(hostName, video)
                gDoc?.let { d -> runCatching { gRepo.logMine(d, outgoing = true, joined = true) } }
                GroupCallApi.ring(id)
            } catch (e: Exception) {
                Log.w(TAG, "startGroupCall failed: ${e.message}")
                failGroup()
            }
        }
    }

    /** Joins a group call that is already running. */
    fun joinGroupCall(callId: String, myName: String, title: String = "") {
        if (_state.value.phase != CallPhase.IDLE) return
        resetInternals()
        _state.value = CallState(
            phase = CallPhase.CONNECTING, callId = callId, peerName = title.ifBlank { "Group call" }, isGroup = true
        )
        CallService.start(appContext)
        scope.launch {
            try {
                val doc = gRepo.getCall(callId)
                if (doc == null || !doc.isActive) {
                    finish(CallEndReason.CANCELLED)
                    return@launch
                }
                gCallId = callId
                gDoc = doc
                _state.update {
                    it.copy(groupId = doc.groupId, isVideo = doc.isVideo, cameraOn = doc.isVideo, speakerOn = doc.isVideo)
                }
                joinSfu(myName, doc.isVideo)
                runCatching { gRepo.logMine(doc, outgoing = false, joined = true) }
            } catch (e: Exception) {
                Log.w(TAG, "joinGroupCall failed: ${e.message}")
                failGroup()
            }
        }
    }

    /** Leave the call. The last person to leave ends it for everyone and leaves a line in the chat. */
    fun leaveGroupCall() {
        if (!_state.value.isGroup || finished) return
        val callId = gCallId
        scope.launch {
            var ended: GroupCallDoc? = null
            val doc = gDoc
            if (doc != null && joinedAtMs > 0) {
                runCatching { gRepo.logMine(doc, outgoing = doc.hostUid == myUid, joined = true, durationSec = (System.currentTimeMillis() - joinedAtMs) / 1000) }
            }
            if (callId.isNotBlank()) ended = runCatching { gRepo.leave(callId) }.getOrNull()
            if (ended != null) {
                runCatching { GroupCallApi.end(callId) }
                val kind = if (ended.isVideo) "Group video call" else "Group voice call"
                val text = if (!othersSeen) "$kind · no answer"
                else "$kind · ${CallRepository.formatDuration((System.currentTimeMillis() - ended.createdAt) / 1000)}"
                runCatching { gRepo.writeChatLine(ended, text) }
            }
            finish(CallEndReason.COMPLETED)
        }
    }

    private fun failGroup() {
        val callId = gCallId
        scope.launch {
            if (callId.isNotBlank()) runCatching { gRepo.leave(callId) }
            finish(CallEndReason.FAILED)
        }
    }

    /** The relay (TURN) servers for this call, fetched once. Without them only STUN is used. */
    private suspend fun groupIceServers(): List<PeerConnection.IceServer> {
        iceCache?.let { return it }
        val stun = PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
        val relay = withTimeoutOrNull(4000) { runCatching { GroupCallApi.iceServers(gCallId) }.getOrNull() }.orEmpty()
        val list = listOf(stun) + relay.map { s ->
            PeerConnection.IceServer.builder(s.urls).apply {
                s.username?.let { setUsername(it) }
                s.credential?.let { setPassword(it) }
            }.createIceServer()
        }
        if (relay.isNotEmpty()) iceCache = list
        return list
    }

    private suspend fun joinSfu(myName: String, video: Boolean) {
        val callId = gCallId
        groupVideo = video
        gMyName = myName
        reconnectTries = 0
        connectSfu(video)
        startAudioRouting()
        startConnectTimeout()

        // Now others can find my session and pull it
        joinedAtMs = System.currentTimeMillis()
        gRepo.join(callId, myName, pushSession, video)
        peersJob = scope.launch { gRepo.observePeers(callId).collect { onGroupPeers(it) } }
        groupCallJob = scope.launch {
            gRepo.observeCall(callId).collect { d ->
                if (d != null) gDoc = d
                if (d != null && !d.isActive && !finished) finish(CallEndReason.COMPLETED)
            }
        }
    }

    /** Builds the sending and receiving connections and their SFU sessions. Used at the start and again after a drop. */
    private suspend fun connectSfu(video: Boolean) {
        val callId = gCallId
        val config = PeerConnection.RTCConfiguration(groupIceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        // ---- the connection that SENDS my microphone and camera
        val pushRef = arrayOfNulls<PeerConnection>(1)
        val push = factory.createPeerConnection(config, object : BaseObserver() {
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (pushRef[0] !== pushPc) return // an old connection that was replaced
                scope.launch { onGroupPushState(newState) }
            }
        }) ?: throw IllegalStateException("could not create the connection")
        pushRef[0] = push
        pushPc = push

        val aTrack = audioTrack ?: run {
            val src = factory.createAudioSource(MediaConstraints())
            audioSource = src
            factory.createAudioTrack("audio0", src).also { audioTrack = it; it.setEnabled(!_state.value.muted) }
        }
        val vTrack = videoTrack ?: run {
            val src = factory.createVideoSource(false)
            videoSource = src
            factory.createVideoTrack("video0", src).also { videoTrack = it; _localVideo.value = it }
        }

        val sendOnly = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
        val aTr = push.addTransceiver(aTrack, sendOnly)
        val vTr = push.addTransceiver(vTrack, sendOnly)
        if (video || _state.value.cameraOn) {
            startCapture()
            vTrack.setEnabled(_state.value.cameraOn || video)
        } else {
            vTrack.setEnabled(false)
        }

        pushSession = GroupCallApi.sfu(callId, "session")?.optString("sessionId").orEmpty()
        if (pushSession.isBlank()) throw IllegalStateException("no sending session")
        val offer = createOfferOn(push)
        setLocalOn(push, offer)
        val tracks = JSONArray()
            .put(JSONObject().put("location", "local").put("mid", aTr.mid).put("trackName", "audio"))
            .put(JSONObject().put("location", "local").put("mid", vTr.mid).put("trackName", "video"))
        val res = GroupCallApi.sfu(callId, "tracks", sessionId = pushSession, tracks = tracks, sdpType = "offer", sdp = offer.description)
            ?: throw IllegalStateException("could not send tracks")
        val answer = res.optJSONObject("sessionDescription") ?: throw IllegalStateException("no answer from the SFU")
        setRemoteOn(push, SessionDescription(SessionDescription.Type.ANSWER, answer.getString("sdp")))

        // ---- the connection that RECEIVES everybody else
        val pullRef = arrayOfNulls<PeerConnection>(1)
        val pull = factory.createPeerConnection(config, object : BaseObserver() {
            override fun onTrack(transceiver: RtpTransceiver?) {
                onGroupTrack(transceiver)
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (pullRef[0] !== pullPc) return
                if (newState == PeerConnection.PeerConnectionState.FAILED) scope.launch { startGroupReconnect() }
            }
        }) ?: throw IllegalStateException("could not create the receiving connection")
        pullRef[0] = pull
        pullPc = pull
        pullSession = GroupCallApi.sfu(callId, "session")?.optString("sessionId").orEmpty()
        if (pullSession.isBlank()) throw IllegalStateException("no receiving session")
    }

    /**
     * The network dropped for good (or the connection failed): build both connections again and keep
     * trying for two minutes, with the "Reconnecting" bar on. Then leave, so nobody sits on a dead call.
     */
    private fun startGroupReconnect() {
        if (finished || !_state.value.isGroup || reconnectJob?.isActive == true) return
        if (_state.value.phase != CallPhase.CONNECTED && _state.value.phase != CallPhase.CONNECTING) return
        dropWatchJob?.cancel()
        reconnectJob = scope.launch {
            _state.update { it.copy(reconnecting = true, weakSignal = false) }
            val deadline = System.currentTimeMillis() + 120_000
            var wait = 1500L
            while (!finished && System.currentTimeMillis() < deadline) {
                try {
                    reconnectTries++
                    dropGroupMedia()
                    connectSfu(groupVideo)
                    // A new sending session: everyone else sees it here and pulls me again
                    val cam = _state.value.cameraOn
                    gRepo.join(gCallId, gMyName, pushSession, cam)
                    if (_state.value.muted) runCatching { gRepo.updatePeer(gCallId, "muted", true) }
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "reconnect failed: ${e.message}")
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(8000L)
                }
            }
            if (!finished) leaveGroupCall()
        }
    }

    /** Closes the two connections (not the microphone or camera) so they can be built again. */
    private suspend fun dropGroupMedia() {
        val oldPush = pushPc
        val oldPull = pullPc
        pushPc = null; pullPc = null
        pullMutex.withLock {
            midOwner.clear(); pulledSessions.clear(); pulling.clear()
        }
        _remoteVideos.value = emptyMap()
        runCatching { oldPush?.close() }
        runCatching { oldPull?.close() }
        runCatching { oldPush?.dispose() }
        runCatching { oldPull?.dispose() }
    }

    private fun onGroupPushState(st: PeerConnection.PeerConnectionState?) {
        when (st) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                dropWatchJob?.cancel()
                reconnectTries = 0
                onConnected()
            }
            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                if (_state.value.phase == CallPhase.CONNECTED) _state.update { it.copy(weakSignal = true) }
                // A short drop heals on its own. If it lasts, build the connections again.
                dropWatchJob?.cancel()
                dropWatchJob = scope.launch {
                    delay(8000)
                    startGroupReconnect()
                }
            }
            PeerConnection.PeerConnectionState.FAILED -> startGroupReconnect()
            else -> Unit
        }
    }

    private fun onGroupPeers(peers: List<GroupPeer>) {
        val me = myUid
        val others = peers.filter { it.uid != me && it.sessionId.isNotBlank() }
        if (others.isNotEmpty()) othersSeen = true
        _state.update {
            it.copy(participants = peers.map { p -> GroupMemberState(p.uid, p.name, p.cameraOn, p.muted, p.uid == me) })
        }
        for (p in others) {
            if (pulledSessions[p.uid] != p.sessionId && pulling.add(p.uid)) {
                scope.launch {
                    var ok = false
                    repeat(3) {
                        if (!ok) {
                            ok = runCatching { pullPeer(p) }.onFailure { Log.w(TAG, "pull ${p.uid}: ${it.message}") }.isSuccess
                            if (!ok) delay(1500)
                        }
                    }
                    pulling.remove(p.uid)
                }
            }
        }
        // People who left
        val ids = others.map { it.uid }.toSet()
        for (uid in pulledSessions.keys.toList()) {
            if (uid !in ids) {
                pulledSessions.remove(uid)
                _remoteVideos.update { it - uid }
            }
        }
    }

    /** Pulls one person's microphone and camera from the SFU. */
    private suspend fun pullPeer(p: GroupPeer) = pullMutex.withLock {
        if (pulledSessions[p.uid] == p.sessionId) return@withLock
        val pull = pullPc ?: return@withLock
        val tracks = JSONArray()
            .put(JSONObject().put("location", "remote").put("sessionId", p.sessionId).put("trackName", "audio"))
            .put(JSONObject().put("location", "remote").put("sessionId", p.sessionId).put("trackName", "video"))
        val res = GroupCallApi.sfu(gCallId, "tracks", sessionId = pullSession, tracks = tracks)
            ?: throw IllegalStateException("pull failed")
        val arr = res.optJSONArray("tracks")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                if (t.has("errorCode")) throw IllegalStateException("track error ${t.optString("errorCode")}")
                val mid = t.optString("mid")
                if (mid.isNotBlank()) midOwner[mid] = p.uid to t.optString("trackName")
            }
        }
        val sd = res.optJSONObject("sessionDescription")
        if (sd != null && sd.optString("type") == "offer") {
            setRemoteOn(pull, SessionDescription(SessionDescription.Type.OFFER, sd.getString("sdp")))
            val answer = createAnswerOn(pull)
            setLocalOn(pull, answer)
            GroupCallApi.sfu(gCallId, "renegotiate", sessionId = pullSession, sdpType = "answer", sdp = answer.description)
                ?: throw IllegalStateException("renegotiate failed")
        }
        pulledSessions[p.uid] = p.sessionId
    }

    private fun onGroupTrack(transceiver: RtpTransceiver?) {
        val t = transceiver ?: return
        val mid = t.mid ?: return
        val owner = midOwner[mid] ?: return
        val track = t.receiver.track() ?: return
        if (track is VideoTrack) {
            track.setEnabled(true)
            _remoteVideos.update { it + (owner.first to track) }
        }
    }

    private suspend fun createOfferOn(c: PeerConnection): SessionDescription = suspendCancellableCoroutine { cont ->
        c.createOffer(object : SimpleSdp() {
            override fun onCreateSuccess(d: SessionDescription?) { if (d != null) cont.resume(d) else cont.resumeWithException(IllegalStateException("no offer")) }
            override fun onCreateFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "offer failed")) }
        }, MediaConstraints())
    }

    private suspend fun createAnswerOn(c: PeerConnection): SessionDescription = suspendCancellableCoroutine { cont ->
        c.createAnswer(object : SimpleSdp() {
            override fun onCreateSuccess(d: SessionDescription?) { if (d != null) cont.resume(d) else cont.resumeWithException(IllegalStateException("no answer")) }
            override fun onCreateFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "answer failed")) }
        }, MediaConstraints())
    }

    private suspend fun setLocalOn(c: PeerConnection, d: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
        c.setLocalDescription(object : SimpleSdp() {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "set local failed")) }
        }, d)
    }

    private suspend fun setRemoteOn(c: PeerConnection, d: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
        c.setRemoteDescription(object : SimpleSdp() {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "set remote failed")) }
        }, d)
    }

    // ================================================================ the connection

    private fun setUpConnection(ice: List<IceServer>, video: Boolean) {
        val servers = ice.map { s ->
            PeerConnection.IceServer.builder(s.urls).apply {
                s.username?.let { setUsername(it) }
                s.credential?.let { setPassword(it) }
            }.createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val connection = factory.createPeerConnection(config, observer)
            ?: throw IllegalStateException("could not create the connection")
        pc = connection

        // Microphone
        val aSource = factory.createAudioSource(MediaConstraints())
        audioSource = aSource
        val aTrack = factory.createAudioTrack("audio0", aSource)
        audioTrack = aTrack
        connection.addTrack(aTrack, listOf(STREAM_ID))

        // Camera: always set up, so a voice call can become a video call without starting over.
        // It is switched off (not captured) unless this is a video call.
        val vSource = factory.createVideoSource(false)
        videoSource = vSource
        val vTrack = factory.createVideoTrack("video0", vSource)
        videoTrack = vTrack
        connection.addTrack(vTrack, listOf(STREAM_ID))
        _localVideo.value = vTrack

        if (video) {
            startCapture()
            vTrack.setEnabled(true)
        } else {
            vTrack.setEnabled(false)
        }
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate?) {
            val cand = c ?: return
            val callId = _state.value.callId
            if (callId.isBlank()) return
            val asCaller = _state.value.isOutgoing
            scope.launch {
                runCatching { repo.addCandidate(callId, asCaller, CandidateDoc(cand.sdpMid, cand.sdpMLineIndex, cand.sdp)) }
            }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track()
            if (track is VideoTrack) scope.launch { _remoteVideo.value = track }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            scope.launch {
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> onConnected()
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        if (_state.value.phase == CallPhase.CONNECTED) _state.update { it.copy(weakSignal = true) }
                        // A short drop is normal on mobile; give it a few seconds to come back
                        dropJob?.cancel()
                        dropJob = scope.launch {
                            delay(DROP_GRACE_SECONDS * 1000L)
                            if (_state.value.phase == CallPhase.CONNECTED) endBecauseLost()
                        }
                    }
                    PeerConnection.PeerConnectionState.FAILED -> endBecauseLost()
                    else -> Unit
                }
            }
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    private fun onConnected() {
        dropJob?.cancel()
        if (_state.value.phase == CallPhase.CONNECTED) {
            // It came back after a short drop
            badStreak = 0
            _state.update { it.copy(weakSignal = false, reconnecting = false) }
            return
        }
        if (finished) return
        ringTimeoutJob?.cancel()
        connectTimeoutJob?.cancel()
        connectedAtMs = System.currentTimeMillis()
        _state.update { it.copy(phase = CallPhase.CONNECTED, seconds = 0, weakSignal = false) }
        startQualityWatch()
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(seconds = (System.currentTimeMillis() - connectedAtMs) / 1000) }
            }
        }
    }

    /** Every few seconds, looks at delay, jitter and lost packets. Two bad readings in a row show the warning. */
    private fun startQualityWatch() {
        qualityJob?.cancel()
        badStreak = 0; goodStreak = 0; lastLost = -1L; lastRecv = -1L
        qualityJob = scope.launch {
            while (true) {
                delay(3000)
                val conn = pc ?: pushPc ?: continue
                try {
                    conn.getStats { report -> scope.launch { judgeQuality(report) } }
                } catch (e: Exception) {
                    Log.w(TAG, "stats: ${e.message}")
                }
            }
        }
    }

    private fun judgeQuality(report: org.webrtc.RTCStatsReport) {
        if (_state.value.phase != CallPhase.CONNECTED) return
        var rtt = 0.0
        var jitter = 0.0
        var lost = -1L
        var recv = -1L
        for (st in report.statsMap.values) {
            val m = st.members
            when (st.type) {
                "candidate-pair" -> if (m["state"] == "succeeded") {
                    (m["currentRoundTripTime"] as? Number)?.let { if (it.toDouble() > 0) rtt = it.toDouble() }
                }
                "inbound-rtp" -> if (m["kind"] == "audio" || m["mediaType"] == "audio") {
                    lost = (m["packetsLost"] as? Number)?.toLong() ?: lost
                    recv = (m["packetsReceived"] as? Number)?.toLong() ?: recv
                    jitter = (m["jitter"] as? Number)?.toDouble() ?: jitter
                }
            }
        }
        var lossRatio = 0.0
        if (lost >= 0 && recv >= 0) {
            if (lastLost >= 0 && lastRecv >= 0) {
                val dl = (lost - lastLost).coerceAtLeast(0)
                val dr = (recv - lastRecv).coerceAtLeast(0)
                if (dl + dr > 0) lossRatio = dl.toDouble() / (dl + dr)
            }
            lastLost = lost; lastRecv = recv
        }
        val bad = rtt > 0.6 || jitter > 0.08 || lossRatio > 0.08
        if (bad) { badStreak++; goodStreak = 0 } else { goodStreak++; badStreak = 0 }
        if (badStreak >= 2 && !_state.value.weakSignal) _state.update { it.copy(weakSignal = true) }
        if (goodStreak >= 2 && _state.value.weakSignal) _state.update { it.copy(weakSignal = false) }
    }

    private fun endBecauseLost() {
        if (finished) return
        val callId = _state.value.callId
        scope.launch {
            runCatching { repo.end(callId) }
            finish(CallEndReason.FAILED, writeLine = if (connectedAtMs > 0) CallStatus.ENDED else CallStatus.MISSED)
        }
    }

    private fun startConnectTimeout() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(CONNECT_SECONDS * 1000L)
            val st = _state.value
            if (st.isGroup && (st.phase == CallPhase.CONNECTING || st.phase == CallPhase.STARTING)) leaveGroupCall()
            else if (st.phase == CallPhase.CONNECTING) endBecauseLost()
        }
    }

    // ================================================================ watching the call document

    private fun observeCallDoc(callId: String) {
        observeJob?.cancel()
        observeJob = scope.launch {
            repo.observeCall(callId).collect { doc ->
                if (doc == null || finished) return@collect
                call = doc
                val s = _state.value
                when {
                    // The other person hung up, declined or it timed out
                    CallStatus.isOver(doc.status) && s.phase != CallPhase.ENDED -> {
                        val reason = when (doc.status) {
                            CallStatus.DECLINED -> CallEndReason.DECLINED
                            CallStatus.CANCELLED, CallStatus.MISSED ->
                                if (s.isOutgoing) CallEndReason.NO_ANSWER else CallEndReason.CANCELLED
                            else -> if (connectedAtMs > 0) CallEndReason.COMPLETED else CallEndReason.CANCELLED
                        }
                        finish(reason, writeLine = doc.status)
                    }
                    // The caller: the other person answered
                    s.isOutgoing && doc.answer != null && !remoteSet -> {
                        ringTimeoutJob?.cancel()
                        _state.update { it.copy(phase = CallPhase.CONNECTING) }
                        runCatching {
                            setRemote(SessionDescription(SessionDescription.Type.fromCanonicalForm(doc.answer.type), doc.answer.sdp))
                        }.onFailure {
                            Log.w(TAG, "remote answer: ${it.message}")
                            endBecauseLost()
                        }
                        startConnectTimeout()
                    }
                }
            }
        }
    }

    private fun observeCandidates(callId: String, fromCaller: Boolean) {
        candidateJob?.cancel()
        candidateJob = scope.launch {
            repo.observeCandidates(callId, fromCaller).collect { c ->
                val ice = IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate)
                if (remoteSet) pc?.addIceCandidate(ice) else pendingCandidates.add(ice)
            }
        }
    }

    // ================================================================ SDP helpers

    private suspend fun createOffer(): SessionDescription = suspendCancellableCoroutine { cont ->
        pc!!.createOffer(object : SimpleSdp() {
            override fun onCreateSuccess(d: SessionDescription?) { if (d != null) cont.resume(d) else cont.resumeWithException(IllegalStateException("no offer")) }
            override fun onCreateFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "offer failed")) }
        }, MediaConstraints())
    }

    private suspend fun createAnswer(): SessionDescription = suspendCancellableCoroutine { cont ->
        pc!!.createAnswer(object : SimpleSdp() {
            override fun onCreateSuccess(d: SessionDescription?) { if (d != null) cont.resume(d) else cont.resumeWithException(IllegalStateException("no answer")) }
            override fun onCreateFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "answer failed")) }
        }, MediaConstraints())
    }

    private suspend fun setLocal(d: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
        pc!!.setLocalDescription(object : SimpleSdp() {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "set local failed")) }
        }, d)
    }

    private suspend fun setRemote(d: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
        pc!!.setRemoteDescription(object : SimpleSdp() {
            override fun onSetSuccess() {
                remoteSet = true
                // Candidates that arrived before the answer can go in now
                pendingCandidates.forEach { pc?.addIceCandidate(it) }
                pendingCandidates.clear()
                cont.resume(Unit)
            }
            override fun onSetFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "set remote failed")) }
        }, d)
    }

    private open class SimpleSdp : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }

    // ================================================================ camera and sound

    private fun startCapture() {
        if (capturing) return
        val source = videoSource ?: return
        try {
            val enumerator = Camera2Enumerator(appContext)
            val front = _state.value.frontCamera
            val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) == front }
                ?: enumerator.deviceNames.firstOrNull() ?: return
            val cap = enumerator.createCapturer(name, null) ?: return
            val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            cap.initialize(helper, appContext, source.capturerObserver)
            cap.startCapture(1280, 720, 30)
            capturer = cap
            surfaceHelper = helper
            capturing = true
        } catch (e: Exception) {
            Log.w(TAG, "camera: ${e.message}")
        }
    }

    private fun stopCapture() {
        if (!capturing) return
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { surfaceHelper?.dispose() }
        capturer = null
        surfaceHelper = null
        capturing = false
    }

    // ---- audio routes (earpiece, speaker, Bluetooth, wired headset)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var routingActive = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onDevicesChanged(added = true)
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onDevicesChanged(added = false)
    }

    private fun isBluetoothType(t: Int): Boolean =
        t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && t == AudioDeviceInfo.TYPE_BLE_HEADSET)

    private fun isWiredType(t: Int): Boolean =
        t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                t == AudioDeviceInfo.TYPE_USB_HEADSET

    /** Devices a call can use right now. */
    private fun communicationDevices(): List<AudioDeviceInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }

    private fun availableRoutes(): List<AudioRoute> {
        val devices = communicationDevices()
        val out = mutableListOf<AudioRoute>()
        if (devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE } || devices.isEmpty()) out.add(AudioRoute.EARPIECE)
        out.add(AudioRoute.SPEAKER)
        if (devices.any { isBluetoothType(it.type) }) out.add(AudioRoute.BLUETOOTH)
        if (devices.any { isWiredType(it.type) }) out.add(AudioRoute.WIRED)
        return out
    }

    private fun bestNonSpeaker(): AudioRoute {
        val a = availableRoutes()
        return when {
            AudioRoute.BLUETOOTH in a -> AudioRoute.BLUETOOTH
            AudioRoute.WIRED in a -> AudioRoute.WIRED
            else -> AudioRoute.EARPIECE
        }
    }

    /** Bumped on every route change so a late retry of an older choice never overrides a newer one. */
    private var routeToken = 0

    private fun applyRoute(route: AudioRoute) {
        val token = ++routeToken
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyRouteModern(route, token, attempt = 0)
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                if (route == AudioRoute.BLUETOOTH) {
                    audioManager.isSpeakerphoneOn = false
                    // A Bluetooth link that was just closed needs a moment before it can be opened again
                    audioManager.stopBluetoothSco()
                    mainHandler.postDelayed({
                        if (token == routeToken) runCatching {
                            audioManager.startBluetoothSco()
                            audioManager.isBluetoothScoOn = true
                        }
                    }, 400)
                } else {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = route == AudioRoute.SPEAKER
                }
            }
        }
    }

    /**
     * Android 12 and newer. Choosing a headset can fail the first time, for example straight after
     * leaving it (the phone is still closing the old link). So it is checked and tried again.
     */
    private fun applyRouteModern(route: AudioRoute, token: Int, attempt: Int) {
        if (token != routeToken) return
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            val devices = audioManager.availableCommunicationDevices
            val target = when (route) {
                AudioRoute.EARPIECE -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                AudioRoute.SPEAKER -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                AudioRoute.BLUETOOTH ->
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                        ?: devices.firstOrNull { isBluetoothType(it.type) }
                AudioRoute.WIRED -> devices.firstOrNull { isWiredType(it.type) }
            }
            var done = false
            if (target != null) {
                // Reset first so an old choice cannot get in the way
                if (route == AudioRoute.BLUETOOTH) audioManager.clearCommunicationDevice()
                done = audioManager.setCommunicationDevice(target) &&
                        (route != AudioRoute.BLUETOOTH || audioManager.communicationDevice?.id == target.id)
            }
            if (!done && route == AudioRoute.BLUETOOTH && attempt < 6) {
                mainHandler.postDelayed({ applyRouteModern(route, token, attempt + 1) }, 500)
            } else if (done && route == AudioRoute.BLUETOOTH) {
                // Check again a moment later that it really stayed on the headset
                mainHandler.postDelayed({
                    if (token == routeToken && audioManager.communicationDevice?.let { isBluetoothType(it.type) } != true && attempt < 6) {
                        applyRouteModern(route, token, attempt + 1)
                    }
                }, 800)
            }
        }
    }

    /** A headset was plugged in or paired, or taken away, during the call. */
    private fun onDevicesChanged(added: Boolean) {
        if (!routingActive) return
        val available = availableRoutes()
        val current = _state.value.audioRoute
        when {
            // A headset just appeared: use it, like a phone call does
            added && AudioRoute.BLUETOOTH in available && current != AudioRoute.BLUETOOTH -> setAudioRoute(AudioRoute.BLUETOOTH)
            added && AudioRoute.WIRED in available && current != AudioRoute.WIRED && current != AudioRoute.BLUETOOTH -> setAudioRoute(AudioRoute.WIRED)
            // The one in use is gone: go back to the phone itself
            current !in available -> setAudioRoute(if (_state.value.isVideo || _state.value.cameraOn) AudioRoute.SPEAKER else AudioRoute.EARPIECE)
            else -> _state.update { it.copy(routes = available) }
        }
    }

    private fun startAudioRouting() {
        savedAudioMode = audioManager.mode
        savedSpeaker = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        routingActive = true
        runCatching { audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler) }
        // Start on a headset when one is connected, otherwise the speaker (video) or the earpiece (voice)
        val available = availableRoutes()
        val first = when {
            AudioRoute.BLUETOOTH in available -> AudioRoute.BLUETOOTH
            AudioRoute.WIRED in available -> AudioRoute.WIRED
            _state.value.speakerOn -> AudioRoute.SPEAKER
            else -> AudioRoute.EARPIECE
        }
        applyRoute(first)
        _state.update { it.copy(audioRoute = first, speakerOn = first == AudioRoute.SPEAKER, routes = available) }
    }

    private fun stopAudioRouting() {
        routingActive = false
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
                audioManager.isSpeakerphoneOn = savedSpeaker
            }
            audioManager.mode = savedAudioMode
        }
    }

    // ================================================================ finishing

    /**
     * Ends everything. [writeLine] is the final status for the "Voice call · 2:41" line in the chat;
     * only the caller's phone writes it (see CallRepository.writeChatLine).
     */
    private fun finish(reason: CallEndReason, writeLine: String? = null) {
        if (finished) return
        finished = true
        val doc = call
        val duration = if (connectedAtMs > 0) (System.currentTimeMillis() - connectedAtMs) / 1000 else 0L
        if (doc != null && writeLine != null) {
            scope.launch { runCatching { repo.writeChatLine(doc, writeLine, duration) } }
        }
        releaseMedia()
        IncomingCallNotifier.cancel(appContext)
        _state.update { it.copy(phase = CallPhase.ENDED, endReason = reason) }
    }

    private fun failAndCleanup() {
        val callId = _state.value.callId
        if (callId.isNotBlank() && _state.value.isOutgoing) {
            scope.launch { runCatching { repo.cancel(callId) } }
        }
        finish(CallEndReason.FAILED)
    }

    private fun releaseMedia() {
        observeJob?.cancel(); candidateJob?.cancel(); ringTimeoutJob?.cancel()
        connectTimeoutJob?.cancel(); tickerJob?.cancel(); dropJob?.cancel(); qualityJob?.cancel()
        peersJob?.cancel(); groupCallJob?.cancel(); peersJob = null; groupCallJob = null
        reconnectJob?.cancel(); dropWatchJob?.cancel(); reconnectJob = null; dropWatchJob = null
        stopCapture()
        runCatching { pushPc?.close() }
        runCatching { pullPc?.close() }
        runCatching { pushPc?.dispose() }
        runCatching { pullPc?.dispose() }
        pushPc = null; pullPc = null
        _remoteVideos.value = emptyMap()
        runCatching { pc?.close() }
        runCatching { pc?.dispose() }
        runCatching { audioTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { videoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        pc = null; audioTrack = null; audioSource = null; videoTrack = null; videoSource = null
        _localVideo.value = null
        _remoteVideo.value = null
        stopAudioRouting()
    }

    private fun resetInternals() {
        releaseMedia()
        call = null
        gCallId = ""; gDoc = null; pushSession = ""; pullSession = ""; othersSeen = false
        iceCache = null; reconnectTries = 0; joinedAtMs = 0L; groupVideo = false
        midOwner.clear(); pulledSessions.clear(); pulling.clear()
        remoteSet = false
        pendingCandidates.clear()
        finished = false
        connectedAtMs = 0L
    }

    companion object {
        private const val TAG = "CallEngine"
        private const val STREAM_ID = "dibadge_stream"
        private const val RING_SECONDS = 45
        private const val CONNECT_SECONDS = 25
        private const val DROP_GRACE_SECONDS = 8

        @Volatile
        private var instance: CallEngine? = null

        fun get(context: Context): CallEngine =
            instance ?: synchronized(this) {
                instance ?: CallEngine(context.applicationContext).also { instance = it }
            }
    }
}