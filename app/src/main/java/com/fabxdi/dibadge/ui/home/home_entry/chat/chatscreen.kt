package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.fabxdi.dibadge.util.ScanNameValidator
import com.fabxdi.dibadge.util.UserColorUtils
import java.io.FileOutputStream
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.ui.res.painterResource
import com.fabxdi.dibadge.R
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.components.AttachmentList
import com.fabxdi.dibadge.ui.home.AvatarPhotoCache
import com.fabxdi.dibadge.ui.home.ChatAvatar
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.ChatDetailsScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.AudioMerger
import com.fabxdi.dibadge.util.AudioPlayer
import com.fabxdi.dibadge.util.AudioRecorder
import com.fabxdi.dibadge.util.FileBlocklistValidator
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.MediaCompressor
import com.fabxdi.dibadge.viewmodel.ChatViewModel
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.GroupMember
import com.fabxdi.dibadge.viewmodel.GroupConnectionItem
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    entry: HomeEntryEntity,
    onBack: () -> Unit,
    onOpenMyForms: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val reminderViewModel: ReminderViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()
    val detailsViewModel: GroupDetailsViewModel = viewModel()

    val homeEntries by reminderViewModel.allHomeEntries.collectAsState(initial = emptyList())
    val currentEntry = homeEntries.find { it.id == entry.id } ?: entry

    LaunchedEffect(currentEntry.id) {
        // Start loading the messages first; clearing the cache is housekeeping and runs off the main thread
        chatViewModel.listenToChatMessages(context, currentEntry.id.toString())
        launch(kotlinx.coroutines.Dispatchers.IO) { MediaCompressor.cleanCache(context) }
        detailsViewModel.loadGroupDetails(
            groupId = currentEntry.id.toString(),
            initialName = currentEntry.title,
            initialSubtitle = currentEntry.subtitle
        )
    }

    // Chats opened before are in memory: their bubbles are there on the very first frame
    remember(currentEntry.id) { chatViewModel.primeFromCache(currentEntry.id.toString()); true }
    val messages by chatViewModel.messages.collectAsState()
    val messagesLoaded by chatViewModel.messagesLoaded.collectAsState()
    val members by detailsViewModel.members.collectAsState()
    val isAdmin by detailsViewModel.isAdmin.collectAsState()
    val allConnections by detailsViewModel.acceptedConnections.collectAsState()
    // Pending invites we sent ("invited") are in the list for the details screen, but are not connected yet
    val acceptedConnections = remember(allConnections) { allConnections.filter { it.otherEntityType != "invited" } }
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    val groupCreatedDateText = remember(currentEntry) {
        currentEntry.timestamp.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }

    var inputText by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAttachmentOptions by remember { mutableStateOf(false) }
    var attachedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showCamera by remember { mutableStateOf(false) }
    var isCameraForStampMode by remember { mutableStateOf(false) }
    var attachSource by remember { mutableStateOf(AttachSource.PICKER) }

    var galleryMedia by remember { mutableStateOf<List<Uri>?>(null) }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }
    var galleryMessage by remember { mutableStateOf<ChatMessage?>(null) }

    var audioPlayerMedia by remember { mutableStateOf<List<Uri>?>(null) }
    var audioPlayerInitialIndex by remember { mutableIntStateOf(0) }

    var selectedMessages by remember { mutableStateOf(setOf<String>()) }
    var selectedAttachmentUri by remember { mutableStateOf<String?>(null) }
    var selectedAttachmentUris by remember { mutableStateOf(setOf<Uri>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var replyingToAttachmentUriState by remember { mutableStateOf<String?>(null) }
    var pendingDurations by remember { mutableStateOf(mapOf<String, Long>()) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteModeForMeOnly by remember { mutableStateOf(false) }
    var showGroupDetails by remember { mutableStateOf(false) }
    var showMessageOptionsSheet by remember { mutableStateOf(false) }
    var selectedCommentMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedSentToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedPollForVotes by remember { mutableStateOf<ChatMessage?>(null) }
    var pollToEnd by remember { mutableStateOf<ChatMessage?>(null) }
    var showShareLocationSheet by remember { mutableStateOf(false) }
    var showNewPollScreen by remember { mutableStateOf(false) }
    var showForwardSheet by remember { mutableStateOf(false) }
    var longPressedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    // Where each bubble was last drawn, so the long-press menu can sit on the bubble
    val bubbleBoundsMap = remember { HashMap<String, androidx.compose.ui.geometry.Rect>() }

    var selectedDocInfoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var isDocInfoPreSend by remember { mutableStateOf(false) }
    var seenThreadIds by remember { mutableStateOf(setOf<String>()) }
    var activePickerTabOverride by remember { mutableStateOf<MentionPickerMode?>(null) }
    var selectedFailedMessage by remember { mutableStateOf<ChatMessage?>(null) }

    val isOneToOne = remember(currentEntry, members) {
        val subLower = currentEntry.subtitle.trim().lowercase()
        subLower in listOf("direct", "chat", "one-to-one") || (members.size <= 2 && !subLower.endsWith("member") && !subLower.endsWith("members") && subLower != "project group" && subLower != "group")
    }

    val otherMember = remember(members, currentUserId) {
        members.find { !it.isYou && it.uid != currentUserId }
    }
    val otherUid = otherMember?.uid ?: ""

    var otherUserNickname by remember { mutableStateOf("") }
    var otherUserPhotoUrl by remember { mutableStateOf("") }
    var otherUserName by remember { mutableStateOf("") }
    var otherUserFirstName by remember { mutableStateOf("") }
    var isBlockedByMe by remember { mutableStateOf(false) }

    LaunchedEffect(otherUid, currentUserId, isOneToOne) {
        if (otherUid.isNotBlank() && isOneToOne) {
            val db = FirebaseFirestore.getInstance()
            // 1. Other user's public profile
            db.collection("users").document(otherUid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val photo = snapshot.getString("photoUrl") ?: snapshot.getString("avatarUrl") ?: ""
                    val disp = snapshot.getString("displayName") ?: currentEntry.title
                    val first = snapshot.getString("firstName") ?: disp.split(" ").firstOrNull().orEmpty()
                    otherUserPhotoUrl = photo
                    otherUserName = disp
                    otherUserFirstName = first
                }
            }

            if (currentUserId.isNotBlank()) {
                // 2. Private nickname given by current user
                db.collection("users").document(currentUserId)
                    .collection("nicknames").document(otherUid)
                    .addSnapshotListener { snap, _ ->
                        otherUserNickname = if (snap != null && snap.exists()) snap.getString("nickname") ?: "" else ""
                    }

                // 3. Block status
                db.collection("blockedUsers").document(currentUserId)
                    .collection("blocked").document(otherUid)
                    .addSnapshotListener { snap, _ ->
                        isBlockedByMe = (snap != null && snap.exists())
                    }
            }
        }
    }

    val prefs = remember(context) { context.getSharedPreferences("chat_drafts", Context.MODE_PRIVATE) }
    val draftKey = remember(currentEntry.id) { "draft_${currentEntry.id}" }

    LaunchedEffect(currentEntry.id) {
        val savedDraft = prefs.getString(draftKey, "") ?: ""
        if (savedDraft.isNotBlank() && inputText.isBlank()) {
            inputText = savedDraft
        }
    }

    DisposableEffect(currentEntry.id) {
        onDispose {
            val current = inputText.trim()
            if (current.isNotBlank()) {
                prefs.edit().putString(draftKey, current).apply()
            } else {
                prefs.edit().remove(draftKey).apply()
            }
        }
    }

    var voiceRecordState by remember { mutableStateOf(VoiceRecordState.NONE) }
    var voiceRecordDurationMs by remember { mutableLongStateOf(0L) }
    var voiceAmplitudes by remember { mutableStateOf(listOf<Int>()) }
    var voiceDragOffsetX by remember { mutableFloatStateOf(0f) }
    var voiceDragOffsetY by remember { mutableFloatStateOf(0f) }
    var voiceAudioFile by remember { mutableStateOf<File?>(null) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewStarted by remember { mutableStateOf(false) }
    var previewProgress by remember { mutableFloatStateOf(0f) }
    var previewPosMs by remember { mutableIntStateOf(0) }
    // Resume: the part recorded after "Resume" is kept in its own file until it is joined on to the note
    var voiceSegmentFile by remember { mutableStateOf<File?>(null) }
    var voiceResumeBaseMs by remember { mutableLongStateOf(0L) }
    var voiceResumeAmps by remember { mutableStateOf(listOf<Int>()) }
    LaunchedEffect(voiceRecordState) {
        if (voiceRecordState != VoiceRecordState.PAUSED) {
            previewStarted = false
            previewProgress = 0f
            previewPosMs = 0
        }
    }

    var recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Allow the microphone in Settings to send voice messages", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(inputText) {
        if (!inputText.contains("@")) {
            activePickerTabOverride = null
        }
    }

    val threadCommentsMap by chatViewModel.threadCommentsMap.collectAsState()

    // Mention picker trigger detection (list logic lives in MentionPickerLogic.kt)
    val mentionTriggerInfo = remember(inputText, members, acceptedConnections, currentUserId) {
        detectMentionTrigger(inputText, members, acceptedConnections, currentUserId)
    }
    val mentionHasMembers = remember(members, currentUserId) { members.any { !it.isYou && it.uid != currentUserId } }
    val mentionHasGroups = acceptedConnections.isNotEmpty()

    val showMentionPicker = mentionTriggerInfo != null

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val isDark = DiBadgeTheme.colors.isDark

    val isCurrentUserMember = remember(members, currentUserId) {
        members.isEmpty() || members.any { it.uid == currentUserId }
    }

    // Theme Tokens
    val pageBgColor = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF)
    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)
    val tealAccent = if (isDark) Color(0xFF8F9CD8) else Color(0xFF3A4694)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)

    if (showGroupDetails) {
        ChatDetailsScreen(
            entry = currentEntry,
            messages = messages,
            onBack = { showGroupDetails = false },
            onDeleteMessage = { messageId, forEveryone ->
                if (forEveryone) {
                    chatViewModel.deleteForEveryone(context, currentEntry.id.toString(), setOf(messageId))
                } else {
                    chatViewModel.deleteForMe(context, currentEntry.id.toString(), setOf(messageId))
                }
            },
            onOpenMyForms = {
                showGroupDetails = false
                onOpenMyForms()
            },
            onReplyMedia = { mediaUrl: String ->
                showGroupDetails = false
                val targetMsg = messages.find { msg -> msg.attachments.contains(mediaUrl) }
                if (targetMsg != null) {
                    replyingTo = targetMsg
                    replyingToAttachmentUriState = mediaUrl
                }
            },
            onShowInChat = { mediaUrl: String ->
                showGroupDetails = false
                val targetMsg = messages.find { msg -> msg.attachments.contains(mediaUrl) || msg.text.contains(mediaUrl) }
                if (targetMsg != null) {
                    highlightedMessageId = targetMsg.id
                }
            }
        )
        return
    }

    if (showNewPollScreen) {
        NewPollScreen(
            isDark = isDark,
            onCreatePoll = { question, options, allowMultiple, showVoters, endsAtMillis ->
                chatViewModel.sendPollMessage(
                    context = context,
                    groupId = currentEntry.id.toString(),
                    question = question,
                    options = options,
                    allowMultiple = allowMultiple,
                    showVoters = showVoters,
                    endsAtMillis = endsAtMillis
                )
            },
            onClose = { showNewPollScreen = false }
        )
        return
    }

    val groupedMessages = remember(messages) {
        messages.groupBy { it.timestamp.toLocalDate() }.toSortedMap()
    }

    val messageToLazyIndex = remember(groupedMessages) {
        val map = mutableMapOf<String, Int>()
        var currentIndex = 0
        groupedMessages.forEach { (_, dateMessages) ->
            currentIndex++
            dateMessages.forEach { message ->
                map[message.id] = currentIndex
                currentIndex++
            }
        }
        map
    }

    // ---- Search in this chat ----
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val searchMatchIds = remember(messages, searchQuery, isSearchOpen, groupedMessages) {
        val q = searchQuery.trim()
        if (!isSearchOpen || q.isEmpty()) {
            emptyList<String>()
        } else {
            groupedMessages.values.flatten().filter { m ->
                !m.isDeleted && !m.isHiddenForMe && messageToLazyIndex.containsKey(m.id) &&
                        (listOfNotNull(m.text, m.libraryFileName, m.pollQuestion, m.locationPlaceName) +
                                m.pollOptions.map { it.text } +
                                m.attachments.map { android.net.Uri.decode(it.substringAfterLast('/')) })
                            .any { it.contains(q, ignoreCase = true) }
            }.map { it.id }
        }
    }
    // Int.MAX_VALUE = "not moved yet", which means the newest match
    var searchIndexRaw by remember(searchQuery) { mutableStateOf(Int.MAX_VALUE) }
    val searchIndex = if (searchMatchIds.isEmpty()) -1 else {
        if (searchIndexRaw == Int.MAX_VALUE) searchMatchIds.lastIndex else searchIndexRaw.coerceIn(0, searchMatchIds.lastIndex)
    }
    val currentSearchId: String? = searchMatchIds.getOrNull(searchIndex)
    fun closeSearch() {
        isSearchOpen = false
        searchQuery = ""
    }
    fun searchOlder() {
        if (searchMatchIds.isNotEmpty()) searchIndexRaw = if (searchIndex <= 0) searchMatchIds.lastIndex else searchIndex - 1
    }
    fun searchNewer() {
        if (searchMatchIds.isNotEmpty()) searchIndexRaw = if (searchIndex >= searchMatchIds.lastIndex) 0 else searchIndex + 1
    }

    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0L) }
    val recorder = remember { AudioRecorder(context) }
    val player = remember { AudioPlayer(context) }
    LaunchedEffect(isPreviewPlaying) {
        while (isPreviewPlaying) {
            val total = player.getDuration()
            if (total > 0) {
                val cur = player.getCurrentPosition()
                previewPosMs = cur
                previewProgress = (cur.toFloat() / total).coerceIn(0f, 1f)
            }
            delay(80)
        }
    }
    var audioFile by remember { mutableStateOf<File?>(null) }

    // Joins the part recorded after "Resume" on to the voice note recorded so far (no re-encoding)
    fun finishVoiceSegment() {
        val seg = voiceSegmentFile ?: return
        voiceSegmentFile = null
        val base = voiceAudioFile
        if (base == null) {
            seg.delete()
            return
        }
        val merged = File(base.parentFile, "VOICE_${System.currentTimeMillis()}.m4a")
        if (AudioMerger.merge(listOf(base, seg), merged)) {
            base.delete()
            seg.delete()
            voiceAudioFile = merged
        } else {
            // Keep what was recorded before the resume
            seg.delete()
            merged.delete()
            voiceRecordDurationMs = voiceResumeBaseMs
            voiceAmplitudes = voiceResumeAmps
            Toast.makeText(context, "Could not add the new part", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.stop()
            player.stop()
        }
    }

    LaunchedEffect(voiceRecordState) {
        if (voiceRecordState == VoiceRecordState.HOLD || voiceRecordState == VoiceRecordState.LOCKED) {
            val startMs = System.currentTimeMillis()
            val baseMs = voiceResumeBaseMs                    // time already recorded before a resume
            val amps = voiceResumeAmps.toMutableList()
            while (voiceRecordState == VoiceRecordState.HOLD || voiceRecordState == VoiceRecordState.LOCKED) {
                delay(100)
                voiceRecordDurationMs = baseMs + (System.currentTimeMillis() - startMs)
                val amp = recorder.getMaxAmplitude()
                amps.add(amp)
                voiceAmplitudes = amps.takeLast(25)

                if (voiceRecordDurationMs >= 15 * 60 * 1000L) {
                    recorder.stop()
                    finishVoiceSegment()
                    voiceRecordState = VoiceRecordState.PAUSED
                    break
                }
            }
        }
    }

    BackHandler {
        if (isSearchOpen) {
            closeSearch()
        } else if (longPressedMessage != null) {
            longPressedMessage = null
        } else if (selectedAttachmentUris.isNotEmpty()) {
            selectedAttachmentUris = emptySet()
        } else if (attachedFiles.isNotEmpty()) {
            // Back closes the attachment preview and shows the chat again
            attachedFiles = emptyList()
            pendingDurations = emptyMap()
        } else if (selectedMessages.isNotEmpty()) {
            selectedMessages = emptySet()
            selectedAttachmentUri = null
        } else if (editingMessage != null) {
            editingMessage = null
            inputText = ""
        } else if (replyingTo != null) {
            replyingTo = null
        } else if (showAttachmentOptions) {
            showAttachmentOptions = false
        } else if (showEmojiPicker) {
            showEmojiPicker = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(highlightedMessageId) {
        if (highlightedMessageId != null) {
            delay(2000)
            highlightedMessageId = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) showCamera = true
        }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            attachedFiles = processNewUris(uris, attachedFiles, context)
            if (uris.isNotEmpty()) attachSource = AttachSource.PICKER
        }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            attachedFiles = processNewUris(uris, attachedFiles, context)
            if (uris.isNotEmpty()) attachSource = AttachSource.PICKER
        }
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            attachedFiles = processNewUris(uris, attachedFiles, context)
            if (uris.isNotEmpty()) attachSource = AttachSource.PICKER
        }
    )

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            attachedFiles = processNewUris(uris, attachedFiles, context)
            if (uris.isNotEmpty()) attachSource = AttachSource.PICKER
        }
    )

    val docScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val gmsResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pdfUri = gmsResult?.pdf?.uri
                if (pdfUri != null) {
                    val defaultName = ScanNameValidator.generateDefaultScanName()
                    val cacheFile = File(context.cacheDir, defaultName)
                    try {
                        context.contentResolver.openInputStream(pdfUri)?.use { input ->
                            FileOutputStream(cacheFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val safeUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            cacheFile
                        )
                        attachedFiles = processNewUris(listOf(safeUri), attachedFiles, context)
                        attachSource = AttachSource.SCAN
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    )

    if (showCamera) {
        CameraCaptureScreen(
            isStampCamera = isCameraForStampMode,
            onImageCaptured = { uri ->
                attachedFiles = processNewUris(listOf(uri), attachedFiles, context)
                attachSource = if (isCameraForStampMode) AttachSource.STAMP else AttachSource.CAMERA
                showCamera = false
            },
            onClose = { showCamera = false }
        )
        return
    }

    if (galleryMedia != null) {
        MediaGalleryScreen(
            mediaUris = galleryMedia!!,
            initialIndex = galleryInitialIndex,
            isDark = isDark,
            onClose = {
                galleryMedia = null
                galleryMessage = null
            },
            onRemovePhoto = { indexToRemove ->
                val uriToRemove = galleryMedia!!.getOrNull(indexToRemove)
                if (uriToRemove != null) {
                    attachedFiles = attachedFiles.filter { it != uriToRemove }
                    pendingDurations = pendingDurations.filter { it.key != uriToRemove.toString() }
                    val updatedList = galleryMedia!!.filterIndexed { idx, _ -> idx != indexToRemove }
                    if (updatedList.isEmpty()) {
                        galleryMedia = null
                        galleryMessage = null
                    } else {
                        galleryMedia = updatedList
                    }
                }
            },
            onSaveEditedPhoto = { indexToReplace, newUri ->
                val oldUri = galleryMedia!!.getOrNull(indexToReplace)
                if (oldUri != null) {
                    attachedFiles = attachedFiles.map { if (it == oldUri) newUri else it }
                    galleryMedia = galleryMedia!!.mapIndexed { idx, uri -> if (idx == indexToReplace) newUri else uri }
                }
            }
        )
        return
    }

    if (audioPlayerMedia != null) {
        AudioPlayerScreen(
            audioUris = audioPlayerMedia!!,
            initialIndex = audioPlayerInitialIndex,
            isDark = isDark,
            player = player,
            onClose = {
                audioPlayerMedia = null
            },
            onRemoveAudio = { indexToRemove ->
                val uriToRemove = audioPlayerMedia!!.getOrNull(indexToRemove)
                if (uriToRemove != null) {
                    attachedFiles = attachedFiles.filter { it != uriToRemove }
                    pendingDurations = pendingDurations.filter { it.key != uriToRemove.toString() }
                    val updatedList = audioPlayerMedia!!.filterIndexed { idx, _ -> idx != indexToRemove }
                    if (updatedList.isEmpty()) {
                        audioPlayerMedia = null
                    } else {
                        audioPlayerMedia = updatedList
                    }
                }
            }
        )
        return
    }

    val commonEmojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
        "🙂", "🙃", "😉", "😌", "😋", "😛", "😝", "😜", "🤪", "🤨",
        "🧐", "🤓", "😎", "🤩", "👍", "👎", "👌", "🤝", "🙏", "👏",
        "🙌", "👐", "🤲", "💼", "📁", "📂", "📅", "📆", "📈", "📊",
        "📋", "📌", "📍", "📎", "📝", "💻", "🖥️", "📧", "🚀", "💡",
        "🎯", "✅", "❌", "⌛", "⏳", "⏰", "🛠️", "⚙️", "🔗"
    )

    // If the messages are already here, the list is created at the newest message, so it never
    // shows the top first and nothing has to scroll.
    val startIndex = if (messages.isEmpty()) 0 else {
        (if (groupCreatedDateText != null) 1 else 0) + groupedMessages.size + messages.size - 1
    }
    // Created again the moment the messages have loaded, so it always starts at the newest message
    val listState = remember(messagesLoaded) { LazyListState(firstVisibleItemIndex = startIndex) }

    // True while the newest message is out of view: shows the "scroll to bottom" arrow
    val showScrollToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && listState.canScrollForward && lastVisible < info.totalItemsCount - 1
        }
    }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    LaunchedEffect(currentSearchId) {
        val id = currentSearchId ?: return@LaunchedEffect
        val idx = messageToLazyIndex[id] ?: return@LaunchedEffect
        val off = if (messages.isNotEmpty() && groupCreatedDateText != null) 1 else 0
        listState.animateScrollToItem(idx + off)
    }

    // Always end at the real last item. The list also holds date headers, so
    // messages.size - 1 is not the last item and the newest bubble stayed hidden.
    // The list is only drawn once the messages have loaded, and it starts at the newest message
    val initialScrollDone = messagesLoaded

    LaunchedEffect(messages.size, initialScrollDone) {
        if (messages.isNotEmpty() && initialScrollDone) {
            delay(60)
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) listState.animateScrollToItem(last)
        }
    }

    // Delete Message Sheet
    if (showDeleteDialog && selectedMessages.isNotEmpty()) {
        val selectedMsgObjects = remember(selectedMessages, messages) {
            messages.filter { selectedMessages.contains(it.id) }
        }
        val allOwn = remember(selectedMsgObjects) {
            selectedMsgObjects.isNotEmpty() && selectedMsgObjects.all { it.isSentByMe }
        }
        val canDeleteForEveryone = remember(allOwn, isAdmin, selectedMsgObjects) {
            allOwn || (isAdmin && selectedMsgObjects.all { !it.isSentByMe })
        }

        DeleteMessageSheet(
            selectedCount = selectedMessages.size,
            canDeleteForEveryone = canDeleteForEveryone,
            onDeleteForEveryone = {
                chatViewModel.deleteForEveryone(
                    context = context,
                    groupId = currentEntry.id.toString(),
                    selectedMessageIds = selectedMessages,
                    deletedByRole = if (allOwn) "sender" else "admin"
                )
                selectedMessages = emptySet()
                showDeleteDialog = false
                showMessageOptionsSheet = false
            },
            onDeleteForMe = {
                chatViewModel.deleteForMe(
                    context = context,
                    groupId = currentEntry.id.toString(),
                    selectedMessageIds = selectedMessages
                )
                selectedMessages = emptySet()
                showDeleteDialog = false
                showMessageOptionsSheet = false
            },
            onDismiss = {
                showDeleteDialog = false
            }
        )
    }

    // Long Press Message Options Bottom Sheet
    if (showMessageOptionsSheet && selectedMessages.isNotEmpty()) {
        val selectedMsg = messages.find { it.id == selectedMessages.first() }
        val isDeletedMsg = selectedMsg?.isDeleted == true

        ModalBottomSheet(
            onDismissRequest = {
                showMessageOptionsSheet = false
                selectedMessages = emptySet()
            },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = cardColor,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.45f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                if (!isDeletedMsg) {
                    // Option: Reply
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                replyingTo = selectedMsg
                                replyingToAttachmentUriState = selectedAttachmentUri
                                showMessageOptionsSheet = false
                                selectedMessages = emptySet()
                            }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = primaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "Reply", fontSize = 15.sp, color = primaryTextColor)
                    }

                    // Option: Copy
                    if (selectedMsg != null && selectedMsg.text.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("chat_message", selectedMsg.text)
                                    clipboard.setPrimaryClip(clip)
                                    showMessageOptionsSheet = false
                                    selectedMessages = emptySet()
                                }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = primaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = "Copy", fontSize = 15.sp, color = primaryTextColor)
                        }
                    }
                    // Option: Edit
                    val canEdit = selectedMessages.size == 1 && selectedMsg != null && selectedMsg.isSentByMe && !selectedMsg.isDeleted && !selectedMsg.isForwarded && !selectedMsg.isVoiceNote
                    if (canEdit && selectedMsg != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable {
                                    editingMessage = selectedMsg
                                    inputText = selectedMsg.text
                                    showMessageOptionsSheet = false
                                    selectedMessages = emptySet()
                                }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit",
                                tint = primaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = if (selectedMsg.attachments.isNotEmpty()) "Edit caption" else "Edit message",
                                fontSize = 15.sp,
                                color = primaryTextColor
                            )
                        }
                    }
                }

                // Option: Delete
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            showMessageOptionsSheet = false
                            showDeleteDialog = true
                        }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = errorColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Delete", fontSize = 15.sp, color = errorColor)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            // Header Top Bar: 48dp height below status bar, card color, covers status bar, no divider, no elevation
            ChatTopBar(
                currentEntry = currentEntry,
                messages = messages,
                selectedMessages = selectedMessages,
                isOneToOne = isOneToOne,
                otherUserNickname = otherUserNickname,
                otherUserPhotoUrl = otherUserPhotoUrl,
                otherUserName = otherUserName,
                isSearchOpen = isSearchOpen,
                searchQuery = searchQuery,
                searchMatchCount = searchMatchIds.size,
                searchIndex = searchIndex,
                searchFocusRequester = searchFocusRequester,
                cardColor = cardColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                errorColor = errorColor,
                onBack = onBack,
                onClearSelection = {
                    selectedMessages = emptySet()
                    selectedAttachmentUri = null
                },
                onReply = { msg ->
                    replyingTo = msg
                    replyingToAttachmentUriState = msg.attachments.firstOrNull()
                    selectedMessages = emptySet()
                },
                onForward = { showForwardSheet = true },
                onEdit = { msg ->
                    editingMessage = msg
                    inputText = msg.text
                    selectedMessages = emptySet()
                },
                onInfo = { selectedSentToMessage = it },
                onDelete = { showDeleteDialog = true },
                onOpenDetails = { showGroupDetails = true },
                onOpenSearch = { isSearchOpen = true },
                onCloseSearch = { closeSearch() },
                onSearchQueryChange = { searchQuery = it },
                onSearchOlder = { searchOlder() },
                onSearchNewer = { searchNewer() }
            )
        },
        bottomBar = {
            if (isBlockedByMe) {
                val fn = otherUserFirstName.ifBlank { (if (otherUserName.isNotBlank()) otherUserName else currentEntry.title).split(" ").firstOrNull().orEmpty() }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(cardColor),
                    color = cardColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "You blocked $fn",
                            fontSize = 14.sp,
                            color = secondaryTextColor
                        )

                        Text(
                            text = "Unblock",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tealAccent,
                            modifier = Modifier
                                .clickable {
                                    val db = FirebaseFirestore.getInstance()
                                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                    if (uid.isNotBlank() && otherUid.isNotBlank()) {
                                        db.collection("blockedUsers").document(uid)
                                            .collection("blocked").document(otherUid)
                                            .delete()
                                        Toast.makeText(context, "$fn unblocked", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                }
            } else {
                // Input Bar: 84dp high including navigation inset, page color background, no top divider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (attachedFiles.isNotEmpty()) Color.Black.copy(alpha = 0.45f) else pageBgColor)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 12.dp, end = 12.dp, top = if (attachedFiles.isNotEmpty()) 0.dp else 8.dp, bottom = 8.dp)
                ) {
                    // Mention Picker Card above field (aligned with message input field bounds: 44dp start, 52dp end)
                    if (showMentionPicker && mentionTriggerInfo != null) {
                        val detectedMode = mentionTriggerInfo.first
                        val bothKinds = mentionHasMembers && mentionHasGroups
                        val activeMode = when {
                            bothKinds -> activePickerTabOverride ?: detectedMode
                            mentionHasMembers -> MentionPickerMode.MEMBERS
                            else -> MentionPickerMode.LINKED_GROUPS
                        }

                        val lastAt = mentionTriggerInfo.third
                        val queryText = if (lastAt != -1 && lastAt < inputText.length) {
                            inputText.substring(lastAt + 1).removePrefix(" ").trim().lowercase()
                        } else ""

                        val otherMembersList = remember(members, currentUserId) {
                            members.filter { !it.isYou && it.uid != currentUserId }
                        }

                        val filteredMembers = if (queryText.isEmpty()) otherMembersList else otherMembersList.filter { m ->
                            m.displayName.lowercase().split(" ").any { word -> word.startsWith(queryText) } ||
                                    m.displayName.lowercase().contains(queryText)
                        }

                        val filteredGroups = if (queryText.isEmpty()) acceptedConnections else acceptedConnections.filter { conn ->
                            conn.otherEntityName.lowercase().contains(queryText)
                        }

                        MentionPickerCard(
                            mode = activeMode,
                            showTabs = bothKinds,
                            members = filteredMembers,
                            linkedGroups = filteredGroups,
                            isDark = isDark,
                            cardColor = cardColor,
                            outlineColor = outlineColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            dimTextColor = dimTextColor,
                            onTabSelected = { newMode ->
                                activePickerTabOverride = newMode
                            },
                            onSelectMember = { member ->
                                val prefix = inputText.substring(0, lastAt)
                                val name = member?.displayName ?: "All"
                                inputText = "$prefix@$name "
                                activePickerTabOverride = null
                            },
                            onSelectLinkedGroup = { group ->
                                val prefix = inputText.substring(0, lastAt)
                                val name = group?.otherEntityName ?: "All"
                                inputText = "$prefix@$name "
                                activePickerTabOverride = null
                            },
                            modifier = Modifier.padding(start = 44.dp, end = 52.dp, bottom = 8.dp)
                        )
                    }

                    // Attachment Preview Strip above typing box
                    if (attachedFiles.isNotEmpty()) {
                        // The preview sits on the dimmed chat (no light box behind it), edge to edge
                        Box(
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val extra = 24.dp.roundToPx()
                                    val p = measurable.measure(
                                        constraints.copy(
                                            minWidth = constraints.maxWidth + extra,
                                            maxWidth = constraints.maxWidth + extra
                                        )
                                    )
                                    layout(constraints.maxWidth, p.height) { p.place(-extra / 2, 0) }
                                }
                        ) {
                            AttachmentPreviewStrip(
                                attachedUris = attachedFiles,
                                durationsMap = pendingDurations,
                                selectedUris = selectedAttachmentUris,
                                isDark = isDark,
                                pageBgColor = Color.Transparent,
                                cardColor = cardColor,
                                outlineColor = outlineColor,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                errorColor = errorColor,
                                onToggleSelect = { uri ->
                                    selectedAttachmentUris = if (selectedAttachmentUris.contains(uri)) {
                                        selectedAttachmentUris - uri
                                    } else {
                                        selectedAttachmentUris + uri
                                    }
                                },
                                onExitSelectionMode = {
                                    selectedAttachmentUris = emptySet()
                                },
                                onDeleteSelected = {
                                    attachedFiles = attachedFiles.filter { !selectedAttachmentUris.contains(it) }
                                    pendingDurations = pendingDurations.filter { !selectedAttachmentUris.contains(Uri.parse(it.key)) }
                                    selectedAttachmentUris = emptySet()
                                },
                                onImageClick = { uris, index ->
                                    galleryMedia = uris
                                    galleryInitialIndex = index
                                },
                                onAudioClick = { uris, index ->
                                    audioPlayerMedia = uris
                                    audioPlayerInitialIndex = index
                                },
                                onFileClick = { uri ->
                                    // Open in the in-app document screen (like images open in the in-app viewer)
                                    val docName = FilePickerUtils.getFileName(context, uri)
                                    if (docName.substringAfterLast('.', "").equals("pdf", ignoreCase = true)) {
                                        selectedPdfUri = uri
                                    } else {
                                        // Types the app can't show itself: choose an app from outside
                                        if (!FilePickerUtils.openFileInExternalApp(context, uri, docName)) {
                                            Toast.makeText(context, "No longer available", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Composer Reply Preview Bar directly above typing box (diBadge style)
                    if (replyingTo != null) {
                        val replyMsg = replyingTo!!
                        val isReplySenderYou = replyMsg.isSentByMe || replyMsg.senderName == "Me"
                        val replySenderName = if (isReplySenderYou) "yourself" else replyMsg.senderName

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = cardColor,
                            border = BorderStroke(1.dp, outlineColor),
                            shadowElevation = if (!isDark) 1.5.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = "Replying",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Replying to $replySenderName",
                                        fontSize = 12.sp,
                                        color = secondaryTextColor
                                    )
                                    val textPreview = if (replyMsg.text.isNotBlank()) {
                                        replyMsg.text.take(200)
                                    } else if (replyMsg.isVoiceNote) {
                                        "Voice message"
                                    } else if (replyMsg.msgType == "location") {
                                        "Location"
                                    } else if (replyMsg.msgType == "poll") {
                                        "Poll"
                                    } else {
                                        "Attachment"
                                    }
                                    Text(
                                        text = textPreview,
                                        fontSize = 14.sp,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!replyingToAttachmentUriState.isNullOrBlank() || replyMsg.attachments.isNotEmpty()) {
                                    val thumbUri = replyingToAttachmentUriState ?: replyMsg.attachments.firstOrNull()
                                    if (thumbUri != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            AsyncImage(
                                                model = thumbUri,
                                                contentDescription = "Attachment preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        replyingTo = null
                                        replyingToAttachmentUriState = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel reply",
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Composer Edit Preview Card
                    if (editingMessage != null) {
                        EditMessagePreviewCard(
                            editingMessage = editingMessage!!,
                            onCancelEdit = {
                                editingMessage = null
                                inputText = ""
                            },
                            cardColor = cardColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            outlineColor = outlineColor,
                            isDark = isDark
                        )
                    }

                    if (showEmojiPicker) {
                        FullEmojiPicker(
                            containerColor = cardColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = tealAccent,
                            onEmojiSelected = { emoji -> inputText += emoji }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (voiceRecordState != VoiceRecordState.NONE) {
                        // VOICE RECORDING OVERLAY BAR (HOLD, LOCKED, PAUSED)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (voiceRecordState == VoiceRecordState.HOLD) {
                                // Lock Pill positioned above the enlarged mic button
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-8).dp, y = (-72).dp)
                                        .size(width = 44.dp, height = 84.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    color = cardColor,
                                    shadowElevation = if (!isDark) 2.dp else 0.dp
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Lock",
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Lock recording",
                                            tint = primaryTextColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Main Recording Pill Bar
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = cardColor,
                                    shadowElevation = if (!isDark) 1.5.dp else 0.dp
                                ) {
                                    when (voiceRecordState) {
                                        VoiceRecordState.HOLD -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val infiniteTransition = rememberInfiniteTransition()
                                                    val pulseAlpha by infiniteTransition.animateFloat(
                                                        initialValue = 0.3f,
                                                        targetValue = 1.0f,
                                                        animationSpec = infiniteRepeatable(
                                                            animation = tween(600),
                                                            repeatMode = RepeatMode.Reverse
                                                        )
                                                    )
                                                    Surface(
                                                        modifier = Modifier.size(10.dp).alpha(pulseAlpha),
                                                        shape = CircleShape,
                                                        color = errorColor
                                                    ) {}

                                                    Spacer(modifier = Modifier.width(8.dp))

                                                    val durationSec = voiceRecordDurationMs / 1000L
                                                    val timeStr = String.format(Locale.getDefault(), "%d:%02d", durationSec / 60, durationSec % 60)
                                                    Text(
                                                        text = timeStr,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (durationSec >= 14 * 60 + 30) errorColor else primaryTextColor
                                                    )
                                                }

                                                val hintAlpha = ((120f + voiceDragOffsetX) / 120f).coerceIn(0f, 1f)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .offset(x = (voiceDragOffsetX / 2f).dp)
                                                        .alpha(hintAlpha)
                                                ) {
                                                    Text(
                                                        text = "< Slide to cancel",
                                                        fontSize = 14.sp,
                                                        color = secondaryTextColor
                                                    )
                                                }
                                            }
                                        }
                                        VoiceRecordState.LOCKED -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        recorder.stop()
                                                        voiceAudioFile?.delete()
                                                        voiceSegmentFile?.delete()
                                                        voiceSegmentFile = null
                                                        voiceRecordState = VoiceRecordState.NONE
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Discard recording",
                                                        tint = secondaryTextColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val infiniteTransition = rememberInfiniteTransition()
                                                    val pulseAlpha by infiniteTransition.animateFloat(
                                                        initialValue = 0.3f,
                                                        targetValue = 1.0f,
                                                        animationSpec = infiniteRepeatable(
                                                            animation = tween(600),
                                                            repeatMode = RepeatMode.Reverse
                                                        )
                                                    )
                                                    Surface(
                                                        modifier = Modifier.size(10.dp).alpha(pulseAlpha),
                                                        shape = CircleShape,
                                                        color = errorColor
                                                    ) {}
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    val durationSec = voiceRecordDurationMs / 1000L
                                                    Text(
                                                        text = String.format(Locale.getDefault(), "%d:%02d", durationSec / 60, durationSec % 60),
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = primaryTextColor
                                                    )
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .width(110.dp)
                                                        .height(22.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val bars = if (voiceAmplitudes.isNotEmpty()) voiceAmplitudes.takeLast(18) else List(18) { 6 }
                                                    bars.forEach { amp ->
                                                        val heightFrac = (amp / 32767f).coerceIn(0.15f, 1f)
                                                        Surface(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight(heightFrac),
                                                            shape = RoundedCornerShape(1.dp),
                                                            color = secondaryTextColor
                                                        ) {}
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        // Finalize the file now so the preview can play it (a paused file is not playable)
                                                        recorder.stop()
                                                        finishVoiceSegment()
                                                        voiceRecordState = VoiceRecordState.PAUSED
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Pause,
                                                        contentDescription = "Pause recording",
                                                        tint = primaryTextColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                        VoiceRecordState.PAUSED -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        player.stop()
                                                        recorder.stop()
                                                        voiceAudioFile?.delete()
                                                        voiceRecordState = VoiceRecordState.NONE
                                                        isPreviewPlaying = false
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Discard recording",
                                                        tint = secondaryTextColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                Surface(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clickable {
                                                            if (isPreviewPlaying) {
                                                                player.pause()
                                                                isPreviewPlaying = false
                                                            } else if (previewStarted) {
                                                                player.resume()
                                                                isPreviewPlaying = true
                                                            } else {
                                                                voiceAudioFile?.let { file ->
                                                                    player.playFile(Uri.fromFile(file)) {
                                                                        isPreviewPlaying = false
                                                                        previewStarted = false
                                                                        previewProgress = 0f
                                                                        previewPosMs = 0
                                                                    }
                                                                    previewStarted = true
                                                                    isPreviewPlaying = true
                                                                }
                                                            }
                                                        },
                                                    shape = CircleShape,
                                                    color = Color.Transparent
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                            contentDescription = "Preview recording",
                                                            tint = tealAccent,
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                    }
                                                }

                                                androidx.compose.foundation.Canvas(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(horizontal = 8.dp)
                                                        .height(22.dp)
                                                ) {
                                                    val pad = 6.dp.toPx()
                                                    val cy = size.height / 2f
                                                    val knobX = pad + (size.width - 2 * pad) * previewProgress
                                                    drawLine(
                                                        color = secondaryTextColor.copy(alpha = 0.35f),
                                                        start = androidx.compose.ui.geometry.Offset(pad, cy),
                                                        end = androidx.compose.ui.geometry.Offset(size.width - pad, cy),
                                                        strokeWidth = 4.dp.toPx(),
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                    )
                                                    if (previewProgress > 0f) {
                                                        drawLine(
                                                            color = tealAccent,
                                                            start = androidx.compose.ui.geometry.Offset(pad, cy),
                                                            end = androidx.compose.ui.geometry.Offset(knobX, cy),
                                                            strokeWidth = 4.dp.toPx(),
                                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                        )
                                                    }
                                                    drawCircle(tealAccent, 6.dp.toPx(), androidx.compose.ui.geometry.Offset(knobX, cy))
                                                }

                                                val shownSec = if (previewStarted) previewPosMs / 1000L else voiceRecordDurationMs / 1000L
                                                Text(
                                                    text = String.format(Locale.getDefault(), "%d:%02d", shownSec / 60, shownSec % 60),
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = primaryTextColor
                                                )

                                                // Resume: keep recording and add to the end of this voice note
                                                if (voiceRecordDurationMs < 15 * 60 * 1000L) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(CircleShape)
                                                            .background(errorColor.copy(alpha = 0.12f))
                                                            .border(1.5.dp, errorColor, CircleShape)
                                                            .clickable {
                                                                val base = voiceAudioFile
                                                                if (base != null) {
                                                                    player.stop()
                                                                    isPreviewPlaying = false
                                                                    previewStarted = false
                                                                    previewProgress = 0f
                                                                    previewPosMs = 0
                                                                    val seg = File(base.parentFile, "VOICE_part_${System.currentTimeMillis()}.m4a")
                                                                    val started = try {
                                                                        recorder.start(seg)
                                                                    } catch (e: Exception) {
                                                                        false
                                                                    }
                                                                    if (started) {
                                                                        voiceSegmentFile = seg
                                                                        voiceResumeBaseMs = voiceRecordDurationMs
                                                                        voiceResumeAmps = voiceAmplitudes
                                                                        voiceRecordState = VoiceRecordState.LOCKED
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    } else {
                                                                        seg.delete()
                                                                        Toast.makeText(context, "Could not resume recording", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Mic,
                                                            contentDescription = "Resume recording",
                                                            tint = errorColor,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        else -> {}
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (voiceRecordState == VoiceRecordState.HOLD) {
                                    // 64dp Enlarged Mic Button directly under finger
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .offset(x = 4.dp, y = (-8).dp)
                                            .pointerInput(Unit) {
                                                detectDragGestures(
                                                    onDragStart = { },
                                                    onDrag = { _, dragAmount ->
                                                        voiceDragOffsetX += dragAmount.x
                                                        voiceDragOffsetY += dragAmount.y
                                                        if (voiceDragOffsetY < -100f) {
                                                            voiceRecordState = VoiceRecordState.LOCKED
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        if (voiceRecordState == VoiceRecordState.HOLD) {
                                                            if (voiceDragOffsetX < -120f) {
                                                                recorder.stop()
                                                                voiceAudioFile?.delete()
                                                                voiceRecordState = VoiceRecordState.NONE
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            } else if (voiceAudioFile != null && voiceRecordDurationMs >= 1000L) {
                                                                recorder.stop()
                                                                val normWaveform = normalizeWaveformTo64(voiceAmplitudes)
                                                                chatViewModel.sendVoiceNoteMessage(
                                                                    context = context,
                                                                    groupId = currentEntry.id.toString(),
                                                                    audioFile = voiceAudioFile!!,
                                                                    durationMs = voiceRecordDurationMs,
                                                                    waveform = normWaveform
                                                                )
                                                                voiceRecordState = VoiceRecordState.NONE
                                                            } else {
                                                                recorder.stop()
                                                                voiceAudioFile?.delete()
                                                                Toast.makeText(context, "Hold to record, release to send", Toast.LENGTH_SHORT).show()
                                                                voiceRecordState = VoiceRecordState.NONE
                                                            }
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = CircleShape,
                                            color = primaryTextColor,
                                            shadowElevation = if (!isDark) 3.dp else 0.dp
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = "Recording",
                                                    tint = pageBgColor,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Teal Send Button for LOCKED / PAUSED states
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clickable {
                                                player.stop()
                                                recorder.stop()
                                                finishVoiceSegment()
                                                if (voiceAudioFile != null && voiceRecordDurationMs >= 1000L) {
                                                    val normWaveform = normalizeWaveformTo64(voiceAmplitudes)
                                                    chatViewModel.sendVoiceNoteMessage(
                                                        context = context,
                                                        groupId = currentEntry.id.toString(),
                                                        audioFile = voiceAudioFile!!,
                                                        durationMs = voiceRecordDurationMs,
                                                        waveform = normWaveform
                                                    )
                                                }
                                                voiceRecordState = VoiceRecordState.NONE
                                                isPreviewPlaying = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_send_arrow),
                                            contentDescription = "Send voice note",
                                            tint = tealAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!isCurrentUserMember) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = cardColor,
                            shadowElevation = if (!isDark) 1.5.dp else 0.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "You can't send messages to this group",
                                    fontSize = 14.sp,
                                    color = secondaryTextColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val performSend: () -> Unit = {
                                val currentText = inputText.trim()
                                chatViewModel.sendMessage(
                                    context = context,
                                    groupId = currentEntry.id.toString(),
                                    text = currentText,
                                    attachmentUris = attachedFiles,
                                    durationsMap = pendingDurations,
                                    replyingTo = replyingTo,
                                    replyingToAttachmentUri = replyingToAttachmentUriState
                                )

                                // Cross-post to mentioned linked groups with mention stripped for receiver
                                val mentionedLinkedGroups = mutableSetOf<Pair<String, String>>()
                                if (currentText.contains("@All linked groups") || currentText.contains("@All")) {
                                    acceptedConnections.forEach { conn ->
                                        mentionedLinkedGroups.add(conn.otherEntityId to conn.otherEntityName)
                                    }
                                } else {
                                    acceptedConnections.forEach { conn ->
                                        if (currentText.contains("@${conn.otherEntityName}")) {
                                            mentionedLinkedGroups.add(conn.otherEntityId to conn.otherEntityName)
                                        }
                                    }
                                }

                                for ((targetGroupId, targetGroupName) in mentionedLinkedGroups) {
                                    if (targetGroupId != currentEntry.id.toString() && targetGroupId.isNotBlank()) {
                                        val crossPostText = stripGroupMention(currentText, targetGroupName)
                                        if (crossPostText.isNotBlank() || attachedFiles.isNotEmpty()) {
                                            chatViewModel.sendMessage(
                                                context = context,
                                                groupId = targetGroupId,
                                                text = crossPostText,
                                                attachmentUris = attachedFiles,
                                                durationsMap = pendingDurations,
                                                replyingTo = null,
                                                replyingToAttachmentUri = null
                                            )
                                        }
                                    }
                                }

                                // Process member mentions for de-duplicated notification in sender's group
                                val mentionedMemberUids = mutableSetOf<String>()
                                val otherMembers = members.filter { !it.isYou && it.uid != currentUserId }

                                if (currentText.contains("@All")) {
                                    otherMembers.forEach { mentionedMemberUids.add(it.uid) }
                                }
                                otherMembers.forEach { member ->
                                    if (currentText.contains("@${member.displayName}", ignoreCase = true)) {
                                        mentionedMemberUids.add(member.uid)
                                    }
                                }

                                val textBody = stripGroupMention(currentText)
                                val notificationBody = if (textBody.isNotBlank()) {
                                    textBody
                                } else if (attachedFiles.isNotEmpty()) {
                                    val category = getAttachmentCategory(context, attachedFiles.first())
                                    val count = attachedFiles.size
                                    when (category) {
                                        AttachmentCategory.IMAGE -> if (count == 1) "1 photo" else "$count photos"
                                        AttachmentCategory.VIDEO -> if (count == 1) "1 video" else "$count videos"
                                        AttachmentCategory.AUDIO -> if (count == 1) "1 audio file" else "$count audio files"
                                        AttachmentCategory.DOCUMENT -> if (count == 1) "1 document" else "$count documents"
                                    }
                                } else "Sent a message"

                                val currentSenderName = FirebaseAuth.getInstance().currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Me"

                                for (memberUid in mentionedMemberUids) {
                                    if (memberUid.isNotBlank()) {
                                        chatViewModel.sendMemberMentionNotification(
                                            context = context,
                                            targetUid = memberUid,
                                            senderName = currentSenderName,
                                            groupName = currentEntry.title,
                                            body = notificationBody
                                        )
                                    }
                                }

                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                prefs.edit().remove(draftKey).apply()

                                inputText = ""
                                attachedFiles = emptyList()
                                showEmojiPicker = false
                                replyingTo = null
                                replyingToAttachmentUriState = null
                                pendingDurations = emptyMap()
                                selectedAttachmentUris = emptySet()
                            }
                            if (attachedFiles.isNotEmpty()) {
                                // The button follows how the waiting files were added:
                                //   camera or stamp camera -> camera again, scan -> scanner again,
                                //   files picked from the phone -> "+" and the file picker.
                                val addByScan = attachSource == AttachSource.SCAN
                                val addByCamera = attachSource == AttachSource.CAMERA || attachSource == AttachSource.STAMP
                                // 48dp raised circle button (replaces the paperclip while attachments are ready)
                                Surface(
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .size(48.dp)
                                        .clickable {
                                            if (selectedAttachmentUris.isNotEmpty()) {
                                                // Selection mode: this button deletes the selected attachments
                                                attachedFiles = attachedFiles.filter { !selectedAttachmentUris.contains(it) }
                                                pendingDurations = pendingDurations.filter { !selectedAttachmentUris.contains(Uri.parse(it.key)) }
                                                selectedAttachmentUris = emptySet()
                                                return@clickable
                                            }
                                            if (attachedFiles.size >= 10) {
                                                Toast.makeText(context, "You can attach up to 10 files", Toast.LENGTH_SHORT).show()
                                            } else if (addByScan) {
                                                launchDocumentScanner(context, docScannerLauncher)
                                            } else if (addByCamera) {
                                                isCameraForStampMode = attachSource == AttachSource.STAMP
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                    showCamera = true
                                                } else {
                                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            } else {
                                                val activeCategory = getAttachmentCategory(context, attachedFiles.first())
                                                when (activeCategory) {
                                                    AttachmentCategory.IMAGE -> imagePickerLauncher.launch("image/*")
                                                    AttachmentCategory.VIDEO -> videoPickerLauncher.launch("video/*")
                                                    AttachmentCategory.AUDIO -> audioPickerLauncher.launch("audio/*")
                                                    AttachmentCategory.DOCUMENT -> documentPickerLauncher.launch(arrayOf("*/*"))
                                                }
                                            }
                                        },
                                    shape = CircleShape,
                                    color = if (selectedAttachmentUris.isNotEmpty()) Color.White else cardColor,
                                    shadowElevation = if (!isDark) 1.5.dp else 0.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (selectedAttachmentUris.isNotEmpty()) Icons.Default.Delete
                                            else if (addByScan) Icons.Outlined.DocumentScanner
                                            else if (addByCamera) Icons.Default.CameraAlt
                                            else Icons.Default.Add,
                                            contentDescription = if (selectedAttachmentUris.isNotEmpty()) "Delete selected attachments"
                                            else if (addByScan) "Scan another page"
                                            else if (addByCamera) "Take another photo or video"
                                            else "Add more attachments",
                                            tint = if (selectedAttachmentUris.isNotEmpty()) errorColor else primaryTextColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                // Paper clip (24dp, secondary) in 40dp touch target
                                Box(
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .size(40.dp)
                                        .clickable(enabled = editingMessage == null) { showAttachmentOptions = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach",
                                        tint = if (editingMessage != null) secondaryTextColor.copy(alpha = 0.35f) else secondaryTextColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // Field: 48dp high, fully rounded 24dp, card fill (soft shadow in light, no outline)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp, max = 120.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = cardColor,
                                shadowElevation = if (!isDark) 1.5.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Emoji Icon (24dp)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clickable { showEmojiPicker = !showEmojiPicker },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SentimentSatisfiedAlt,
                                            contentDescription = "Emoji",
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Message Input Field (16sp, dim color placeholder)
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (inputText.isEmpty() && attachedFiles.isEmpty()) {
                                            Text(
                                                text = "Message",
                                                fontSize = 16.sp,
                                                color = dimTextColor
                                            )
                                        }
                                        BasicTextField(
                                            value = inputText,
                                            onValueChange = { newValue ->
                                                if (newValue.length <= 4000) {
                                                    val oldValue = inputText
                                                    if (newValue.length < oldValue.length) {
                                                        val pattern = Regex("""@([A-Za-z0-9_]+(?:\s+[A-Za-z0-9_]+)*)\s?""")
                                                        var handled = false
                                                        pattern.findAll(oldValue).forEach { match ->
                                                            val range = match.range
                                                            if (newValue.length in (range.first + 1)..range.last) {
                                                                inputText = oldValue.removeRange(range.first, range.last + 1)
                                                                handled = true
                                                            }
                                                        }
                                                        if (!handled) {
                                                            inputText = newValue
                                                        }
                                                    } else {
                                                        inputText = newValue
                                                    }
                                                }
                                            },
                                            maxLines = 5,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 16.sp,
                                                color = primaryTextColor
                                            ),
                                            visualTransformation = { annotatedString ->
                                                val acceptedGroupNames = acceptedConnections.map { it.otherEntityName }
                                                val formatted = buildMentionAnnotatedString(
                                                    text = annotatedString.text,
                                                    primaryTextColor = primaryTextColor,
                                                    tealAccent = tealAccent,
                                                    acceptedGroupNames = acceptedGroupNames
                                                )
                                                TransformedText(formatted, OffsetMapping.Identity)
                                            },
                                            cursorBrush = SolidColor(primaryTextColor),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                // Touching the typing box closes the emoji panel, even if the box already has focus
                                                .pointerInput(Unit) {
                                                    awaitEachGesture {
                                                        awaitFirstDown(
                                                            requireUnconsumed = false,
                                                            pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                                                        )
                                                        showEmojiPicker = false
                                                    }
                                                }
                                                .onFocusChanged { if (it.isFocused) showEmojiPicker = false }
                                                .onKeyEvent { keyEvent ->
                                                    val textWithoutMentions = stripGroupMention(inputText)
                                                    val canSendMsg = (textWithoutMentions.isNotEmpty() || attachedFiles.isNotEmpty()) && selectedAttachmentUris.isEmpty()
                                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                                        if (!keyEvent.isShiftPressed && canSendMsg) {
                                                            // trigger send logic via check or send
                                                            true
                                                        } else false
                                                    } else false
                                                }
                                        )
                                    }

                                    // Camera Icon (24dp, secondary) - hidden when typing
                                    val textWithoutGroupMentions = stripGroupMention(inputText)
                                    val isTyping = textWithoutGroupMentions.isNotEmpty() || attachedFiles.isNotEmpty()
                                    if (!isTyping) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable {
                                                    isCameraForStampMode = false
                                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                        showCamera = true
                                                    } else {
                                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = "Camera",
                                                tint = secondaryTextColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    } else if (editingMessage == null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .clickable { performSend() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_send_arrow),
                                                contentDescription = "Send message",
                                                tint = tealAccent,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            val slotHidden = editingMessage == null && (attachedFiles.isNotEmpty() || stripGroupMention(inputText).isNotEmpty())
                            Spacer(modifier = Modifier.width(if (slotHidden) 0.dp else 8.dp))

                            // Microphone OR Check (Save Edit) Slot. Send now lives inside the field. (Save Edit) Slot
                            if (editingMessage != null) {
                                val isMediaCaptionEdit = editingMessage!!.attachments.isNotEmpty()
                                val canSaveEdit = if (isMediaCaptionEdit) {
                                    inputText.trim() != editingMessage!!.text.trim()
                                } else {
                                    inputText.trim().isNotBlank() && inputText.trim() != editingMessage!!.text.trim()
                                }

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clickable(enabled = canSaveEdit) {
                                            if (canSaveEdit) {
                                                chatViewModel.editMessage(
                                                    context = context,
                                                    groupId = currentEntry.id.toString(),
                                                    messageId = editingMessage!!.id,
                                                    newText = inputText
                                                )
                                                editingMessage = null
                                                inputText = ""
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Save edit",
                                        tint = if (canSaveEdit) tealAccent else secondaryTextColor.copy(alpha = 0.40f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                val isSelectionMode = selectedAttachmentUris.isNotEmpty()
                                val textWithoutGroupMentions = stripGroupMention(inputText)
                                val isTyping = (textWithoutGroupMentions.isNotEmpty() || attachedFiles.isNotEmpty()) && !isSelectionMode
                                val isActionEnabled = isSelectionMode || isTyping

                                if (!isSelectionMode && !isTyping) {
                                    Box(
                                        modifier = Modifier.size(44.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .clickable {
                                                    // Tap to start. The recording opens with pause, delete and send buttons.
                                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    } else {
                                                        player.stop()
                                                        val audioFile = File(context.cacheDir, "VOICE_${System.currentTimeMillis()}.m4a")
                                                        val started = try {
                                                            recorder.start(audioFile)
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                            false
                                                        }
                                                        if (started) {
                                                            voiceAudioFile = audioFile
                                                            voiceSegmentFile = null
                                                            voiceResumeBaseMs = 0L
                                                            voiceResumeAmps = emptyList()
                                                            voiceRecordDurationMs = 0L
                                                            voiceAmplitudes = emptyList()
                                                            voiceDragOffsetX = 0f
                                                            voiceDragOffsetY = 0f
                                                            voiceRecordState = VoiceRecordState.LOCKED
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        } else {
                                                            audioFile.delete()
                                                            Toast.makeText(context, "Could not start recording", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Record audio",
                                                tint = primaryTextColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showEmojiPicker = false
                    showAttachmentOptions = false
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (messagesLoaded) LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (messages.isNotEmpty() && groupCreatedDateText != null) {
                        item {
                            if (isOneToOne) {
                                ChatStartedHeader(
                                    createdDateText = groupCreatedDateText,
                                    secondaryTextColor = secondaryTextColor
                                )
                            } else {
                                GroupCreatedHeader(
                                    createdDateText = groupCreatedDateText,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }
                        }
                    }

                    groupedMessages.forEach { (date, dateMessages) ->
                        stickyHeader(key = "date-$date") {
                            DateHeader(date = date, isDark = isDark, secondaryTextColor = secondaryTextColor)
                        }
                        items(
                            count = dateMessages.size,
                            key = { index -> dateMessages[index].id }
                        ) { index ->
                            val rawMsg = dateMessages[index]
                            val isFirstInRun = index == 0 || dateMessages[index - 1].senderName != rawMsg.senderName || dateMessages[index - 1].isSentByMe != rawMsg.isSentByMe
                            // Messages from the same person in the same minute are shown as one group
                            val groupedWithPrev = index > 0 && canGroupMessages(dateMessages[index - 1], rawMsg)
                            val groupedWithNext = index < dateMessages.size - 1 && canGroupMessages(rawMsg, dateMessages[index + 1])

                            val isGrpMention = rawMsg.isGroupMention || rawMsg.text.contains("@All") || acceptedConnections.any { rawMsg.text.contains("@${it.otherEntityName}") }
                            val threadIdKey = rawMsg.threadId ?: rawMsg.id

                            if (isGrpMention) {
                                LaunchedEffect(threadIdKey) {
                                    chatViewModel.listenToComments(threadIdKey)
                                }
                            }

                            val commentsForMsg = threadCommentsMap[threadIdKey] ?: emptyList()
                            val isThreadSeen = seenThreadIds.contains(threadIdKey)
                            val unreadCount = if (isThreadSeen) 0 else commentsForMsg.size
                            val latestComment = commentsForMsg.lastOrNull()

                            val matchedGroupNames = acceptedConnections.mapNotNull { conn ->
                                if (rawMsg.text.contains("@${conn.otherEntityName}")) conn.otherEntityName else null
                            }

                            val msgToRender = if (isGrpMention) {
                                rawMsg.copy(
                                    isGroupMention = true,
                                    threadId = threadIdKey,
                                    commentCount = commentsForMsg.size,
                                    unreadCommentCount = unreadCount,
                                    sendingGroupName = rawMsg.sendingGroupName ?: currentEntry.title,
                                    mentionedGroupNames = if (rawMsg.mentionedGroupNames.isNotEmpty()) rawMsg.mentionedGroupNames else matchedGroupNames,
                                    latestCommentAuthor = latestComment?.senderName,
                                    latestCommentText = latestComment?.text
                                )
                            } else rawMsg

                            if (msgToRender.msgType == "connectionRequest") {
                                ConnectionRequestCard(
                                    message = msgToRender,
                                    cardColor = cardColor,
                                    outlineColor = outlineColor,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    accentColor = tealAccent,
                                    onRespond = { id, st -> chatViewModel.respondToConnection(id, st) }
                                )
                            } else if (msgToRender.msgType == "connectionNotice") {
                                ConnectionNoticeLine(msgToRender, secondaryTextColor)
                            } else if (msgToRender.msgType == "systemLibraryAdded" || msgToRender.msgType == "system_library_added") {
                                SystemLibraryChip(
                                    message = msgToRender,
                                    cardFillColor = cardColor,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onChipClick = {
                                        showGroupDetails = true
                                    }
                                )
                            } else {
                                CompositionLocalProvider(LocalOpenPdf provides { pdfUri -> selectedPdfUri = pdfUri }) {
                                    MessageBubble(
                                        message = msgToRender,
                                        player = player,
                                        isSelected = selectedMessages.contains(msgToRender.id),
                                        isAnySelected = selectedMessages.isNotEmpty(),
                                        isFirstInRun = isFirstInRun,
                                        isGroupedWithPrev = groupedWithPrev,
                                        isGroupedWithNext = groupedWithNext,
                                        isOneToOne = isOneToOne,
                                        onBubbleBounds = { bubbleBoundsMap[msgToRender.id] = it },
                                        onLongClick = { uri ->
                                            if (selectedMessages.isEmpty()) {
                                                longPressedMessage = msgToRender
                                            } else {
                                                selectedMessages = if (selectedMessages.contains(msgToRender.id)) selectedMessages - msgToRender.id else selectedMessages + msgToRender.id
                                            }
                                        },
                                        onClick = {
                                            if (selectedMessages.isNotEmpty()) {
                                                selectedMessages = if (selectedMessages.contains(msgToRender.id)) selectedMessages - msgToRender.id else selectedMessages + msgToRender.id
                                            }
                                        },
                                        onReplyClick = { originalId, _ ->
                                            // Tapping the quoted message jumps to the original and highlights it
                                            val lazyIdx = messageToLazyIndex[originalId]
                                            if (lazyIdx != null) {
                                                val headerOffset = if (messages.isNotEmpty() && groupCreatedDateText != null) 1 else 0
                                                scope.launch {
                                                    listState.animateScrollToItem(lazyIdx + headerOffset)
                                                    highlightedMessageId = originalId
                                                }
                                            }
                                        },
                                        isHighlighted = highlightedMessageId == msgToRender.id || (isSearchOpen && currentSearchId == msgToRender.id),
                                        onMediaClick = { mediaUris, startIndex ->
                                            galleryMedia = mediaUris
                                            galleryInitialIndex = startIndex
                                            galleryMessage = msgToRender
                                        },
                                        onCommentsClick = { clickedMsg ->
                                            val tKey = clickedMsg.threadId ?: clickedMsg.id
                                            seenThreadIds = seenThreadIds + tKey
                                            selectedCommentMessage = clickedMsg
                                        },
                                        onSentToClick = { clickedMsg ->
                                            selectedSentToMessage = clickedMsg
                                        },
                                        onSwipeToReply = { uri ->
                                            replyingTo = msgToRender
                                            replyingToAttachmentUriState = uri
                                        },
                                        onRetryFailedMessage = { clickedMsg ->
                                            selectedFailedMessage = clickedMsg
                                        },
                                        onStopLiveLocation = { liveMessageId ->
                                            chatViewModel.stopLiveLocation(context, currentEntry.id.toString(), liveMessageId)
                                        },
                                        onVoteOption = { messageId, optionId ->
                                            messages.find { it.id == messageId }?.let {
                                                chatViewModel.votePoll(currentEntry.id.toString(), it, optionId)
                                            }
                                        },
                                        onViewVotesClick = { selectedPollForVotes = it },
                                        onEndPoll = { pollToEnd = it }
                                    )
                                }
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToBottom && initialScrollDone,
                    enter = androidx.compose.animation.fadeIn(tween(150)) + androidx.compose.animation.scaleIn(tween(150)),
                    exit = androidx.compose.animation.fadeOut(tween(120)) + androidx.compose.animation.scaleOut(tween(120)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = cardColor,
                        border = BorderStroke(1.dp, outlineColor),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch {
                                    val last = listState.layoutInfo.totalItemsCount - 1
                                    if (last >= 0) listState.animateScrollToItem(last)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to latest message",
                                tint = primaryTextColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                if (messages.isEmpty() && messagesLoaded) {
                    val emptyKey = currentEntry.id.toString()
                    val emptyPhoto = if (isOneToOne) otherUserPhotoUrl.ifBlank { AvatarPhotoCache.photoFor(emptyKey) } else AvatarPhotoCache.photoFor(emptyKey)
                    val emptyTitle = if (isOneToOne && otherUserName.isNotBlank()) otherUserName else currentEntry.title
                    val emptyFirstName = if (isOneToOne) {
                        otherUserFirstName.ifBlank { emptyTitle.split(" ").firstOrNull().orEmpty() }
                    } else ""
                    EmptyChatState(
                        title = emptyTitle,
                        idForColor = emptyKey,
                        photoUrl = emptyPhoto,
                        isOneToOne = isOneToOne,
                        createdDateText = groupCreatedDateText,
                        memberCount = members.size,
                        firstName = emptyFirstName,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        isDark = isDark,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Dim the chat while photos or files are waiting to be sent, so the preview tiles stand out
                if (attachedFiles.isNotEmpty() && !showMentionPicker) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .pointerInput(Unit) {
                                // Taps on the dimmed chat do nothing while attachments are pending
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                    )
                }

                // 50% Black Scrim over chat area when mention picker is open
                if (showMentionPicker) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.50f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val lastAt = mentionTriggerInfo?.third ?: -1
                                if (lastAt != -1) {
                                    inputText = inputText.substring(0, lastAt)
                                }
                            }
                    )
                }
            }

            // Attach Sheet (The Paper Clip): 4 columns, 2 rows, card background, 24dp top corners
            if (showAttachmentOptions) {
                AttachOptionsSheet(
                    attachedFiles = attachedFiles,
                    isGroupChat = currentEntry.title.lowercase() != "direct",
                    isDark = isDark,
                    cardColor = cardColor,
                    outlineColor = outlineColor,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    onDismiss = { showAttachmentOptions = false },
                    onStamp = {
                        isCameraForStampMode = true
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            showCamera = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        showAttachmentOptions = false
                    },
                    onPhoto = {
                        imagePickerLauncher.launch("image/*")
                        showAttachmentOptions = false
                    },
                    onVideo = {
                        videoPickerLauncher.launch("video/*")
                        showAttachmentOptions = false
                    },
                    onAudio = {
                        audioPickerLauncher.launch("audio/*")
                        showAttachmentOptions = false
                    },
                    onDocuments = {
                        documentPickerLauncher.launch(arrayOf("*/*"))
                        showAttachmentOptions = false
                    },
                    onScan = {
                        showAttachmentOptions = false
                        launchDocumentScanner(context, docScannerLauncher)
                    },
                    onLocation = {
                        showAttachmentOptions = false
                        showShareLocationSheet = true
                    },
                    onPoll = {
                        showAttachmentOptions = false
                        showNewPollScreen = true
                    }
                )
            }

            if (selectedCommentMessage != null) {
                val targetMsg = selectedCommentMessage!!
                val tId = targetMsg.threadId ?: targetMsg.id
                val commentsList = threadCommentsMap[tId] ?: emptyList()

                CommentThreadSheet(
                    threadId = tId,
                    comments = commentsList,
                    onPostComment = { commentText ->
                        val currentName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Me"
                        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        chatViewModel.postComment(
                            threadId = tId,
                            text = commentText,
                            senderName = currentName,
                            senderUid = currentUid,
                            groupName = currentEntry.title
                        )
                    },
                    onDismiss = { selectedCommentMessage = null }
                )
            }

            if (selectedSentToMessage != null) {
                val targetMsg = selectedSentToMessage!!
                val targetGroups = if (targetMsg.mentionedGroupDetails.isNotEmpty()) {
                    targetMsg.mentionedGroupDetails
                } else {
                    targetMsg.mentionedGroupNames.map { name ->
                        GroupDeliveryStatus(
                            groupId = name,
                            groupName = name,
                            isDelivered = true,
                            deliveredTime = "Just now"
                        )
                    }
                }

                SentToSheet(
                    targetGroups = targetGroups,
                    onRetryGroup = { groupId ->
                        // Retry posting to specific group if failed
                    },
                    onDismiss = { selectedSentToMessage = null }
                )
            }

            if (showForwardSheet && selectedMessages.isNotEmpty()) {
                val selectedList = remember(selectedMessages, messages) {
                    messages.filter { selectedMessages.contains(it.id) }
                }

                val directChats = remember(homeEntries) {
                    homeEntries.filter { it.id != currentEntry.id }
                }
                val activeGroups = remember(acceptedConnections) {
                    acceptedConnections.map { conn ->
                        HomeEntryEntity(
                            id = conn.otherEntityId.toIntOrNull() ?: conn.otherEntityId.hashCode(),
                            title = conn.otherEntityName,
                            subtitle = "Group",
                            initials = conn.otherEntityName.take(1).uppercase()
                        )
                    }
                }

                ForwardToSheet(
                    recentChats = directChats,
                    groups = activeGroups,
                    isDark = isDark,
                    onSendForward = { targetEntries ->
                        chatViewModel.forwardMessages(
                            context = context,
                            selectedMessages = selectedList,
                            targetEntries = targetEntries
                        )
                        val count = targetEntries.size
                        val toastMsg = if (count == 1) {
                            "Forwarded to ${targetEntries.first().title}"
                        } else {
                            "Forwarded to $count chats"
                        }
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()

                        showForwardSheet = false
                        selectedMessages = emptySet()
                    },
                    onDismiss = {
                        showForwardSheet = false
                    }
                )
            }

            longPressedMessage?.let { targetMsg ->
                MessageActionsOverlay(
                    targetMsg = targetMsg,
                    bubbleBounds = bubbleBoundsMap[targetMsg.id],
                    isDark = isDark,
                    cardColor = cardColor,
                    outlineColor = outlineColor,
                    primaryTextColor = primaryTextColor,
                    errorColor = errorColor,
                    onDismiss = { longPressedMessage = null },
                    onReply = {
                        replyingTo = targetMsg
                        replyingToAttachmentUriState = targetMsg.attachments.firstOrNull()
                        longPressedMessage = null
                    },
                    onForward = {
                        selectedMessages = setOf(targetMsg.id)
                        showForwardSheet = true
                        longPressedMessage = null
                    },
                    onEdit = {
                        editingMessage = targetMsg
                        inputText = targetMsg.text
                        longPressedMessage = null
                    },
                    onInfo = {
                        selectedSentToMessage = targetMsg
                        longPressedMessage = null
                    },
                    onSelect = {
                        selectedMessages = setOf(targetMsg.id)
                        longPressedMessage = null
                    },
                    onDelete = {
                        selectedMessages = setOf(targetMsg.id)
                        showDeleteDialog = true
                        longPressedMessage = null
                    }
                )
            }

            ChatOverlays(
                chatViewModel = chatViewModel,
                currentEntry = currentEntry,
                isDark = isDark,
                currentUserId = currentUserId,
                showShareLocationSheet = showShareLocationSheet,
                onDismissShareLocation = { showShareLocationSheet = false },
                pollToEnd = pollToEnd,
                onDismissPollToEnd = { pollToEnd = null },
                selectedPollForVotes = selectedPollForVotes,
                onDismissVotes = { selectedPollForVotes = null },
                selectedPdfUri = selectedPdfUri,
                onClosePdf = { selectedPdfUri = null },
                selectedDocInfoUri = selectedDocInfoUri,
                isDocInfoPreSend = isDocInfoPreSend,
                onCloseDocInfo = { selectedDocInfoUri = null },
                attachedFiles = attachedFiles,
                onRemoveAttached = { u -> attachedFiles = attachedFiles.filter { it != u } },
                selectedFailedMessage = selectedFailedMessage,
                onDismissFailed = { selectedFailedMessage = null }
            )
        }
    }
}

/** True when two neighbouring messages belong in one group: same person, same minute, normal messages. */
private fun canGroupMessages(a: ChatMessage, b: ChatMessage): Boolean {
    fun plain(m: ChatMessage) = !m.isDeleted && !m.isGroupMention && !m.text.contains("@All") &&
            m.msgType != "systemLibraryAdded" && m.msgType != "system_library_added" &&
            m.msgType != "connectionRequest" && m.msgType != "connectionNotice"
    return plain(a) && plain(b) &&
            a.isSentByMe == b.isSentByMe &&
            a.senderName == b.senderName &&
            a.timestamp.truncatedTo(java.time.temporal.ChronoUnit.MINUTES) == b.timestamp.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
}