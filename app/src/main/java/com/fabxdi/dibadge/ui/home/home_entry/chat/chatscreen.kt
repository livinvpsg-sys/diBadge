package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.ChatDetailsScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.AudioPlayer
import com.fabxdi.dibadge.util.AudioRecorder
import com.fabxdi.dibadge.util.FilePickerUtils
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

data class ChatMessage(
    val id: String,
    val text: String,
    val senderName: String,
    val isSentByMe: Boolean,
    val attachments: List<String> = emptyList(),
    val attachmentDurations: Map<String, Long> = emptyMap(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val replyingTo: ChatMessage? = null,
    val replyingToAttachmentUri: String? = null,
    val isEdited: Boolean = false,
    val isGroupMention: Boolean = false,
    val threadId: String? = null,
    val commentCount: Int = 0,
    val unreadCommentCount: Int = 0,
    val sendingGroupName: String? = null,
    val isCrossPostFailed: Boolean = false,
    val mentionedGroupNames: List<String> = emptyList(),
    val mentionedGroupDetails: List<GroupDeliveryStatus> = emptyList(),
    val latestCommentAuthor: String? = null,
    val latestCommentText: String? = null
)

fun stripGroupMention(text: String, groupName: String? = null): String {
    if (text.isBlank()) return ""
    var result = text
    if (groupName != null && groupName.isNotBlank()) {
        result = result.replace("@$groupName", "", ignoreCase = true)
    }
    result = result.replace("@All linked groups", "", ignoreCase = true)
    result = result.replace("@All", "", ignoreCase = true)
    return result.trim()
}

enum class AttachmentCategory {
    IMAGE, VIDEO, AUDIO, DOCUMENT
}

fun getAttachmentCategory(context: Context, uri: Uri): AttachmentCategory {
    val fileName = FilePickerUtils.getFileName(context, uri).lowercase()
    val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
    val ext = fileName.substringAfterLast('.', "")

    return when {
        mimeType?.startsWith("image/") == true || ext in listOf("jpg", "jpeg", "png") -> AttachmentCategory.IMAGE
        mimeType?.startsWith("video/") == true || ext == "mp4" -> AttachmentCategory.VIDEO
        mimeType?.startsWith("audio/") == true || ext in listOf("m4a", "mp3") -> AttachmentCategory.AUDIO
        else -> AttachmentCategory.DOCUMENT
    }
}

fun processNewUris(newUris: List<Uri>, currentFiles: List<Uri>, context: Context): List<Uri> {
    if (currentFiles.size >= 10) {
        Toast.makeText(context, "You can attach up to 10 files", Toast.LENGTH_SHORT).show()
        return currentFiles
    }

    val currentCat = if (currentFiles.isNotEmpty()) getAttachmentCategory(context, currentFiles.first()) else null

    val availableSlotCount = 10 - currentFiles.size
    val limitedUris = newUris.take(availableSlotCount)
    if (newUris.size > availableSlotCount) {
        Toast.makeText(context, "You can attach up to 10 files", Toast.LENGTH_SHORT).show()
    }

    val validUris = mutableListOf<Uri>()
    var sizeExceeded = false

    for (uri in limitedUris) {
        val cat = getAttachmentCategory(context, uri)
        if (currentCat != null && cat != currentCat) {
            continue
        }
        val sizeBytes = FilePickerUtils.getFileSizeBytes(context, uri)
        if (sizeBytes > 25 * 1024 * 1024) {
            sizeExceeded = true
        } else {
            validUris.add(uri)
        }
    }

    if (sizeExceeded) {
        Toast.makeText(context, "This file is over 25 MB", Toast.LENGTH_SHORT).show()
    }

    return currentFiles + validUris
}

@Composable
fun DateHeader(
    date: LocalDate,
    isDark: Boolean,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val sevenDaysAgo = today.minusDays(7)

    val text = when {
        date == today -> "Today"
        date == yesterday -> "Yesterday"
        date.isAfter(sevenDaysAgo) -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }

    val pillBgColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = pillBgColor,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = if (!isDark) 1.5.dp else 1.dp,
            tonalElevation = 0.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor
            )
        }
    }
}

