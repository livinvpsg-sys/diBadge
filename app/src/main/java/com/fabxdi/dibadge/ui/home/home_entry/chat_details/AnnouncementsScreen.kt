package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.ui.components.DiBadgeFab
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.AnnouncementItem
import com.fabxdi.dibadge.viewmodel.AnnouncementViewModel
import com.fabxdi.dibadge.viewmodel.GroupMember
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun dayText(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun dayTimeText(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.getDefault()))

/**
 * Announcements for one group chat. Everyone can read them and confirm with "Got it".
 * Only the owner and admins (canUserManage) see the + button, the ⋮ menu and can delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(
    groupId: String,
    groupName: String,
    canUserManage: Boolean,
    members: List<GroupMember>,
    currentUserRole: String,
    onBack: () -> Unit,
    viewModel: AnnouncementViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val me = FirebaseAuth.getInstance().currentUser
    val myUid = me?.uid ?: ""
    val myName = me?.displayName?.ifBlank { null } ?: "Me"

    LaunchedEffect(groupId) { viewModel.start(groupId) }
    val items by viewModel.items.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    // Re-check once in a while so an announcement moves to Previous when its time runs out
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    var showNew by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var readSheetId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<AnnouncementItem?>(null) }

    if (showNew) {
        NewAnnouncementScreen(
            onClose = { showNew = false },
            onPost = { text, mustAck, days ->
                viewModel.post(
                    groupId = groupId,
                    text = text,
                    authorName = myName,
                    authorRole = currentUserRole,
                    mustAck = mustAck,
                    autoEndDays = days
                ) { ok ->
                    if (ok) Toast.makeText(context, "Announcement posted", Toast.LENGTH_SHORT).show()
                }
                showNew = false
            }
        )
        return
    }

    val ongoing = items.filter { it.isOngoing(now) }
    val previous = items.filterNot { it.isOngoing(now) }
    val memberUids = remember(members) { members.map { it.uid }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Announcements",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            if (loaded && items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Campaign,
                        contentDescription = null,
                        tint = diBadgeColors.tabInactive,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No announcements yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (canUserManage) "Tap + to post one to the group" else "Notices from the owner and admins show up here",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    if (ongoing.isNotEmpty()) {
                        item(key = "h_ongoing") { SectionLabel("ONGOING") }
                        items(ongoing, key = { it.id }) { a ->
                            AnnouncementCard(
                                item = a,
                                ongoing = true,
                                expanded = expandedId == a.id,
                                canManage = canUserManage,
                                myUid = myUid,
                                memberUids = memberUids,
                                onToggle = { expandedId = if (expandedId == a.id) null else a.id },
                                onGotIt = { viewModel.markRead(groupId, a.id) },
                                onSeeWhoRead = { readSheetId = a.id },
                                onEndNow = {
                                    viewModel.endNow(groupId, a.id)
                                    Toast.makeText(context, "Moved to Previous", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = { deleteTarget = a }
                            )
                        }
                    }
                    if (previous.isNotEmpty()) {
                        item(key = "h_previous") { SectionLabel("PREVIOUS") }
                        items(previous, key = { it.id }) { a ->
                            AnnouncementCard(
                                item = a,
                                ongoing = false,
                                expanded = expandedId == a.id,
                                canManage = canUserManage,
                                myUid = myUid,
                                memberUids = memberUids,
                                onToggle = { expandedId = if (expandedId == a.id) null else a.id },
                                onGotIt = { viewModel.markRead(groupId, a.id) },
                                onSeeWhoRead = { readSheetId = a.id },
                                onEndNow = {},
                                onDelete = { deleteTarget = a }
                            )
                        }
                    }
                }
            }

            if (canUserManage) {
                DiBadgeFab(
                    onClick = { showNew = true },
                    contentDescription = "New announcement",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }

    val sheetItem = readSheetId?.let { id -> items.find { it.id == id } }
    if (sheetItem != null) {
        ReadStatusSheet(
            item = sheetItem,
            members = members,
            onDismiss = { readSheetId = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this announcement?") },
            text = { Text("It will be removed for everyone in the group.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(groupId, target.id)
                    if (expandedId == target.id) expandedId = null
                    if (readSheetId == target.id) readSheetId = null
                    deleteTarget = null
                }) { Text("Delete", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
    )
}

/**
 * Collapsed: a fixed-height card with one line of text. Tap the card to open it, tap again (or the ^) to close.
 * "Got it" and the ⋮ menu have their own taps and do not open or close the card.
 */
