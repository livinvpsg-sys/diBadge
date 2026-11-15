package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.my_forms.GroupFormsScreen
import com.fabxdi.dibadge.viewmodel.ConnectionsViewModel
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.GroupTaskViewModel
import com.google.firebase.auth.FirebaseAuth

/**
 * Shows the Members / Groups / Tasks sheet, or the Announcements / Forms / Credentials / Library screen, straight over the chat screen.
 * No group details screen is composed, so nothing flashes.
 * action: "announcements" | "members" | "groups" | "tasks" | "forms" | "credentials" | "library"
 */
@Composable
fun ChatMenuSheetHost(
    entry: HomeEntryEntity,
    action: String,
    onClose: () -> Unit,
    onOpenMyForms: () -> Unit = {},
    detailsViewModel: GroupDetailsViewModel = viewModel()
) {
    val context = LocalContext.current
    val groupTaskViewModel: GroupTaskViewModel = viewModel()

    LaunchedEffect(entry.id) {
        detailsViewModel.loadGroupDetails(
            groupId = entry.id.toString(),
            initialName = entry.title,
            initialSubtitle = entry.subtitle
        )
    }

    val groupName by detailsViewModel.groupName.collectAsState()
    val groupCode by detailsViewModel.groupCode.collectAsState()
    val members by detailsViewModel.members.collectAsState()
    val isAdmin by detailsViewModel.isAdmin.collectAsState()
    val acceptedConnections by detailsViewModel.acceptedConnections.collectAsState()
    val pendingRequests by detailsViewModel.pendingRequests.collectAsState()
    val codeLookupResult by detailsViewModel.codeLookupResult.collectAsState()
    val groupTasks by groupTaskViewModel.getTasksForGroup(entry.id.toString())
        .collectAsState(initial = emptyList())

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "me"
    val resolvedUserRole = remember(members, currentUserId, isAdmin) {
        resolveUserRole(members = members, currentUserId = currentUserId, isAdmin = isAdmin)
    }
    val canUserManage = remember(resolvedUserRole) { canUserAssignTasks(resolvedUserRole) }

    var showAddMember by remember { mutableStateOf(false) }

    when (action) {
        "announcements", "forms", "credentials", "library" -> {
            BackHandler { onClose() }
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (action) {
                    "announcements" -> AnnouncementsScreen(
                        groupId = entry.id.toString(),
                        groupName = groupName,
                        canUserManage = canUserManage,
                        members = members,
                        currentUserRole = resolvedUserRole,
                        onBack = onClose
                    )
                    "forms" -> GroupFormsScreen(
                        groupId = entry.id.toString(),
                        groupName = groupName,
                        canUserManage = canUserManage,
                        onBack = onClose,
                        onOpenMyForms = { onClose(); onOpenMyForms() }
                    )
                    "credentials" -> CredentialsScreen(
                        groupId = entry.id.toString(),
                        groupName = groupName,
                        canUserManage = canUserManage,
                        members = members,
                        onBack = onClose
                    )
                    else -> GroupLibraryScreen(
                        groupId = entry.id.toString(),
                        groupName = groupName,
                        canUserManage = canUserManage,
                        onBack = onClose
                    )
                }
            }
        }

        "members" -> if (!showAddMember) {
            val userMember = members.find { it.uid == currentUserId || it.isYou }
            val userRole = userMember?.role?.lowercase() ?: (if (isAdmin) "owner" else "member")
            MembersBottomSheet(
                groupName = groupName,
                members = members,
                currentUserId = currentUserId,
                currentRole = userRole,
                onAddPeopleClick = { showAddMember = true },
                onRoleChange = { memberId, newRole ->
                    detailsViewModel.updateMemberRole(context, memberId, newRole)
                },
                onTransferOwnership = { newOwnerId ->
                    detailsViewModel.transferGroupOwnership(context, newOwnerId)
                },
                onExitGroup = {
                    detailsViewModel.leaveGroup(context) { onClose() }
                },
                onDismiss = onClose
            )
        }

        "groups" -> GroupsBottomSheet(
            ownGroupCode = groupCode,
            connectedGroups = acceptedConnections,
            pendingRequests = pendingRequests,
            canUserManage = canUserManage,
            onCodeLookup = { code -> detailsViewModel.lookupGroupCode(code) },
            codeLookupResult = codeLookupResult,
            onSendInvite = { targetId, targetType, name ->
                detailsViewModel.sendConnectRequest(targetId, targetType) {
                    Toast.makeText(context, "Invite sent to $name", Toast.LENGTH_SHORT).show()
                }
            },
            onAcceptInvite = { connectionId, name ->
                detailsViewModel.updateConnectionStatus(connectionId, "accepted")
                Toast.makeText(context, "$name is now connected", Toast.LENGTH_SHORT).show()
            },
            onDeclineInvite = { connectionId ->
                detailsViewModel.updateConnectionStatus(connectionId, "declined")
                Toast.makeText(context, "Invitation declined", Toast.LENGTH_SHORT).show()
            },
            onRemoveConnection = { connectionId, name ->
                detailsViewModel.updateConnectionStatus(connectionId, "removed")
                Toast.makeText(context, "$name removed", Toast.LENGTH_SHORT).show()
            },
            onDismiss = onClose
        )

        "tasks" -> {
            val currentUserName =
                FirebaseAuth.getInstance().currentUser?.displayName?.ifBlank { null } ?: "Me"
            TasksBottomSheet(
                groupId = entry.id.toString(),
                tasks = groupTasks,
                members = members,
                currentRole = resolvedUserRole,
                currentUserName = currentUserName,
                currentUserUid = currentUserId,
                linkedGroups = acceptedConnections
                    .filter { it.otherEntityType == "personalGroup" && it.otherEntityId != entry.id.toString() }
                    .map { LinkedGroupOption(id = it.otherEntityId, name = it.otherEntityName) },
                loadLinkedMembers = { gid -> fetchGroupMembersForTask(gid) },
                onAssignTaskConfirmed = { input ->
                    groupTaskViewModel.assignTasks(
                        title = input.title,
                        groupId = entry.id.toString(),
                        assignees = input.assignees,
                        assignerName = currentUserName,
                        assignerUid = currentUserId,
                        dueDate = input.dueDate,
                        dueTime = input.dueTime,
                        note = input.note,
                        priority = input.priority,
                        remindBeforeMinutes = input.remindBeforeMinutes,
                        checklist = input.checklist
                    )
                },
                onToggleChecklistItem = { task, index ->
                    groupTaskViewModel.toggleChecklistItem(entry.id.toString(), task.id, index)
                },
                onMarkDone = { task, done ->
                    groupTaskViewModel.updateTaskCompletion(
                        groupId = entry.id.toString(),
                        taskId = task.id,
                        isCompleted = done,
                        userName = currentUserName,
                        userUid = currentUserId,
                        onUpdated = {}
                    )
                },
                onDismiss = onClose
            )
        }
    }

    if (showAddMember) {
        val connectionsViewModel: ConnectionsViewModel = viewModel()
        val userConnections by connectionsViewModel.connections.collectAsState()
        val existingUids = remember(members) { members.map { it.uid }.toSet() }
        val existingNames = remember(members) { members.map { it.displayName.lowercase() }.toSet() }
        val candidates = remember(userConnections, existingUids, existingNames) {
            userConnections
                .filter { c ->
                    val uid = c.uid.ifBlank { c.id }
                    !existingUids.contains(uid) && !existingNames.contains(c.name.lowercase())
                }
                .map { c ->
                    AddConnectionCandidate(
                        id = c.uid.ifBlank { c.id },
                        name = c.name,
                        email = c.organization.ifBlank { c.role }
                    )
                }
        }
        AddPeopleSheet(
            candidates = candidates,
            totalConnectionsCount = userConnections.size,
            onAddSelected = { added ->
                showAddMember = false
                for (cand in added) detailsViewModel.addMemberToGroup(cand.id)
                if (added.isNotEmpty()) {
                    Toast.makeText(context, "Added ${added.size} member(s)", Toast.LENGTH_SHORT).show()
                }
                onClose()
            },
            onDismiss = { showAddMember = false; onClose() }
        )
    }
}