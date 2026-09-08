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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.components.AttachmentList
import com.fabxdi.dibadge.util.AudioPlayer
import com.fabxdi.dibadge.util.AudioRecorder
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.UserColorUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate

data class ChatMessage(
    val id: String,
    val text: String,
    val senderName: String,
    val isSentByMe: Boolean,
    val attachments: List<String> = emptyList(),
    val attachmentDurations: Map<String, Long> = emptyMap(), // URI string to duration in seconds
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val replyingTo: ChatMessage? = null,
    val replyingToAttachmentUri: String? = null,
    val isEdited: Boolean = false
)

@Composable
fun DateHeader(date: LocalDate) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM d, yyyy") }
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    val text = when (date) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> date.format(formatter)
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VoiceNotePreviewItem(
    uri: Uri,
    player: AudioPlayer,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                if (player.isPlaying()) {
                    val duration = player.getDuration()
                    if (duration > 0) {
                        progress = player.getCurrentPosition().toFloat() / duration
                    }
                } else {
                    isPlaying = false
                    progress = 0f
                }
                delay(50)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(vertical = 4.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            player.stop()
                            isPlaying = false
                            progress = 0f
                        } else {
                            player.playFile(uri) {
                                isPlaying = false
                                progress = 0f
                            }
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                val centerY = size.height / 2
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = Color.White,
                    start = Offset(0f, centerY),
                    end = Offset(size.width * progress, centerY),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(size.width * progress, centerY)
                )
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFF869694),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    entry: HomeEntryEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage("1", "Hi there!", "John", false),
                ChatMessage("2", "Hello! How can I help you?", "Me", true),
                ChatMessage("3", "I have a question about the certificate.", "Mary", false),
                ChatMessage("4", "Sure, go ahead and ask.", "Me", true)
            )
        )
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
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var replyingToAttachmentUriState by remember { mutableStateOf<String?>(null) }
    var pendingDurations by remember { mutableStateOf(mapOf<String, Long>()) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val groupedMessages = remember(messages) {
        messages.groupBy { it.timestamp.toLocalDate() }.toSortedMap()
    }

    val messageToLazyIndex = remember(groupedMessages) {
        val map = mutableMapOf<String, Int>()
        var currentIndex = 0
        groupedMessages.forEach { (_, dateMessages) ->
            currentIndex++ // For the date header
            dateMessages.forEach { message ->
                map[message.id] = currentIndex
                currentIndex++
            }
        }
        map
    }

    var isRecording by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
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

    // Handle Back Button
    BackHandler {
        if (selectedMessages.isNotEmpty()) {
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

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                showCamera = true
            }
        }
    )

    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            val currentCount = attachedFiles.size
            val remainingSpace = 101 - currentCount
            
            if (uris.size > remainingSpace) {
                Toast.makeText(context, "Limit reached. Only 101 files allowed.", Toast.LENGTH_LONG).show()
            }

            if (remainingSpace > 0) {
                val newUris = uris.take(remainingSpace)
                newUris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {}
                }
                attachedFiles = attachedFiles + newUris
            }
        }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris -> attachedFiles = attachedFiles + uris }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris -> attachedFiles = attachedFiles + uris }
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris -> attachedFiles = attachedFiles + uris }
    )

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris -> attachedFiles = attachedFiles + uris }
    )

    LaunchedEffect(isRecording, isPaused) {
        if (isRecording && !isPaused) {
            while (true) {
                delay(1000)
                recordingTime += 1
            }
        }
    }

    if (showCamera) {
        CameraCaptureScreen(
            onImageCaptured = { uri ->
                attachedFiles = attachedFiles + uri
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
                messages = messages.filter { it.id != galleryMessage?.id }
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
        "🎯", "✅", "❌", "⌛", "⏳", "⌚", "⏰", "🛠️", "⚙️", "🔗"
    )

    val listState = rememberLazyListState()
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val imageUris = attachedFiles.filter { uri ->
        val mimeType = context.contentResolver.getType(uri)
        mimeType?.startsWith("image/") == true ||
        uri.path?.lowercase()?.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") } == true
    }

    if (showDeleteDialog) {
        val selectedMsgs = messages.filter { selectedMessages.contains(it.id) }
        val onlyMineSelected = selectedMsgs.all { it.isSentByMe }
        
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete message?") },
            text = { Text("Are you sure you want to delete ${if (selectedMessages.size > 1) "these messages" else "this message"}?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (onlyMineSelected) {
                        TextButton(
                            onClick = {
                                messages = messages.filter { !selectedMessages.contains(it.id) }
                                selectedMessages = emptySet()
                                showDeleteDialog = false
                            }
                        ) {
                            Text("Delete for everyone", color = Color.Red)
                        }
                    }
                    TextButton(
                        onClick = {
                            messages = messages.filter { !selectedMessages.contains(it.id) }
                            selectedMessages = emptySet()
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Delete for me", color = Color.Red)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (selectedMessages.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedMessages.size}", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { 
                            selectedMessages = emptySet()
                            selectedAttachmentUri = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        if (selectedMessages.size == 1) {
                            val msg = messages.find { it.id == selectedMessages.first() }
                            if (msg?.isSentByMe == true && msg.attachments.isEmpty()) {
                                IconButton(onClick = {
                                    editingMessage = msg
                                    inputText = msg.text
                                    selectedMessages = emptySet()
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                            }
                            IconButton(onClick = {
                                replyingTo = msg
                                replyingToAttachmentUriState = selectedAttachmentUri
                                selectedMessages = emptySet()
                                selectedAttachmentUri = null
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply")
                            }
                        }
                        val hasMediaSelected = messages.filter { selectedMessages.contains(it.id) }.any { it.attachments.isNotEmpty() }
                        if (!hasMediaSelected) {
                            IconButton(
                                onClick = {
                                    val texts = messages.filter { selectedMessages.contains(it.id) }
                                        .joinToString("\n") { it.text }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("chat_message", texts)
                                    clipboard.setPrimaryClip(clip)
                                    selectedMessages = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy, 
                                    contentDescription = "Copy",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = entry.title, 
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (entry.subtitle.isNotBlank()) {
                                Text(
                                    text = entry.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack, 
                                    contentDescription = "Back", 
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = UserColorUtils.getColorForName(entry.title)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = entry.title.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = {}, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.MoreVert, 
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(Color.Transparent)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = showAttachmentOptions || showEmojiPicker
                    ) {
                        showAttachmentOptions = false
                        showEmojiPicker = false
                    }
            ) {
                if (showEmojiPicker) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(260.dp).padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(40.dp),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(commonEmojis.size) { index ->
                                Box(
                                    modifier = Modifier.size(40.dp).clickable { inputText += commonEmojis[index] },
                                    contentAlignment = Alignment.Center
                                ) { Text(text = commonEmojis[index], fontSize = 24.sp) }
                            }
                        }
                    }
                }

                val isOnlyVoiceNote = attachedFiles.size == 1 && run {
                    val uri = attachedFiles.first()
                    val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
                    mimeType?.startsWith("audio/") == true || 
                    uri.path?.lowercase()?.endsWith(".m4a") == true
                }

                if (attachedFiles.isNotEmpty() && !isOnlyVoiceNote) {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            val mediaUrisPreview = attachedFiles.filter { uri ->
                                val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
                                mimeType?.startsWith("image/") == true || 
                                mimeType?.startsWith("video/") == true ||
                                uri.path?.lowercase()?.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".mp4") } == true
                            }
                            val otherUrisPreview = attachedFiles.filter { !mediaUrisPreview.contains(it) }

                            if (mediaUrisPreview.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.height(100.dp)
                                ) {
                                    items(mediaUrisPreview) { uri ->
                                        val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true || 
                                                      uri.path?.lowercase()?.endsWith(".mp4") == true
                                        
                                        Box(modifier = Modifier.size(100.dp)) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { FilePickerUtils.openFile(context, uri) },
                                                contentScale = ContentScale.Crop
                                            )

                                            if (isVideo) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Video",
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .align(Alignment.Center)
                                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                        .padding(4.dp)
                                                )
                                            }
                                            
                                            IconButton(
                                                onClick = { 
                                                    val uriToRemove = uri
                                                    attachedFiles = attachedFiles.filter { it != uriToRemove }
                                                    pendingDurations = pendingDurations.filter { it.key != uriToRemove.toString() }
                                                },
                                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            if (otherUrisPreview.isNotEmpty()) {
                                if (mediaUrisPreview.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    otherUrisPreview.forEach { uri ->
                                        val isAudio = context.contentResolver.getType(uri)?.startsWith("audio/") == true || 
                                                     uri.path?.lowercase()?.endsWith(".m4a") == true
                                        if (isAudio) {
                                            VoiceNotePreviewItem(
                                                uri = uri,
                                                player = player,
                                                onRemove = { 
                                                    attachedFiles = attachedFiles.filter { it != uri }
                                                    pendingDurations = pendingDurations.filter { it.key != uri.toString() }
                                                },
                                                modifier = Modifier.widthIn(min = 200.dp, max = 260.dp)
                                            )
                                        } else {
                                            AttachmentList(
                                                context = context,
                                                attachedFiles = listOf(uri),
                                                onRemove = { _ ->
                                                    attachedFiles =
                                                        attachedFiles.filter { it != uri }
                                                    pendingDurations =
                                                        pendingDurations.filter { it.key != uri.toString() }
                                                },
                                                onAddClick = { },
                                                modifier = Modifier.widthIn(
                                                    min = 150.dp,
                                                    max = 260.dp
                                                ),
                                                showAddButton = false,
                                                player = player
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (replyingTo != null) {
                    val replyColor = UserColorUtils.getColorForName(replyingTo!!.senderName)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = replyingTo!!.senderName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = replyColor
                                )
                                Text(
                                    text = replyingTo!!.text.ifBlank { 
                                        val uri = replyingToAttachmentUriState ?: replyingTo!!.attachments.firstOrNull()
                                        if (uri != null) {
                                            val fileName = FilePickerUtils.getFileName(context, Uri.parse(uri)).lowercase()
                                            when {
                                                fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".jpeg") -> "Photo"
                                                fileName.endsWith(".mp4") -> "Video"
                                                fileName.endsWith(".pdf") -> "PDF"
                                                else -> "Attachment"
                                            }
                                        } else "Attachment"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val replyUriString = replyingToAttachmentUriState ?: replyingTo!!.attachments.firstOrNull()
                            if (replyUriString != null) {
                                val replyUri = Uri.parse(replyUriString)
                                val mimeType = try { context.contentResolver.getType(replyUri) } catch (e: Exception) { null }
                                val isMedia = mimeType?.startsWith("image/") == true || 
                                             mimeType?.startsWith("video/") == true ||
                                             replyUri.path?.lowercase()?.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".mp4") } == true
                                
                                if (isMedia) {
                                    AsyncImage(
                                        model = replyUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            IconButton(onClick = { 
                                replyingTo = null
                                replyingToAttachmentUriState = null
                            }) {
                                Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (editingMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp).height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Edit Message",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF00A884)
                                )
                                Text(
                                    text = editingMessage!!.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { editingMessage = null; inputText = "" }) {
                                Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = if (isOnlyVoiceNote) 8.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showAttachmentOptions) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AttachmentOption(Icons.Default.Image, "Image", Color(0xFFE91E63)) {
                                    imagePickerLauncher.launch("image/*")
                                    showAttachmentOptions = false
                                }
                                AttachmentOption(Icons.Default.VideoLibrary, "Video", Color(0xFF9C27B0)) {
                                    videoPickerLauncher.launch("video/*")
                                    showAttachmentOptions = false
                                }
                                AttachmentOption(Icons.Default.AudioFile, "Audio", Color(0xFFFF9800)) {
                                    audioPickerLauncher.launch("audio/*")
                                    showAttachmentOptions = false
                                }
                                AttachmentOption(Icons.Default.Description, "Document", Color(0xFF2196F3)) {
                                    documentPickerLauncher.launch(arrayOf("*/*"))
                                    showAttachmentOptions = false
                                }
                            }
                        }
                    } else {
                        if (!isRecording && !isOnlyVoiceNote) {
                            IconButton(
                                onClick = { 
                                    if (attachedFiles.isEmpty()) {
                                        showAttachmentOptions = true 
                                    } else {
                                        val firstUri = attachedFiles.first()
                                        val mimeType = try { context.contentResolver.getType(firstUri) } catch (e: Exception) { null }
                                        val fileName = FilePickerUtils.getFileName(context, firstUri).lowercase()
                                        
                                        when {
                                            mimeType?.startsWith("image/") == true || fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".jpeg") -> {
                                                imagePickerLauncher.launch("image/*")
                                            }
                                            mimeType?.startsWith("video/") == true || fileName.endsWith(".mp4") -> {
                                                videoPickerLauncher.launch("video/*")
                                            }
                                            mimeType?.startsWith("audio/") == true || fileName.endsWith(".m4a") -> {
                                                audioPickerLauncher.launch("audio/*")
                                            }
                                            else -> {
                                                documentPickerLauncher.launch(arrayOf("*/*"))
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(40.dp),
                                enabled = attachedFiles.size < 101
                            ) {
                                Icon(
                                    if (attachedFiles.isNotEmpty()) Icons.Default.Add else Icons.Default.AttachFile, 
                                    null, 
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        
                        if (isOnlyVoiceNote) {
                            VoiceNotePreviewItem(
                                uri = attachedFiles.first(),
                                player = player,
                                onRemove = { 
                                    attachedFiles = emptyList()
                                    pendingDurations = emptyMap()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Surface(
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                if (isRecording) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Mic, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isPaused) "Paused" else String.format(Locale.getDefault(), "%02d:%02d", recordingTime / 60, recordingTime % 60),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (isLocked) {
                                            IconButton(onClick = { if (isPaused) { recorder.resume(); isPaused = false } else { recorder.pause(); isPaused = true } }, modifier = Modifier.size(32.dp)) { Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
                                            IconButton(onClick = { 
                                                val finalDuration = recordingTime
                                                recorder.stop()
                                                isRecording = false; isLocked = false; isPaused = false
                                                audioFile?.let { 
                                                    val uri = Uri.fromFile(it)
                                                    attachedFiles = attachedFiles + uri
                                                    pendingDurations = pendingDurations + (uri.toString() to finalDuration)
                                                }
                                            }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Stop, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
                                        } else { Text("Slide to lock", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        IconButton(onClick = { recorder.stop(); audioFile?.delete(); isRecording = false; isLocked = false; isPaused = false }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
                                        IconButton(onClick = { showEmojiPicker = !showEmojiPicker }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.SentimentSatisfiedAlt, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) }
                                        TextField(
                                            value = inputText,
                                            onValueChange = { inputText = it },
                                            modifier = Modifier.weight(1f),
                                            placeholder = { Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 16.sp) },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                                        )
                                        if (attachedFiles.isEmpty()) {
                                            IconButton(
                                                onClick = { 
                                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                        showCamera = true
                                                    } else {
                                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp),
                                                enabled = attachedFiles.size < 101
                                            ) { Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))

                        val showSend = inputText.isNotEmpty() || attachedFiles.isNotEmpty()
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .pointerInput(showSend) {
                                    if (!showSend) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.first()
                                                when (event.type) {
                                                    PointerEventType.Press -> {
                                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                            val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
                                                            audioFile = file
                                                            recordingTime = 0
                                                            recorder.start(file)
                                                            isRecording = true; isLocked = false; isPaused = false
                                                        } else { audioPermissionLauncher.launch(
                                                            Manifest.permission.RECORD_AUDIO) }
                                                    }
                                                    PointerEventType.Move -> { if (isRecording && !isLocked && change.position.y < -150) { isLocked = true } }
                                                    PointerEventType.Release -> {
                                                        if (isRecording && !isLocked) { 
                                                            val finalDuration = recordingTime
                                                            recorder.stop()
                                                            isRecording = false
                                                            audioFile?.let { 
                                                                val uri = Uri.fromFile(it)
                                                                attachedFiles = attachedFiles + uri
                                                                pendingDurations = pendingDurations + (uri.toString() to finalDuration)
                                                            } 
                                                        } 
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            shape = CircleShape,
                            color = if (showSend) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (showSend) 2.dp else 0.dp,
                            onClick = {
                                if (showSend) {
                                    if (editingMessage != null) {
                                        messages = messages.map {
                                            if (it.id == editingMessage!!.id) {
                                                it.copy(text = inputText.trim(), isEdited = true)
                                            } else it
                                        }
                                        editingMessage = null
                                    } else {
                                        messages = messages + ChatMessage(
                                            id = System.currentTimeMillis().toString(), 
                                            text = inputText.trim(), 
                                            senderName = "Me", 
                                            isSentByMe = true, 
                                            attachments = attachedFiles.map { it.toString() },
                                            attachmentDurations = pendingDurations,
                                            replyingTo = replyingTo,
                                            replyingToAttachmentUri = replyingToAttachmentUriState
                                        )
                                    }
                                    inputText = ""; attachedFiles = emptyList(); showEmojiPicker = false; replyingTo = null; replyingToAttachmentUriState = null; pendingDurations = emptyMap()
                                }
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (showSend) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showEmojiPicker = false
                showAttachmentOptions = false
            }
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                groupedMessages.forEach { (date, dateMessages) ->
                    item {
                        DateHeader(date)
                    }
                    items(dateMessages.size) { index ->
                        val message = dateMessages[index]
                        MessageBubble(
                            message = message, 
                            player = player,
                            isSelected = selectedMessages.contains(message.id),
                            isAnySelected = selectedMessages.isNotEmpty(),
                            onLongClick = { uri -> 
                                selectedMessages = selectedMessages + message.id
                                selectedAttachmentUri = uri
                            },
                            onClick = {
                                if (selectedMessages.isNotEmpty()) {
                                    selectedMessages = if (selectedMessages.contains(message.id)) selectedMessages - message.id else selectedMessages + message.id
                                    if (selectedMessages.isEmpty()) selectedAttachmentUri = null
                                }
                            },
                            onReplyClick = { originalId, specificUri ->
                                val originalMessage = messages.find { it.id == originalId }
                                if (originalMessage != null && originalMessage.attachments.isNotEmpty()) {
                                    val uris = originalMessage.attachments.map { Uri.parse(it) }
                                    val mediaUris = uris.filter { uri ->
                                        val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
                                        mimeType?.startsWith("image/") == true || 
                                        mimeType?.startsWith("video/") == true ||
                                        uri.path?.lowercase()?.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".mp4") } == true
                                    }
                                    if (mediaUris.isNotEmpty()) {
                                        galleryMedia = mediaUris
                                        galleryInitialIndex = if (specificUri != null) {
                                            mediaUris.indexOfFirst { it.toString() == specificUri }.coerceAtLeast(0)
                                        } else 0
                                        galleryMessage = originalMessage
                                    } else {
                                        FilePickerUtils.openFile(context, if (specificUri != null) Uri.parse(specificUri) else uris.first())
                                    }
                                }

                                highlightedMessageId = originalId
                                val lazyIndex = messageToLazyIndex[originalId]
                                if (lazyIndex != null) {
                                    scope.launch {
                                        listState.animateScrollToItem(lazyIndex)
                                    }
                                }
                            },
                            isHighlighted = highlightedMessageId == message.id,
                            onMediaClick = { mediaUris, startIndex -> 
                                galleryMedia = mediaUris 
                                galleryInitialIndex = startIndex
                                galleryMessage = message
                            },
                            onSwipeToReply = { uri ->
                                replyingTo = message
                                replyingToAttachmentUriState = uri
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}
