package com.fabxdi.dibadge.ui.connections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.fabxdi.dibadge.ui.home.buildSearchAnnotatedString
import com.fabxdi.dibadge.util.capitalizeName
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.fabxdi.dibadge.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Videocam
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.call.CallActivity
import com.fabxdi.dibadge.call.CallEngine
import com.fabxdi.dibadge.call.CallPermissions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.CountryData
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.ConnectionItem
import com.fabxdi.dibadge.viewmodel.ConnectionsViewModel

@Composable
fun ConnectionsScreen(
    onBack: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    onOpenChat: (ConnectionItem) -> Unit = {},
    viewModel: ConnectionsViewModel = viewModel()
) {
    val connections by viewModel.connections.collectAsState()
    val receivedRequests by viewModel.receivedRequests.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedConnectionId by remember { mutableStateOf<String?>(null) }
    val blockedPeople by viewModel.blockedPeople.collectAsState()
    var blockTarget by remember { mutableStateOf<ConnectionItem?>(null) }
    var disconnectTarget by remember { mutableStateOf<ConnectionItem?>(null) }
    var profileTarget by remember { mutableStateOf<ConnectionItem?>(null) }
    val allSuggestionsForProfile by viewModel.suggestions.collectAsState()
    var showBlockedList by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Calling a connection from the list (voice, or video from the opened card)
    var pendingCall by remember { mutableStateOf<Pair<ConnectionItem, Boolean>?>(null) }
    fun beginCall(item: ConnectionItem, video: Boolean) {
        val me = FirebaseAuth.getInstance().currentUser ?: return
        val engine = CallEngine.get(context)
        if (engine.isBusy) { CallActivity.open(context); return }
        // Same chat id the 1:1 chat uses, so the call shows up in that chat
        val key = listOf(me.uid, item.uid).sorted().joinToString("_")
        val chatId = (key.hashCode() and 0x7FFFFFFF).let { if (it == 0) 1 else it }.toString()
        FirebaseFirestore.getInstance().collection("personalGroups").document(chatId)
            .set(
                mapOf("id" to chatId, "type" to "direct", "members" to listOf(me.uid, item.uid), "createdBy" to me.uid),
                SetOptions.merge()
            )
            .addOnCompleteListener {
                engine.startCall(chatId, item.uid, me.displayName ?: "Me", capitalizeName(item.name), video)
                CallActivity.open(context)
            }
    }
    val callPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val p = pendingCall
        pendingCall = null
        val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = p?.second != true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (p != null && micOk && camOk) beginCall(p.first, p.second)
        else if (p != null) Toast.makeText(context, "Permission is needed for calls", Toast.LENGTH_SHORT).show()
    }
    fun callConnection(item: ConnectionItem, video: Boolean) {
        if (item.uid.isBlank()) return
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (video) need.add(Manifest.permission.CAMERA)
        val missing = need.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED } +
                CallPermissions.bluetoothOnce(context)
        if (missing.isEmpty()) beginCall(item, video) else { pendingCall = item to video; callPermLauncher.launch(missing.toTypedArray()) }
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    // Debounced search of people on diBadge
    LaunchedEffect(searchQuery, isSearchActive) {
        if (!isSearchActive) {
            viewModel.clearSearch()
        } else {
            delay(350)
            viewModel.searchPeople(searchQuery)
        }
    }

    BackHandler(enabled = true) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tabInactiveColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(pageBgColor)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    if (isSearchActive) {
                        // Search Bar Mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search people or organization",
                                            fontSize = 15.sp,
                                            color = secondaryTextColor
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 15.sp,
                                            color = primaryTextColor
                                        ),
                                        cursorBrush = SolidColor(tealAccent),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        isSearchActive = false
                                        searchQuery = ""
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close search",
                                        tint = primaryTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Normal Title Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Connections",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            )

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { isSearchActive = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search people",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = bottomBar
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(pageBgColor)
            ) {
                if (isSearchActive) {
                    // ==================== SEARCH MODE RESULTS ====================
                    val q = searchQuery.trim().lowercase()
                    val filteredConnected = connections.filter {
                        it.name.lowercase().contains(q) || it.organization.lowercase().contains(q)
                    }

                    if (q.isNotEmpty() && filteredConnected.isEmpty() && searchResults.isEmpty() && !searching) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No people found",
                                fontSize = 15.sp,
                                color = secondaryTextColor
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (filteredConnected.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "CONNECTED",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryTextColor,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(filteredConnected, key = { it.id }) { item ->
                                    ConnectionRow(
                                        item = item,
                                        isExpanded = false,
                                        primaryTextColor = primaryTextColor,
                                        secondaryTextColor = secondaryTextColor,
                                        cardBgColor = cardBgColor,
                                        tealAccent = tealAccent,
                                        errorColor = errorColor,
                                        onClick = {
                                            profileTarget = item
                                        },
                                        onBlock = { blockTarget = item },
                                        onDisconnect = { disconnectTarget = item },
                                        onChatClick = { onOpenChat(item) },
                                        onCallClick = { video -> callConnection(item, video) },
                                        onAvatarClick = { profileTarget = item },
                                        query = searchQuery.trim()
                                    )
                                }
                            }

                            if (searching && searchResults.isEmpty()) {
                                item {
                                    Text(
                                        text = "Searching...",
                                        fontSize = 13.sp,
                                        color = secondaryTextColor,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }

                            val connectedUids = connections.map { it.uid }.toSet()
                            val people = searchResults.filter { it.uid !in connectedUids }
                            if (people.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "PEOPLE ON DIBADGE",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryTextColor,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(people, key = { "p_" + it.uid }) { person ->
                                    val incoming = receivedRequests.firstOrNull { it.uid == person.uid }
                                    val outgoing = sentRequests.any { it.uid == person.uid }
                                    PersonSearchRow(
                                        item = person,
                                        actionLabel = when {
                                            incoming != null -> "Accept"
                                            outgoing -> "Pending"
                                            else -> "Connect"
                                        },
                                        actionEnabled = !outgoing,
                                        primaryTextColor = primaryTextColor,
                                        secondaryTextColor = secondaryTextColor,
                                        tealAccent = tealAccent,
                                        onAction = {
                                            if (incoming != null) viewModel.acceptRequest(incoming)
                                            else viewModel.sendInvite(person)
                                        },
                                        onAvatarClick = { profileTarget = person },
                                        onClick = { profileTarget = person },
                                        query = searchQuery.trim()
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ============ CHIPS: Connections / Requests / Sent, plus people you may know ============
                    val defaultTab = when {
                        receivedRequests.isNotEmpty() -> "requests"
                        connections.isNotEmpty() -> "connections"
                        sentRequests.isNotEmpty() -> "sent"
                        else -> "connections"
                    }
                    var pickedTab by remember { mutableStateOf<String?>(null) }
                    var expandedSuggestionId by remember { mutableStateOf<String?>(null) }
                    val tab = pickedTab ?: defaultTab

                    val allSuggestions by viewModel.suggestions.collectAsState()
                    val suggestions = remember(allSuggestions, connections, receivedRequests, sentRequests, blockedPeople) {
                        val known = (connections + receivedRequests + sentRequests + blockedPeople).map { it.uid }.toSet()
                        allSuggestions.filter { it.uid !in known }.take(6)
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ConnectionsChip(
                                label = "Connected",
                                count = connections.size,
                                selected = tab == "connections",
                                highlightCount = false,
                                pageBgColor = pageBgColor,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                outlineColor = outlineColor,
                                tealAccent = tealAccent,
                                onClick = { pickedTab = "connections" }
                            )
                            ConnectionsChip(
                                label = "Requests",
                                count = receivedRequests.size,
                                selected = tab == "requests",
                                highlightCount = receivedRequests.isNotEmpty(),
                                pageBgColor = pageBgColor,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                outlineColor = outlineColor,
                                tealAccent = tealAccent,
                                onClick = { pickedTab = "requests" }
                            )
                            ConnectionsChip(
                                label = "Sent",
                                count = sentRequests.size,
                                selected = tab == "sent",
                                highlightCount = false,
                                pageBgColor = pageBgColor,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                outlineColor = outlineColor,
                                tealAccent = tealAccent,
                                onClick = { pickedTab = "sent" }
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (tab) {
                                "requests" -> {
                                    if (receivedRequests.isEmpty()) {
                                        item(key = "requests_empty") {
                                            TabEmptyState(
                                                title = "No requests right now",
                                                subtitle = "When someone wants to connect, it shows up here",
                                                primaryTextColor = primaryTextColor,
                                                secondaryTextColor = secondaryTextColor
                                            )
                                        }
                                    } else {
                                        items(receivedRequests, key = { "r_" + it.id }) { item ->
                                            RequestRow(
                                                item = item,
                                                received = true,
                                                primaryTextColor = primaryTextColor,
                                                secondaryTextColor = secondaryTextColor,
                                                tealAccent = tealAccent,
                                                onAccept = { viewModel.acceptRequest(item) },
                                                onDecline = { viewModel.declineRequest(item) },
                                                onClick = { profileTarget = item }
                                            )
                                        }
                                    }
                                }

                                "sent" -> {
                                    if (sentRequests.isEmpty()) {
                                        item(key = "sent_empty") {
                                            TabEmptyState(
                                                title = "No sent requests",
                                                subtitle = "Requests you send wait here until the other person replies",
                                                primaryTextColor = primaryTextColor,
                                                secondaryTextColor = secondaryTextColor
                                            )
                                        }
                                    } else {
                                        items(sentRequests, key = { "s_" + it.id }) { item ->
                                            RequestRow(
                                                item = item,
                                                received = false,
                                                primaryTextColor = primaryTextColor,
                                                secondaryTextColor = secondaryTextColor,
                                                tealAccent = tealAccent,
                                                onClick = { profileTarget = item }
                                            )
                                        }
                                    }
                                    item(key = "sent_search") {
                                        Text(
                                            text = "Search people",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tealAccent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isSearchActive = true }
                                                .padding(vertical = 16.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }

                                else -> {
                                    if (connections.isEmpty()) {
                                        item(key = "no_connections_yet") {
                                            TabEmptyState(
                                                title = "No connections yet",
                                                subtitle = when {
                                                    receivedRequests.isNotEmpty() -> "You have ${receivedRequests.size} request${if (receivedRequests.size == 1) "" else "s"} waiting"
                                                    sentRequests.isNotEmpty() -> "Your sent requests are waiting for a reply"
                                                    else -> "Connect with people you work with"
                                                },
                                                primaryTextColor = primaryTextColor,
                                                secondaryTextColor = secondaryTextColor
                                            )
                                        }
                                    } else {
                                        items(connections, key = { it.id }) { item ->
                                            ConnectionRow(
                                                item = item,
                                                isExpanded = false,
                                                primaryTextColor = primaryTextColor,
                                                secondaryTextColor = secondaryTextColor,
                                                cardBgColor = cardBgColor,
                                                tealAccent = tealAccent,
                                                errorColor = errorColor,
                                                onClick = {
                                                    profileTarget = item
                                                },
                                                onBlock = { blockTarget = item },
                                                onDisconnect = { disconnectTarget = item },
                                                onChatClick = { onOpenChat(item) },
                                                onCallClick = { video -> callConnection(item, video) },
                                                onAvatarClick = { profileTarget = item }
                                            )
                                        }
                                    }

                                    if (suggestions.isNotEmpty()) {
                                        item(key = "header_suggestions") {
                                            Text(
                                                text = "People you may know",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = primaryTextColor,
                                                modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
                                            )
                                        }
                                        items(suggestions, key = { "sg_" + it.uid }) { person ->
                                            SuggestionRow(
                                                item = person,
                                                isExpanded = false,
                                                primaryTextColor = primaryTextColor,
                                                secondaryTextColor = secondaryTextColor,
                                                cardBgColor = cardBgColor,
                                                tealAccent = tealAccent,
                                                onClick = { profileTarget = person },
                                                onConnect = {
                                                    expandedSuggestionId = null
                                                    viewModel.sendInvite(person)
                                                },
                                                onAvatarClick = { profileTarget = person }
                                            )
                                        }
                                    }

                                    item(key = "connections_search") {
                                        Text(
                                            text = "Search people",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tealAccent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isSearchActive = true }
                                                .padding(vertical = 16.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }

                                    if (blockedPeople.isNotEmpty()) {
                                        item(key = "blocked_link") {
                                            Text(
                                                text = "Blocked people (${blockedPeople.size})",
                                                fontSize = 13.sp,
                                                color = secondaryTextColor,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showBlockedList = true }
                                                    .padding(vertical = 12.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

        // The full profile, opened by tapping anyone's photo. It uses the live copy of that person when there is one,
        // and closes by itself once a connected person is disconnected.
        profileTarget?.let { opened ->
            val live = (connections + receivedRequests + sentRequests + allSuggestionsForProfile)
                .firstOrNull { it.uid.isNotBlank() && it.uid == opened.uid }
            val person = live ?: opened
            if (opened.status == "connected" && connections.none { it.uid == opened.uid }) {
                LaunchedEffect(opened.uid) { profileTarget = null }
            } else {
                ConnectionProfileScreen(
                    item = person,
                    onBack = { profileTarget = null },
                    onChat = { profileTarget = null; onOpenChat(person) },
                    onCall = { video -> profileTarget = null; callConnection(person, video) },
                    onDisconnect = { disconnectTarget = person },
                    onAccept = { profileTarget = null; viewModel.acceptRequest(person) },
                    onDecline = { profileTarget = null; viewModel.declineRequest(person) },
                    onCancel = { profileTarget = null; viewModel.cancelSentRequest(person) },
                    onConnect = { profileTarget = null; viewModel.sendInvite(person) }
                )
            }
        }
    }

    // Confirm before disconnecting
    disconnectTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { disconnectTarget = null },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Disconnect from ${capitalizeName(target.name)}?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "You will no longer appear in each other's connections. Your chat history stays on your phone and ${capitalizeName(target.name)} is not notified. You can send a new request later.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = target
                        disconnectTarget = null
                        expandedConnectionId = null
                        viewModel.removeConnection(t)
                    }
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold, color = errorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { disconnectTarget = null }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        )
    }

    // Confirm before blocking
    blockTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Block ${capitalizeName(target.name)}?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "You will be disconnected. They won't be able to message you, send you a request, or find your account in search. You can unblock them anytime.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = target
                        blockTarget = null
                        expandedConnectionId = null
                        viewModel.blockUser(t)
                    }
                ) {
                    Text("Block", fontWeight = FontWeight.Bold, color = errorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockTarget = null }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        )
    }

    // Blocked people, with Unblock
    if (showBlockedList) {
        AlertDialog(
            onDismissRequest = { showBlockedList = false },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Blocked people",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                if (blockedPeople.isEmpty()) {
                    Text("No one is blocked.", fontSize = 14.sp, color = secondaryTextColor)
                } else {
                    Column {
                        blockedPeople.forEach { person ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = capitalizeName(person.name),
                                        fontSize = 15.sp,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (person.organization.isNotBlank()) {
                                        Text(
                                            text = person.organization,
                                            fontSize = 12.sp,
                                            color = secondaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Text(
                                    text = "Unblock",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tealAccent,
                                    modifier = Modifier
                                        .clickable { viewModel.unblockUser(person) }
                                        .padding(horizontal = 8.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedList = false }) {
                    Text("Done", fontWeight = FontWeight.Bold, color = tealAccent)
                }
            }
        )
    }
}

/** A filled chat bubble with two lines of text inside, in the app accent colour. */
@Composable
fun ChatFilledIcon(tint: Color, contentDescription: String?, size: Int = 24) {
    Canvas(
        modifier = Modifier
            .size(size.dp)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
    ) {
        val k = this.size.width / 24f
        val bubble = Path().apply {
            moveTo(5f * k, 4f * k)
            lineTo(19f * k, 4f * k)
            quadraticBezierTo(21f * k, 4f * k, 21f * k, 6f * k)
            lineTo(21f * k, 15f * k)
            quadraticBezierTo(21f * k, 17f * k, 19f * k, 17f * k)
            lineTo(10f * k, 17f * k)
            lineTo(5f * k, 21.5f * k)
            lineTo(5f * k, 17f * k)
            quadraticBezierTo(3f * k, 17f * k, 3f * k, 15f * k)
            lineTo(3f * k, 6f * k)
            quadraticBezierTo(3f * k, 4f * k, 5f * k, 4f * k)
            close()
        }
        drawPath(bubble, color = tint)
        val line = Stroke(width = 1.6f * k, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(7.5f * k, 9f * k), Offset(16.5f * k, 9f * k), strokeWidth = line.width, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(7.5f * k, 12.5f * k), Offset(13.5f * k, 12.5f * k), strokeWidth = line.width, cap = StrokeCap.Round)
    }
}

@Composable
fun ConnectionRow(
    item: ConnectionItem,
    isExpanded: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    tealAccent: Color,
    errorColor: Color,
    onClick: () -> Unit,
    onBlock: () -> Unit,
    onDisconnect: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onCallClick: (video: Boolean) -> Unit = {},
    onAvatarClick: () -> Unit = {},
    query: String = ""
) {
    val avatarBgColor = remember(item.id, item.uid) { UserColorUtils.getColorForName(item.uid.ifBlank { item.id }) }
    val countryName = remember(item.country) {
        if (item.country.isBlank()) "" else CountryData.getCountryDisplayName(item.country)
    }

    // The whole card opens: it gets a background and grows downwards
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isExpanded) cardBgColor else Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { onClick() }
                    .padding(horizontal = if (isExpanded) 12.dp else 0.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onAvatarClick() },
                    shape = CircleShape,
                    color = avatarBgColor
                ) {
                    if (item.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = item.photoUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = capitalizeName(item.name).take(1),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildSearchAnnotatedString(capitalizeName(item.name), query, primaryTextColor, searchHighlightColor()),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Open card: the location sits right under the name. Closed card: organisation, role and date instead.
                    if (isExpanded && countryName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = "Country",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = countryName,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                color = secondaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Closed card: company and role with their icons too. When open they show below.
                    if (!isExpanded && (item.organization.isNotBlank() || item.role.isNotBlank())) {
                        Box(modifier = Modifier.padding(top = 2.dp)) {
                            CompanyRoleLine(
                                organization = item.organization,
                                role = item.role,
                                textColor = secondaryTextColor,
                                iconColor = secondaryTextColor,
                                fontSize = 13.sp
                            )
                        }
                    }
                    // Closed card: the date they connected, under the organisation and role (or under the name)
                    if (!isExpanded && item.connectedAt > 0L) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "Connected " + remember(item.connectedAt) {
                                    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                                        .format(java.util.Date(item.connectedAt))
                                },
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                color = secondaryTextColor
                            )
                        }
                    }
                }

                // Closed card: chat and voice call are always one tap away
                if (!isExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onChatClick, modifier = Modifier.size(40.dp)) {
                            ChatFilledIcon(
                                tint = tealAccent,
                                contentDescription = "Chat with ${capitalizeName(item.name)}",
                                size = 24
                            )
                        }
                        IconButton(onClick = { onCallClick(false) }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Call,
                                contentDescription = "Call ${capitalizeName(item.name)}",
                                tint = tealAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)
                ) {
                    CompanyRoleLine(
                        organization = item.organization,
                        role = item.role,
                        textColor = primaryTextColor,
                        iconColor = secondaryTextColor,
                        fontSize = 15.sp
                    )
                    if (item.connectedAt > 0L) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Connected " + remember(item.connectedAt) {
                                    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                                        .format(java.util.Date(item.connectedAt))
                                },
                                fontSize = 14.sp,
                                color = secondaryTextColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    // Chat, Call and Video on one line. Tap the top of the card to close it. Disconnect is on the profile screen.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ConnectionActionPill("Chat", tealAccent, Modifier.weight(1f), onChatClick) {
                            ChatFilledIcon(tint = tealAccent, contentDescription = null, size = 15)
                        }
                        ConnectionActionPill("Call", tealAccent, Modifier.weight(1f), { onCallClick(false) }) {
                            Icon(Icons.Outlined.Call, null, tint = tealAccent, modifier = Modifier.size(15.dp))
                        }
                        ConnectionActionPill("Video", tealAccent, Modifier.weight(1f), { onCallClick(true) }) {
                            Icon(Icons.Outlined.Videocam, null, tint = tealAccent, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Outlined pill button used in the opened connection card (Chat, Call, Video). */
@Composable
internal fun ConnectionActionPill(
    label: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).clickable { onClick() },
        shape = RoundedCornerShape(15.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 12.sp, color = color, maxLines = 1)
        }
    }
}

/**
 * Shared look for received and sent requests: collapsed shows name and company | role with the
 * two actions underneath; tapping the card opens it to show country and bio too.
 */
@Composable
private fun RequestCardBody(
    item: ConnectionItem,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    firstLabel: String,
    firstColor: Color,
    firstBold: Boolean,
    firstEnabled: Boolean,
    onFirst: () -> Unit,
    secondLabel: String,
    onSecond: () -> Unit,
    onAvatarClick: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val avatarBgColor = remember(item.id, item.uid) { UserColorUtils.getColorForName(item.uid.ifBlank { item.id }) }
    val countryName = remember(item.country) {
        if (item.country.isBlank()) "" else CountryData.getCountryDisplayName(item.country)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cardBgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onAvatarClick() },
                    shape = CircleShape,
                    color = avatarBgColor
                ) {
                    if (item.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = item.photoUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = capitalizeName(item.name).take(1),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = capitalizeName(item.name),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (expanded) {
                        if (countryName.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = "Country",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = countryName, fontSize = 13.sp, color = secondaryTextColor)
                            }
                        }
                    } else {
                        CompanyRoleLine(
                            organization = item.organization,
                            role = item.role,
                            textColor = secondaryTextColor,
                            iconColor = secondaryTextColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                CompanyRoleLine(
                    organization = item.organization,
                    role = item.role,
                    textColor = primaryTextColor,
                    iconColor = secondaryTextColor,
                    fontSize = 15.sp
                )
                if (item.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = item.bio, fontSize = 14.sp, color = secondaryTextColor)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.padding(start = if (expanded) 0.dp else 58.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = firstLabel,
                        fontSize = 14.sp,
                        fontWeight = if (firstBold) FontWeight.Bold else FontWeight.Normal,
                        color = firstColor,
                        modifier = Modifier
                            .clickable(enabled = firstEnabled) { onFirst() }
                            .heightIn(min = 32.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(end = 22.dp)
                    )
                    Text(
                        text = secondLabel,
                        fontSize = 14.sp,
                        color = secondaryTextColor,
                        modifier = Modifier
                            .clickable { onSecond() }
                            .heightIn(min = 32.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }
                if (expanded) {
                    IconButton(onClick = { expanded = false }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Collapse",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A request in the Requests or Sent list. It looks like a connection row: photo, name, company | role and the
 * date it was sent. On the right: Decline and Accept for a received request, "Pending" for a sent one.
 * Tapping the row opens the full profile.
 */
@Composable
private fun RequestRow(
    item: ConnectionItem,
    received: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    onClick: () -> Unit
) {
    val avatarBgColor = remember(item.id, item.uid) { UserColorUtils.getColorForName(item.uid.ifBlank { item.id }) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(44.dp).clip(CircleShape), shape = CircleShape, color = avatarBgColor) {
            if (item.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = item.photoUrl,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = capitalizeName(item.name).take(1),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = capitalizeName(item.name),
                fontSize = 16.sp,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.organization.isNotBlank() || item.role.isNotBlank()) {
                Box(modifier = Modifier.padding(top = 2.dp)) {
                    CompanyRoleLine(
                        organization = item.organization,
                        role = item.role,
                        textColor = secondaryTextColor,
                        iconColor = secondaryTextColor,
                        fontSize = 13.sp
                    )
                }
            }
            if (item.timestamp > 0L) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = "Requested " + remember(item.timestamp) {
                            java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                                .format(java.util.Date(item.timestamp))
                        },
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        color = secondaryTextColor
                    )
                }
            }
        }

        if (received) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecline, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Decline ${capitalizeName(item.name)}",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onAccept, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Accept ${capitalizeName(item.name)}",
                        tint = tealAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else {
            Text(text = "Pending", fontSize = 13.sp, color = secondaryTextColor)
        }
    }
}

@Composable
fun ReceivedRequestCard(
    item: ConnectionItem,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    tealAccent: Color,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onAvatarClick: () -> Unit = {}
) {
    RequestCardBody(
        item = item,
        primaryTextColor = primaryTextColor,
        secondaryTextColor = secondaryTextColor,
        cardBgColor = cardBgColor,
        firstLabel = "Accept",
        firstColor = tealAccent,
        firstBold = true,
        firstEnabled = true,
        onFirst = onAccept,
        secondLabel = "Decline",
        onSecond = onDecline,
        onAvatarClick = onAvatarClick
    )
}

@Composable
fun SentRequestCard(
    item: ConnectionItem,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    onCancel: () -> Unit,
    onAvatarClick: () -> Unit = {}
) {
    RequestCardBody(
        item = item,
        primaryTextColor = primaryTextColor,
        secondaryTextColor = secondaryTextColor,
        cardBgColor = cardBgColor,
        firstLabel = "Pending",
        firstColor = primaryTextColor,
        firstBold = true,
        firstEnabled = false,
        onFirst = {},
        secondLabel = "Cancel",
        onSecond = onCancel,
        onAvatarClick = onAvatarClick
    )
}

@Composable
fun PersonSearchRow(
    item: ConnectionItem,
    actionLabel: String,
    actionEnabled: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color,
    onAction: () -> Unit,
    onAvatarClick: () -> Unit = {},
    onClick: () -> Unit = {},
    query: String = ""
) {
    val avatarBgColor = remember(item.id, item.uid) { UserColorUtils.getColorForName(item.uid.ifBlank { item.id }) }
    val personCountry = remember(item.country) {
        if (item.country.isBlank()) "" else CountryData.getCountryDisplayName(item.country)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onAvatarClick() },
            shape = CircleShape,
            color = avatarBgColor
        ) {
            if (item.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = item.photoUrl,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = capitalizeName(item.name).take(1),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildSearchAnnotatedString(capitalizeName(item.name), query, primaryTextColor, searchHighlightColor()),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (personCountry.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = "Country",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = personCountry, fontSize = 13.sp, lineHeight = 16.sp, color = secondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.organization.isNotBlank() || item.role.isNotBlank()) {
                Box(modifier = Modifier.padding(top = 2.dp)) {
                    CompanyRoleLine(
                        organization = item.organization,
                        role = item.role,
                        textColor = secondaryTextColor,
                        iconColor = secondaryTextColor,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Outlined button: blue for Connect and Accept, grey for Pending
        val buttonColor = if (actionEnabled) tealAccent else secondaryTextColor
        Surface(
            modifier = Modifier.clickable(enabled = actionEnabled) { onAction() },
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, buttonColor.copy(alpha = if (actionEnabled) 1f else 0.6f))
        ) {
            Text(
                text = actionLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = buttonColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun ConnectionsChip(
    label: String,
    count: Int,
    selected: Boolean,
    highlightCount: Boolean,
    pageBgColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    outlineColor: Color,
    tealAccent: Color,
    onClick: () -> Unit
) {
    // Same size and look as the filter chips on the Home screen (All, Unread, Groups)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) tealAccent else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) tealAccent else secondaryTextColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = count.toString(),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (highlightCount && !selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> tealAccent.copy(alpha = 0.7f)
                highlightCount -> tealAccent
                else -> secondaryTextColor
            }
        )
    }
}

@Composable
private fun TabEmptyState(
    title: String,
    subtitle: String,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = primaryTextColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = secondaryTextColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** Company and role on one line, each with a small icon: [building] Company | [briefcase] Role */
@Composable
private fun CompanyRoleLine(
    organization: String,
    role: String,
    textColor: Color,
    iconColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    if (organization.isBlank() && role.isBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (organization.isNotBlank()) {
            Icon(
                imageVector = Icons.Outlined.Business,
                contentDescription = "Company",
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = organization,
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.25f).sp,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (organization.isNotBlank() && role.isNotBlank()) {
            Text(
                text = "  |  ",
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.25f).sp,
                color = iconColor
            )
        }
        if (role.isNotBlank()) {
            Icon(
                imageVector = Icons.Outlined.WorkOutline,
                contentDescription = "Role",
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = role,
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.25f).sp,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
fun SuggestionRow(
    item: ConnectionItem,
    isExpanded: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    tealAccent: Color,
    onClick: () -> Unit,
    onConnect: () -> Unit,
    onAvatarClick: () -> Unit = {}
) {
    val avatarBgColor = remember(item.id, item.uid) { UserColorUtils.getColorForName(item.uid.ifBlank { item.id }) }
    val countryName = remember(item.country) {
        if (item.country.isBlank()) "" else CountryData.getCountryDisplayName(item.country)
    }

    // The whole card opens: it gets a background and grows downwards
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isExpanded) cardBgColor else Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { onClick() }
                    .padding(horizontal = if (isExpanded) 12.dp else 0.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onAvatarClick() },
                    shape = CircleShape,
                    color = avatarBgColor
                ) {
                    if (item.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = item.photoUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = capitalizeName(item.name).take(1),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = capitalizeName(item.name),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (isExpanded) {
                        // Expanded: country under the name
                        if (countryName.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = "Country",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = countryName, fontSize = 13.sp, color = secondaryTextColor)
                            }
                        }
                    } else {
                        // Collapsed: company | role under the name
                        CompanyRoleLine(
                            organization = item.organization,
                            role = item.role,
                            textColor = secondaryTextColor,
                            iconColor = secondaryTextColor,
                            fontSize = 12.sp
                        )
                    }
                }

                // Connect button on the right, same look as in the search results
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.clickable { onConnect() },
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, tealAccent)
                ) {
                    Text(
                        text = "Connect",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)
                ) {
                    CompanyRoleLine(
                        organization = item.organization,
                        role = item.role,
                        textColor = primaryTextColor,
                        iconColor = secondaryTextColor,
                        fontSize = 15.sp
                    )
                    if (item.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = item.bio, fontSize = 14.sp, color = secondaryTextColor)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connect",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = tealAccent,
                            modifier = Modifier
                                .clickable { onConnect() }
                                .heightIn(min = 48.dp)
                                .padding(end = 16.dp)
                                .wrapContentHeight(Alignment.CenterVertically)
                        )
                        IconButton(onClick = onClick) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Collapse",
                                tint = primaryTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The soft yellow behind the letters you typed, the same as the Home search. */
@Composable
private fun searchHighlightColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFFB300).copy(alpha = 0.35f) else Color(0xFFFFF59D)