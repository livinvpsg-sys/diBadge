package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.ui.components.DocumentTypeTile
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.LibraryCategoryItem
import com.fabxdi.dibadge.viewmodel.LibraryFileItem
import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCategoryDetailScreen(
    groupId: String,
    category: LibraryCategoryItem,
    allCategories: List<LibraryCategoryItem>,
    canUpload: Boolean,
    canUserManage: Boolean,
    onBack: () -> Unit,
    onOpenUploadSheet: () -> Unit,
    detailsViewModel: GroupDetailsViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    val libraryFiles by detailsViewModel.libraryFiles.collectAsState()
    val categoryFiles = remember(libraryFiles, category.id) {
        libraryFiles.filter { it.categoryId == category.id }.sortedByDescending { it.uploadedAt }
    }

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    BackHandler {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    val fileActions = rememberLibraryFileActions(groupId, detailsViewModel)
    var showMenu by remember { mutableStateOf(false) }

    var showRenameCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteCategoryDialog by remember { mutableStateOf(false) }
    var categoryNameInput by remember { mutableStateOf(category.name) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val isDark = diBadgeColors.isDark
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)

    // Filter files if searching
    val searchTrimmed = searchQuery.trim()
    val isSearchActive = isSearching && searchTrimmed.length >= 2
    val filteredFiles = remember(categoryFiles, searchTrimmed, isSearchActive) {
        if (!isSearchActive) categoryFiles
        else categoryFiles.filter { it.name.contains(searchTrimmed, ignoreCase = true) }
    }

    // Group files by formatted date heading
    val groupedByDateFiles = remember(filteredFiles) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

        filteredFiles.groupBy { f ->
            val date = Instant.ofEpochMilli(f.uploadedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            when {
                date == today -> "Today"
                date == yesterday -> "Yesterday"
                else -> date.format(dateFormatter)
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
            ) {
                if (isSearching) {
                    // Search Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isSearching = false
                                searchQuery = ""
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search",
                                tint = primaryTextColor
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = cardFillColor,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search in ${category.name}...",
                                            fontSize = 14.sp,
                                            color = dimTextColor
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 14.sp, color = primaryTextColor),
                                        cursorBrush = SolidColor(primaryTextColor),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester)
                                    )
                                }

                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    }
                } else {
                    // Standard Top Bar
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
                            text = category.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = primaryTextColor
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.width(236.dp)
                            ) {
                                // 1. Search (Everyone)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clickable {
                                            showMenu = false
                                            isSearching = true
                                            searchQuery = ""
                                        }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Search",
                                        fontSize = 16.sp,
                                        color = primaryTextColor
                                    )
                                }

                                if (canUserManage) {
                                    // 2. Rename category
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clickable {
                                                showMenu = false
                                                categoryNameInput = category.name
                                                showRenameCategoryDialog = true
                                            }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = null,
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = "Rename category",
                                            fontSize = 16.sp,
                                            color = primaryTextColor
                                        )
                                    }

                                    // 3. Delete category
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clickable {
                                                showMenu = false
                                                showDeleteCategoryDialog = true
                                            }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = errorColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = "Delete category",
                                            fontSize = 16.sp,
                                            color = errorColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor)
            }
        },
        floatingActionButton = {
            if (canUpload && !isSearching) {
                Surface(
                    modifier = Modifier
                        .padding(end = 4.dp, bottom = 12.dp)
                        .size(56.dp)
                        .clickable { onOpenUploadSheet() },
                    shape = RoundedCornerShape(18.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, outlineColor),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Upload",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            if (isSearchActive && filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matches",
                        fontSize = 15.sp,
                        color = secondaryTextColor
                    )
                }
            } else if (categoryFiles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No documents yet",
                        fontSize = 15.sp,
                        color = secondaryTextColor
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    groupedByDateFiles.forEach { (dateHeading, filesInDate) ->
                        item(key = dateHeading) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = dateHeading,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filesInDate.forEach { fileItem ->
                                        LibraryFileCardItem(
                                            fileItem = fileItem,
                                            canEdit = canUserManage || fileItem.uploadedByUid == currentUserId,
                                            canRevise = canUpload,
                                            isDark = isDark,
                                            cardBgColor = cardBgColor,
                                            outlineColor = outlineColor,
                                            primaryTextColor = primaryTextColor,
                                            secondaryTextColor = secondaryTextColor,
                                            errorColor = errorColor,
                                            onFileClick = {
                                                if (fileItem.downloadUrl.isNotBlank()) {
                                                    FilePickerUtils.openFile(context, Uri.parse(fileItem.downloadUrl))
                                                }
                                            },
                                            onNewRevisionClick = { fileActions.newRevision(fileItem) },
                                            onRenameClick = { fileActions.rename(fileItem) },
                                            onMoveClick = { fileActions.move(fileItem) },
                                            onDeleteClick = { fileActions.delete(fileItem) }
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

    // Rename Category Dialog
    if (showRenameCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showRenameCategoryDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = cardBgColor,
            title = { Text("Rename category", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Column {
                    BasicTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = primaryTextColor),
                        cursorBrush = SolidColor(primaryTextColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(thickness = 1.dp, color = outlineColor, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (categoryNameInput.isNotBlank()) {
                            detailsViewModel.renameLibraryCategory(groupId, category.id, categoryNameInput.trim())
                            Toast.makeText(context, "Category renamed", Toast.LENGTH_SHORT).show()
                            showRenameCategoryDialog = false
                        }
                    }
                ) {
                    Text("Save", color = tealAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameCategoryDialog = false }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        )
    }

    // Delete Category Dialog
    if (showDeleteCategoryDialog) {
        val filesInCat = categoryFiles
        AlertDialog(
            onDismissRequest = { showDeleteCategoryDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = cardBgColor,
            title = { Text("Delete category?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Text(
                    text = if (filesInCat.isNotEmpty()) {
                        "Move files to Files (no category)?"
                    } else {
                        "Delete category ${category.name}?"
                    },
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        detailsViewModel.deleteLibraryCategory(
                            groupId = groupId,
                            categoryId = category.id,
                            targetMoveCategoryId = null,
                            targetMoveCategoryName = null
                        )
                        Toast.makeText(context, "Category deleted", Toast.LENGTH_SHORT).show()
                        showDeleteCategoryDialog = false
                        onBack()
                    }
                ) {
                    Text("Delete", color = errorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCategoryDialog = false }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        )
    }

    LibraryFileDialogs(
        actions = fileActions,
        groupId = groupId,
        allCategories = allCategories,
        detailsViewModel = detailsViewModel
    )
}

@Composable
fun LibraryFileCardItem(
    fileItem: LibraryFileItem,
    canEdit: Boolean,
    canRevise: Boolean,
    isDark: Boolean,
    cardBgColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    errorColor: Color,
    onFileClick: () -> Unit,
    onNewRevisionClick: () -> Unit,
    onRenameClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val formattedTime = remember(fileItem.uploadedAt) {
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        Instant.ofEpochMilli(fileItem.uploadedAt).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter).lowercase()
    }

    val sizeStr = remember(fileItem.sizeBytes) {
        FilePickerUtils.formatFileSize(fileItem.sizeBytes)
    }

    val ext = remember(fileItem.name) {
        fileItem.name.substringAfterLast('.', "pdf").lowercase()
    }

    val displayNameWithRev = remember(fileItem.name, fileItem.revision) {
        if (!fileItem.revision.isNullOrBlank()) {
            "${fileItem.name} · ${fileItem.revision}"
        } else {
            fileItem.name
        }
    }

    val sublineText = "${ext.uppercase()} · $sizeStr · ${fileItem.uploadedByName} · $formattedTime"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable { onFileClick() },
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocumentTypeTile(extension = ext, isDark = isDark, widthDp = 28, heightDp = 34)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayNameWithRev,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = sublineText,
                    fontSize = 12.sp,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (canRevise || canEdit) Box {
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
                    if (canRevise) {
                        DropdownMenuItem(
                            text = { Text("Upload new revision") },
                            onClick = {
                                showMenu = false
                                onNewRevisionClick()
                            }
                        )
                    }
                    if (canEdit) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move") },
                            onClick = {
                                showMenu = false
                                onMoveClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = errorColor) },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}
