package com.fabxdi.dibadge.ui.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fabxdi.dibadge.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.InboxEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.InboxViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpenMessageDetail: (InboxEntity) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: InboxViewModel = viewModel()
) {
    val allItems by viewModel.allInboxItems.collectAsState(initial = emptyList())
    val pendingActionsCount by viewModel.pendingActionsCount.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") } // "All" or "Actions"
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedItems by remember { mutableStateOf(setOf<InboxEntity>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val isSelectionMode = selectedItems.isNotEmpty()

    BackHandler(enabled = true) {
        if (isSelectionMode) {
            selectedItems = emptySet()
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            // Default back action
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

    val today = remember { LocalDate.now() }

    val filteredList = remember(allItems, selectedFilter, searchQuery) {
        var list = allItems
        if (selectedFilter == "Actions") {
            list = list.filter { it.actionStatus == "pending" && it.requiredAction != "none" }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.senderName.lowercase().contains(q) ||
                        it.subject.lowercase().contains(q) ||
                        it.body.lowercase().contains(q)
            }
        }
        list
    }

    val todayItems = remember(filteredList, today) {
        filteredList.filter {
            val date = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            date.isEqual(today)
        }
    }

    val earlierItems = remember(filteredList, today) {
        filteredList.filter {
            val date = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            !date.isEqual(today)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBgColor)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                if (isSelectionMode) {
                    // Selection Mode Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedItems = emptySet() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear selection",
                                    tint = primaryTextColor
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedItems.size} selected",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Select all",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tealAccent,
                                modifier = Modifier
                                    .clickable {
                                        val deletable = filteredList.filter { it.actionStatus != "pending" || it.requiredAction == "none" }.toSet()
                                        selectedItems = deletable
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete selected",
                                    tint = primaryTextColor
                                )
                            }
                        }
                    }
                } else if (isSearchActive) {
                    // Search Bar Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = primaryTextColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = cardBgColor
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search inbox",
                                            fontSize = 15.sp,
                                            color = tabInactiveColor
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
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Standard Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Inbox",
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
                                contentDescription = "Search inbox",
                                tint = primaryTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter Pills: "All" and "Actions"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // All Pill
                        val isAllSelected = selectedFilter == "All"
                        Surface(
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { selectedFilter = "All" },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, if (isAllSelected) primaryTextColor else outlineColor)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "All",
                                    fontSize = 13.sp,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = primaryTextColor
                                )
                            }
                        }

                        // Actions Pill
                        val isActionsSelected = selectedFilter == "Actions"
                        Surface(
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { selectedFilter = "Actions" },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, if (isActionsSelected) primaryTextColor else outlineColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Actions",
                                    fontSize = 13.sp,
                                    fontWeight = if (isActionsSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = primaryTextColor
                                )
                                if (pendingActionsCount > 0) {
                                    Text(
                                        text = if (pendingActionsCount >= 100) "99+" else pendingActionsCount.toString(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tealAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, outlineColor),
                    modifier = Modifier.padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
                ) {
                    Text(
                        text = data.visuals.message,
                        fontSize = 14.sp,
                        color = primaryTextColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
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
            when {
                // Empty Inbox
                allItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_inbox),
                                contentDescription = null,
                                tint = tabInactiveColor,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Nothing here yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Messages from your organisation and requests to sign or approve will show up here",
                                fontSize = 13.sp,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Empty Actions Filter
                selectedFilter == "Actions" && filteredList.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = tabInactiveColor,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Nothing waiting for you",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Requests to sign, approve or confirm will show up here",
                                fontSize = 13.sp,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Empty Search Results
                isSearchActive && filteredList.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = tabInactiveColor,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No results",
                                fontSize = 16.sp,
                                color = primaryTextColor
                            )
                        }
                    }
                }

                // Main Inbox List
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSearchActive) {
                            item(key = "search_results_header") {
                                Text(
                                    text = "Results",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(filteredList, key = { it.id }) { item ->
                                val isItemSelected = selectedItems.contains(item)
                                InboxRowItem(
                                    item = item,
                                    isSelected = isItemSelected,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    cardBgColor = cardBgColor,
                                    outlineColor = outlineColor,
                                    tealAccent = tealAccent,
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (item.actionStatus == "pending" && item.requiredAction != "none") {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Answer this request before deleting it.")
                                                }
                                            } else {
                                                selectedItems = if (isItemSelected) selectedItems - item else selectedItems + item
                                            }
                                        } else {
                                            viewModel.markAsRead(item)
                                            onOpenMessageDetail(item)
                                        }
                                    },
                                    onLongClick = {
                                        if (item.actionStatus == "pending" && item.requiredAction != "none") {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Answer this request before deleting it.")
                                            }
                                        } else {
                                            selectedItems = selectedItems + item
                                        }
                                    }
                                )
                            }
                        } else {
                            if (todayItems.isNotEmpty()) {
                                item(key = "header_today") {
                                    Text(
                                        text = "Today",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryTextColor,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(todayItems, key = { it.id }) { item ->
                                    val isItemSelected = selectedItems.contains(item)
                                    InboxRowItem(
                                        item = item,
                                        isSelected = isItemSelected,
                                        primaryTextColor = primaryTextColor,
                                        secondaryTextColor = secondaryTextColor,
                                        cardBgColor = cardBgColor,
                                        outlineColor = outlineColor,
                                        tealAccent = tealAccent,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (item.actionStatus == "pending" && item.requiredAction != "none") {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Answer this request before deleting it.")
                                                    }
                                                } else {
                                                    selectedItems = if (isItemSelected) selectedItems - item else selectedItems + item
                                                }
                                            } else {
                                                viewModel.markAsRead(item)
                                                onOpenMessageDetail(item)
                                            }
                                        },
                                        onLongClick = {
                                            if (item.actionStatus == "pending" && item.requiredAction != "none") {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Answer this request before deleting it.")
                                                }
                                            } else {
                                                selectedItems = selectedItems + item
                                            }
                                        }
                                    )
                                }
                            }

                            if (earlierItems.isNotEmpty()) {
                                item(key = "header_earlier") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Earlier",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryTextColor,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(earlierItems, key = { it.id }) { item ->
                                    val isItemSelected = selectedItems.contains(item)
                                    InboxRowItem(
                                        item = item,
                                        isSelected = isItemSelected,
                                        primaryTextColor = primaryTextColor,
                                        secondaryTextColor = secondaryTextColor,
                                        cardBgColor = cardBgColor,
                                        outlineColor = outlineColor,
                                        tealAccent = tealAccent,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (item.actionStatus == "pending" && item.requiredAction != "none") {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Answer this request before deleting it.")
                                                    }
                                                } else {
                                                    selectedItems = if (isItemSelected) selectedItems - item else selectedItems + item
                                                }
                                            } else {
                                                viewModel.markAsRead(item)
                                                onOpenMessageDetail(item)
                                            }
                                        },
                                        onLongClick = {
                                            if (item.actionStatus == "pending" && item.requiredAction != "none") {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Answer this request before deleting it.")
                                                }
                                            } else {
                                                selectedItems = selectedItems + item
                                            }
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

    if (showDeleteConfirmDialog && selectedItems.isNotEmpty()) {
        val count = selectedItems.size
        val titleText = if (count == 1) "Delete message?" else "Delete $count messages?"

        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = titleText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deletedList = selectedItems.toList()
                        viewModel.deleteInboxItems(deletedList)
                        selectedItems = emptySet()
                        showDeleteConfirmDialog = false
                        coroutineScope.launch {
                            val msg = if (count == 1) "Message deleted" else "$count messages deleted"
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = errorColor
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryTextColor
                    )
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxRowItem(
    item: InboxEntity,
    isSelected: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    outlineColor: Color,
    tealAccent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isOrgMessage = item.type.startsWith("organisation")
    val avatarBgColor = if (isOrgMessage) cardBgColor else remember(item.senderName) { UserColorUtils.getColorForName(item.senderName) }

    val formattedTime = remember(item.timestamp) {
        val date = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        when {
            date.isEqual(today) -> Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
            date.isEqual(today.minusDays(1)) -> "Yesterday"
            date.isAfter(today.minusDays(7)) -> date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
            else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        }
    }

    val isDark = primaryTextColor.red > 0.5f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) tealAccent.copy(alpha = 0.18f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: 44dp Avatar
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (isSelected) tealAccent else avatarBgColor,
                border = if (isOrgMessage && !isSelected) BorderStroke(1.dp, outlineColor) else null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (isDark) Color(0xFF0E1114) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (isOrgMessage) {
                        Icon(
                            imageVector = Icons.Outlined.Business,
                            contentDescription = "Organisation",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = item.senderName.take(1).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Middle: Line 1 = Sender name, Line 2 = Subject
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.senderName,
                    fontSize = 16.sp,
                    fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.Normal,
                    color = if (!item.isRead) primaryTextColor else secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subject,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Time + Unread Teal Dot
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = formattedTime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
                if (!item.isRead) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = tealAccent
                    ) {}
                }
            }
        }
    }
}
