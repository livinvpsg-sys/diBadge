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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.GroupConnectionItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsBottomSheet(
    ownGroupCode: String = "7N4K-!HVE",
    connectedGroups: List<GroupConnectionItem>,
    pendingRequests: List<GroupConnectionItem>, // Incoming invitations
    canUserManage: Boolean = true, // Owner or Admin
    onCodeLookup: (String) -> Unit,
    codeLookupResult: GroupConnectionItem?,
    onSendInvite: (targetId: String, targetType: String, name: String) -> Unit,
    onAcceptInvite: (connectionId: String, name: String) -> Unit,
    onDeclineInvite: (connectionId: String) -> Unit,
    onRemoveConnection: (connectionId: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchInput by remember { mutableStateOf("") }
    var searchAttempted by remember { mutableStateOf(false) }
    var showSearchPill by remember { mutableStateOf(false) }
    var selectedConnectionForOption by remember { mutableStateOf<GroupConnectionItem?>(null) }
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error
    val isDark = primaryTextColor.red > 0.5f
    val searchBgColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)

    // Code Input Formatting: uppercase, ignore spaces, auto-dash after 4th char
    fun formatCodeInput(raw: String): String {
        val uppercase = raw.uppercase().filter { it.isLetterOrDigit() || "!@#$%&?-".contains(it) && !it.isWhitespace() }
        val clean = uppercase.replace("-", "")
        return if (clean.length > 4) {
            "${clean.substring(0, 4)}-${clean.substring(4, minOf(clean.length, 8))}"
        } else {
            clean
        }
    }

    // Process Search Execution on 9-character code entry
    LaunchedEffect(searchInput) {
        if (searchInput.length == 9) {
            searchAttempted = true
            if (searchInput.equals(ownGroupCode, ignoreCase = true) || searchInput.replace("-", "").equals(ownGroupCode.replace("-", ""), ignoreCase = true)) {
                snackbarHostState.showSnackbar("This is your own group code")
            } else {
                onCodeLookup(searchInput)
            }
        } else {
            searchAttempted = false
        }
    }

    // Filtered / Ordered Connected List: Pending outgoing invites first ("Invited"), then connected groups alphabetically
    val sortedConnectedList = remember(connectedGroups) {
        connectedGroups.sortedWith(Comparator { a, b ->
            val aIsInvited = a.otherEntityName.startsWith("Invited:") || a.otherEntityType == "invited"
            val bIsInvited = b.otherEntityName.startsWith("Invited:") || b.otherEntityType == "invited"

            if (aIsInvited != bIsInvited) {
                if (aIsInvited) -1 else 1
            } else {
                a.otherEntityName.compareTo(b.otherEntityName, ignoreCase = true)
            }
        })
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
                val countStr = if (sortedConnectedList.isEmpty()) "Groups" else "Groups (${sortedConnectedList.size})"
                Text(
                    text = countStr,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )

                if (canUserManage) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                showSearchPill = !showSearchPill
                                if (!showSearchPill) {
                                    searchInput = ""
                                    searchAttempted = false
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GroupAdd,
                            contentDescription = "Add group",
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

            Spacer(modifier = Modifier.height(4.dp))

            // Search by Group Code Only (Appears when Add button is clicked)
            if (canUserManage && showSearchPill) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = searchBgColor
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Group code",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchInput.isEmpty()) {
                                Text(
                                    text = "Enter group code",
                                    fontSize = 16.sp,
                                    color = dimColor
                                )
                            }
                            BasicTextField(
                                value = searchInput,
                                onValueChange = { raw ->
                                    if (raw.length <= 9) {
                                        searchInput = formatCodeInput(raw)
                                    }
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp,
                                    color = primaryTextColor
                                ),
                                cursorBrush = SolidColor(tealAccent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchInput.isNotEmpty()) {
                            IconButton(
                                onClick = { searchInput = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Result Row / No Match State
                if (searchAttempted && !searchInput.equals(ownGroupCode, ignoreCase = true)) {
                    if (codeLookupResult != null) {
                        val result = codeLookupResult
                        val avatarColor = remember(result.otherEntityName) { UserColorUtils.getColorForName(result.otherEntityName) }
                        val isAlreadyConnected = connectedGroups.any { it.otherEntityId == result.otherEntityId }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = avatarColor,
                                    border = BorderStroke(1.dp, outlineColor)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(result.otherEntityName.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = result.otherEntityName,
                                    fontSize = 16.sp,
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isAlreadyConnected) {
                                Text("Connected", fontSize = 15.sp, color = secondaryTextColor)
                            } else {
                                Text(
                                    text = "Invite",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tealAccent,
                                    modifier = Modifier
                                        .clickable {
                                            onSendInvite(result.otherEntityId, result.otherEntityType, result.otherEntityName)
                                            val name = result.otherEntityName
                                            searchInput = ""
                                            searchAttempted = false
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Invite sent to $name")
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = outlineColor)
                        Spacer(modifier = Modifier.height(10.dp))
                    } else {
                        // No Group Found State
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No group found", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Check the code and try again", fontSize = 13.sp, color = secondaryTextColor)
                            }
                        }
                    }
                }
            }

            // Invitations Received Section (Owner and Admins Only)
            if (canUserManage && pendingRequests.isNotEmpty()) {
                Text(
                    text = "Invitations (${pendingRequests.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )

                pendingRequests.forEach { invite ->
                    val avatarColor = remember(invite.otherEntityName) { UserColorUtils.getColorForName(invite.otherEntityName) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = avatarColor,
                                border = BorderStroke(1.dp, outlineColor)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(invite.otherEntityName.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(invite.otherEntityName, fontSize = 16.sp, color = primaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Invited your group", fontSize = 13.sp, color = secondaryTextColor)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Decline",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryTextColor,
                                modifier = Modifier
                                    .clickable {
                                        onDeclineInvite(invite.connectionId)
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Invitation declined") }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Accept",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tealAccent,
                                modifier = Modifier
                                    .clickable {
                                        onAcceptInvite(invite.connectionId, invite.otherEntityName)
                                        val name = invite.otherEntityName
                                        coroutineScope.launch { snackbarHostState.showSnackbar("$name is now connected") }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 8.dp)
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Connected Groups List / Empty State
            if (sortedConnectedList.isEmpty() && pendingRequests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Text(
                            text = "No connected groups yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter a group code to connect with a group",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (sortedConnectedList.isNotEmpty()) {
                val maxListHeight = (minOf(sortedConnectedList.size, 8) * 64).dp
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight)
                ) {
                    items(sortedConnectedList, key = { it.connectionId }) { item ->
                        val isInvitedOutgoing = item.otherEntityName.startsWith("Invited:") || item.otherEntityType == "invited"
                        val displayName = item.otherEntityName.replace("Invited:", "").trim()
                        val avatarColor = remember(displayName) { UserColorUtils.getColorForName(displayName) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable(enabled = canUserManage) {
                                    selectedConnectionForOption = item
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = avatarColor,
                                    border = BorderStroke(1.dp, outlineColor)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(displayName.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Text(displayName, fontSize = 16.sp, color = primaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isInvitedOutgoing) "Waiting for a reply" else "12 members",
                                        fontSize = 13.sp,
                                        color = secondaryTextColor
                                    )
                                }
                            }

                            if (isInvitedOutgoing) {
                                Text("Invited", fontSize = 15.sp, color = secondaryTextColor)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(start = 60.dp), thickness = 1.dp, color = outlineColor)
                    }
                }
            }
        }
    }

    // Remove / Cancel Invite Option Sheet (Second Bottom Sheet)
    if (selectedConnectionForOption != null) {
        val conn = selectedConnectionForOption!!
        val isInvitedOutgoing = conn.otherEntityName.startsWith("Invited:") || conn.otherEntityType == "invited"
        val displayName = conn.otherEntityName.replace("Invited:", "").trim()

        ModalBottomSheet(
            onDismissRequest = { selectedConnectionForOption = null },
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
                    text = displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable {
                            if (isInvitedOutgoing) {
                                onRemoveConnection(conn.connectionId, displayName)
                                selectedConnectionForOption = null
                                coroutineScope.launch { snackbarHostState.showSnackbar("Invite canceled") }
                            } else {
                                showRemoveConfirmDialog = true
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = errorColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isInvitedOutgoing) "Cancel invite" else "Remove group",
                            fontSize = 16.sp,
                            color = errorColor
                        )
                        Text(
                            text = if (isInvitedOutgoing) "Cancel connection invitation" else "Disconnect this group from your group",
                            fontSize = 13.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }
        }

        if (showRemoveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRemoveConfirmDialog = false },
                title = {
                    Text("Remove $displayName?", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                },
                text = {
                    Text("This group will be disconnected from your group. You can connect again with the group code.", fontSize = 14.sp, lineHeight = 20.sp, color = secondaryTextColor)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRemoveConfirmDialog = false
                            selectedConnectionForOption = null
                            onRemoveConnection(conn.connectionId, displayName)
                            coroutineScope.launch { snackbarHostState.showSnackbar("$displayName removed") }
                        }
                    ) {
                        Text("Remove", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = errorColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveConfirmDialog = false }) {
                        Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Normal, color = secondaryTextColor)
                    }
                },
                containerColor = cardBgColor,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
