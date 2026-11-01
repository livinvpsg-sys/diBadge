package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
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
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.viewmodel.DocViewModel
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.LibraryCategoryItem
import com.fabxdi.dibadge.viewmodel.LibraryFileItem
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupLibraryScreen(
    groupId: String,
    groupName: String,
    canUserManage: Boolean,
    onBack: () -> Unit,
    detailsViewModel: GroupDetailsViewModel = viewModel(),
    docViewModel: DocViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    LaunchedEffect(groupId) {
        detailsViewModel.listenToLibrary(groupId)
    }

    val categories by detailsViewModel.libraryCategories.collectAsState()
    val libraryFiles by detailsViewModel.libraryFiles.collectAsState()
    val whoCanUploadSetting by detailsViewModel.whoCanUploadLibrary.collectAsState()
    val myDocsList by docViewModel.allDocuments.collectAsState(initial = emptyList())

    val canUpload = remember(canUserManage, whoCanUploadSetting) {
        canUserManage || whoCanUploadSetting == "all_members"
    }

    val uncategorizedFiles = remember(libraryFiles) {
        libraryFiles.filter { it.categoryId.isNullOrBlank() }.sortedByDescending { it.uploadedAt }
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

    // Keep only the id: the category is looked up live, so a rename shows up straight away and a delete closes the screen.
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val selectedCategoryForDetail = categories.firstOrNull { it.id == selectedCategoryId }
    var showUploadSheet by remember { mutableStateOf(false) }
    var targetCategoryForUpload by remember { mutableStateOf<LibraryCategoryItem?>(null) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showUploadPermissionDialog by remember { mutableStateOf(false) }
    var categoryToRename by remember { mutableStateOf<LibraryCategoryItem?>(null) }
    var categoryToDelete by remember { mutableStateOf<LibraryCategoryItem?>(null) }

    var categoryNameInput by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    val fileActions = rememberLibraryFileActions(groupId, detailsViewModel)

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

    // Search Results Computation
    val searchTrimmed = searchQuery.trim()
    val isSearchActive = isSearching && searchTrimmed.length >= 2

    val matchingCategories = remember(categories, searchTrimmed, isSearchActive) {
        if (!isSearchActive) emptyList()
        else categories.filter { it.name.contains(searchTrimmed, ignoreCase = true) }
    }

    val matchingFiles = remember(libraryFiles, searchTrimmed, isSearchActive) {
        if (!isSearchActive) emptyList()
        else libraryFiles.filter { it.name.contains(searchTrimmed, ignoreCase = true) }.sortedByDescending { it.uploadedAt }
    }

    // Upload sheet. It is drawn before the category screen branch below so it also opens from inside a category.
    if (showUploadSheet) {
        val targetCat = targetCategoryForUpload
        UploadToLibrarySheet(
            initialCategoryId = targetCat?.id,
            initialCategoryName = targetCat?.name,
            isDark = isDark,
            onUploadFiles = { uploadConfigs ->
                uploadConfigs.forEach { config ->
                    detailsViewModel.uploadFileToLibrary(
                        groupId = groupId,
                        categoryId = config.categoryId ?: targetCat?.id,
                        categoryName = config.categoryName ?: targetCat?.name,
                        fileUri = config.uri,
                        name = config.name,
                        revision = null,
                        notifyMembers = true,
                        context = context
                    )
                }
                showUploadSheet = false
            },
            onDismiss = { showUploadSheet = false }
        )
    }

    if (selectedCategoryForDetail != null) {
        val targetCat = selectedCategoryForDetail
        GroupCategoryDetailScreen(
            groupId = groupId,
            category = targetCat,
            allCategories = categories,
            canUpload = canUpload,
            canUserManage = canUserManage,
            onBack = { selectedCategoryId = null },
            onOpenUploadSheet = {
                targetCategoryForUpload = targetCat
                showUploadSheet = true
            },
            detailsViewModel = detailsViewModel
        )
        return
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
                                            text = "Search files & categories...",
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
                            text = LIBRARY_TITLE,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor,
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
                                    // 2. Create category (Owner & Admins)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clickable {
                                                showMenu = false
                                                categoryNameInput = ""
                                                showAddCategoryDialog = true
                                            }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CreateNewFolder,
                                            contentDescription = null,
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = "Create category",
                                            fontSize = 16.sp,
                                            color = primaryTextColor
                                        )
                                    }

                                    // 3. Who can upload (Owner & Admins)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clickable {
                                                showMenu = false
                                                showUploadPermissionDialog = true
                                            }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FileUpload,
                                            contentDescription = null,
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = "Who can upload",
                                            fontSize = 16.sp,
                                            color = primaryTextColor
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
                        .clickable {
                            targetCategoryForUpload = null
                            showUploadSheet = true
                        },
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
            if (isSearchActive) {
                // SEARCH RESULTS MODE
                if (matchingCategories.isEmpty() && matchingFiles.isEmpty()) {
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
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (matchingCategories.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Categories",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            items(matchingCategories, key = { "c_${it.id}" }) { categoryItem ->
                                val fileCount = libraryFiles.count { it.categoryId == categoryItem.id }
                                LibraryCategoryCardRow(
                                    category = categoryItem,
                                    fileCount = fileCount,
                                    canManageCategory = canUserManage,
                                    isDark = isDark,
                                    cardBgColor = cardBgColor,
                                    cardFillColor = cardFillColor,
                                    outlineColor = outlineColor,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    errorColor = errorColor,
                                    onCategoryClick = { selectedCategoryId = categoryItem.id },
                                    onRenameClick = {
                                        categoryToRename = categoryItem
                                        categoryNameInput = categoryItem.name
                                    },
                                    onDeleteClick = { categoryToDelete = categoryItem }
                                )
                            }
                        }

                        if (matchingFiles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Files",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(matchingFiles, key = { "f_${it.id}" }) { fileItem ->
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
            } else if (categories.isEmpty() && uncategorizedFiles.isEmpty()) {
                // Empty State
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
                // STANDARD CATEGORIES + UNCATEGORIZED FILES LIST
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Categories list
                    items(categories, key = { "c_${it.id}" }) { categoryItem ->
                        val fileCount = libraryFiles.count { it.categoryId == categoryItem.id }
                        LibraryCategoryCardRow(
                            category = categoryItem,
                            fileCount = fileCount,
                            canManageCategory = canUserManage,
                            isDark = isDark,
                            cardBgColor = cardBgColor,
                            cardFillColor = cardFillColor,
                            outlineColor = outlineColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            errorColor = errorColor,
                            onCategoryClick = { selectedCategoryId = categoryItem.id },
                            onRenameClick = {
                                categoryToRename = categoryItem
                                categoryNameInput = categoryItem.name
                            },
                            onDeleteClick = { categoryToDelete = categoryItem }
                        )
                    }

                    // Uncategorized Files Section
                    if (uncategorizedFiles.isNotEmpty()) {
                        item {
                            Text(
                                text = "Files",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryTextColor,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }

                        items(uncategorizedFiles, key = { "f_${it.id}" }) { fileItem ->
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

    LibraryFileDialogs(
        actions = fileActions,
        groupId = groupId,
        allCategories = categories,
        detailsViewModel = detailsViewModel
    )

    // Create Category Dialog
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = cardBgColor,
            title = { Text("Create category", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Column {
                    Text("Category name", fontSize = 12.sp, color = secondaryTextColor)
                    Spacer(modifier = Modifier.height(4.dp))
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
                            detailsViewModel.addLibraryCategory(groupId, categoryNameInput.trim())
                            Toast.makeText(context, "Category created", Toast.LENGTH_SHORT).show()
                            showAddCategoryDialog = false
                        }
                    }
                ) {
                    Text("Create", color = tealAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        )
    }

    // Rename Category Dialog
    if (categoryToRename != null) {
        val targetCat = categoryToRename!!
        AlertDialog(
            onDismissRequest = { categoryToRename = null },
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
                            detailsViewModel.renameLibraryCategory(groupId, targetCat.id, categoryNameInput.trim())
                            Toast.makeText(context, "Category renamed", Toast.LENGTH_SHORT).show()
                            categoryToRename = null
                        }
                    }
                ) {
                    Text("Save", color = tealAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToRename = null }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        )
    }

    // Delete Category Dialog (asks "Move files to Files (no category)?")
    if (categoryToDelete != null) {
        val targetCat = categoryToDelete!!
        val filesInCat = libraryFiles.filter { it.categoryId == targetCat.id }

        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = cardBgColor,
            title = { Text("Delete category?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Text(
                    text = if (filesInCat.isNotEmpty()) {
                        "Move files to Files (no category)?"
                    } else {
                        "Delete category ${targetCat.name}?"
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
                            categoryId = targetCat.id,
                            targetMoveCategoryId = null,
                            targetMoveCategoryName = null
                        )
                        Toast.makeText(context, "Category deleted", Toast.LENGTH_SHORT).show()
                        categoryToDelete = null
                    }
                ) {
                    Text("Delete", color = errorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        )
    }

    // Who Can Upload Settings Dialog
    if (showUploadPermissionDialog) {
        var selectedPerm by remember { mutableStateOf(whoCanUploadSetting) }
        AlertDialog(
            onDismissRequest = { showUploadPermissionDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = cardBgColor,
            title = { Text("Who can upload", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPerm = "admins_only" }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPerm == "admins_only",
                            onClick = { selectedPerm = "admins_only" },
                            colors = RadioButtonDefaults.colors(selectedColor = tealAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Admins only", fontSize = 15.sp, color = primaryTextColor)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPerm = "all_members" }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPerm == "all_members",
                            onClick = { selectedPerm = "all_members" },
                            colors = RadioButtonDefaults.colors(selectedColor = tealAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "All members", fontSize = 15.sp, color = primaryTextColor)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        detailsViewModel.updateLibraryUploadPermission(groupId, selectedPerm)
                        Toast.makeText(context, "Settings updated", Toast.LENGTH_SHORT).show()
                        showUploadPermissionDialog = false
                    }
                ) {
                    Text("Save", color = tealAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadPermissionDialog = false }) {
                    Text("Cancel", color = secondaryTextColor)
                }
            }
        )
    }
}

@Composable
private fun LibraryCategoryCardRow(
    category: LibraryCategoryItem,
    fileCount: Int,
    canManageCategory: Boolean,
    isDark: Boolean,
    cardBgColor: Color,
    cardFillColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    errorColor: Color,
    onCategoryClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable { onCategoryClick() },
        shape = RoundedCornerShape(14.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = cardFillColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = primaryTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val countText = if (fileCount == 1) "1 file" else "$fileCount files"
                Text(
                    text = countText,
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )
            }

            if (canManageCategory) {
                Box {
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
                        DropdownMenuItem(
                            text = { Text("Rename category") },
                            onClick = {
                                showMenu = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete category", color = errorColor) },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
