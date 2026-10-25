package com.fabxdi.dibadge.ui.my_forms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.FormQuestionData
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.components.DiBadgeTimePicker
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.FormViewModel
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.encodeSignersToJson
import com.fabxdi.dibadge.viewmodel.encodeSingleSignerToJson
import com.fabxdi.dibadge.viewmodel.parseLogosFromJson
import com.fabxdi.dibadge.viewmodel.parseQuestionsFromJson
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFillScreen(
    groupForm: GroupFormEntity,
    onClose: () -> Unit,
    onSubmitted: () -> Unit,
    formViewModel: FormViewModel = viewModel(),
    detailsViewModel: GroupDetailsViewModel = viewModel()
) {
    val questions = remember(groupForm.questionsJson) {
        parseQuestionsFromJson(groupForm.questionsJson)
    }

    val hasSignaturesQuestion = remember(questions) {
        questions.any { it.type == "signatures" }
    }

    val groupMembers by detailsViewModel.members.collectAsState(initial = emptyList())

    var answersMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedWorkers by remember { mutableStateOf<List<FormSigner>>(emptyList()) }
    var selectedSupervisor by remember { mutableStateOf<FormSigner?>(null) }

    var showSelectWorkersSheet by remember { mutableStateOf(false) }
    var showSelectSupervisorSheet by remember { mutableStateOf(false) }

    var validationErrorIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    var activeDatePickerQuestionId by remember { mutableStateOf<String?>(null) }
    var activeTimePickerQuestionId by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val dimColor = diBadgeColors.tabInactive

    val currentUser = FirebaseAuth.getInstance().currentUser
    val submitterUid = currentUser?.uid ?: "me"
    val submitterName = currentUser?.displayName?.ifBlank { null } ?: "Member"

    val hasFilledEntries = remember(answersMap, selectedWorkers, selectedSupervisor) {
        answersMap.values.any { it.isNotBlank() } || selectedWorkers.isNotEmpty() || selectedSupervisor != null
    }

    fun handleClose() {
        if (hasFilledEntries) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler { handleClose() }

    fun handleSubmit() {
        val missingRequiredIds = mutableSetOf<String>()
        for (q in questions) {
            if (q.isRequired && q.type != "signatures") {
                val ans = answersMap[q.id] ?: ""
                if (ans.isBlank()) {
                    missingRequiredIds.add(q.id)
                }
            }
        }

        if (missingRequiredIds.isNotEmpty()) {
            validationErrorIds = missingRequiredIds
        } else {
            validationErrorIds = emptySet()
            val isSignatureFlow = hasSignaturesQuestion && (selectedWorkers.isNotEmpty() || selectedSupervisor != null)
            val submissionStatus = if (isSignatureFlow) "awaitingSignatures" else "submitted"

            formViewModel.submitFormWithSignatures(
                groupId = groupForm.groupId,
                formId = groupForm.formId,
                version = groupForm.version,
                submitterUid = submitterUid,
                submitterName = submitterName,
                answersMap = answersMap,
                status = submissionStatus,
                workersJson = encodeSignersToJson(selectedWorkers),
                supervisorJson = encodeSingleSignerToJson(selectedSupervisor),
                onSubmitted = onSubmitted
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = groupForm.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        val sublineStr = if (groupForm.isOrgForm) "From ${groupForm.orgName}" else "Added by ${groupForm.addedBy}"
                        Text(
                            text = sublineStr,
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleClose() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = primaryTextColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        val logosData = remember(groupForm.logosJson) { parseLogosFromJson(groupForm.logosJson) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            FormHeaderLogosStrip(logos = logosData)

            if (groupForm.description.isNotBlank()) {
                Text(
                    text = groupForm.description,
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            questions.forEachIndexed { idx, q ->
                val hasError = validationErrorIds.contains(q.id)
                val currentAns = answersMap[q.id] ?: ""

                val displayAns = if (q.type == "date" && currentAns.isBlank()) {
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                } else currentAns

                if (q.type == "signatures") {
                    // Signatures block: Workers & Supervisor
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBgColor,
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Workers Section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Workers", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                Text("Select one or more", fontSize = 12.sp, color = secondaryTextColor)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (selectedWorkers.isEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clickable { showSelectWorkersSheet = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.background,
                                    border = BorderStroke(1.dp, outlineColor)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = dimColor, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Select workers", fontSize = 15.sp, color = dimColor)
                                    }
                                }
                            } else {
                                Column {
                                    selectedWorkers.forEach { w ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showSelectWorkersSheet = true }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val avatarColor = remember(w.name) { UserColorUtils.getColorForName(w.name) }
                                            Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = avatarColor) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(w.name.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(w.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Supervisor Section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Supervisor", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                Text("Select one", fontSize = 12.sp, color = secondaryTextColor)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (selectedSupervisor == null) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clickable { showSelectSupervisorSheet = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.background,
                                    border = BorderStroke(1.dp, outlineColor)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = dimColor, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Select supervisor", fontSize = 15.sp, color = dimColor)
                                    }
                                }
                            } else {
                                val s = selectedSupervisor!!
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showSelectSupervisorSheet = true }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val avatarColor = remember(s.name) { UserColorUtils.getColorForName(s.name) }
                                    Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = avatarColor) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(s.name.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                                }
                            }
                        }
                    }
                } else {
                    // Standard Question Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBgColor,
                        border = BorderStroke(1.dp, if (hasError) colors.error else outlineColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row {
                                Text(
                                    text = "${idx + 1}. ${q.label.ifBlank { q.type }}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryTextColor
                                )
                                if (q.isRequired) {
                                    Text(" *", fontSize = 16.sp, color = colors.error)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            when (q.type) {
                                "short_answer" -> {
                                    OutlinedTextField(
                                        value = currentAns,
                                        onValueChange = { newText ->
                                            answersMap = answersMap + (q.id to newText)
                                            if (newText.isNotBlank()) validationErrorIds = validationErrorIds - q.id
                                        },
                                        placeholder = { Text("Your answer") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                                "long_answer" -> {
                                    OutlinedTextField(
                                        value = currentAns,
                                        onValueChange = { newText ->
                                            answersMap = answersMap + (q.id to newText)
                                            if (newText.isNotBlank()) validationErrorIds = validationErrorIds - q.id
                                        },
                                        placeholder = { Text("Your answer") },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 4
                                    )
                                }
                                "date" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clickable { activeDatePickerQuestionId = q.id },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = displayAns.ifBlank { "Select date" },
                                            fontSize = 15.sp,
                                            color = primaryTextColor
                                        )
                                    }
                                }
                                "time" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clickable { activeTimePickerQuestionId = q.id },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = currentAns.ifBlank { "Select time" },
                                            fontSize = 15.sp,
                                            color = if (currentAns.isBlank()) secondaryTextColor else primaryTextColor
                                        )
                                    }
                                }
                                "single_choice" -> {
                                    Column {
                                        q.options.forEach { optionText ->
                                            val isSelected = currentAns == optionText
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clickable {
                                                        answersMap = answersMap + (q.id to optionText)
                                                        validationErrorIds = validationErrorIds - q.id
                                                    },
                                                shape = RoundedCornerShape(20.dp),
                                                color = if (isSelected) cardBgColor else Color.Transparent,
                                                border = BorderStroke(1.dp, if (isSelected) tealAccent else outlineColor)
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Text(
                                                        text = optionText,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = primaryTextColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    OutlinedTextField(
                                        value = currentAns,
                                        onValueChange = { newText ->
                                            answersMap = answersMap + (q.id to newText)
                                            if (newText.isNotBlank()) validationErrorIds = validationErrorIds - q.id
                                        },
                                        placeholder = { Text("Your answer") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }

                            if (hasError) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Required",
                                    fontSize = 12.sp,
                                    color = colors.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Text (Submit / Send for signature)
            val actionLabel = if (hasSignaturesQuestion && (selectedWorkers.isNotEmpty() || selectedSupervisor != null)) {
                "Send for signature"
            } else {
                "Submit"
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealAccent,
                    modifier = Modifier
                        .clickable { handleSubmit() }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (showSelectWorkersSheet) {
        SelectSignersSheet(
            isSupervisor = false,
            members = groupMembers,
            initialSelected = selectedWorkers,
            onDone = { signers ->
                selectedWorkers = signers
                showSelectWorkersSheet = false
            },
            onDismiss = { showSelectWorkersSheet = false }
        )
    }

    if (showSelectSupervisorSheet) {
        SelectSignersSheet(
            isSupervisor = true,
            members = groupMembers,
            initialSelected = if (selectedSupervisor != null) listOf(selectedSupervisor!!) else emptyList(),
            onDone = { signers ->
                selectedSupervisor = signers.firstOrNull()
                showSelectSupervisorSheet = false
            },
            onDismiss = { showSelectSupervisorSheet = false }
        )
    }

    if (activeDatePickerQuestionId != null) {
        val qId = activeDatePickerQuestionId!!
        DiBadgeDatePicker(
            selectedDate = LocalDate.now(),
            onDateSelected = { date ->
                val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                answersMap = answersMap + (qId to dateStr)
                validationErrorIds = validationErrorIds - qId
                activeDatePickerQuestionId = null
            },
            onDismiss = { activeDatePickerQuestionId = null }
        )
    }

    if (activeTimePickerQuestionId != null) {
        val qId = activeTimePickerQuestionId!!
        DiBadgeTimePicker(
            initialTime = LocalTime.now(),
            title = "Select Time",
            onTimeSelected = { time ->
                val timeStr = time.format(DateTimeFormatter.ofPattern("h:mm a"))
                answersMap = answersMap + (qId to timeStr)
                validationErrorIds = validationErrorIds - qId
                activeTimePickerQuestionId = null
            },
            onDismiss = { activeTimePickerQuestionId = null }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this form?", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor) },
            text = { Text("What you filled in will be lost.", fontSize = 14.sp, color = secondaryTextColor) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onClose()
                    }
                ) {
                    Text("Discard", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
