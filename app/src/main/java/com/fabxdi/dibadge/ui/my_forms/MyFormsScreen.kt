package com.fabxdi.dibadge.ui.my_forms

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.data.FormEntity
import com.fabxdi.dibadge.ui.components.DiBadgeExtendedFab
import com.fabxdi.dibadge.ui.components.DiBadgeFab
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.FormViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFormsScreen(
    onBack: () -> Unit,
    formViewModel: FormViewModel = viewModel()
) {
    BackHandler { onBack() }

    val userForms by formViewModel.allUserForms.collectAsState(initial = emptyList())

    var formToEdit by remember { mutableStateOf<FormEntity?>(null) }
    var showFormBuilder by remember { mutableStateOf(false) }
    var formForOptions by remember { mutableStateOf<FormEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddToGroupSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive

    if (showFormBuilder) {
        FormBuilderScreen(
            formToEdit = formToEdit,
            onClose = {
                showFormBuilder = false
                formToEdit = null
            },
            onSave = {
                showFormBuilder = false
                formToEdit = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Form saved")
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Forms",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (userForms.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_forms_outline),
                            contentDescription = null,
                            tint = dimColor,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Create your first form",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Make forms for your groups, like site reports and checklists",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
                ) {
                    itemsIndexed(userForms, key = { _, form -> form.id }) { index, form ->
                        FormRowItem(
                            form = form,
                            showDivider = index < userForms.size - 1,
                            onClick = { formForOptions = form }
                        )
                    }
                }
            }

            DiBadgeExtendedFab(
                text = "Create",
                onClick = {
                    formToEdit = null
                    showFormBuilder = true
                },
                contentDescription = "Create form",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 36.dp)
            )
        }
    }

    // Form Options Sheet
    if (formForOptions != null) {
        val selectedForm = formForOptions!!
        ModalBottomSheet(
            onDismissRequest = { formForOptions = null },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = cardBgColor,
            scrimColor = Color.Black.copy(alpha = 0.45f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = selectedForm.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Edit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            formForOptions = null
                            formToEdit = selectedForm
                            showFormBuilder = true
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Edit",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }

                // 2. Add to group
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            showAddToGroupSheet = true
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Add to group",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }

                // 3. Duplicate
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            formForOptions = null
                            formViewModel.duplicateUserForm(selectedForm)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Duplicated form")
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileCopy,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Duplicate",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // 4. Delete
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            showDeleteConfirmDialog = true
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Delete",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirmDialog && formForOptions != null) {
        val targetForm = formForOptions!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete this form?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "It will be removed from your groups. Submitted forms stay.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        formForOptions = null
                        formViewModel.deleteUserForm(targetForm)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Form deleted")
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showAddToGroupSheet && formForOptions != null) {
        AddToGroupSheet(
            form = formForOptions!!,
            onDismiss = {
                showAddToGroupSheet = false
                formForOptions = null
            },
            onAdded = { count ->
                showAddToGroupSheet = false
                formForOptions = null
                coroutineScope.launch {
                    val msg = if (count == 1) "Added to 1 group" else "Added to $count groups"
                    snackbarHostState.showSnackbar(msg)
                }
            }
        )
    }
}

@Composable
fun FormRowItem(
    form: FormEntity,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val outlineColor = colors.outline

    val updatedDateStr = remember(form.updatedAtMs) {
        val date = Instant.ofEpochMilli(form.updatedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 44dp Rounded Icon Tile
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, outlineColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_forms_outline),
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = form.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Updated $updatedDateStr",
                    fontSize = 13.sp,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 58.dp),
                thickness = 1.dp,
                color = outlineColor
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PREVIEWS
// ---------------------------------------------------------------------------

@Preview(name = "My Forms - Empty Light", widthDp = 412, showBackground = true)
@Composable
fun MyFormsEmptyLightPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MyFormsScreen(onBack = {})
        }
    }
}

@Preview(
    name = "My Forms - Empty Dark",
    widthDp = 412,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun MyFormsEmptyDarkPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.DARK) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MyFormsScreen(onBack = {})
        }
    }
}
