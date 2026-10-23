package com.fabxdi.dibadge.ui.my_docs

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.DocEntity
import com.fabxdi.dibadge.ui.calendar.AttachmentFile
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.DocViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDocsScreen(
    onBack: () -> Unit,
    viewModel: DocViewModel = viewModel()
) {
    val context = LocalContext.current
    val allDocs by viewModel.allDocuments.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedDocs by remember { mutableStateOf(setOf<DocEntity>()) }

    var activeDocForDetail by remember { mutableStateOf<DocEntity?>(null) }
    var docToEdit by remember { mutableStateOf<DocEntity?>(null) }
    var initialFileForNewDoc by remember { mutableStateOf<AttachmentFile?>(null) }
    var showNewDocForm by remember { mutableStateOf(false) }

    var showAddDocSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val isSelectionMode = selectedDocs.isNotEmpty()

    BackHandler(enabled = true) {
        if (showNewDocForm) {
            showNewDocForm = false
            docToEdit = null
            initialFileForNewDoc = null
        } else if (activeDocForDetail != null) {
            activeDocForDetail = null
        } else if (isSelectionMode) {
            selectedDocs = emptySet()
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

    val today = remember { LocalDate.now() }

    // Pick Photo Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = AttachmentFile(uri.toString(), "Photo", 1_200_000L, false)
            initialFileForNewDoc = file
            showNewDocForm = true
        }
    }

    // Pick PDF Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = AttachmentFile(uri.toString(), "Document.pdf", 350_000L, true)
            initialFileForNewDoc = file
            showNewDocForm = true
        }
    }

    // Filter & Sort Documents by Urgency
    val sortedDocs = remember(allDocs, searchQuery, today) {
        var list = allDocs
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                        it.number.lowercase().contains(q) ||
                        it.type.lowercase().contains(q)
            }
        }

        // Sorting: Expired -> Expires Soon -> Valid -> No Expiry
        list.sortedWith(Comparator { a, b ->
            val statusScoreA = getStatusScore(a, today)
            val statusScoreB = getStatusScore(b, today)

            if (statusScoreA != statusScoreB) {
                statusScoreA.compareTo(statusScoreB)
            } else {
                if (a.noExpiry && b.noExpiry) {
                    a.name.compareTo(b.name, ignoreCase = true)
                } else if (a.expiryDate != null && b.expiryDate != null) {
                    a.expiryDate.compareTo(b.expiryDate)
                } else {
                    a.name.compareTo(b.name, ignoreCase = true)
                }
            }
        })
    }

    if (showNewDocForm) {
        NewDocFormScreen(
            docToEdit = docToEdit,
            initialFile = initialFileForNewDoc,
            onSave = { savedDoc ->
                viewModel.saveDocument(savedDoc)
                showNewDocForm = false
                docToEdit = null
                initialFileForNewDoc = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Document saved")
                }
            },
            onClose = {
                showNewDocForm = false
                docToEdit = null
                initialFileForNewDoc = null
            }
        )
        return
    }

    if (activeDocForDetail != null) {
        DocDetailsScreen(
            doc = activeDocForDetail!!,
            onBack = { activeDocForDetail = null },
            onEdit = {
                docToEdit = activeDocForDetail
                activeDocForDetail = null
                showNewDocForm = true
            },
            onDeleteConfirmed = {
                val targetDoc = activeDocForDetail!!
                viewModel.deleteDocument(targetDoc)
                activeDocForDetail = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Document deleted")
                }
            },
            onShare = {
                showShareSheet = true
            }
        )
        return
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
                    // Selection Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedDocs = emptySet() },
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
                                text = "${selectedDocs.size} selected",
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
                                    .clickable { selectedDocs = sortedDocs.toSet() }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showShareSheet = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share selected",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete selected",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Standard Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = primaryTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "My Docs",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            color = primaryTextColor
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && !showNewDocForm && activeDocForDetail == null) {
                Surface(
                    onClick = { showAddDocSheet = true },
                    modifier = Modifier
                        .padding(bottom = 28.dp, end = 4.dp)
                        .size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add document",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
        ) {
            // Search Bar
            if (!isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(14.dp),
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
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search",
                                    fontSize = 16.sp,
                                    color = tabInactiveColor
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    color = primaryTextColor
                                ),
                                cursorBrush = SolidColor(tealAccent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // List / Empty State
            if (sortedDocs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Badge,
                            contentDescription = null,
                            tint = tabInactiveColor,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Keep your documents together",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap + to add an ID or certificate with its expiry date",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedDocs, key = { it.id }) { doc ->
                        val isItemSelected = selectedDocs.contains(doc)
                        DocRowItem(
                            doc = doc,
                            today = today,
                            isSelected = isItemSelected,
                            cardBgColor = cardBgColor,
                            outlineColor = outlineColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            tealAccent = tealAccent,
                            errorColor = errorColor,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedDocs = if (isItemSelected) selectedDocs - doc else selectedDocs + doc
                                } else {
                                    activeDocForDetail = doc
                                }
                            },
                            onLongClick = {
                                selectedDocs = selectedDocs + doc
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDocSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddDocSheet = false },
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
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Add document",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            showAddDocSheet = false
                            photoPickerLauncher.launch("image/*")
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Choose photo", fontSize = 16.sp, color = primaryTextColor)
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            showAddDocSheet = false
                            pdfPickerLauncher.launch("application/pdf")
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Choose file (PDF)", fontSize = 16.sp, color = primaryTextColor)
                }
            }
        }
    }

    if (showShareSheet && selectedDocs.isNotEmpty()) {
        ShareDocSheet(
            doc = selectedDocs.first(),
            context = context,
            onShareWithOrg = { orgName ->
                val docsList = selectedDocs.toList()
                for (d in docsList) {
                    viewModel.shareDocumentWithOrg(d, orgName)
                }
                selectedDocs = emptySet()
                showShareSheet = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Shared with $orgName")
                }
            },
            onDismiss = { showShareSheet = false }
        )
    }

    if (showDeleteConfirmDialog && selectedDocs.isNotEmpty()) {
        val count = selectedDocs.size
        val titleText = if (count == 1) "Delete 1 document?" else "Delete $count documents?"
        val hasShared = selectedDocs.any { it.sharedOrganisations.isNotEmpty() }

        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(titleText, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
            },
            text = {
                Column {
                    Text("The documents and their files will be removed from your account.", fontSize = 14.sp, color = secondaryTextColor)
                    if (hasShared) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Copies already shared with organisations stay with them.", fontSize = 13.sp, color = secondaryTextColor)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deletedList = selectedDocs.toList()
                        viewModel.deleteDocuments(deletedList)
                        selectedDocs = emptySet()
                        showDeleteConfirmDialog = false
                        coroutineScope.launch {
                            val msg = if (count == 1) "Document deleted" else "$count documents deleted"
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                ) {
                    Text("Delete", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = errorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocRowItem(
    doc: DocEntity,
    today: LocalDate,
    isSelected: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color,
    errorColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val typeIcon = when (doc.type.lowercase()) {
        "id" -> Icons.Outlined.Badge
        "certificate" -> Icons.Outlined.CardMembership
        "licence" -> Icons.Outlined.CreditCard
        else -> Icons.Outlined.Description
    }

    val typeLabel = when (doc.type.lowercase()) {
        "id" -> "ID"
        "certificate" -> "Certificate"
        "licence" -> "Licence"
        else -> "Other"
    }

    val sublineText = if (doc.number.isNotBlank()) "$typeLabel · ${doc.number}" else typeLabel

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }

    val (statusLabel, statusColor) = remember(doc.expiryDate, doc.noExpiry, today) {
        if (doc.noExpiry || doc.expiryDate == null) {
            "No expiry" to secondaryTextColor
        } else {
            val daysDiff = ChronoUnit.DAYS.between(today, doc.expiryDate)
            when {
                daysDiff < 0 -> "Expired" to errorColor
                daysDiff <= 30 -> {
                    val label = when (daysDiff) {
                        0L -> "Today"
                        1L -> "Tomorrow"
                        else -> "In $daysDiff days"
                    }
                    label to primaryTextColor
                }
                else -> "Valid" to secondaryTextColor
            }
        }
    }

    val isDark = primaryTextColor.red > 0.5f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = if (isSelected) tealAccent.copy(alpha = 0.18f) else cardBgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 44dp Round Icon
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = if (isSelected) tealAccent else Color.Transparent,
                    border = if (!isSelected) BorderStroke(1.dp, outlineColor) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = if (isDark) Color(0xFF0E1114) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Name + Subline
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sublineText,
                        fontSize = 13.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Status Word + Expiry Date
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = statusLabel,
                        fontSize = 13.sp,
                        fontWeight = if (statusLabel == "Expired" || statusLabel.startsWith("In ") || statusLabel == "Today" || statusLabel == "Tomorrow") FontWeight.SemiBold else FontWeight.Normal,
                        color = statusColor
                    )
                    if (!doc.noExpiry && doc.expiryDate != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = doc.expiryDate.format(dateFormatter),
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 80.dp),
                thickness = 1.dp,
                color = outlineColor
            )
        }
    }
}

private fun getStatusScore(doc: DocEntity, today: LocalDate): Int {
    if (doc.noExpiry || doc.expiryDate == null) return 4
    val daysDiff = ChronoUnit.DAYS.between(today, doc.expiryDate)
    return when {
        daysDiff < 0 -> 1 // Expired first
        daysDiff <= 30 -> 2 // Expires soon
        else -> 3 // Valid
    }
}
