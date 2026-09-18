package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatMessage
import com.fabxdi.dibadge.viewmodel.GroupFolderFile
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DetailActionItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
    }
}

private fun formatTimestampToDateHeader(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) {
            date.format(DateTimeFormatter.ofPattern("MMMM d"))
        } else {
            date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailsScreen(
    entry: HomeEntryEntity,
    messages: List<ChatMessage> = emptyList(),
    onBack: () -> Unit,
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

    var activeTab by remember { mutableIntStateOf(0) } // 0=Media, 1=Members, 2=Groups, 3=Forms, 4=Folders
    var selectedMediaCategory by remember { mutableStateOf("Media") } // "Media", "Docs"
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }
    var editDescriptionInput by remember { mutableStateOf("") }

    var showAddMemberDialog by remember { mutableStateOf(false) }
    var memberSearchQuery by remember { mutableStateOf("") }

    var groupCodeSearchQuery by remember { mutableStateOf("") }
    var customToastMessage by remember { mutableStateOf<String?>(null) }

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
                detailsViewModel.uploadGroupAvatarPhoto(context, uri)
            }
        }
    )

    Scaffold(
        topBar = {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
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
                    // Profile Image / Avatar (Clickable to change photo, NO camera badge)
                    Surface(
                        modifier = Modifier
                            .size(100.dp)
                            .clickable { avatarPickerLauncher.launch("image/*") },
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

                    // Group Name (Clickable -> Edit Name Popup ONLY)
                    Text(
                        text = groupName.ifBlank { "Group" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable {
                            editNameInput = groupName
                            showEditNameDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Description Text (Clickable -> Edit Description Popup ONLY)
                    Text(
                        text = groupSubtitle.ifBlank { "Add group description" },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable {
                            editDescriptionInput = groupSubtitle
                            showEditDescriptionDialog = true
                        }
                    )
                }

                // 5 ICONS IN ONE ROW (Media, Members, Groups, Forms, Folders) - Directly below description
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailActionItem(
                        icon = Icons.Outlined.PhotoLibrary,
                        label = "Media",
                        isSelected = (activeTab == 0),
                        onClick = { activeTab = 0 }
                    )
                    DetailActionItem(
                        icon = Icons.Outlined.Person,
                        label = "Members",
                        isSelected = (activeTab == 1),
                        onClick = { activeTab = 1 }
                    )
                    DetailActionItem(
                        icon = Icons.Outlined.People,
                        label = "Groups",
                        isSelected = (activeTab == 2),
                        onClick = { activeTab = 2 }
                    )
                    DetailActionItem(
                        icon = Icons.Outlined.Description,
                        label = "Forms",
                        isSelected = (activeTab == 3),
                        onClick = { activeTab = 3 }
                    )
                    DetailActionItem(
                        icon = Icons.Outlined.Folder,
                        label = "Folders",
                        isSelected = (activeTab == 4),
                        onClick = { activeTab = 4 }
                    )
                }

                // THIN DIVIDER LINE AFTER ACTION ICONS
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // GROUP CODE SECTION
                val displayCode = groupCode.ifBlank { "A7B9-K2%X" }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("group_code", displayCode)
                            clipboard.setPrimaryClip(clip)
                            customToastMessage = "Code copied"
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your group code: ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Text(
                        text = displayCode,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("group_code", displayCode)
                            clipboard.setPrimaryClip(clip)
                            customToastMessage = "Code copied"
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Code",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // SECTION CONTENT BASED ON SELECTED ICON
                if (activeTab == 0) {
                    // TAB 0: Media with 2 Floating Pill Categories ("Media" and "Docs")
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
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    rowItems.forEach { item ->
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .aspectRatio(1f)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable {
                                                                    FilePickerUtils.openFile(context, Uri.parse(item.url))
                                                                }
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

                                            filesList.forEach { file ->
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
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            when (activeTab) {
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
            } // end main Column inside Box

            // FLOATING PILL AT SCREEN BOTTOM CENTER (Media & Docs - Perfectly Centered at Bottom)
            if (activeTab == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
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

            // Custom Pure Text Toast Overlay (No Android App Icon!)
            AnimatedVisibility(
                visible = customToastMessage != null,
                modifier = Modifier.padding(bottom = 32.dp)
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
        } // end Box
    } // end else isLoading
} // end Scaffold lambda

    // Edit Group Name Popup (NO heading title, Name field ONLY)
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = null, // NO HEADING TITLE
            text = {
                Column {
                    TextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        placeholder = { 
                            Text(
                                text = "Name",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNameInput.isNotBlank()) {
                            detailsViewModel.updateGroupName(context, editNameInput)
                            showEditNameDialog = false
                        }
                    },
                    enabled = editNameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Group Description Popup (NO heading title, Description field ONLY)
    if (showEditDescriptionDialog) {
        AlertDialog(
            onDismissRequest = { showEditDescriptionDialog = false },
            title = null, // NO HEADING TITLE
            text = {
                Column {
                    TextField(
                        value = editDescriptionInput,
                        onValueChange = { editDescriptionInput = it },
                        placeholder = { 
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        detailsViewModel.updateGroupSubtitle(context, editDescriptionInput)
                        showEditDescriptionDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDescriptionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Member Dialog
    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddMemberDialog = false
                memberSearchQuery = ""
            },
            title = { Text("Add Member") },
            text = {
                Column {
                    Text(
                        text = if (isAdmin) "Search by phone number or email:" else "Search your personal connections:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = memberSearchQuery,
                        onValueChange = {
                            memberSearchQuery = it
                            detailsViewModel.searchUserToAdd(it)
                        },
                        placeholder = { Text("Phone or email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    memberSearchCandidates.forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    detailsViewModel.addMemberToGroup(candidate.uid) {
                                        showAddMemberDialog = false
                                        memberSearchQuery = ""
                                        Toast.makeText(context, "Added ${candidate.displayName}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = candidate.displayName,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false; memberSearchQuery = "" }) {
                    Text("Close")
                }
            }
        )
    }
}
}
