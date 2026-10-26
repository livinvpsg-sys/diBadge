package com.fabxdi.dibadge.ui.my_forms

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.data.FormSubmissionEntity
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.components.DiBadgeFab
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.FormViewModel
import com.fabxdi.dibadge.viewmodel.parseQuestionsFromJson
import com.fabxdi.dibadge.viewmodel.parseSignersFromJson
import com.fabxdi.dibadge.viewmodel.parseSingleSignerFromJson
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
    onOpenMyForms: () -> Unit = {},
    formViewModel: FormViewModel = viewModel()
) {
    BackHandler { onBack() }

    val groupForms by formViewModel.getFormsForGroup(groupId).collectAsState(initial = emptyList())
    val submissionsList by formViewModel.getSubmissionsForGroup(groupId).collectAsState(initial = emptyList())

    var showChooseFormSheet by remember { mutableStateOf(false) }
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
                        text = "Forms (${submissionsList.size})",
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
                            color = primaryTextColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap + to fill in a new form",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
                ) {
                    items(submissionsList, key = { it.id }) { sub ->
                        val gForm = groupForms.find { it.formId == sub.formId }
                        val formName = gForm?.title ?: "Form Submission"
                        val submittedTimeStr = remember(sub.submittedAtMs) {
                            val dt = Instant.ofEpochMilli(sub.submittedAtMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
                            dt.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))
                        }



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

                                if (sub.status == "awaitingSignatures") {
                                    Text(
                                        text = "In progress",
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

            DiBadgeFab(
                onClick = {
                    showChooseFormSheet = true
                },
                contentDescription = "Choose a form",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 36.dp)
            )
        }
    }

    // Screen 2: Choose a form sheet
    if (showChooseFormSheet) {
        val isOrgGroup = remember(groupName) { groupName.contains("Organisation", ignoreCase = true) }
        val orgNameStr = remember(groupName, isOrgGroup) { if (isOrgGroup) groupName else "" }

        ChooseFormSheet(
            groupForms = groupForms,
            canUserManage = canUserManage,
            isOrgGroup = isOrgGroup,
            orgName = orgNameStr,
            onSelectForm = { gForm ->
                showChooseFormSheet = false
                selectedGroupFormToFill = gForm
            },
            onOpenMyForms = onOpenMyForms,
            onDismiss = { showChooseFormSheet = false }
        )
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
