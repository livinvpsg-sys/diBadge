package com.fabxdi.dibadge.ui.my_forms

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
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
import com.fabxdi.dibadge.data.FormSubmissionEntity
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.FormViewModel
import com.fabxdi.dibadge.viewmodel.parseQuestionsFromJson
import com.fabxdi.dibadge.viewmodel.parseSignersFromJson
import com.fabxdi.dibadge.viewmodel.parseSingleSignerFromJson
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFormsScreen(
    groupId: String,
    groupName: String,
    canUserManage: Boolean = true,
    onBack: () -> Unit,
    formViewModel: FormViewModel = viewModel()
) {
    BackHandler { onBack() }

    val groupForms by formViewModel.getFormsForGroup(groupId).collectAsState(initial = emptyList())
    val userForms by formViewModel.allUserForms.collectAsState(initial = emptyList())
    val submissionsList by formViewModel.getSubmissionsForGroup(groupId).collectAsState(initial = emptyList())

    var showChooseFormSheet by remember { mutableStateOf(false) }
    var showAddFromMyFormsSheet by remember { mutableStateOf(false) }
    var selectedGroupFormToFill by remember { mutableStateOf<GroupFormEntity?>(null) }
    var selectedSubmissionForDetail by remember { mutableStateOf<FormSubmissionEntity?>(null) }
    var selectedSubmissionForSignatureRequest by remember { mutableStateOf<FormSubmissionEntity?>(null) }
    var formToRemove by remember { mutableStateOf<GroupFormEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent

    if (selectedSubmissionForSignatureRequest != null) {
        val sub = selectedSubmissionForSignatureRequest!!
        val gForm = groupForms.find { it.formId == sub.formId }
        FormSignatureRequestScreen(
            submission = sub,
            groupForm = gForm,
            onBack = { selectedSubmissionForSignatureRequest = null }
        )
        return
    }

    if (selectedGroupFormToFill != null) {
        FormFillScreen(
            groupForm = selectedGroupFormToFill!!,
            onClose = { selectedGroupFormToFill = null },
            onSubmitted = {
                val filledFormTitle = selectedGroupFormToFill?.title ?: "Form"
                selectedGroupFormToFill = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Form submitted")
                }
            }
        )
        return
    }

    if (selectedSubmissionForDetail != null) {
        val sub = selectedSubmissionForDetail!!
        val gForm = groupForms.find { it.formId == sub.formId }
        FormSubmissionDetailScreen(
            submission = sub,
            groupForm = gForm,
            onBack = { selectedSubmissionForDetail = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (submissionsList.isEmpty()) "Forms" else "Forms (${submissionsList.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
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
                actions = {
                    if (canUserManage) {
                        Text(
                            text = "Import form",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tealAccent,
                            modifier = Modifier
                                .clickable { showAddFromMyFormsSheet = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
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
            if (submissionsList.isEmpty()) {
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
                            text = "No forms submitted yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap + to fill in a form from your organisation",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
                ) {
                    items(submissionsList, key = { it.id }) { sub ->
                        val gForm = groupForms.find { it.formId == sub.formId }
                        val formName = gForm?.title ?: "Form Submission"
                        val submittedTimeStr = remember(sub.submittedAtMs) {
                            val dt = Instant.ofEpochMilli(sub.submittedAtMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
                            dt.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))
                        }

                        val workersList = remember(sub.workersJson) { parseSignersFromJson(sub.workersJson) }
                        val supervisorSigner = remember(sub.supervisorJson) { parseSingleSignerFromJson(sub.supervisorJson) }
                        val totalSigners = workersList.size + (if (supervisorSigner != null) 1 else 0)
                        val signedCount = workersList.count { it.stamp != null } + (if (supervisorSigner?.stamp != null) 1 else 0)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .clickable {
                                    if (sub.status == "awaitingSignatures") {
                                        selectedSubmissionForSignatureRequest = sub
                                    } else {
                                        selectedSubmissionForDetail = sub
                                    }
                                }
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = formName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${sub.submitterName} · $submittedTimeStr",
                                        fontSize = 12.sp,
                                        color = secondaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (sub.status == "awaitingSignatures" && totalSigners > 0) {
                                    Text(
                                        text = "$signedCount of $totalSigners signed",
                                        fontSize = 13.sp,
                                        color = secondaryTextColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(thickness = 1.dp, color = outlineColor)
                        }
                    }
                }
            }

            // Shared App FAB Style: 52dp rounded square, 14dp corners, fill #2C2C2E, 1dp #8A8A8E outline
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 36.dp)
                    .size(52.dp)
                    .clickable {
                        showChooseFormSheet = true
                    },
                shape = RoundedCornerShape(14.dp),
                color = cardBgColor,
                border = BorderStroke(1.dp, outlineColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Choose a form",
                        tint = primaryTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // Screen 2: Choose a form sheet
    if (showChooseFormSheet) {
        ChooseFormSheet(
            groupForms = groupForms,
            canUserManage = canUserManage,
            onSelectForm = { gForm ->
                showChooseFormSheet = false
                selectedGroupFormToFill = gForm
            },
            onDismiss = { showChooseFormSheet = false }
        )
    }

    // Sheet: Add from my forms
    if (showAddFromMyFormsSheet) {
        val currentUserName = remember { FirebaseAuth.getInstance().currentUser?.displayName ?: "Owner" }
        val currentUserUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "me" }
        val existingFormIdsInGroup = remember(groupForms) { groupForms.map { it.formId }.toSet() }

        var selectedForms by remember { mutableStateOf<Set<FormEntity>>(emptySet()) }
        val canAdd = selectedForms.isNotEmpty()

        ModalBottomSheet(
            onDismissRequest = { showAddFromMyFormsSheet = false },
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
                    text = "Import forms",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Choose forms to bring into this group",
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = outlineColor)
                Spacer(modifier = Modifier.height(12.dp))

                if (userForms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No forms in your My Forms section", fontSize = 15.sp, color = secondaryTextColor)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(userForms, key = { it.id }) { uForm ->
                            val isAlreadyInGroup = existingFormIdsInGroup.contains(uForm.id)
                            val isSelected = selectedForms.contains(uForm)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable(enabled = !isAlreadyInGroup) {
                                        selectedForms = if (isSelected) selectedForms - uForm else selectedForms + uForm
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uForm.title,
                                    fontSize = 16.sp,
                                    color = if (isAlreadyInGroup) dimColor else primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                if (isAlreadyInGroup) {
                                    Text("Added", fontSize = 14.sp, color = dimColor)
                                } else {
                                    Surface(
                                        modifier = Modifier.size(24.dp),
                                        shape = CircleShape,
                                        color = if (isSelected) tealAccent else Color.Transparent,
                                        border = BorderStroke(1.5.dp, if (isSelected) tealAccent else dimColor)
                                    ) {
                                        if (isSelected) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        color = secondaryTextColor,
                        modifier = Modifier
                            .clickable { showAddFromMyFormsSheet = false }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    Text(
                        text = "Import",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canAdd) tealAccent else dimColor,
                        modifier = Modifier
                            .clickable(enabled = canAdd) {
                                showAddFromMyFormsSheet = false
                                val selectedList = selectedForms.toList()
                                for (form in selectedList) {
                                    formViewModel.addFormToGroups(
                                        form = form,
                                        targetGroupIds = listOf(groupId),
                                        currentUserName = currentUserName,
                                        currentUserUid = currentUserUid
                                    )
                                }
                                coroutineScope.launch {
                                    val msg = if (selectedList.size == 1) "Imported ${selectedList.first().title}" else "Imported ${selectedList.size} forms"
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (formToRemove != null) {
        val targetGForm = formToRemove!!
        AlertDialog(
            onDismissRequest = { formToRemove = null },
            title = { Text("Remove from group?", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor) },
            text = { Text("Previous submissions stay in this group.", fontSize = 14.sp, color = secondaryTextColor) },
            confirmButton = {
                TextButton(
                    onClick = {
                        formToRemove = null
                        formViewModel.removeFormFromGroup(groupId, targetGForm.formId)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Form removed from group")
                        }
                    }
                ) {
                    Text("Remove", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { formToRemove = null }) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