@Composable
private fun AnnouncementCard(
    item: AnnouncementItem,
    ongoing: Boolean,
    expanded: Boolean,
    canManage: Boolean,
    myUid: String,
    memberUids: Set<String>,
    onToggle: () -> Unit,
    onGotIt: () -> Unit,
    onSeeWhoRead: () -> Unit,
    onEndNow: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dc = DiBadgeTheme.colors

    val iRead = item.readAt.containsKey(myUid)
    val readCount = memberUids.count { item.readAt.containsKey(it) }
    val total = memberUids.size.coerceAtLeast(1)
    var menuOpen by remember { mutableStateOf(false) }

    val roleLabel = when (item.authorRole.lowercase()) {
        "owner" -> " (Owner)"
        "admin" -> " (Admin)"
        else -> ""
    }
    val endedText = "Ended ${dayText(item.endedOn())}"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, if (ongoing) dc.accent.copy(alpha = 0.55f) else colors.outline)
    ) {
        Column(
            modifier = Modifier
                .clickable { onToggle() }
                .then(if (expanded) Modifier.heightIn(min = 96.dp) else Modifier.height(96.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = if (expanded) Arrangement.spacedBy(8.dp) else Arrangement.SpaceBetween
        ) {
            // Who and when, with the admin menu on the right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ongoing) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = dc.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "${item.authorName}$roleLabel · ${dayText(item.createdAt)}",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (canManage) {
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (item.mustAck) {
                                DropdownMenuItem(
                                    text = { Text("See who read") },
                                    onClick = { menuOpen = false; onSeeWhoRead() }
                                )
                            }
                            if (ongoing) {
                                DropdownMenuItem(
                                    text = { Text("End now") },
                                    onClick = { menuOpen = false; onEndNow() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = colors.error) },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
            }

            // The message: one line when collapsed, everything when open
            Text(
                text = item.text,
                fontSize = 14.sp,
                color = colors.onBackground,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )

            // Status line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    !item.mustAck -> {
                        Text(
                            text = "No confirmation needed",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (!ongoing) Text(endedText, fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                    canManage && ongoing -> {
                        Text("Read $readCount of $total", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(10.dp))
                        LinearProgressIndicator(
                            progress = { readCount.toFloat() / total },
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = dc.accentFill,
                            trackColor = colors.outline.copy(alpha = 0.4f)
                        )
                    }
                    canManage -> {
                        Text(
                            text = "Read by $readCount of $total",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(endedText, fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                    iRead -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.onBackground,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "You read this",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        if (!ongoing) Text(endedText, fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                    ongoing -> {
                        Text(
                            text = "Please confirm you have read this",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onGotIt,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = dc.accentFill,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Got it", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Text(
                            text = "Not confirmed",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(endedText, fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                }
            }

            if (expanded) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Collapse",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/** Admin view: who has read the announcement and who has not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadStatusSheet(
    item: AnnouncementItem,
    members: List<GroupMember>,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dc = DiBadgeTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val readers = members.filter { item.readAt.containsKey(it.uid) }
        .sortedByDescending { item.readAt[it.uid] ?: 0L }
    val pending = members.filter { !item.readAt.containsKey(it.uid) }
    val total = members.size.coerceAtLeast(1)
    var tab by remember { mutableIntStateOf(if (pending.isNotEmpty()) 0 else 1) }
    val shown = if (tab == 0) pending else readers

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Read by ${readers.size} of ${members.size}",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { readers.size.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = dc.accentFill,
                trackColor = colors.outline.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text("Pending ${pending.size}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = dc.accentFill,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text("Read ${readers.size}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = dc.accentFill,
                        selectedLabelColor = Color.White
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (shown.isEmpty()) {
                Text(
                    text = if (tab == 0) "Everyone has read this" else "No one has read this yet",
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(shown, key = { it.uid }) { m ->
                        val name = m.displayName.replace("(You)", "", ignoreCase = true).trim()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(dc.well),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name.take(1).uppercase().ifBlank { "?" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                fontSize = 15.sp,
                                color = colors.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (tab == 0) "Not yet" else dayTimeText(item.readAt[m.uid] ?: 0L),
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

private val AUTO_END_CHOICES = listOf(
    0 to "Never",
    1 to "1 day",
    3 to "3 days",
    7 to "7 days",
    30 to "30 days"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewAnnouncementScreen(
    onClose: () -> Unit,
    onPost: (text: String, mustAck: Boolean, autoEndDays: Int) -> Unit
) {
    BackHandler { onClose() }

    val colors = MaterialTheme.colorScheme
    val dc = DiBadgeTheme.colors

    var text by rememberSaveable { mutableStateOf("") }
    var mustAck by rememberSaveable { mutableStateOf(true) }
    var autoEndDays by rememberSaveable { mutableIntStateOf(7) }
    var daysMenuOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "New announcement",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.onBackground
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { onPost(text.trim(), mustAck, autoEndDays) },
                        enabled = text.isNotBlank(),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dc.accentFill,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .height(36.dp)
                    ) {
                        Text("Post", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "MESSAGE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp)
            )
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 500) text = it },
                placeholder = { Text("Write the announcement") },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                supportingText = {
                    Text(
                        text = "${text.length} / 500",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = dc.accent,
                    unfocusedBorderColor = colors.outline,
                    cursorColor = dc.accent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 150.dp)
                    .focusRequester(focusRequester)
            )

            Text(
                text = "OPTIONS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { mustAck = !mustAck }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Members must acknowledge", fontSize = 15.sp, color = colors.onBackground)
                    Text(
                        text = "Shows a Got it button and a read count",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }
                Switch(
                    checked = mustAck,
                    onCheckedChange = { mustAck = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = dc.accentFill,
                        checkedThumbColor = Color.White,
                        checkedBorderColor = dc.accentFill
                    )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-unpin after", fontSize = 15.sp, color = colors.onBackground)
                    Text(
                        text = "Then it moves to Previous",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = dc.well,
                        modifier = Modifier.clickable { daysMenuOpen = true }
                    ) {
                        Text(
                            text = AUTO_END_CHOICES.first { it.first == autoEndDays }.second,
                            fontSize = 13.sp,
                            color = colors.onBackground,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                    DropdownMenu(expanded = daysMenuOpen, onDismissRequest = { daysMenuOpen = false }) {
                        AUTO_END_CHOICES.forEach { (days, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { autoEndDays = days; daysMenuOpen = false }
                            )
                        }
                    }
                }
            }

            Text(
                text = "Only the owner and admins can post announcements",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            )
        }
    }
}