package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import kotlinx.coroutines.delay

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailsScreen(
    groupId: String,
    onBack: () -> Unit,
    detailsViewModel: GroupDetailsViewModel = viewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val tileHeight = (configuration.screenHeightDp / 4).dp.coerceAtLeast(150.dp)

    LaunchedEffect(groupId) {
        detailsViewModel.loadGroupDetails(groupId)
    }

    val groupName by detailsViewModel.groupName.collectAsState()
    val groupSubtitle by detailsViewModel.groupSubtitle.collectAsState()
    val groupCode by detailsViewModel.groupCode.collectAsState()
    val members by detailsViewModel.members.collectAsState()
    val isAdmin by detailsViewModel.isAdmin.collectAsState()
    val acceptedConnections by detailsViewModel.acceptedConnections.collectAsState()
    val pendingRequests by detailsViewModel.pendingRequests.collectAsState()
    val files by detailsViewModel.files.collectAsState()
    val isLoading by detailsViewModel.isLoading.collectAsState()
    val codeLookupResult by detailsViewModel.codeLookupResult.collectAsState()
    val memberSearchCandidates by detailsViewModel.memberSearchCandidates.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0=Members, 1=Groups, 2=Forms, 3=Folders
    var showEditDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }
    var editSubtitleInput by remember { mutableStateOf("") }

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
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // RECTANGLE TILE (1/4th screen height)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tileHeight),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Right Top Pencil Edit Icon
                        IconButton(
                            onClick = {
                                editNameInput = groupName
                                editSubtitleInput = groupSubtitle
                                showEditDialog = true
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Group Details",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Center Group Details
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                color = UserColorUtils.getColorForName(groupName)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = groupName.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = groupName.ifBlank { "Group" },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (groupSubtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = groupSubtitle,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // GROUP CODE SECTION (Without white card background)
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
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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

                // 4 ICONS IN ONE ROW (Members, Groups, Forms, Folders)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailActionItem(
                        icon = Icons.Outlined.Person,
                        label = "Members",
                        isSelected = (activeTab == 0),
                        onClick = { activeTab = 0 }
                    )
                    DetailActionItem(
                        icon = Icons.Outlined.People,
                        label = "Groups",
                        isSelected = (activeTab == 1),
                        onClick = { activeTab = 1 }
                    )
                    DetailActionItem(
                        icon = Icons.Outlined.Description,
                        label = "Forms",
                        isSelected = (activeTab == 2),
                        onClick = { activeTab = 2 }
                    )
                    DetailActionItem(
                        icon = Icons.Outlined.Folder,
                        label = "Folders",
                        isSelected = (activeTab == 3),
                        onClick = { activeTab = 3 }
                    )
                }

                // SECTION CONTENT BASED ON SELECTED ICON
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when (activeTab) {
                            0 -> {
                                // TAB 0: Members
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

                            1 -> {
                                // TAB 1: Groups (Connections)
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

                            2 -> {
                                // TAB 2: Forms
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

                            3 -> {
                                // TAB 3: Folders
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

            // Custom Pure Text Toast Overlay (No Android App Icon!)
            AnimatedVisibility(
                visible = customToastMessage != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
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

    // Edit Group Details Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Group Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editSubtitleInput,
                        onValueChange = { editSubtitleInput = it },
                        label = { Text("Details / Subtitle") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNameInput.isNotBlank()) {
                            detailsViewModel.updateGroupDetails(editNameInput, editSubtitleInput) {
                                showEditDialog = false
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
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
