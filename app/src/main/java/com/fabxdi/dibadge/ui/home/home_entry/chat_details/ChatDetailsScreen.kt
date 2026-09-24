package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatMessage
import com.fabxdi.dibadge.viewmodel.GroupFolderFile
import com.fabxdi.dibadge.viewmodel.GroupMediaItem
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
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
    var memberSearchQuery by remember { mutableStateOf("") }

    var groupCodeSearchQuery by remember { mutableStateOf("") }
    var customToastMessage by remember { mutableStateOf<String?>(null) }
    var showHeaderDropdown by remember { mutableStateOf(false) }
    var showGroupCodeDialog by remember { mutableStateOf(false) }

    var pendingAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var avatarRotationAngle by remember { mutableFloatStateOf(0f) }
    var showAvatarCropDialog by remember { mutableStateOf(false) }
    var showFullscreenAvatarViewer by remember { mutableStateOf(false) }

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
                    title = { }, // No title in header as requested
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
                        Box {
                            IconButton(onClick = { showHeaderDropdown = true }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Default.MoreVert, 
                                    contentDescription = "Menu Options",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DropdownMenu(
                                expanded = showHeaderDropdown,
                                onDismissRequest = { showHeaderDropdown = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Group code",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    onClick = {
                                        showHeaderDropdown = false
                                        showGroupCodeDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val categories = listOf("Media", "Docs")
                        categories.forEach { category ->
                            val isCategorySelected = (selectedMediaCategory == category)
                            Text(
                                text = category,
                                fontSize = 14.sp,
                                fontWeight = if (isCategorySelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCategorySelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.clickable { selectedMediaCategory = category }
                            )
                        }
                    }
                }
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
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // HEADER SECTION (Moved UP, NO camera icon badge, NO "Group 0 members" line)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Image / Avatar (Clickable to view photo or pick new photo)
                    Surface(
                        modifier = Modifier
                            .size(100.dp)
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
                        color = UserColorUtils.getColorForName(entry.id.toString())
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
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Group Name (Clickable -> Edit Name Popup ONLY, NO flashing box)
                    Text(
                        text = groupName.ifBlank { "Group" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            editNameInput = groupName
                            showEditNameDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Description Text (Clickable -> Edit Description Popup ONLY, NO flashing box)
                    val displaySubtitle = if (groupSubtitle == "test") "" else groupSubtitle
                    Text(
                        text = displaySubtitle.ifBlank { "Add group description" },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            editDescriptionInput = displaySubtitle
                            showEditDescriptionDialog = true
                        }
                    )
                }

                // 4 ACTION ICONS IN ONE ROW (Members, Groups, Forms, Folders)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailActionItem(
                        painter = painterResource(id = R.drawable.ic_members),
                        label = "Members",
                        isSelected = (selectedActionTab == 1),
                        onClick = { selectedActionTab = if (selectedActionTab == 1) null else 1 }
                    )
                    DetailActionItem(
                        painter = painterResource(id = R.drawable.ic_groups),
                        label = "Groups",
                        isSelected = (selectedActionTab == 2),
                        onClick = { selectedActionTab = if (selectedActionTab == 2) null else 2 }
                    )
                    DetailActionItem(
                        icon = Icons.Outlined.Description,
                        label = "Forms",
                        isSelected = (selectedActionTab == 3),
                        onClick = { selectedActionTab = if (selectedActionTab == 3) null else 3 }
                    )
                    DetailActionItem(
                        icon = Icons.Outlined.Folder,
                        label = "Folders",
                        isSelected = (selectedActionTab == 4),
                        onClick = { selectedActionTab = if (selectedActionTab == 4) null else 4 }
                    )
                }

                // IF AN ACTION ICON IS SELECTED: SHOW CARD LIST ABOVE MEDIA
                if (selectedActionTab != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            when (selectedActionTab) {
                                1 -> {
                                    // TAB 1: Members
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Members (${members.size})",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        TextButton(onClick = { showAddMemberDialog = true }) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Member", modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    members.forEach { member ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(36.dp),
                                                shape = CircleShape,
                                                color = UserColorUtils.getColorForName(member.displayName)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = member.displayName.take(1).uppercase(),
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = member.displayName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (member.email.isNotBlank() || member.phoneNumber.isNotBlank()) {
                                                    Text(
                                                        text = member.email.ifBlank { member.phoneNumber },
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    }
                                }

                                2 -> {
                                    // TAB 2: Groups (Connections)
                                    Text(
                                        text = "Connected Groups",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = groupCodeSearchQuery,
                                        onValueChange = {
                                            groupCodeSearchQuery = it
                                            detailsViewModel.lookupGroupCode(it)
                                        },
                                        placeholder = { Text("Paste group code (e.g. ABCD-1234)") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    if (codeLookupResult != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = codeLookupResult!!.otherEntityName,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Type: ${codeLookupResult!!.otherEntityType}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        detailsViewModel.sendConnectRequest(
                                                            targetEntityId = codeLookupResult!!.otherEntityId,
                                                            targetEntityType = codeLookupResult!!.otherEntityType,
                                                            onDone = {
                                                                groupCodeSearchQuery = ""
                                                                Toast.makeText(context, "Connect request sent!", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                ) {
                                                    Text("Connect")
                                                }
                                            }
                                        }
                                    }

                                    if (pendingRequests.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Pending Incoming Requests",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        pendingRequests.forEach { req ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = req.otherEntityName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                Row {
                                                    IconButton(onClick = { detailsViewModel.updateConnectionStatus(req.connectionId, "accepted") }) {
                                                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                    IconButton(onClick = { detailsViewModel.updateConnectionStatus(req.connectionId, "declined") }) {
                                                        Icon(Icons.Default.Close, contentDescription = "Decline", tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (acceptedConnections.isEmpty()) {
                                        Text(
                                            text = "No connected groups yet.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        acceptedConnections.forEach { conn ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = conn.otherEntityName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                IconButton(onClick = { detailsViewModel.updateConnectionStatus(conn.connectionId, "removed") }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        }
                                    }
                                }

                                3 -> {
                                    // TAB 3: Forms
                                    Text(
                                        text = "Forms",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Organization connected forms will appear here once connected.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                4 -> {
                                    // TAB 4: Folders
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Folders (${files.size})",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Button(onClick = { filePickerLauncher.launch("*/*") }) {
                                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Upload")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (files.isEmpty()) {
                                        Text(
                                            text = "No files uploaded to this group folder yet.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        files.forEach { file ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        FilePickerUtils.openFile(context, Uri.parse(file.downloadUrl))
                                                    }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.InsertDriveFile,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = file.name,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // THIN DIVIDER LINE AFTER ACTION ICONS
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

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
        } // end Box
    } // end else isLoading

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

    // Group Code Popup
    GroupCodeDialog(
        show = showGroupCodeDialog,
        groupCode = groupCode,
        onDismiss = { showGroupCodeDialog = false },
        onCopy = { code ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("group_code", code)
            clipboard.setPrimaryClip(clip)
            showGroupCodeDialog = false
            customToastMessage = "Code copied"
        }
    )

    // Add Member Dialog
    AddMemberDialog(
        show = showAddMemberDialog,
        isAdmin = isAdmin,
        searchQuery = memberSearchQuery,
        onQueryChange = {
            memberSearchQuery = it
            detailsViewModel.searchUserToAdd(it)
        },
        candidates = memberSearchCandidates,
        onAddCandidate = { candidate ->
            detailsViewModel.addMemberToGroup(candidate.uid) {
                showAddMemberDialog = false
                memberSearchQuery = ""
                Toast.makeText(context, "Added ${candidate.displayName}", Toast.LENGTH_SHORT).show()
            }
        },
        onDismiss = {
            showAddMemberDialog = false
            memberSearchQuery = ""
        }
    )

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