@Composable
fun GroupCreatedHeader(
    createdDateText: String,
    secondaryTextColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Group created $createdDateText",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = secondaryTextColor,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    entry: HomeEntryEntity,
    onBack: () -> Unit,
    onOpenMyForms: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val reminderViewModel: ReminderViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()
    val detailsViewModel: GroupDetailsViewModel = viewModel()

    val homeEntries by reminderViewModel.allHomeEntries.collectAsState(initial = emptyList())
    val currentEntry = homeEntries.find { it.id == entry.id } ?: entry

    LaunchedEffect(currentEntry.id) {
        chatViewModel.listenToChatMessages(context, currentEntry.id.toString())
        detailsViewModel.loadGroupDetails(
            groupId = currentEntry.id.toString(),
            initialName = currentEntry.title,
            initialSubtitle = currentEntry.subtitle
        )
    }

    val messages by chatViewModel.messages.collectAsState()
    val members by detailsViewModel.members.collectAsState()
    val acceptedConnections by detailsViewModel.acceptedConnections.collectAsState()
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    val groupCreatedDateText = remember(currentEntry) {
        currentEntry.timestamp.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }

    var inputText by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAttachmentOptions by remember { mutableStateOf(false) }
    var attachedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showCamera by remember { mutableStateOf(false) }

    var galleryMedia by remember { mutableStateOf<List<Uri>?>(null) }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }
    var galleryMessage by remember { mutableStateOf<ChatMessage?>(null) }

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
    var seenThreadIds by remember { mutableStateOf(setOf<String>()) }
    var activePickerTabOverride by remember { mutableStateOf<MentionPickerMode?>(null) }

    LaunchedEffect(inputText) {
        if (!inputText.contains("@")) {
            activePickerTabOverride = null
        }
    }

    val threadCommentsMap by chatViewModel.threadCommentsMap.collectAsState()

    // Mention picker trigger detection
    val mentionTriggerInfo = remember(inputText, members, acceptedConnections, currentUserId) {
        val text = inputText
        val lastAt = text.lastIndexOf('@')
        if (lastAt != -1 && (lastAt == 0 || text[lastAt - 1].isWhitespace())) {
            val afterAt = text.substring(lastAt + 1)
            if (afterAt.startsWith(" ")) {
                // LINKED GROUPS mode (@ )
                val query = afterAt.substring(1).trim().lowercase()
                if (!afterAt.substring(1).contains("\n")) {
                    val filteredGroups = if (query.isEmpty()) {
                        acceptedConnections
                    } else {
                        acceptedConnections.filter { it.otherEntityName.lowercase().contains(query) }
                    }
                    Triple(MentionPickerMode.LINKED_GROUPS, filteredGroups, lastAt)
                } else null
            } else {
                // MEMBERS mode (@)
                val query = afterAt.trim().lowercase()
                if (!afterAt.contains("\n") && !afterAt.contains(" ")) {
                    val otherMembers = members.filter { !it.isYou && it.uid != currentUserId }
                    val filteredMembers = if (query.isEmpty()) {
                        otherMembers
                    } else {
                        otherMembers.filter { m ->
                            m.displayName.lowercase().split(" ").any { word -> word.startsWith(query) } ||
                            m.displayName.lowercase().contains(query)
                        }
                    }
                    Triple(MentionPickerMode.MEMBERS, filteredMembers, lastAt)
                } else null
            }
        } else null
    }

    val showMentionPicker = mentionTriggerInfo != null

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val isDark = colors.onBackground.red > 0.5f

    // Theme Tokens
    val pageBgColor = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF)
    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)
    val tealAccent = if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)

    if (showGroupDetails) {
        ChatDetailsScreen(
            entry = currentEntry,
            messages = messages,
            onBack = { showGroupDetails = false },
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
                val targetMsg = messages.find { msg -> msg.attachments.contains(mediaUrl) }
                if (targetMsg != null) {
                    highlightedMessageId = targetMsg.id
                }
            }
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

    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0L) }
    val recorder = remember { AudioRecorder(context) }
    val player = remember { AudioPlayer(context) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recorder.stop()
            player.stop()
        }
    }

    BackHandler {
        if (selectedAttachmentUris.isNotEmpty()) {
            selectedAttachmentUris = emptySet()
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
        onResult = { uris -> attachedFiles = processNewUris(uris, attachedFiles, context) }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris -> attachedFiles = processNewUris(uris, attachedFiles, context) }
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris -> attachedFiles = processNewUris(uris, attachedFiles, context) }
    )

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris -> attachedFiles = processNewUris(uris, attachedFiles, context) }
    )

    if (showCamera) {
        CameraCaptureScreen(
            onImageCaptured = { uri ->
                attachedFiles = processNewUris(listOf(uri), attachedFiles, context)
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
            onClose = {
                galleryMedia = null
                galleryMessage = null
            },
            onReply = {
                replyingTo = galleryMessage
                galleryMedia = null
                galleryMessage = null
            },
            onDelete = {
                galleryMessage?.let {
                    chatViewModel.deleteMessages(context, currentEntry.id.toString(), setOf(it.id))
                }
                galleryMedia = null
                galleryMessage = null
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

    val listState = rememberLazyListState()

    val isScrolling = listState.isScrollInProgress
    var isFloatingDateVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            isFloatingDateVisible = true
        } else {
            delay(1500)
            isFloatingDateVisible = false
        }
    }

    val floatingAlpha by animateFloatAsState(
        targetValue = if (isFloatingDateVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300)
    )

    val topVisibleDate = remember(listState.firstVisibleItemIndex, groupedMessages) {
        if (messages.isNotEmpty()) {
            val firstIdx = listState.firstVisibleItemIndex
            val msg = messages.getOrNull(firstIdx.coerceIn(0, messages.size - 1))
            msg?.timestamp?.toLocalDate() ?: LocalDate.now()
        } else LocalDate.now()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete this message?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = if (deleteModeForMeOnly) "Only you will no longer see it." else "This message will be deleted for everyone.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatViewModel.deleteMessages(context, currentEntry.id.toString(), selectedMessages)
                        selectedMessages = emptySet()
                        showDeleteDialog = false
                        showMessageOptionsSheet = false
                    }
                ) {
                    Text("Delete", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = errorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }
            },
            containerColor = cardColor,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Long Press Message Options Bottom Sheet
    if (showMessageOptionsSheet && selectedMessages.isNotEmpty()) {
        val selectedMsg = messages.find { it.id == selectedMessages.first() }
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

                // Option: Delete for me
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            deleteModeForMeOnly = true
                            showDeleteDialog = true
                        }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete for me",
                        tint = errorColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Delete for me", fontSize = 15.sp, color = errorColor)
                }

                // Option: Delete for everyone (if sent by me)
                if (selectedMsg?.isSentByMe == true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                deleteModeForMeOnly = false
                                showDeleteDialog = true
                            }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete for everyone",
                            tint = errorColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "Delete for everyone", fontSize = 15.sp, color = errorColor)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            // Header Top Bar: 48dp height below status bar, card color, covers status bar, no divider, no elevation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Arrow (24dp icon in 48dp touch area, 12dp from left edge)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 40dp Round Avatar + Group Name / Subtitle
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showGroupDetails = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = cardColor,
                            border = BorderStroke(1.dp, outlineColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = currentEntry.title.take(1).uppercase(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryTextColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = currentEntry.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val rawSub = currentEntry.subtitle.trim()
                            val subLower = rawSub.lowercase()
                            val isMemberCount = subLower.endsWith("member") || subLower.endsWith("members")
                            val isPlaceholder = subLower == "test" || subLower == "add group description"
                            val descriptionText = if (isMemberCount || isPlaceholder) "" else rawSub

                            if (descriptionText.isNotBlank()) {
                                Text(
                                    text = descriptionText,
                                    fontSize = 13.sp,
                                    color = secondaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Three-dot menu (22dp in 48dp touch target)
                    IconButton(
                        onClick = { showGroupDetails = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = primaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Input Bar: 84dp high including navigation inset, page color background, no top divider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBgColor)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Mention Picker Card above field (aligned with message input field bounds: 44dp start, 52dp end)
                if (showMentionPicker && mentionTriggerInfo != null) {
                    val detectedMode = mentionTriggerInfo.first
                    val activeMode = activePickerTabOverride ?: detectedMode

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
                    AttachmentPreviewStrip(
                        attachedUris = attachedFiles,
                        durationsMap = pendingDurations,
                        selectedUris = selectedAttachmentUris,
                        isDark = isDark,
                        pageBgColor = pageBgColor,
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
                            val count = selectedAttachmentUris.size
                            val removedFiles = attachedFiles.filter { selectedAttachmentUris.contains(it) }
                            val removedDurations = pendingDurations.filter { selectedAttachmentUris.contains(Uri.parse(it.key)) }

                            attachedFiles = attachedFiles.filter { !selectedAttachmentUris.contains(it) }
                            pendingDurations = pendingDurations.filter { !selectedAttachmentUris.contains(Uri.parse(it.key)) }
                            selectedAttachmentUris = emptySet()

                            scope.launch {
                                val messageText = if (count == 1) "1 removed" else "$count removed"
                                val result = snackbarHostState.showSnackbar(
                                    message = messageText,
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    attachedFiles = attachedFiles + removedFiles
                                    pendingDurations = pendingDurations + removedDurations
                                }
                            }
                        },
                        onImageClick = { uris, index ->
                            galleryMedia = uris
                            galleryInitialIndex = index
                        },
                        onFileClick = { uri ->
                            FilePickerUtils.openFile(context, uri)
                        }
                    )
                }

                if (showEmojiPicker) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(bottom = 8.dp),
                        color = cardColor,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(40.dp),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(commonEmojis.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clickable { inputText += commonEmojis[index] },
                                    contentAlignment = Alignment.Center
                                ) { Text(text = commonEmojis[index], fontSize = 24.sp) }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (attachedFiles.isNotEmpty()) {
                        // 48dp Raised Circle "+" Button (replaces paperclip while attachments ready)
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable {
                                    if (attachedFiles.size >= 10) {
                                        Toast.makeText(context, "You can attach up to 10 files", Toast.LENGTH_SHORT).show()
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
                            color = cardColor,
                            shadowElevation = if (!isDark) 1.5.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add more attachments",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        // Paper clip (24dp, secondary) in 40dp touch target
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { showAttachmentOptions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attach",
                                tint = secondaryTextColor,
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
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Camera Icon (24dp, secondary) - hidden when typing
                            val textWithoutGroupMentions = stripGroupMention(inputText)
                            val isTyping = (textWithoutGroupMentions.isNotEmpty() || attachedFiles.isNotEmpty()) && selectedAttachmentUris.isEmpty()
                            if (!isTyping) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable {
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
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Microphone OR Send OR Trash/Delete Icon (24dp, in 44dp touch area)
                    val isSelectionMode = selectedAttachmentUris.isNotEmpty()
                    val textWithoutGroupMentions = stripGroupMention(inputText)
                    val isTyping = (textWithoutGroupMentions.isNotEmpty() || attachedFiles.isNotEmpty()) && !isSelectionMode
                    val isActionEnabled = isSelectionMode || isTyping

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(enabled = isActionEnabled) {
                                if (isSelectionMode) {
                                    // Delete selected attachments action
                                    val count = selectedAttachmentUris.size
                                    val removedFiles = attachedFiles.filter { selectedAttachmentUris.contains(it) }
                                    val removedDurations = pendingDurations.filter { selectedAttachmentUris.contains(Uri.parse(it.key)) }

                                    attachedFiles = attachedFiles.filter { !selectedAttachmentUris.contains(it) }
                                    pendingDurations = pendingDurations.filter { !selectedAttachmentUris.contains(Uri.parse(it.key)) }
                                    selectedAttachmentUris = emptySet()

                                    scope.launch {
                                        val messageText = if (count == 1) "1 removed" else "$count removed"
                                        val result = snackbarHostState.showSnackbar(
                                            message = messageText,
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            attachedFiles = attachedFiles + removedFiles
                                            pendingDurations = pendingDurations + removedDurations
                                        }
                                    }
                                } else if (isTyping) {
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

                                    val notificationBody = stripGroupMention(currentText)
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

                                    inputText = ""
                                    attachedFiles = emptyList()
                                    showEmojiPicker = false
                                    replyingTo = null
                                    replyingToAttachmentUriState = null
                                    pendingDurations = emptyMap()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelectionMode) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected attachments",
                                tint = errorColor,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (isTyping) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = tealAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record audio",
                                tint = primaryTextColor,
                                modifier = Modifier.size(24.dp)
                            )
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
            if (messages.isEmpty()) {
                // Empty State: Centered a bit above middle
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp)
                        .offset(y = (-40).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = dimTextColor,
                            modifier = Modifier.size(44.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Start the conversation",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Messages, photos and documents shared here are seen by everyone in this group",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (groupCreatedDateText != null) {
                        item {
                            GroupCreatedHeader(
                                createdDateText = groupCreatedDateText,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                    }

                    groupedMessages.forEach { (date, dateMessages) ->
                        item {
                            DateHeader(date = date, isDark = isDark, secondaryTextColor = secondaryTextColor)
                        }
                        items(dateMessages.size) { index ->
                            val rawMsg = dateMessages[index]
                            val isFirstInRun = index == 0 || dateMessages[index - 1].senderName != rawMsg.senderName || dateMessages[index - 1].isSentByMe != rawMsg.isSentByMe

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

                            MessageBubble(
                                message = msgToRender,
                                player = player,
                                isSelected = selectedMessages.contains(msgToRender.id),
                                isAnySelected = selectedMessages.isNotEmpty(),
                                isFirstInRun = isFirstInRun,
                                onLongClick = { uri ->
                                    selectedMessages = setOf(msgToRender.id)
                                    selectedAttachmentUri = uri
                                    showMessageOptionsSheet = true
                                },
                                onClick = {
                                    if (selectedMessages.isNotEmpty()) {
                                        selectedMessages = if (selectedMessages.contains(msgToRender.id)) selectedMessages - msgToRender.id else selectedMessages + msgToRender.id
                                    }
                                },
                                onReplyClick = { originalId, specificUri ->
                                    val originalMessage = messages.find { it.id == originalId }
                                    if (originalMessage != null) {
                                        replyingTo = originalMessage
                                        replyingToAttachmentUriState = specificUri
                                    }
                                },
                                isHighlighted = highlightedMessageId == msgToRender.id,
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
                                }
                            )
                        }
                    }
                }
            }

            // Floating date pill while scrolling
            if (floatingAlpha > 0f && messages.isNotEmpty()) {
                DateHeader(
                    date = topVisibleDate,
                    isDark = isDark,
                    secondaryTextColor = secondaryTextColor,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .graphicsLayer { alpha = floatingAlpha }
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
    }

    // Attach Sheet (The Paper Clip): Card color background, 20dp top corners, 56dp outline-only buttons with line icons
    if (showAttachmentOptions) {
        val activeCategory = remember(attachedFiles) {
            if (attachedFiles.isNotEmpty()) getAttachmentCategory(context, attachedFiles.first()) else null
        }

        ModalBottomSheet(
            onDismissRequest = { showAttachmentOptions = false },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = cardColor,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.45f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(width = 32.dp, height = 4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = secondaryTextColor.copy(alpha = 0.40f)
                    ) {}
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AttachColumnItem(
                        icon = Icons.Outlined.Image,
                        label = "Image",
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = activeCategory == null || activeCategory == AttachmentCategory.IMAGE
                    ) {
                        imagePickerLauncher.launch("image/*")
                        showAttachmentOptions = false
                    }

                    AttachColumnItem(
                        icon = Icons.Outlined.VideoLibrary,
                        label = "Video",
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = activeCategory == null || activeCategory == AttachmentCategory.VIDEO
                    ) {
                        videoPickerLauncher.launch("video/*")
                        showAttachmentOptions = false
                    }

                    AttachColumnItem(
                        icon = Icons.Outlined.AudioFile,
                        label = "Audio",
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = activeCategory == null || activeCategory == AttachmentCategory.AUDIO
                    ) {
                        audioPickerLauncher.launch("audio/*")
                        showAttachmentOptions = false
                    }

                    AttachColumnItem(
                        icon = Icons.Outlined.Description,
                        label = "Document",
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = activeCategory == null || activeCategory == AttachmentCategory.DOCUMENT
                    ) {
                        documentPickerLauncher.launch(arrayOf("*/*"))
                        showAttachmentOptions = false
                    }
                }

                if (activeCategory != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Finish or remove your current attachments first",
                        fontSize = 12.sp,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
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
}

@Composable
private fun AttachColumnItem(
    icon: ImageVector,
    label: String,
    outlineColor: Color,
    secondaryTextColor: Color,
    primaryTextColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (!enabled) Modifier.alpha(0.35f) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, outlineColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = primaryTextColor
        )
    }
}
