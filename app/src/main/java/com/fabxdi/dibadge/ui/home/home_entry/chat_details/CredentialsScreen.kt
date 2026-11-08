package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.DocEntity
import com.fabxdi.dibadge.ui.components.DocumentTypeTile
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.viewmodel.MyCredentialViewModel
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.GroupMember
import com.fabxdi.dibadge.viewmodel.GroupCredentialItem
import com.fabxdi.dibadge.viewmodel.GroupCredentialRequestItem
import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(
    groupId: String,
    groupName: String,
    canUserManage: Boolean,
    members: List<GroupMember>,
    onBack: () -> Unit,
    detailsViewModel: GroupDetailsViewModel = viewModel(),
    docViewModel: MyCredentialViewModel = viewModel()
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    LaunchedEffect(groupId) {
        detailsViewModel.listenToCredentials(groupId)
        detailsViewModel.listenToCredentialRequests(groupId)
    }

    val credentials by detailsViewModel.credentials.collectAsState()
    val credentialRequests by detailsViewModel.credentialRequests.collectAsState()
    val myDocsList by docViewModel.allDocuments.collectAsState(initial = emptyList())

    var showRequestSheet by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedCertForViewer by remember { mutableStateOf<GroupCredentialItem?>(null) }
    var certToEdit by remember { mutableStateOf<GroupCredentialItem?>(null) }
    var certToRemove by remember { mutableStateOf<GroupCredentialItem?>(null) }
    var requestToFulfill by remember { mutableStateOf<GroupCredentialRequestItem?>(null) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error
    val isDark = diBadgeColors.isDark

    // Filter visible credentials: Admins see all, normal members see ONLY their own
    val visibleCredentials = remember(credentials, canUserManage, currentUserId) {
        if (canUserManage) credentials else credentials.filter { it.ownerUid == currentUserId }
    }

    // Requests addressed to current user
    val myPendingRequests = remember(credentialRequests, currentUserId) {
        credentialRequests.filter { it.memberUid == currentUserId && it.status == "pending" }
    }

    // Group credentials and pending requests by member UID using Members list order
    val memberRows = remember(visibleCredentials, credentialRequests, members, currentUserId, canUserManage) {
        val certMap = visibleCredentials.groupBy { it.ownerUid }
        val reqMap = if (canUserManage) credentialRequests.filter { it.status == "pending" }.groupBy { it.memberUid } else emptyMap()

        val result = mutableListOf<Triple<GroupMember, List<GroupCredentialItem>, List<GroupCredentialRequestItem>>>()

        members.forEach { member ->
            val userCerts = certMap[member.uid] ?: emptyList()
            val userReqs = reqMap[member.uid] ?: emptyList()
            if (userCerts.isNotEmpty() || userReqs.isNotEmpty()) {
                result.add(Triple(member, userCerts, userReqs))
            }
        }

        // Add any remaining members who have credentials but not in members list
        certMap.forEach { (uid, certList) ->
            if (result.none { it.first.uid == uid }) {
                val dummyMember = GroupMember(
                    uid = uid,
                    displayName = certList.firstOrNull()?.ownerName ?: "User",
                    isYou = uid == currentUserId
                )
                val userReqs = reqMap[uid] ?: emptyList()
                result.add(Triple(dummyMember, certList, userReqs))
            }
        }

        result
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "Credentials",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor,
                        modifier = Modifier.weight(1f)
                    )

                    if (canUserManage) {
                        Text(
                            text = "Request",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = tealAccent,
                            modifier = Modifier
                                .clickable { showRequestSheet = true }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = "Add",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable { showAddSheet = true }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            if (memberRows.isEmpty() && myPendingRequests.isEmpty()) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No credentials yet",
                        fontSize = 15.sp,
                        color = secondaryTextColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Add credential",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable { showAddSheet = true }
                            .padding(8.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // "Requested from you" section for members
                    if (!canUserManage && myPendingRequests.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Requested from you",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    myPendingRequests.forEach { req ->
                                        PendingRequestCardItem(
                                            request = req,
                                            isAdmin = false,
                                            cardBgColor = cardBgColor,
                                            outlineColor = outlineColor,
                                            primaryTextColor = primaryTextColor,
                                            secondaryTextColor = secondaryTextColor,
                                            errorColor = errorColor,
                                            onRequestClick = { requestToFulfill = req },
                                            onRemindClick = {},
                                            onCancelClick = {}
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Members grouped credentials and pending requests
                    memberRows.forEach { (member, certList, reqList) ->
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Member Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = CircleShape,
                                        color = cardBgColor,
                                        border = BorderStroke(1.dp, outlineColor)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = member.displayName.take(1).uppercase(),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = primaryTextColor
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    val nameText = if (member.uid == currentUserId) "You" else member.displayName
                                    Text(
                                        text = nameText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryTextColor,
                                        modifier = Modifier.weight(1f)
                                    )

                                    val countText = if (certList.isEmpty() && reqList.isNotEmpty()) {
                                        "Waiting"
                                    } else if (certList.size == 1) {
                                        "1 credential"
                                    } else {
                                        "${certList.size} credentials"
                                    }

                                    Text(
                                        text = countText,
                                        fontSize = 12.sp,
                                        color = secondaryTextColor,
                                        fontWeight = if (certList.isEmpty() && reqList.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Fulfilled Credentials List
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    certList.forEach { cert ->
                                        CredentialItemCard(
                                            cert = cert,
                                            isOwn = cert.ownerUid == currentUserId,
                                            isDark = isDark,
                                            cardBgColor = cardBgColor,
                                            outlineColor = outlineColor,
                                            primaryTextColor = primaryTextColor,
                                            secondaryTextColor = secondaryTextColor,
                                            errorColor = errorColor,
                                            onCertClick = { selectedCertForViewer = cert },
                                            onEditClick = { certToEdit = cert },
                                            onRemoveClick = { certToRemove = cert }
                                        )
                                    }

                                    // Admin view of pending requests for member
                                    if (canUserManage) {
                                        reqList.forEach { req ->
                                            PendingRequestCardItem(
                                                request = req,
                                                isAdmin = true,
                                                cardBgColor = cardBgColor,
                                                outlineColor = outlineColor,
                                                primaryTextColor = primaryTextColor,
                                                secondaryTextColor = secondaryTextColor,
                                                errorColor = errorColor,
                                                onRequestClick = {},
                                                onRemindClick = {
                                                    Toast.makeText(context, "Reminder sent to ${req.memberName}", Toast.LENGTH_SHORT).show()
                                                },
                                                onCancelClick = {
                                                    detailsViewModel.cancelCredentialRequest(groupId, req.id)
                                                    Toast.makeText(context, "Request cancelled", Toast.LENGTH_SHORT).show()
                                                }
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
    }

    // Request Credentials Sheet
    if (showRequestSheet) {
        RequestCredentialSheet(
            members = members,
            isDark = isDark,
            onRequestCreated = { certName, targetMembers ->
                detailsViewModel.createCredentialRequests(
                    groupId = groupId,
                    certName = certName,
                    targetMembers = targetMembers
                )
                Toast.makeText(context, "Request sent to ${targetMembers.size} members", Toast.LENGTH_SHORT).show()
                showRequestSheet = false
            },
            onDismiss = { showRequestSheet = false }
        )
    }

    // Add / Manual Upload Credential Sheet
    if (showAddSheet) {
        AddCredentialSheet(
            myDocsList = myDocsList,
            members = members,
            isAdmin = canUserManage,
            isDark = isDark,
            onAddCredential = { doc, certName, expiresAtMs, targetMember ->
                if (canUserManage && targetMember != null) {
                    detailsViewModel.addCredentialForMember(
                        groupId = groupId,
                        targetMemberUid = targetMember.uid,
                        targetMemberName = targetMember.displayName,
                        myDocId = doc.id,
                        fileUri = doc.files.firstOrNull() ?: "",
                        name = certName,
                        expiresAtMs = expiresAtMs
                    )
                } else {
                    detailsViewModel.addCredential(
                        groupId = groupId,
                        myDocId = doc.id,
                        fileUri = doc.files.firstOrNull() ?: "",
                        name = certName,
                        expiresAtMs = expiresAtMs
                    )
                }
                Toast.makeText(context, "Credential added", Toast.LENGTH_SHORT).show()
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }

    // Fulfill Credential Sheet for Member
    if (requestToFulfill != null) {
        val targetReq = requestToFulfill!!
        FulfillCredentialSheet(
            request = targetReq,
            myDocsList = myDocsList,
            isDark = isDark,
            onFulfill = { certItems ->
                detailsViewModel.submitMultipleCredentials(
                    groupId = groupId,
                    targetMemberUid = currentUserId,
                    targetMemberName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Me",
                    certItems = certItems,
                    requestId = targetReq.id
                )
                Toast.makeText(context, "Submitted ${certItems.size} credentials", Toast.LENGTH_SHORT).show()
                requestToFulfill = null
            },
            onDismiss = { requestToFulfill = null }
        )
    }

    // Edit Credential Sheet
    if (certToEdit != null) {
        val targetCert = certToEdit!!
        EditCredentialSheet(
            cert = targetCert,
            isDark = isDark,
            onSave = { newName, newExpiresAtMs ->
                detailsViewModel.updateCredential(
                    groupId = groupId,
                    certId = targetCert.id,
                    newName = newName,
                    newExpiresAtMs = newExpiresAtMs
                )
                Toast.makeText(context, "Credential updated", Toast.LENGTH_SHORT).show()
                certToEdit = null
            },
            onDismiss = { certToEdit = null }
        )
    }

    // Confirm Remove Dialog
    if (certToRemove != null) {
        val targetCert = certToRemove!!
        AlertDialog(
            onDismissRequest = { certToRemove = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = cardBgColor,
            title = {
                Text("Remove from group?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
            },
            text = {
                Text(
                    "Remove credential from this group? The file stays in your My Credentials.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        detailsViewModel.removeCredential(groupId, targetCert.id)
                        Toast.makeText(context, "Credential removed", Toast.LENGTH_SHORT).show()
                        certToRemove = null
                    }
                ) {
                    Text("Remove", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = errorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { certToRemove = null }) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }
            }
        )
    }

    // Fullscreen Document Viewer
    if (selectedCertForViewer != null) {
        val cert = selectedCertForViewer!!
        if (cert.fileUri.isNotBlank()) {
            FilePickerUtils.openFile(context, Uri.parse(cert.fileUri))
            selectedCertForViewer = null
        }
    }
}

@Composable
private fun CredentialItemCard(
    cert: GroupCredentialItem,
    isOwn: Boolean,
    isDark: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    errorColor: Color,
    onCertClick: () -> Unit,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val expiryDate = remember(cert.expiresAt) {
        cert.expiresAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    val isExpired = remember(expiryDate, today) {
        expiryDate != null && today.isAfter(expiryDate)
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCertClick() },
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ext = cert.fileUri.substringAfterLast('.', "pdf").lowercase()
            DocumentTypeTile(extension = ext, isDark = isDark, widthDp = 28, heightDp = 34)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cert.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val expiryText = when {
                    expiryDate == null -> "No expiry date"
                    isExpired -> "Expires ${expiryDate.format(dateFormatter)} · Expired"
                    else -> "Expires ${expiryDate.format(dateFormatter)}"
                }

                val subline = if (!cert.addedByName.isNullOrBlank()) {
                    "$expiryText · Added by ${cert.addedByName}"
                } else {
                    expiryText
                }

                Text(
                    text = subline,
                    fontSize = 12.sp,
                    color = if (isExpired) errorColor else secondaryTextColor,
                    fontWeight = if (isExpired) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            if (isOwn) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit name or expiry") },
                            onClick = {
                                showMenu = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from group", color = errorColor) },
                            onClick = {
                                showMenu = false
                                onRemoveClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingRequestCardItem(
    request: GroupCredentialRequestItem,
    isAdmin: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    errorColor: Color,
    onRequestClick: () -> Unit,
    onRemindClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRequestClick() },
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocumentTypeTile(extension = "pdf", isDark = true, widthDp = 28, heightDp = 34)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Requested",
                    fontSize = 12.sp,
                    color = secondaryTextColor
                )
            }

            if (isAdmin) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Send reminder") },
                            onClick = {
                                showMenu = false
                                onRemindClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel request", color = errorColor) },
                            onClick = {
                                showMenu = false
                                onCancelClick()
                            }
                        )
                    }
                }
            }
        }
    }
}