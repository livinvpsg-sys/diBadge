package com.fabxdi.dibadge.ui.my_forms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.ui.home.QrScannerScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.GroupMember
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectSignersSheet(
    isMultiple: Boolean = true,
    members: List<GroupMember>,
    linkedGroupMembers: Map<String, List<GroupMember>> = emptyMap(),
    initialSelected: List<FormSigner>,
    onDone: (List<FormSigner>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSigners by remember(initialSelected) { mutableStateOf(initialSelected) }
    var addedOutsidePeople by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var scannedFromQr by remember { mutableStateOf(false) }
    var showUserQrScanner by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUserId = currentUser?.uid ?: "me"
    val currentUserName = currentUser?.displayName?.ifBlank { null } ?: "Applicant"

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val isDark = primaryTextColor.red > 0.5f

    val canSend = selectedSigners.isNotEmpty()

    val searchTrimmed = searchQuery.trim()
    val isSearching = searchTrimmed.isNotBlank()

    val allKnownMembers = remember(members, linkedGroupMembers) {
        (members + linkedGroupMembers.values.flatten()).distinctBy { it.uid }
    }

    val exactMatchMember = remember(allKnownMembers, searchTrimmed) {
        if (!isSearching) null
        else {
            val q = searchTrimmed.lowercase()
            allKnownMembers.find {
                it.badgeNumber.lowercase() == q || (it.email.isNotBlank() && it.email.lowercase() == q)
            } ?: if (q.startsWith("db-") || q.contains("@")) {
                GroupMember(uid = "found_$q", displayName = "User $searchTrimmed", badgeNumber = searchTrimmed)
            } else null
        }
    }

    // Partition members into Selected vs Unselected
    val selectedUids = remember(selectedSigners) { selectedSigners.map { it.uid }.toSet() }
    val isApplicantSelected = remember(selectedUids, currentUserId) { selectedUids.contains(currentUserId) }

    val unselectedThisGroup = remember(members, selectedUids, currentUserId) {
        members.filter { !selectedUids.contains(it.uid) && it.uid != currentUserId }
    }

    val unselectedLinkedGroups = remember(linkedGroupMembers, selectedUids, currentUserId) {
        linkedGroupMembers.mapValues { (_, list) ->
            list.filter { !selectedUids.contains(it.uid) && it.uid != currentUserId }
        }.filterValues { it.isNotEmpty() }
    }

    if (showUserQrScanner) {
        QrScannerScreen(
            onBack = { showUserQrScanner = false },
            onConnectionRequestSent = { uid ->
                showUserQrScanner = false
                val foundMember = allKnownMembers.find { it.uid == uid } ?: GroupMember(uid = uid, displayName = "Scanned User", badgeNumber = "DB-$uid")
                val isAlreadySelected = selectedSigners.any { it.uid == foundMember.uid }

                if (isAlreadySelected) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("${foundMember.name} is already selected")
                    }
                } else {
                    scannedFromQr = true
                    searchQuery = foundMember.badgeNumber.ifBlank { foundMember.uid }
                }
            }
        )
        return
    }

    fun handleAddFoundPerson(member: GroupMember) {
        val isInGroupList = allKnownMembers.any { it.uid == member.uid }
        val isAlreadySelected = selectedSigners.any { it.uid == member.uid }

        if (isAlreadySelected) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("${member.name} is already selected")
            }
            searchQuery = ""
            scannedFromQr = false
            return
        }

        if (isInGroupList) {
            selectedSigners = if (isMultiple) {
                selectedSigners + FormSigner(uid = member.uid, name = member.name)
            } else {
                listOf(FormSigner(uid = member.uid, name = member.name, isSupervisor = true))
            }
            coroutineScope.launch {
                snackbarHostState.showSnackbar("${member.name} selected")
            }
        } else {
            if (isMultiple) {
                if (!addedOutsidePeople.any { it.uid == member.uid }) {
                    addedOutsidePeople = addedOutsidePeople + member
                }
                selectedSigners = selectedSigners + FormSigner(uid = member.uid, name = member.name)
            } else {
                addedOutsidePeople = listOf(member)
                selectedSigners = listOf(FormSigner(uid = member.uid, name = member.name, isSupervisor = true))
            }
            coroutineScope.launch {
                snackbarHostState.showSnackbar(if (isMultiple) "${member.name} added" else "${member.name} selected")
            }
        }

        searchQuery = ""
        scannedFromQr = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                // 1. Title (No Subtitle)
                Text(
                    text = if (isMultiple) "Select members" else "Select member",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Search Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Badge number or email", fontSize = 15.sp, color = dimColor)
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    scannedFromQr = false
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = primaryTextColor),
                                cursorBrush = SolidColor(tealAccent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; scannedFromQr = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = secondaryTextColor, modifier = Modifier.size(18.dp))
                            }
                        }

                        IconButton(onClick = { showUserQrScanner = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = "Scan user QR",
                                tint = tealAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.60f))

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearching) {
                    if (exactMatchMember != null) {
                        val member = exactMatchMember
                        val isInGroup = allKnownMembers.any { it.uid == member.uid }
                        val avatarColor = remember(member.name) { UserColorUtils.getColorForName(member.name) }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = colors.background,
                            border = BorderStroke(1.dp, outlineColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = avatarColor) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(member.name.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(member.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                                        val secondaryStr = if (scannedFromQr) {
                                            if (isInGroup) "Scanned from QR" else "Scanned from QR · Not in this group"
                                        } else {
                                            val subDetail = listOfNotNull(member.badgeNumber.ifBlank { null }, member.email.ifBlank { null }).joinToString(" · ")
                                            subDetail.ifBlank { if (isInGroup) "Member" else "Not in this group" }
                                        }
                                        Text(secondaryStr, fontSize = 12.sp, color = secondaryTextColor)
                                    }
                                }

                                Text(
                                    text = "Add",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tealAccent,
                                    modifier = Modifier
                                        .clickable { handleAddFoundPerson(member) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No user found", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = primaryTextColor, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Check the badge number or email", fontSize = 13.sp, color = secondaryTextColor, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    // Search hint text
                    Text(
                        text = "Search by badge number or email",
                        fontSize = 13.sp,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Names are not searched, so only the exact person is shown",
                        fontSize = 12.sp,
                        color = dimColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        // SELECTED (n) Section at Top
                        if (selectedSigners.isNotEmpty()) {
                            item {
                                val selectedTitle = if (isMultiple) "SELECTED (${selectedSigners.size})" else "SELECTED"
                                Text(
                                    text = selectedTitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(selectedSigners, key = { "selected_${it.uid}" }) { signer ->
                                val avatarColor = remember(signer.name) { UserColorUtils.getColorForName(signer.name) }
                                val isInThisGroup = members.any { it.uid == signer.uid }
                                val memberInfo = allKnownMembers.find { it.uid == signer.uid }
                                val badgeText = memberInfo?.badgeNumber ?: ""
                                val displayNameStr = if (signer.uid == currentUserId) "${signer.name} (You)" else signer.name

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clickable {
                                            if (isMultiple) {
                                                selectedSigners = selectedSigners.filter { it.uid != signer.uid }
                                                addedOutsidePeople = addedOutsidePeople.filter { it.uid != signer.uid }
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = avatarColor) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(signer.name.take(1).uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(displayNameStr, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val subLine = if (isInThisGroup || signer.uid == currentUserId) {
                                                if (badgeText.isNotBlank()) badgeText else "Member"
                                            } else {
                                                if (badgeText.isNotBlank()) "$badgeText · Not in this group" else "Not in this group"
                                            }
                                            Text(subLine, fontSize = 12.sp, color = secondaryTextColor)
                                        }
                                    }

                                    if (isMultiple) {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = tealAccent,
                                            border = BorderStroke(0.dp, tealAccent)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = if (isDark) Color(0xFF0E1114) else Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            border = BorderStroke(2.dp, tealAccent)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = tealAccent) {}
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(thickness = 1.dp, color = outlineColor)
                            }

                            item { Spacer(modifier = Modifier.height(12.dp)) }
                        }

                        // YOU Section (shown when applicant is not currently selected)
                        if (!isApplicantSelected) {
                            item {
                                Text(
                                    text = "YOU",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            item {
                                val avatarColor = remember(currentUserName) { UserColorUtils.getColorForName(currentUserName) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clickable {
                                            if (isMultiple) {
                                                selectedSigners = selectedSigners + FormSigner(uid = currentUserId, name = currentUserName)
                                            } else {
                                                selectedSigners = listOf(FormSigner(uid = currentUserId, name = currentUserName, isSupervisor = true))
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = avatarColor) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(currentUserName.take(1).uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text("$currentUserName (You)", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                                    }

                                    if (isMultiple) {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Transparent,
                                            border = BorderStroke(2.dp, secondaryTextColor)
                                        ) {}
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            border = BorderStroke(2.dp, secondaryTextColor)
                                        ) {}
                                    }
                                }

                                HorizontalDivider(thickness = 1.dp, color = outlineColor)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // THIS GROUP Section (unselected members)
                        if (unselectedThisGroup.isNotEmpty()) {
                            item {
                                Text(
                                    text = "THIS GROUP (${unselectedThisGroup.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(unselectedThisGroup, key = { "this_${it.uid}" }) { member ->
                                val avatarColor = remember(member.name) { UserColorUtils.getColorForName(member.name) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clickable {
                                            if (isMultiple) {
                                                selectedSigners = selectedSigners + FormSigner(uid = member.uid, name = member.name)
                                            } else {
                                                selectedSigners = listOf(FormSigner(uid = member.uid, name = member.name, isSupervisor = true))
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = avatarColor) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(member.name.take(1).uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(member.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (member.badgeNumber.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(member.badgeNumber, fontSize = 12.sp, color = secondaryTextColor)
                                            }
                                        }
                                    }

                                    if (isMultiple) {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Transparent,
                                            border = BorderStroke(2.dp, secondaryTextColor)
                                        ) {}
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            border = BorderStroke(2.dp, secondaryTextColor)
                                        ) {}
                                    }
                                }

                                HorizontalDivider(thickness = 1.dp, color = outlineColor)
                            }
                        }

                        // Linked Group Sections (unselected members)
                        unselectedLinkedGroups.forEach { (groupName, linkedMembers) ->
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "${groupName.uppercase()} (${linkedMembers.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(linkedMembers, key = { "linked_${groupName}_${it.uid}" }) { member ->
                                val avatarColor = remember(member.name) { UserColorUtils.getColorForName(member.name) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clickable {
                                            if (isMultiple) {
                                                selectedSigners = selectedSigners + FormSigner(uid = member.uid, name = member.name)
                                            } else {
                                                selectedSigners = listOf(FormSigner(uid = member.uid, name = member.name, isSupervisor = true))
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = avatarColor) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(member.name.take(1).uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(member.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (member.badgeNumber.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(member.badgeNumber, fontSize = 12.sp, color = secondaryTextColor)
                                            }
                                        }
                                    }

                                    if (isMultiple) {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Transparent,
                                            border = BorderStroke(2.dp, secondaryTextColor)
                                        ) {}
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            border = BorderStroke(2.dp, secondaryTextColor)
                                        ) {}
                                    }
                                }

                                HorizontalDivider(thickness = 1.dp, color = outlineColor)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Send Request Bar
                val sendText = when {
                    !isMultiple -> "Send request"
                    selectedSigners.isEmpty() -> "Send request"
                    else -> "Send request (${selectedSigners.size})"
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sendText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSend) tealAccent else dimColor,
                        modifier = Modifier
                            .clickable(enabled = canSend) {
                                onDone(selectedSigners)
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
