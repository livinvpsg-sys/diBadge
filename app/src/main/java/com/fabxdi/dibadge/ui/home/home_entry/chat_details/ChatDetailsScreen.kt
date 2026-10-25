package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
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
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatMessage
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailsScreen(
    entry: HomeEntryEntity,
    messages: List<ChatMessage> = emptyList(),
    onBack: () -> Unit,
    onReplyMedia: (String) -> Unit = {},
    onShowInChat: (String) -> Unit = {},
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

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val pageBgColor = colors.background
    val tealAccent = diBadgeColors.accent
    val isDark = primaryTextColor.red > 0.5f

    var selectedActionTab by remember { mutableStateOf<Int?>(null) } // null=None (Media default), 1=Members, 2=Groups, 3=Forms, 4=Folders
    var selectedMediaCategory by remember { mutableStateOf("Media") } // "Media", "Docs"
    var selectedMediaIds by remember { mutableStateOf(setOf<String>()) }
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
    var showGroupFoldersScreen by remember { mutableStateOf(false) }
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
            onBack = { showGroupFormsScreen = false }
        )
        return
    }

    if (showGroupFoldersScreen) {
        GroupFoldersScreen(
            groupId = entry.id.toString(),
            groupName = groupName,
            canUserManage = canUserManage,
            onBack = { showGroupFoldersScreen = false }
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
                        val allSelectedMediaItems = remember(selectedMediaIds, media, files) {
                            val list = mutableListOf<GroupMediaItem>()
                            for (id in selectedMediaIds) {
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

                        // If single selection: show Reply Icon
                        if (selectedMediaIds.size == 1) {
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

                        // Share Icon
                        IconButton(onClick = {
                            shareMultipleMediaItems(context, allSelectedMediaItems)
                            selectedMediaIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Forward Icon
                        IconButton(onClick = {
                            shareMultipleMediaItems(context, allSelectedMediaItems)
                            selectedMediaIds = emptySet()
                        }) {
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
                                if (selectedItem != null) {
                                    val mediaUrl = selectedItem.url
                                    selectedMediaIds = emptySet()
                                    onShowInChat(mediaUrl)
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
                        IconButton(
                            onClick = { showHeaderDropdown = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (isAdmin && pendingRequests.isNotEmpty()) {
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
                    // 88dp Round Avatar
                    val raisedFill = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
                    Surface(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (groupPhotoUrl.isNotBlank()) {
                                    showFullscreenAvatarViewer = true
                                } else {
                                    avatarPickerLauncher.launch("image/*")
                                }
                            },
                        shape = CircleShape,
                        color = raisedFill,
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        if (groupPhotoUrl.isNotBlank()) {
                            AsyncImage(
                                model = groupPhotoUrl,
                                contentDescription = "Group Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = groupName.take(1).uppercase(),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryTextColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Group Name (22sp Medium)
                    Text(
                        text = groupName.ifBlank { "Group" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            editNameInput = groupName
                            showEditNameDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val memberCountText = if (members.size == 1) "1 member" else "${members.size} members"
                    Text(
                        text = memberCountText,
                        fontSize = 13.sp,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { showMembersSheet = true }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Two Equal-Width Tabs: Media and Docs (44dp high)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Media Tab (Left)
                        val isMediaSelected = selectedMediaCategory == "Media"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selectedMediaCategory = "Media" },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Media",
                                    fontSize = 15.sp,
                                    fontWeight = if (isMediaSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isMediaSelected) primaryTextColor else secondaryTextColor
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(if (isMediaSelected) primaryTextColor else Color.Transparent)
                                )
                            }
                        }

                        // Docs Tab (Right)
                        val isDocsSelected = selectedMediaCategory == "Docs"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selectedMediaCategory = "Docs" },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Docs",
                                    fontSize = 15.sp,
                                    fontWeight = if (isDocsSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isDocsSelected) primaryTextColor else secondaryTextColor
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(if (isDocsSelected) primaryTextColor else Color.Transparent)
                                )
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
                                val mediaItems = media.filter { it.mediaType == "image" || it.mediaType == "video" }
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
                                                            contentScale = ContentScale.Crop
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
        ChatDetailsMenuSheet(
            isMuted = isMuted,
            canUserManage = canUserManage,
            waitingInvitesCount = pendingRequests.size,
            isConnectedToOrg = acceptedConnections.isNotEmpty(),
            onSelectMembers = { showMembersSheet = true },
            onSelectGroups = { showGroupsSheet = true },
            onSelectForms = { showGroupFormsScreen = true },
            onSelectTasks = { showTasksSheet = true },
            onSelectFolders = { showGroupFoldersScreen = true },
            onSelectMute = { showMuteSheet = true },
            onSelectUnmute = {
                isMuted = false
                customToastMessage = "Notifications unmuted"
            },
            onSelectClearChat = { showClearChatDialog = true },
            onSelectGroupCode = { showGroupCodeDialog = true },
            onDismiss = { showHeaderDropdown = false }
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
        val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName?.ifBlank { null } ?: "Livin Varghese"
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
            containerColor = cardBgColor,
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

    // Delete Media Popup
    DeleteMediaDialog(
        show = showDeleteMediaDialog,
        onDismiss = { showDeleteMediaDialog = false },
        onDeleteForMe = {
            detailsViewModel.deleteSelectedMediaForMe(context, selectedMediaIds)
            selectedMediaIds = emptySet()
            showDeleteMediaDialog = false
        },
        onDeleteForAll = {
            detailsViewModel.deleteSelectedMediaForAll(context, selectedMediaIds)
            selectedMediaIds = emptySet()
            showDeleteMediaDialog = false
        }
    )

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
