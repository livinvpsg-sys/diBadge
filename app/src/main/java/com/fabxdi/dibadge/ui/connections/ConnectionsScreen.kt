package com.fabxdi.dibadge.ui.connections

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.fabxdi.dibadge.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.ConnectionItem
import com.fabxdi.dibadge.viewmodel.ConnectionsViewModel

@Composable
fun ConnectionsScreen(
    onBack: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: ConnectionsViewModel = viewModel()
) {
    val connections by viewModel.connections.collectAsState()
    val receivedRequests by viewModel.receivedRequests.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()

    var activeScreenMode by remember { mutableStateOf("connections") } // "connections" or "requests"
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedConnectionId by remember { mutableStateOf<String?>(null) }

    val pendingCount = receivedRequests.size + sentRequests.size

    // Auto-return to Screen 1 (Connections) when requests reach 0
    LaunchedEffect(pendingCount) {
        if (pendingCount == 0 && activeScreenMode == "requests") {
            activeScreenMode = "connections"
        }
    }

    BackHandler(enabled = true) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (activeScreenMode == "requests") {
            activeScreenMode = "connections"
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

                if (q.isNotEmpty() && filteredConnected.isEmpty()) {
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
                                    isExpanded = expandedConnectionId == item.id,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    cardBgColor = cardBgColor,
                                    errorColor = errorColor,
                                    onClick = {
                                        expandedConnectionId = if (expandedConnectionId == item.id) null else item.id
                                    },
                                    onRemove = { viewModel.removeConnection(item) }
                                )
                            }
                        }
                    }
                }
            } else if (activeScreenMode == "connections") {
                // ==================== SCREEN 1: CONNECTIONS VIEW ====================
                val isEmpty = connections.isEmpty() && pendingCount == 0

                if (isEmpty) {
                    // Empty State: No connections & no requests
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_connections),
                                contentDescription = null,
                                tint = tabInactiveColor,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No connections yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Search for people on diBadge to connect",
                                fontSize = 13.sp,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Search people",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tealAccent,
                                modifier = Modifier.clickable { isSearchActive = true }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Switch Card (only when pendingCount > 0)
                        if (pendingCount > 0) {
                            item(key = "switch_card_requests") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clickable { activeScreenMode = "requests" },
                                    shape = RoundedCornerShape(14.dp),
                                    color = cardBgColor
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Requests",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = primaryTextColor
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = pendingCount.toString(),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tealAccent
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "View requests",
                                                tint = secondaryTextColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // Connected People Flat Rows
                        items(connections, key = { it.id }) { item ->
                            ConnectionRow(
                                item = item,
                                isExpanded = expandedConnectionId == item.id,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                cardBgColor = cardBgColor,
                                errorColor = errorColor,
                                onClick = {
                                    expandedConnectionId = if (expandedConnectionId == item.id) null else item.id
                                },
                                onRemove = { viewModel.removeConnection(item) }
                            )
                        }
                    }
                }
            } else {
                // ==================== SCREEN 2: REQUESTS VIEW ====================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Switch Card (Back to Connections)
                    item(key = "switch_card_connections") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { activeScreenMode = "connections" },
                            shape = RoundedCornerShape(14.dp),
                            color = cardBgColor
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Connections",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = primaryTextColor
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = connections.size.toString(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tealAccent
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Back to connections",
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Section 1: Received Requests
                    if (receivedRequests.isNotEmpty()) {
                        item(key = "header_received") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Received ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryTextColor
                                )
                                Text(
                                    text = receivedRequests.size.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        items(receivedRequests, key = { it.id }) { item ->
                            ReceivedRequestCard(
                                item = item,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                cardBgColor = cardBgColor,
                                tealAccent = tealAccent,
                                onAccept = { viewModel.acceptRequest(item) },
                                onDecline = { viewModel.declineRequest(item) }
                            )
                        }
                    }

                    // Section 2: Sent Requests
                    if (sentRequests.isNotEmpty()) {
                        item(key = "header_sent") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Sent ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryTextColor
                                )
                                Text(
                                    text = sentRequests.size.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        items(sentRequests, key = { it.id }) { item ->
                            SentRequestCard(
                                item = item,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                cardBgColor = cardBgColor,
                                onCancel = { viewModel.cancelSentRequest(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionRow(
    item: ConnectionItem,
    isExpanded: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    errorColor: Color,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val avatarBgColor = remember(item.id) { UserColorUtils.getColorForName(item.id) }
    val sublineText = remember(item.organization, item.role) {
        if (item.organization.isNotBlank() && item.role.isNotBlank()) "${item.organization} • ${item.role}"
        else item.organization.ifBlank { item.role }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable { onClick() }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
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
                            text = item.name.take(1).uppercase(),
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
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sublineText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sublineText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 58.dp, bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = cardBgColor
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (item.bio.isNotBlank()) {
                        Text(
                            text = item.bio,
                            fontSize = 13.sp,
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove connection",
                            tint = errorColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
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
    onDecline: () -> Unit
) {
    val avatarBgColor = remember(item.id) { UserColorUtils.getColorForName(item.id) }
    val sublineText = remember(item.organization, item.role) {
        if (item.organization.isNotBlank() && item.role.isNotBlank()) "${item.organization} • ${item.role}"
        else item.organization.ifBlank { item.role }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cardBgColor
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
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
                                text = item.name.take(1).uppercase(),
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
                        text = item.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    if (sublineText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sublineText,
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Line inset 58dp from left
            Row(
                modifier = Modifier.padding(start = 58.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Text(
                    text = "Accept",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealAccent,
                    modifier = Modifier.clickable { onAccept() }
                )
                Text(
                    text = "Decline",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor,
                    modifier = Modifier.clickable { onDecline() }
                )
            }
        }
    }
}

@Composable
fun SentRequestCard(
    item: ConnectionItem,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    onCancel: () -> Unit
) {
    val avatarBgColor = remember(item.id) { UserColorUtils.getColorForName(item.id) }
    val sublineText = remember(item.organization, item.role) {
        if (item.organization.isNotBlank() && item.role.isNotBlank()) "${item.organization} • ${item.role}"
        else item.organization.ifBlank { item.role }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cardBgColor
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
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
                                text = item.name.take(1).uppercase(),
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
                        text = item.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                    if (sublineText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sublineText,
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Line inset 58dp from left
            Row(
                modifier = Modifier.padding(start = 58.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Text(
                    text = "Pending",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor,
                    modifier = Modifier.clickable { onCancel() }
                )
            }
        }
    }
}
