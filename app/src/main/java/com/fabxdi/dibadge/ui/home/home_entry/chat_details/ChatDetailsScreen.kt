package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.fabxdi.dibadge.ui.calendar.GroupScheduleScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.GroupTaskViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Link
import com.fabxdi.dibadge.util.UrlExtractor
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.home.AvatarPhotoCache
import com.fabxdi.dibadge.ui.home.ChatAvatar
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatMessage
import com.fabxdi.dibadge.ui.home.home_entry.chat.DeleteMessageSheet
import com.fabxdi.dibadge.ui.my_forms.GroupFormsScreen
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.viewmodel.GroupFolderFile
import com.fabxdi.dibadge.viewmodel.GroupMediaItem
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.ConnectionsViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChatLinkItem(
    val url: String,
    val domain: String,
    val senderName: String,
    val timestamp: LocalDateTime,
    val messageId: String
)


private fun audioUrlOf(msg: ChatMessage): String? =
    msg.attachments.firstOrNull { u ->
        val l = u.lowercase()
        l.contains("audio") || l.contains(".m4a") || l.contains(".mp3") || l.contains(".wav") ||
                l.contains(".aac") || l.contains(".ogg") || l.contains(".opus")
    } ?: msg.attachments.firstOrNull()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsAudioRow(
    message: ChatMessage,
    url: String,
    player: com.fabxdi.dibadge.util.AudioPlayer,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color,
    outlineColor: Color,
    cardFillColor: Color,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onToggle: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val uri = remember(url) { Uri.parse(url) }
    var active by remember(url) { mutableStateOf(false) }
    var paused by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(false) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var positionSec by remember(url) { mutableLongStateOf(0L) }
    var durationSec by remember(url) {
        mutableLongStateOf(
            message.attachmentDurations[url]
                ?: message.attachmentDurations.filterKeys { !it.startsWith("sz_") }.values.firstOrNull()
                ?: 0L
        )
    }

    // Follow the player while this audio is the one playing. Another audio taking over resets this one.
    LaunchedEffect(active) {
        while (active) {
            if (player.currentSource() != url) {
                active = false; paused = false; loading = false; progress = 0f; positionSec = 0L
                break
            }
            val ready = player.isPrepared()
            loading = !ready
            if (ready) {
                paused = !player.isPlaying()
                val total = player.getDuration()
                val cur = player.getCurrentPosition()
                if (total > 0) {
                    durationSec = (total / 1000).toLong()
                    progress = (cur.toFloat() / total).coerceIn(0f, 1f)
                    positionSec = (cur / 1000).toLong()
                }
            }
            delay(100)
        }
    }

    fun fmt(sec: Long) = String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60)

    fun toggle() {
        if (active && player.currentSource() == url) {
            if (player.isPlaying()) { player.pause(); paused = true } else { player.resume(); paused = false }
        } else {
            player.playFile(uri) { active = false; paused = false; loading = false; progress = 0f; positionSec = 0L }
            active = true; paused = false; loading = true
        }
    }

    val fileName = remember(url) { FilePickerUtils.getFileName(context, uri) }
    val isVoice = message.isVoiceNote || fileName.startsWith("VOICE_") || message.msgType == "audio"
    val title = if (isVoice) "Voice message" else fileName
    val ext = fileName.substringAfterLast('.', "").uppercase()
    val sizeBytes = message.attachmentDurations["sz_" + url] ?: 0L
    val subtitle = listOf(
        if (isVoice) "" else ext,
        if (durationSec > 0) fmt(durationSec) else "",
        if (!isVoice && sizeBytes > 0L) FilePickerUtils.formatFileSize(sizeBytes) else "",
        message.senderName
    ).filter { it.isNotBlank() }.joinToString(" \u00B7 ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(onClick = { if (selectionMode) onToggle() }, onLongClick = onLongPress)
            .background(if (selected) cardFillColor.copy(alpha = 0.6f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(40.dp)
                .clickable { if (selectionMode) onToggle() else toggle() },
            shape = CircleShape,
            color = cardFillColor,
            border = BorderStroke(1.dp, outlineColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = primaryTextColor)
                } else {
                    Icon(
                        imageVector = if (active && !paused) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (active && !paused) "Pause audio" else "Play audio",
                        tint = primaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (active) fmt(positionSec) + (if (durationSec > 0) " / " + fmt(durationSec) else "") + " \u00B7 " + message.senderName else subtitle,
                fontSize = 12.sp,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (active) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .pointerInput(url) {
                            detectTapGestures { pos ->
                                if (player.isPrepared()) {
                                    val frac = (pos.x / size.width).coerceIn(0f, 1f)
                                    progress = frac
                                    player.seekTo((frac * player.getDuration()).toInt())
                                }
                            }
                        }
                ) {
                    val cy = size.height / 2f
                    val x1 = size.width
                    val knobX = x1 * progress
                    drawLine(
                        color = outlineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, cy),
                        end = androidx.compose.ui.geometry.Offset(x1, cy),
                        strokeWidth = 3.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    if (progress > 0f) {
                        drawLine(
                            color = tealAccent,
                            start = androidx.compose.ui.geometry.Offset(0f, cy),
                            end = androidx.compose.ui.geometry.Offset(knobX, cy),
                            strokeWidth = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                    drawCircle(tealAccent, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(knobX, cy))
                }
            }
        }

        if (selected) {
            Spacer(modifier = Modifier.width(10.dp))
            SelectedTick()
        }
    }
}

/** Small dark circle with a white tick, the same mark used on selected media. */
@Composable
private fun SelectedTick() {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = Color(0xFF11161A)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailsScreen(
    entry: HomeEntryEntity,
    messages: List<ChatMessage> = emptyList(),
    onBack: () -> Unit,
    onOpenMyForms: () -> Unit = {},
    onReplyMedia: (String) -> Unit = {},
    onShowInChat: (String) -> Unit = {},
    onDeleteMessage: (messageId: String, forEveryone: Boolean) -> Unit = { _, _ -> },
    detailsViewModel: GroupDetailsViewModel = viewModel()
) {
    BackHandler { onBack() }

    val context = LocalContext.current

    LaunchedEffect(entry.id, entry.title, entry.subtitle, messages) {
        detailsViewModel.loadGroupDetails(
            groupId = entry.id.toString(),
            initialName = entry.title,
            initialSubtitle = entry.subtitle
        )
        if (messages.isNotEmpty()) {
            detailsViewModel.syncChatMessagesMedia(context, messages)
        }
    }

    val groupName by detailsViewModel.groupName.collectAsState()
    val groupSubtitle by detailsViewModel.groupSubtitle.collectAsState()
    val groupPhotoUrl by detailsViewModel.groupPhotoUrl.collectAsState()
    val groupCode by detailsViewModel.groupCode.collectAsState()
    val members by detailsViewModel.members.collectAsState()
    val isAdmin by detailsViewModel.isAdmin.collectAsState()
    val acceptedConnections by detailsViewModel.acceptedConnections.collectAsState()
    val pendingRequests by detailsViewModel.pendingRequests.collectAsState()
    val files by detailsViewModel.files.collectAsState()
    val media by detailsViewModel.media.collectAsState()
    val isLoading by detailsViewModel.isLoading.collectAsState()
    val codeLookupResult by detailsViewModel.codeLookupResult.collectAsState()
    val memberSearchCandidates by detailsViewModel.memberSearchCandidates.collectAsState()

    val isOneToOne by detailsViewModel.isOneToOne.collectAsState()
    val otherUserName by detailsViewModel.otherUserName.collectAsState()
    val otherUserFirstName by detailsViewModel.otherUserFirstName.collectAsState()
    val otherUserNickname by detailsViewModel.otherUserNickname.collectAsState()
    val otherUserPhotoUrl by detailsViewModel.otherUserPhotoUrl.collectAsState()
    val otherUserOrg by detailsViewModel.otherUserOrg.collectAsState()
    val isOtherUserBlocked by detailsViewModel.isOtherUserBlocked.collectAsState()

    var showBlockUserDialog by remember { mutableStateOf(false) }
    var showNicknameSheet by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val isDark = colors.onBackground.red > 0.5f

    val pageBgColor = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF)
    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)
    val tealAccent = if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)

    var selectedActionTab by remember { mutableStateOf<Int?>(null) } // null=None (Media default), 1=Members, 2=Groups, 3=Forms, 4=Folders
    var selectedMediaCategory by remember { mutableStateOf("Media") } // "Media", "Docs"
    var selectedMediaIds by remember { mutableStateOf(setOf<String>()) }
    // One player for the whole Audio list, so starting one audio pauses the other
    val detailsAudioPlayer = remember { com.fabxdi.dibadge.util.AudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { detailsAudioPlayer.stop() } }
    // Pictures that cannot be loaded any more (file deleted). They are left out of the grid.
    var failedMediaUrls by remember { mutableStateOf(setOf<String>()) }
    var selectedViewerMedia by remember { mutableStateOf<GroupMediaItem?>(null) }
    var showDeleteMediaDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }
    var editDescriptionInput by remember { mutableStateOf("") }

    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var showGroupsSheet by remember { mutableStateOf(false) }
    var showTasksSheet by remember { mutableStateOf(false) }
    var showGroupFormsScreen by remember { mutableStateOf(false) }
    var showGroupCalendarScreen by remember { mutableStateOf(false) }
    var showCredentialsScreen by remember { mutableStateOf(false) }
    var showLibraryScreen by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var showMuteSheet by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var memberSearchQuery by remember { mutableStateOf("") }

    val groupTaskViewModel: GroupTaskViewModel = viewModel()
    val groupTasks by groupTaskViewModel.getTasksForGroup(entry.id.toString()).collectAsState(initial = emptyList())

    var groupCodeSearchQuery by remember { mutableStateOf("") }
    var customToastMessage by remember { mutableStateOf<String?>(null) }
    var showHeaderDropdown by remember { mutableStateOf(false) }
    var showGroupCodeDialog by remember { mutableStateOf(false) }

    var pendingAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var avatarRotationAngle by remember { mutableFloatStateOf(0f) }
    var showAvatarCropDialog by remember { mutableStateOf(false) }
    var showFullscreenAvatarViewer by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val showScrolledTitle = remember {
        derivedStateOf { scrollState.value > 120 }
    }

    LaunchedEffect(customToastMessage) {
        if (customToastMessage != null) {
            delay(2000)
            customToastMessage = null
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                detailsViewModel.uploadFileToGroupFolder(context, uri)
            }
        }
    )

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                pendingAvatarUri = uri
                avatarRotationAngle = 0f
                showAvatarCropDialog = true
            }
        }
    )

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "me"
    val resolvedUserRole = remember(members, currentUserId, isAdmin) {
        resolveUserRole(
            members = members,
            currentUserId = currentUserId,
            isAdmin = isAdmin
        )
    }
    val canUserManage = remember(resolvedUserRole) {
        canUserAssignTasks(resolvedUserRole)
    }

    if (showGroupFormsScreen) {
        GroupFormsScreen(
            groupId = entry.id.toString(),
            groupName = groupName,
            canUserManage = canUserManage,
            onBack = { showGroupFormsScreen = false },
            onOpenMyForms = onOpenMyForms
        )
        return
    }

    if (showGroupCalendarScreen) {
        GroupScheduleScreen(
            groupId = entry.id.toString(),
            groupName = groupName,
            canUserManage = canUserManage,
            members = members,
            onBack = { showGroupCalendarScreen = false }
        )
        return
    }

    if (showCredentialsScreen) {
        CredentialsScreen(
            groupId = entry.id.toString(),
            groupName = groupName,
            canUserManage = canUserManage,
            members = members,
            onBack = { showCredentialsScreen = false }
        )
        return
    }

    if (showLibraryScreen) {
        GroupLibraryScreen(
            groupId = entry.id.toString(),
            groupName = groupName,
            canUserManage = canUserManage,
            onBack = { showLibraryScreen = false }
        )
        return
    }

    Scaffold(
        topBar = {
            if (selectedMediaIds.isNotEmpty()) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedMediaIds.size} selected",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedMediaIds = emptySet() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        val selectedLinkUrls = selectedMediaIds.filter { it.startsWith("l:") }
                            .map { it.removePrefix("l:").substringAfter(":") }
                        val allSelectedMediaItems = remember(selectedMediaIds, media, files, messages) {
                            val list = mutableListOf<GroupMediaItem>()
                            for (id in selectedMediaIds) {
                                if (id.startsWith("l:")) continue
                                if (id.startsWith("a:")) {
                                    val am = messages.find { it.id == id.removePrefix("a:") }
                                    val au = am?.let { audioUrlOf(it) }
                                    if (am != null && au != null) {
                                        list.add(GroupMediaItem(id = id, url = au, name = FilePickerUtils.getFileName(context, Uri.parse(au)), mediaType = "audio", timestamp = am.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                                    }
                                    continue
                                }
                                val m = media.find { it.id == id }
                                if (m != null) {
                                    list.add(m)
                                } else {
                                    val f = files.find { it.id == id }
                                    if (f != null) {
                                        list.add(GroupMediaItem(id = f.id, url = f.downloadUrl, name = f.name, mediaType = "document", timestamp = f.timestamp))
                                    }
                                }
                            }
                            list
                        }

                        // If single selection: show Reply Icon (not for links)
                        if (selectedMediaIds.size == 1 && selectedLinkUrls.isEmpty()) {
                            val selectedItem = allSelectedMediaItems.firstOrNull()
                            IconButton(onClick = {
                                if (selectedItem != null) {
                                    val mediaUrl = selectedItem.url
                                    selectedMediaIds = emptySet()
                                    onReplyMedia(mediaUrl)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = "Reply",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Delete Icon
                        IconButton(onClick = {
                            showDeleteMediaDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Share Icon (links are shared as text)
                        val shareSelected = {
                            if (allSelectedMediaItems.isNotEmpty()) {
                                shareMultipleMediaItems(context, allSelectedMediaItems)
                            } else if (selectedLinkUrls.isNotEmpty()) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, selectedLinkUrls.joinToString("\n"))
                                }
                                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            selectedMediaIds = emptySet()
                        }
                        IconButton(onClick = { shareSelected() }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Forward Icon
                        IconButton(onClick = { shareSelected() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = "Forward",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer { scaleX = -1f }
                            )
                        }

                        // Show in Chat Icon (Single selection only)
                        if (selectedMediaIds.size == 1) {
                            val selectedItem = allSelectedMediaItems.firstOrNull()
                            IconButton(onClick = {
                                val target = selectedItem?.url ?: selectedLinkUrls.firstOrNull()
                                if (target != null) {
                                    selectedMediaIds = emptySet()
                                    onShowInChat(target)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubble,
                                    contentDescription = "Show in chat",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        if (showScrolledTitle.value) {
                            Text(
                                text = groupName.ifBlank { "Group" },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            if (!isOneToOne) {
                                IconButton(
                                    onClick = { showGroupCalendarScreen = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_calendar),
                                        contentDescription = "Schedule",
                                        tint = primaryTextColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showHeaderDropdown = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = primaryTextColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (!isOneToOne && isAdmin && pendingRequests.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 2.dp, y = (-2).dp)
                                                .size(9.dp)
                                                .background(tealAccent, shape = CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = pageBgColor
                    )
                )
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // HEADER SECTION (Centered 88dp round avatar, name, description, member count)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 88dp Round Avatar (same look as the home list and the chat header)
                        val entryKey = entry.id.toString()
                        val avatarPhoto = if (isOneToOne) otherUserPhotoUrl else groupPhotoUrl
                        val avatarTitle = entry.title

                        // Share the photo with the home list and chat header. A blank photo is only
                        // removed after a short wait, so the first load doesn't wipe it.
                        LaunchedEffect(avatarPhoto, entryKey) {
                            if (avatarPhoto.isNotBlank()) {
                                AvatarPhotoCache.put(context, entryKey, avatarPhoto)
                            } else {
                                delay(1500)
                                AvatarPhotoCache.put(context, entryKey, "")
                            }
                        }

                        ChatAvatar(
                            title = avatarTitle,
                            idForColor = entryKey,
                            size = 88.dp,
                            photoUrl = avatarPhoto,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (avatarPhoto.isNotBlank()) {
                                        showFullscreenAvatarViewer = true
                                    } else if (!isOneToOne) {
                                        avatarPickerLauncher.launch("image/*")
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Name (22sp Medium)
                        val displayName = if (isOneToOne) (otherUserName.ifBlank { entry.title }) else groupName.ifBlank { "Group" }
                        Text(
                            text = displayName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(
                                enabled = !isOneToOne,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                editNameInput = groupName
                                showEditNameDialog = true
                            }
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Line 2: Nickname (if 1-to-1) OR Description (if group)
                        if (isOneToOne) {
                            if (otherUserNickname.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .clickable { showNicknameSheet = true }
                                        .padding(vertical = 2.dp, horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = otherUserNickname,
                                        fontSize = 14.sp,
                                        color = secondaryTextColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit nickname",
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Add nickname",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tealAccent,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .clickable { showNicknameSheet = true }
                                        .padding(vertical = 2.dp, horizontal = 6.dp)
                                )
                            }
                        } else {
                            val isMemberCountStr = remember(groupSubtitle) {
                                val sub = groupSubtitle.trim().lowercase()
                                sub.endsWith("member") || sub.endsWith("members")
                            }
                            val displayDescription = if (groupSubtitle == "test" || isMemberCountStr) "" else groupSubtitle
                            Text(
                                text = displayDescription.ifBlank { "Add group description" },
                                fontSize = 14.sp,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    editDescriptionInput = displayDescription
                                    showEditDescriptionDialog = true
                                }
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Line 3: Member Count (13sp Secondary)
                            val memberCountText = if (members.size == 1) "1 member" else "${members.size} members"
                            Text(
                                text = memberCountText,
                                fontSize = 13.sp,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.clickable { showMembersSheet = true }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Tabs: Media | Docs (if 1-to-1) or Media | Docs | Audio | Links (if group)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val categories = if (isOneToOne) listOf("Media", "Docs") else listOf("Media", "Docs", "Audio", "Links")
                            categories.forEach { category ->
                                val isSelected = selectedMediaCategory == category
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { selectedMediaCategory = category },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = category,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) primaryTextColor else secondaryTextColor,
                                            maxLines = 1
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .background(if (isSelected) primaryTextColor else Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = outlineColor)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // MEDIA CONTENT (ALWAYS VISIBLE BELOW DIVIDER LINE)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp)
                            .padding(top = 4.dp)
                    ) {
                        // Media Content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 68.dp)
                        ) {
                            when (selectedMediaCategory) {
                                "Media" -> {
                                    val mediaItems = media.filter { (it.mediaType == "image" || it.mediaType == "video") && it.url !in failedMediaUrls }
                                    if (mediaItems.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "no media",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    } else {
                                        val groupedMedia = remember(mediaItems) {
                                            mediaItems.groupBy { formatTimestampToDateHeader(it.timestamp) }
                                        }

                                        groupedMedia.forEach { (dateHeader, itemsList) ->
                                            Text(
                                                text = dateHeader,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )

                                            itemsList.chunked(3).forEach { rowItems ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    rowItems.forEach { item ->
                                                        val isSelected = selectedMediaIds.contains(item.id)
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .aspectRatio(1f)
                                                                .combinedClickable(
                                                                    onLongClick = {
                                                                        selectedMediaIds = if (isSelected) {
                                                                            selectedMediaIds - item.id
                                                                        } else {
                                                                            selectedMediaIds + item.id
                                                                        }
                                                                    },
                                                                    onClick = {
                                                                        if (selectedMediaIds.isNotEmpty()) {
                                                                            selectedMediaIds = if (isSelected) {
                                                                                selectedMediaIds - item.id
                                                                            } else {
                                                                                selectedMediaIds + item.id
                                                                            }
                                                                        } else {
                                                                            selectedViewerMedia = item
                                                                        }
                                                                    }
                                                                )
                                                        ) {
                                                            AsyncImage(
                                                                model = item.url,
                                                                contentDescription = "Media",
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop,
                                                                onError = {
                                                                    if (item.mediaType == "image") {
                                                                        failedMediaUrls = failedMediaUrls + item.url
                                                                        selectedMediaIds = selectedMediaIds - item.id
                                                                    }
                                                                }
                                                            )
                                                            if (item.mediaType == "video") {
                                                                Icon(
                                                                    imageVector = Icons.Default.PlayArrow,
                                                                    contentDescription = "Play Video",
                                                                    tint = Color.White,
                                                                    modifier = Modifier
                                                                        .size(28.dp)
                                                                        .align(Alignment.Center)
                                                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                                        .padding(4.dp)
                                                                )
                                                            }

                                                            if (isSelected) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .background(Color.Black.copy(alpha = 0.35f)),
                                                                    contentAlignment = Alignment.TopEnd
                                                                ) {
                                                                    Surface(
                                                                        modifier = Modifier
                                                                            .padding(6.dp)
                                                                            .size(22.dp),
                                                                        shape = CircleShape,
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    ) {
                                                                        Box(contentAlignment = Alignment.Center) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Check,
                                                                                contentDescription = "Selected",
                                                                                tint = Color.White,
                                                                                modifier = Modifier.size(16.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    repeat(3 - rowItems.size) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                }

                                "Docs" -> {
                                    val docFiles = (files + media.filter { it.mediaType == "document" }.map {
                                        GroupFolderFile(
                                            id = it.id,
                                            name = it.name.ifBlank { "Document" },
                                            downloadUrl = it.url,
                                            timestamp = it.timestamp
                                        )
                                    }).distinctBy { it.downloadUrl }

                                    if (docFiles.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "no documents",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    } else {
                                        val groupedDocs = remember(docFiles) {
                                            docFiles.groupBy { formatTimestampToDateHeader(it.timestamp) }
                                        }

                                        groupedDocs.forEach { (dateHeader, filesList) ->
                                            Text(
                                                text = dateHeader,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )

                                            filesList.chunked(3).forEach { rowFiles ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    rowFiles.forEach { file ->
                                                        val isSelected = selectedMediaIds.contains(file.id)
                                                        DocumentTileItem(
                                                            file = file,
                                                            isSelected = isSelected,
                                                            onLongClick = {
                                                                selectedMediaIds = if (isSelected) {
                                                                    selectedMediaIds - file.id
                                                                } else {
                                                                    selectedMediaIds + file.id
                                                                }
                                                            },
                                                            onClick = {
                                                                if (selectedMediaIds.isNotEmpty()) {
                                                                    selectedMediaIds = if (isSelected) {
                                                                        selectedMediaIds - file.id
                                                                    } else {
                                                                        selectedMediaIds + file.id
                                                                    }
                                                                } else {
                                                                    FilePickerUtils.openFile(context, Uri.parse(file.downloadUrl))
                                                                }
                                                            }
                                                        )
                                                    }
                                                    repeat(3 - rowFiles.size) {
                                                        Spacer(modifier = Modifier.width(100.dp))
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                }

                                "Audio" -> {
                                    val audioItems = remember(messages) {
                                        messages.filter { msg ->
                                            msg.msgType == "audio" || msg.attachments.any { uriStr ->
                                                uriStr.contains("audio") || uriStr.endsWith(".m4a") || uriStr.endsWith(".mp3") || uriStr.endsWith(".wav")
                                            }
                                        }.sortedByDescending { it.timestamp }
                                    }

                                    if (audioItems.isEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 80.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Headphones,
                                                contentDescription = null,
                                                tint = dimTextColor,
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "No audio yet",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = primaryTextColor
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Voice messages and audio files shared in this chat appear here",
                                                fontSize = 13.sp,
                                                color = secondaryTextColor,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 32.dp)
                                            )
                                        }
                                    } else {
                                        val groupedAudio = remember(audioItems) {
                                            audioItems.groupBy { formatTimestampToDateHeader(it.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                                        }

                                        groupedAudio.forEach { (dateHeader, itemsList) ->
                                            Text(
                                                text = dateHeader,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = secondaryTextColor,
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp)
                                            )

                                            itemsList.forEach { audioMsg ->
                                                val audioUrl = audioMsg.attachments.firstOrNull { u ->
                                                    val l = u.lowercase()
                                                    l.contains("audio") || l.contains(".m4a") || l.contains(".mp3") || l.contains(".wav") ||
                                                            l.contains(".aac") || l.contains(".ogg") || l.contains(".opus")
                                                } ?: audioMsg.attachments.firstOrNull()
                                                if (audioUrl != null) {
                                                    val audioKey = "a:" + audioMsg.id
                                                    val audioSelected = selectedMediaIds.contains(audioKey)
                                                    DetailsAudioRow(
                                                        message = audioMsg,
                                                        url = audioUrl,
                                                        player = detailsAudioPlayer,
                                                        primaryTextColor = primaryTextColor,
                                                        secondaryTextColor = secondaryTextColor,
                                                        tealAccent = tealAccent,
                                                        outlineColor = outlineColor,
                                                        cardFillColor = cardFillColor,
                                                        selected = audioSelected,
                                                        selectionMode = selectedMediaIds.isNotEmpty(),
                                                        onToggle = {
                                                            selectedMediaIds = if (audioSelected) selectedMediaIds - audioKey else selectedMediaIds + audioKey
                                                        },
                                                        onLongPress = {
                                                            selectedMediaIds = if (audioSelected) selectedMediaIds - audioKey else selectedMediaIds + audioKey
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                "Links" -> {
                                    val linkList = remember(messages) {
                                        val list = mutableListOf<ChatLinkItem>()
                                        messages.sortedByDescending { it.timestamp }.forEach { msg ->
                                            UrlExtractor.extractUrls(msg.text).forEach { url ->
                                                val domain = UrlExtractor.extractDomain(url)
                                                list.add(
                                                    ChatLinkItem(
                                                        url = url,
                                                        domain = domain,
                                                        senderName = msg.senderName,
                                                        timestamp = msg.timestamp,
                                                        messageId = msg.id
                                                    )
                                                )
                                            }
                                        }
                                        list
                                    }

                                    if (linkList.isEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 80.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Link,
                                                contentDescription = null,
                                                tint = dimTextColor,
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "No links yet",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = primaryTextColor
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Links shared in this chat appear here",
                                                fontSize = 13.sp,
                                                color = secondaryTextColor,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 32.dp)
                                            )
                                        }
                                    } else {
                                        val groupedLinks = remember(linkList) {
                                            linkList.groupBy { formatTimestampToDateHeader(it.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                                        }

                                        groupedLinks.forEach { (dateHeader, itemsList) ->
                                            Text(
                                                text = dateHeader,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = secondaryTextColor,
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp)
                                            )

                                            itemsList.forEach { linkItem ->
                                                val linkKey = "l:${linkItem.messageId}:${linkItem.url}"
                                                val linkSelected = selectedMediaIds.contains(linkKey)

                                                Box {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(72.dp)
                                                            .combinedClickable(
                                                                onClick = {
                                                                    if (selectedMediaIds.isNotEmpty()) {
                                                                        selectedMediaIds = if (linkSelected) selectedMediaIds - linkKey else selectedMediaIds + linkKey
                                                                    } else try {
                                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(if (!linkItem.url.startsWith("http")) "https://${linkItem.url}" else linkItem.url))
                                                                        context.startActivity(intent)
                                                                    } catch (e: Exception) {
                                                                        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                },
                                                                onLongClick = {
                                                                    selectedMediaIds = if (linkSelected) selectedMediaIds - linkKey else selectedMediaIds + linkKey
                                                                }
                                                            )
                                                            .background(if (linkSelected) cardFillColor.copy(alpha = 0.6f) else Color.Transparent)
                                                            .padding(horizontal = 20.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            modifier = Modifier.size(40.dp),
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = cardFillColor,
                                                            border = BorderStroke(1.dp, outlineColor)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.Link,
                                                                    contentDescription = "Link",
                                                                    tint = secondaryTextColor,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.width(14.dp))

                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = linkItem.domain,
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = primaryTextColor,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = linkItem.url,
                                                                fontSize = 12.sp,
                                                                color = secondaryTextColor,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            val timeStr = linkItem.timestamp.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
                                                            Text(
                                                                text = "${linkItem.senderName} · $timeStr",
                                                                fontSize = 12.sp,
                                                                color = dimTextColor,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        if (linkSelected) {
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            SelectedTick()
                                                        }
                                                    }

                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Custom Pure Text Toast Overlay (Horizontally Centered at Bottom)
                if (customToastMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(24.dp),
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                text = customToastMessage ?: "",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Edit Name Popup
        EditNameDialog(
            show = showEditNameDialog,
            input = editNameInput,
            onInputChange = { editNameInput = it },
            onDismiss = { showEditNameDialog = false },
            onSave = {
                if (editNameInput.isNotBlank()) {
                    detailsViewModel.updateGroupName(context, editNameInput)
                    showEditNameDialog = false
                }
            }
        )

        // Edit Description Popup
        EditDescriptionDialog(
            show = showEditDescriptionDialog,
            input = editDescriptionInput,
            onInputChange = { editDescriptionInput = it },
            onDismiss = { showEditDescriptionDialog = false },
            onSave = {
                detailsViewModel.updateGroupSubtitle(context, editDescriptionInput)
                showEditDescriptionDialog = false
            }
        )

        if (showGroupCodeDialog) {
            GroupCodeSheet(
                groupCode = groupCode,
                onCopy = { code ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("group_code", code)
                    clipboard.setPrimaryClip(clip)
                    showGroupCodeDialog = false
                    customToastMessage = "Group code copied"
                },
                onDismiss = { showGroupCodeDialog = false }
            )
        }

        if (showHeaderDropdown) {
            val otherMember = members.find { !it.isYou }
            val otherUid = otherMember?.uid ?: ""
            val fullNameStr = if (otherUserName.isNotBlank()) otherUserName else groupName

            ChatDetailsMenuSheet(
                isMuted = isMuted,
                canUserManage = canUserManage,
                waitingInvitesCount = pendingRequests.size,
                isConnectedToOrg = acceptedConnections.isNotEmpty(),
                isOneToOne = isOneToOne,
                isBlocked = isOtherUserBlocked,
                onSelectMembers = { showMembersSheet = true },
                onSelectGroups = { showGroupsSheet = true },
                onSelectForms = { showGroupFormsScreen = true },
                onSelectTasks = { showTasksSheet = true },
                onSelectCredentials = { showCredentialsScreen = true },
                onSelectLibrary = { showLibraryScreen = true },
                onSelectMute = { showMuteSheet = true },
                onSelectUnmute = {
                    isMuted = false
                    customToastMessage = "Notifications unmuted"
                },
                onSelectClearChat = { showClearChatDialog = true },
                onSelectGroupCode = { showGroupCodeDialog = true },
                onSelectBlockUser = { showBlockUserDialog = true },
                onSelectUnblockUser = {
                    detailsViewModel.unblockUser(otherUid) {
                        val fn = otherUserFirstName.ifBlank { fullNameStr.split(" ").firstOrNull().orEmpty() }
                        Toast.makeText(context, "$fn unblocked", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showHeaderDropdown = false }
            )
        }

        if (showBlockUserDialog) {
            val otherMember = members.find { !it.isYou }
            val otherUid = otherMember?.uid ?: ""
            val fullNameStr = if (otherUserName.isNotBlank()) otherUserName else groupName
            val fn = otherUserFirstName.ifBlank { fullNameStr.split(" ").firstOrNull().orEmpty() }

            AlertDialog(
                onDismissRequest = { showBlockUserDialog = false },
                containerColor = cardColor,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "Block $fullNameStr?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )
                },
                text = {
                    Text(
                        text = "$fn won't be able to send you messages. You can unblock anytime in Settings, Blocked users.",
                        fontSize = 14.sp,
                        color = secondaryTextColor
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBlockUserDialog = false
                            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                            val isOnline = cm?.activeNetwork != null
                            if (!isOnline) {
                                Toast.makeText(context, "You're offline. Try again when connected", Toast.LENGTH_SHORT).show()
                            } else {
                                detailsViewModel.blockUser(otherUid, fullNameStr, otherUserOrg) {
                                    Toast.makeText(context, "$fn blocked", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            }
                        }
                    ) {
                        Text(
                            text = "Block",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = errorColor
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBlockUserDialog = false }) {
                        Text(
                            text = "Cancel",
                            fontSize = 15.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            )
        }

        if (showClearChatDialog) {
            val fullNameStr = if (otherUserName.isNotBlank()) otherUserName else groupName
            val clearText = if (isOneToOne) {
                "Messages will be removed only for you. $fullNameStr still sees them."
            } else {
                "Messages will be removed only for you. Other group members will still see them."
            }

            AlertDialog(
                onDismissRequest = { showClearChatDialog = false },
                containerColor = cardColor,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "Clear this chat?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )
                },
                text = {
                    Text(
                        text = clearText,
                        fontSize = 14.sp,
                        color = secondaryTextColor
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearChatDialog = false
                            detailsViewModel.clearChatLocally(context, entry.id.toString())
                            Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = "Clear",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = errorColor
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearChatDialog = false }) {
                        Text(
                            text = "Cancel",
                            fontSize = 15.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            )
        }

        if (showNicknameSheet) {
            val otherMember = members.find { !it.isYou }
            val otherUid = otherMember?.uid ?: ""
            val fullNameStr = if (otherUserName.isNotBlank()) otherUserName else groupName

            NicknameSheet(
                fullName = fullNameStr,
                currentNickname = otherUserNickname,
                onSave = { newNick ->
                    detailsViewModel.saveNickname(context, otherUid, newNick) {
                        customToastMessage = if (newNick.isNotBlank()) "Nickname saved" else "Nickname removed"
                    }
                },
                onDismiss = { showNicknameSheet = false }
            )
        }

        if (showGroupsSheet) {
            GroupsBottomSheet(
                ownGroupCode = groupCode,
                connectedGroups = acceptedConnections,
                pendingRequests = pendingRequests,
                canUserManage = canUserManage,
                onCodeLookup = { code ->
                    detailsViewModel.lookupGroupCode(code)
                },
                codeLookupResult = codeLookupResult,
                onSendInvite = { targetId, targetType, name ->
                    detailsViewModel.sendConnectRequest(targetId, targetType) {
                        customToastMessage = "Invite sent to $name"
                    }
                },
                onAcceptInvite = { connectionId, name ->
                    detailsViewModel.updateConnectionStatus(connectionId, "accepted")
                    customToastMessage = "$name is now connected"
                },
                onDeclineInvite = { connectionId ->
                    detailsViewModel.updateConnectionStatus(connectionId, "declined")
                    customToastMessage = "Invitation declined"
                },
                onRemoveConnection = { connectionId, name ->
                    detailsViewModel.updateConnectionStatus(connectionId, "removed")
                    customToastMessage = "$name removed"
                },
                onDismiss = { showGroupsSheet = false }
            )
        }

        if (showTasksSheet) {
            val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName?.ifBlank { null } ?: "Me"
            TasksBottomSheet(
                groupId = entry.id.toString(),
                tasks = groupTasks,
                members = members,
                currentRole = resolvedUserRole,
                currentUserName = currentUserName,
                currentUserUid = currentUserId,
                onAssignTaskConfirmed = { title, assignees, dueDate, dueTime, note ->
                    groupTaskViewModel.assignTasks(
                        title = title,
                        groupId = entry.id.toString(),
                        assignees = assignees,
                        assignerName = currentUserName,
                        assignerUid = currentUserId,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        note = note
                    )
                },
                onDismiss = { showTasksSheet = false }
            )
        }

        if (showMuteSheet) {
            MuteChoiceSheet(
                onMuteConfirmed = { option ->
                    isMuted = true
                    customToastMessage = "Muted for $option"
                },
                onDismiss = { showMuteSheet = false }
            )
        }

        if (showClearChatDialog) {
            AlertDialog(
                onDismissRequest = { showClearChatDialog = false },
                title = {
                    Text("Clear this chat?", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                },
                text = {
                    Text("Messages will be removed only for you. Others in this group still see them.", fontSize = 14.sp, color = secondaryTextColor)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearChatDialog = false
                            customToastMessage = "Chat cleared"
                        }
                    ) {
                        Text("Clear", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearChatDialog = false }) {
                        Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Normal, color = secondaryTextColor)
                    }
                },
                containerColor = cardColor,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Add Member Dialog / Bottom Sheet
        if (showAddMemberDialog) {
            val connectionsViewModel: ConnectionsViewModel = viewModel()
            val userConnections by connectionsViewModel.connections.collectAsState()

            val existingMemberUids = remember(members) { members.map { it.uid }.toSet() }
            val existingMemberNames = remember(members) { members.map { it.displayName.lowercase() }.toSet() }

            val availableCandidates = remember(userConnections, existingMemberUids, existingMemberNames) {
                userConnections
                    .filter { conn ->
                        val uid = conn.uid.ifBlank { conn.id }
                        !existingMemberUids.contains(uid) && !existingMemberNames.contains(conn.name.lowercase())
                    }
                    .map { conn ->
                        AddConnectionCandidate(
                            id = conn.uid.ifBlank { conn.id },
                            name = conn.name,
                            email = conn.organization.ifBlank { conn.role }
                        )
                    }
            }

            AddPeopleSheet(
                candidates = availableCandidates,
                totalConnectionsCount = userConnections.size,
                onAddSelected = { added ->
                    showAddMemberDialog = false
                    for (candidate in added) {
                        detailsViewModel.addMemberToGroup(candidate.id)
                    }
                    if (added.isNotEmpty()) {
                        Toast.makeText(context, "Added ${added.size} member(s)", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = {
                    showAddMemberDialog = false
                }
            )
        }

        // Delete sheet: the same bottom sheet the chat screen uses
        if (showDeleteMediaDialog && selectedMediaIds.isNotEmpty()) {
            val pickedFileIds = selectedMediaIds.filter { !it.startsWith("a:") && !it.startsWith("l:") }.toSet()
            val pickedMessageIds = selectedMediaIds.filter { it.startsWith("a:") || it.startsWith("l:") }
                .map { it.removeRange(0, 2).substringBefore(":") }.toSet()
            // Only your own messages can be deleted for everyone
            val canDeleteForEveryone = pickedMessageIds.all { mid -> messages.find { it.id == mid }?.isSentByMe == true }

            DeleteMessageSheet(
                selectedCount = selectedMediaIds.size,
                canDeleteForEveryone = canDeleteForEveryone,
                onDeleteForEveryone = {
                    if (pickedFileIds.isNotEmpty()) detailsViewModel.deleteSelectedMediaForAll(context, pickedFileIds)
                    pickedMessageIds.forEach { onDeleteMessage(it, true) }
                    selectedMediaIds = emptySet()
                    showDeleteMediaDialog = false
                },
                onDeleteForMe = {
                    if (pickedFileIds.isNotEmpty()) detailsViewModel.deleteSelectedMediaForMe(context, pickedFileIds)
                    pickedMessageIds.forEach { onDeleteMessage(it, false) }
                    selectedMediaIds = emptySet()
                    showDeleteMediaDialog = false
                },
                onDismiss = { showDeleteMediaDialog = false }
            )
        }

        if (showMembersSheet) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "me"
            val userMember = members.find { it.uid == currentUserId || it.isYou }
            val userRole = userMember?.role?.lowercase() ?: (if (isAdmin) "owner" else "member")

            MembersBottomSheet(
                groupName = groupName,
                members = members,
                currentUserId = currentUserId,
                currentRole = userRole,
                onAddPeopleClick = {
                    showMembersSheet = false
                    showAddMemberDialog = true
                },
                onRoleChange = { memberId, newRole ->
                    detailsViewModel.updateMemberRole(context, memberId, newRole)
                },
                onTransferOwnership = { newOwnerId ->
                    detailsViewModel.transferGroupOwnership(context, newOwnerId)
                },
                onExitGroup = {
                    showMembersSheet = false
                    detailsViewModel.leaveGroup(context) {
                        onBack()
                    }
                },
                onDismiss = { showMembersSheet = false }
            )
        }

        // Avatar Crop / Rotate Editor Dialog
        AvatarCropDialog(
            pendingAvatarUri = pendingAvatarUri,
            avatarRotationAngle = avatarRotationAngle,
            onRotate = { avatarRotationAngle = (avatarRotationAngle + 90f) % 360f },
            onCancel = {
                showAvatarCropDialog = false
                pendingAvatarUri = null
            },
            onDone = { uri ->
                showAvatarCropDialog = false
                showFullscreenAvatarViewer = false
                pendingAvatarUri = null
                detailsViewModel.uploadGroupAvatarPhoto(context, uri)
            }
        )

        // Fullscreen Group Avatar Viewer
        FullscreenAvatarViewer(
            show = showFullscreenAvatarViewer,
            groupPhotoUrl = groupPhotoUrl,
            onClose = { showFullscreenAvatarViewer = false },
            onEdit = { avatarPickerLauncher.launch("image/*") },
            onDelete = {
                showFullscreenAvatarViewer = false
                detailsViewModel.deleteGroupAvatarPhoto(context)
            }
        )

        // Fullscreen Media Viewer Overlay
        FullscreenMediaViewer(
            viewerItem = selectedViewerMedia,
            onClose = { selectedViewerMedia = null },
            onReply = { mediaUrl ->
                selectedViewerMedia = null
                onReplyMedia(mediaUrl)
            },
            onDelete = { id ->
                selectedMediaIds = setOf(id)
                showDeleteMediaDialog = true
                selectedViewerMedia = null
            },
            onShare = { item ->
                shareMediaItem(context, item.url, item.mediaType)
            },
            onForward = { item ->
                shareMediaItem(context, item.url, item.mediaType)
            },
            onOpenInChat = { mediaUrl ->
                selectedViewerMedia = null
                onShowInChat(mediaUrl)
            }
        )
    }
}

// ---------------------------------------------------------------------------
// PREVIEWS
// ---------------------------------------------------------------------------

@Preview(name = "Chat Details Media - Light", widthDp = 412, showBackground = true)
@Composable
fun ChatDetailsScreenMediaLightPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatDetailsScreen(
                entry = HomeEntryEntity(id = 1, title = "Site Alpha", subtitle = "Project group", initials = "SA"),
                onBack = {}
            )
        }
    }
}

@Preview(name = "Chat Details Docs - Light", widthDp = 412, showBackground = true)
@Composable
fun ChatDetailsScreenDocsLightPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatDetailsScreen(
                entry = HomeEntryEntity(id = 1, title = "Site Alpha", subtitle = "Project group", initials = "SA"),
                onBack = {}
            )
        }
    }
}

@Preview(
    name = "Chat Details - Dark",
    widthDp = 412,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ChatDetailsScreenDarkPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.DARK) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatDetailsScreen(
                entry = HomeEntryEntity(id = 1, title = "Site Alpha", subtitle = "Project group", initials = "SA"),
                onBack = {}
            )
        }
    }
}