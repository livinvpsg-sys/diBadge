package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.GroupMember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.viewmodel.ConnectionsViewModel
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersBottomSheet(
    groupName: String,
    members: List<GroupMember>,
    currentUserId: String = "me",
    currentRole: String = "admin", // "owner", "admin", "member"
    onAddPeopleClick: () -> Unit,
    onRoleChange: (memberId: String, newRole: String) -> Unit,
    onTransferOwnership: (newOwnerId: String) -> Unit,
    onExitGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var selectedMemberForOptions by remember { mutableStateOf<GroupMember?>(null) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showAddPeopleSheet by remember { mutableStateOf(false) }
    var invitedMemberIds by remember { mutableStateOf(setOf<String>()) }
    var connectedMemberIds by remember { mutableStateOf(setOf<String>()) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val errorColor = colors.error
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val isDark = primaryTextColor.red > 0.5f

    val isUserOwner = currentRole.equals("owner", ignoreCase = true)
    val isUserAdmin = currentRole.equals("admin", ignoreCase = true) || isUserOwner
    val canUserManage = isUserOwner || isUserAdmin

    // Auto-clear warning message after 4 seconds
    LaunchedEffect(warningMessage) {
        if (warningMessage != null) {
            delay(4000)
            warningMessage = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .animateContentSize(animationSpec = tween(200))
        ) {
            // Header Row (48dp high)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Members (${members.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )

                if (canUserManage) {
                    Row(
                        modifier = Modifier
                            .clickable { showAddPeopleSheet = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAdd,
                            contentDescription = "Add member",
                            tint = primaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        )
                    }
                }
            }

            // Red Warning Message Line under header (if Owner or Admin taps Exit)
            if (warningMessage != null) {
                Text(
                    text = warningMessage!!,
                    fontSize = 13.sp,
                    color = errorColor,
                    textAlign = TextAlign.End,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Member Rows List (Capped at max 8 visible rows height ~ 512dp)
            val maxListHeight = (minOf(members.size, 8) * 64).dp
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight)
            ) {
                items(members, key = { it.id }) { member ->
                    val isSelf = member.id == currentUserId || member.name.contains("(You)", ignoreCase = true)
                    val isOwner = member.role.equals("owner", ignoreCase = true)
                    val isAdmin = member.role.equals("admin", ignoreCase = true)
                    val isConnected = connectedMemberIds.contains(member.id)
                    val isInvited = invitedMemberIds.contains(member.id)

                    val roleLabel = when {
                        isOwner -> "Owner"
                        isAdmin -> "Admin"
                        else -> null
                    }

                    val avatarColor = remember(member.name) { UserColorUtils.getColorForName(member.name) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable(enabled = canUserManage) {
                                if (isSelf) {
                                    if (isAdmin && !isOwner) {
                                        selectedMemberForOptions = member
                                    }
                                } else if (!isOwner || isUserOwner) {
                                    selectedMemberForOptions = member
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 44dp Avatar
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = avatarColor,
                            border = BorderStroke(1.dp, outlineColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = member.name.take(1).uppercase(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Name + Subline Role
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSelf && !member.name.contains("(You)")) "${member.name} (You)" else member.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (roleLabel != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = roleLabel,
                                    fontSize = 13.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        // For signed-in user's own row: show "Exit group" on the same line as member name
                        if (isSelf) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val exitColor = if (isUserOwner || isUserAdmin) dimColor else errorColor
                            Text(
                                text = "Exit group",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = exitColor,
                                modifier = Modifier
                                    .clickable {
                                        when {
                                            isUserOwner -> {
                                                warningMessage = "Transfer ownership to another member to exit this group"
                                            }
                                            isUserAdmin -> {
                                                warningMessage = "Remove your admin role to exit this group"
                                            }
                                            else -> {
                                                showExitConfirmDialog = true
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }

                        // Invite / Invited Text Action (Only for non-self, non-connected members)
                        if (!isSelf && !isConnected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isInvited) {
                                Text(
                                    text = "Invited",
                                    fontSize = 15.sp,
                                    color = secondaryTextColor
                                )
                            } else {
                                Text(
                                    text = "Invite",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tealAccent,
                                    modifier = Modifier
                                        .clickable {
                                            invitedMemberIds = invitedMemberIds + member.id
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Invite sent to ${member.name}")
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp),
                        thickness = 1.dp,
                        color = outlineColor
                    )
                }
            }
        }
    }

    // Member Options Sheet (Second Bottom Sheet over Members Sheet)
    if (selectedMemberForOptions != null) {
        val member = selectedMemberForOptions!!
        val isSelf = member.id == currentUserId || member.name.contains("(You)", ignoreCase = true)
        val isAdmin = member.role.equals("admin", ignoreCase = true)
        val isOwner = member.role.equals("owner", ignoreCase = true)

        var showTransferConfirmDialog by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { selectedMemberForOptions = null },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = cardBgColor,
            scrimColor = Color.Black.copy(alpha = 0.55f),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = member.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 1: Make Admin / Remove Admin
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable {
                            val newRole = if (isAdmin) "member" else "admin"
                            onRoleChange(member.id, newRole)
                            val nameStr = member.name
                            selectedMemberForOptions = null
                            coroutineScope.launch {
                                val msg = if (newRole == "admin") "$nameStr is now an admin" else "$nameStr is now a member"
                                snackbarHostState.showSnackbar(msg)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AdminPanelSettings,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isAdmin) "Remove admin" else "Make admin",
                            fontSize = 16.sp,
                            color = primaryTextColor
                        )
                        Text(
                            text = if (isAdmin) "Back to a regular member" else "Can add people and manage this group",
                            fontSize = 13.sp,
                            color = secondaryTextColor
                        )
                    }
                }

                // Option 2 (Owner Only): Transfer Ownership
                if (isUserOwner && !isSelf && !isOwner) {
                    HorizontalDivider(thickness = 1.dp, color = outlineColor)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clickable {
                                showTransferConfirmDialog = true
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Transfer ownership",
                                fontSize = 16.sp,
                                color = primaryTextColor
                            )
                            Text(
                                text = "Make ${member.name} the owner of this group",
                                fontSize = 13.sp,
                                color = secondaryTextColor
                            )
                        }
                    }
                }
            }
        }

        if (showTransferConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showTransferConfirmDialog = false },
                title = {
                    Text("Transfer ownership?", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                },
                text = {
                    Text("${member.name} becomes the owner. You stay in the group as an admin.", fontSize = 14.sp, color = secondaryTextColor)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showTransferConfirmDialog = false
                            selectedMemberForOptions = null
                            onTransferOwnership(member.id)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("${member.name} is now the owner")
                            }
                        }
                    ) {
                        Text("Transfer", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTransferConfirmDialog = false }) {
                        Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Normal, color = secondaryTextColor)
                    }
                },
                containerColor = cardBgColor,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = {
                Text("Exit this group?", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
            },
            text = {
                Text(
                    text = "You will stop receiving messages from this group. An owner or admin can add you back.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        onDismiss()
                        onExitGroup()
                    }
                ) {
                    Text("Exit", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = errorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Normal, color = secondaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showAddPeopleSheet) {
        val connectionsViewModel: ConnectionsViewModel = viewModel()
        val detailsViewModel: GroupDetailsViewModel = viewModel()
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
                showAddPeopleSheet = false
                val count = added.size
                if (count > 0) {
                    for (candidate in added) {
                        detailsViewModel.addMemberToGroup(candidate.id)
                    }
                    val msg = if (count == 1) "1 person added" else "$count people added"
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            },
            onDismiss = { showAddPeopleSheet = false }
        )
    }
}
