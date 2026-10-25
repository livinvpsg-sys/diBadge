package com.fabxdi.dibadge.ui.my_forms

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.FormQuestionData
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.components.DiBadgeTimePicker
import com.fabxdi.dibadge.ui.profile.parseSignatureImageBitmap
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.FormViewModel
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.ProfileViewModel
import com.fabxdi.dibadge.viewmodel.encodeSignersToJson
import com.fabxdi.dibadge.viewmodel.encodeSingleSignerToJson
import com.fabxdi.dibadge.viewmodel.parseLogosFromJson
import com.fabxdi.dibadge.viewmodel.parseQuestionsFromJson
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFillScreen(
    groupForm: GroupFormEntity,
    onClose: () -> Unit,
    onSubmitted: () -> Unit,
    formViewModel: FormViewModel = viewModel(),
    detailsViewModel: GroupDetailsViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel()
) {
    val questions = remember(groupForm.questionsJson) {
        parseQuestionsFromJson(groupForm.questionsJson)
    }

    val hasSignaturesQuestion = remember(questions) {
        questions.any { it.type in listOf("signatures", "single_signature", "multiple_signatures") }
    }

    val groupMembers by detailsViewModel.members.collectAsState(initial = emptyList())
    val profileData by profileViewModel.profileData.collectAsState()

    var answersMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedWorkers by remember { mutableStateOf<List<FormSigner>>(emptyList()) }
    var selectedSupervisor by remember { mutableStateOf<FormSigner?>(null) }

    var showSelectWorkersSheet by remember { mutableStateOf(false) }
    var showSelectSupervisorSheet by remember { mutableStateOf(false) }
    var showApplicantYourSignatureSheet by remember { mutableStateOf(false) }
    var showNoSignatureDialog by remember { mutableStateOf(false) }

    var activeSignatureFieldLabel by remember { mutableStateOf("Signature") }
    var validationErrorIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    var activeDatePickerQuestionId by remember { mutableStateOf<String?>(null) }
    var activeTimePickerQuestionId by remember { mutableStateOf<String?>(null) }
    var activePhotoQuestionId by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            if (uris.isNotEmpty() && activePhotoQuestionId != null) {
                val qId = activePhotoQuestionId!!
                val existingListStr = answersMap[qId] ?: ""
                val existingList = if (existingListStr.isNotBlank()) existingListStr.split(",") else emptyList()
                val newUrisStr = uris.map { it.toString() }
                val combined = (existingList + newUrisStr).distinct()
                answersMap = answersMap + (qId to combined.joinToString(","))
                validationErrorIds = validationErrorIds - qId
            }
        }
    )

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
    val submitterName = currentUser?.displayName?.ifBlank { null } ?: "Applicant"

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
            if (q.isRequired && q.type !in listOf("signatures", "single_signature", "multiple_signatures")) {
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

            val allSignersList = selectedWorkers + listOfNotNull(selectedSupervisor)
            val allSigned = allSignersList.isNotEmpty() && allSignersList.all { it.stamp != null }
            val submissionStatus = if (hasSignaturesQuestion && !allSigned) "awaitingSignatures" else "submitted"

            formViewModel.submitFormWithSignatures(
                groupId = groupForm.groupId,
                formId = groupForm.formId,
                formTitle = groupForm.title,
                version = groupForm.version,
                submitterUid = submitterUid,
                submitterName = submitterName,
                answersMap = answersMap,
                status = submissionStatus,
                workersJson = encodeSignersToJson(selectedWorkers),
                supervisorJson = encodeSingleSignerToJson(selectedSupervisor),
                onSubmitted = {
                    if (submissionStatus == "submitted") {
                        onSubmitted()
                    } else {
                        onSubmitted()
                    }
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

                if (q.type in listOf("signatures", "single_signature", "multiple_signatures")) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBgColor,
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Question / Signature Field Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = q.label.ifBlank { "Signature" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                                Text(
                                    text = if (q.type == "single_signature") "Select one" else "Select one or more",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val signersList = if (q.type == "single_signature") listOfNotNull(selectedSupervisor) else selectedWorkers

                            if (signersList.isEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clickable {
                                            activeSignatureFieldLabel = q.label.ifBlank { "Signature" }
                                            if (q.type == "single_signature") showSelectSupervisorSheet = true
                                            else showSelectWorkersSheet = true
                                        },
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
                                        Text(
                                            text = if (q.type == "single_signature") "Select member" else "Select members",
                                            fontSize = 15.sp,
                                            color = dimColor
                                        )
                                    }
                                }
                            } else {
                                Column {
                                    signersList.forEach { signer ->
                                        val isApplicantSigner = signer.uid == submitterUid
                                        val isSigned = signer.stamp != null

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    activeSignatureFieldLabel = q.label.ifBlank { "Signature" }
                                                    if (isApplicantSigner && !isSigned) {
                                                        if (profileData.signatureUrl.isBlank()) {
                                                            showNoSignatureDialog = true
                                                        } else {
                                                            showApplicantYourSignatureSheet = true
                                                        }
                                                    } else {
                                                        if (q.type == "single_signature") showSelectSupervisorSheet = true
                                                        else showSelectWorkersSheet = true
                                                    }
                                                }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                val avatarColor = remember(signer.name) { UserColorUtils.getColorForName(signer.name) }
                                                Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = avatarColor) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(signer.name.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column {
                                                    val displayNameStr = if (isApplicantSigner) "${signer.name} (You)" else signer.name
                                                    Text(displayNameStr, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    if (isSigned) {
                                                        Text("${signer.stamp!!.fullName} · ${signer.stamp.badgeNumber}", fontSize = 12.sp, color = secondaryTextColor)
                                                    } else if (isApplicantSigner) {
                                                        Text("Ready to sign", fontSize = 12.sp, color = secondaryTextColor)
                                                    } else {
                                                        Text("Waiting", fontSize = 12.sp, color = secondaryTextColor)
                                                    }
                                                }
                                            }

                                            if (isApplicantSigner && !isSigned) {
                                                Text(
                                                    text = "Sign",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tealAccent,
                                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                                                )
                                            } else if (isSigned && signer.stamp?.signatureUrl?.isNotBlank() == true) {
                                                val sigBitmap = remember(signer.stamp.signatureUrl) { parseSignatureImageBitmap(signer.stamp.signatureUrl) }
                                                if (sigBitmap != null) {
                                                    Image(
                                                        bitmap = sigBitmap,
                                                        contentDescription = "Stamp",
                                                        modifier = Modifier.size(width = 72.dp, height = 36.dp).clip(RoundedCornerShape(4.dp))
                                                    )
                                                }
                                            }
                                        }
                                    }
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

                            Spacer(modifier = Modifier.height(12.dp))

                            when (q.type) {
                                "short_answer" -> {
                                    DimLineTextField(
                                        value = currentAns,
                                        onValueChange = { newText ->
                                            answersMap = answersMap + (q.id to newText)
                                            if (newText.isNotBlank()) validationErrorIds = validationErrorIds - q.id
                                        },
                                        isError = hasError,
                                        isSingleLine = true
                                    )
                                }
                                "long_answer" -> {
                                    DimLineTextField(
                                        value = currentAns,
                                        onValueChange = { newText ->
                                            answersMap = answersMap + (q.id to newText)
                                            if (newText.isNotBlank()) validationErrorIds = validationErrorIds - q.id
                                        },
                                        isError = hasError,
                                        isSingleLine = false
                                    )
                                }
                                "number" -> {
                                    DimLineTextField(
                                        value = currentAns,
                                        onValueChange = { newText ->
                                            val filtered = filterNumberInput(newText)
                                            answersMap = answersMap + (q.id to filtered)
                                            if (filtered.isNotBlank()) validationErrorIds = validationErrorIds - q.id
                                        },
                                        isError = hasError,
                                        isSingleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                                "photos" -> {
                                    val photoUris = remember(currentAns) {
                                        if (currentAns.isNotBlank()) currentAns.split(",") else emptyList()
                                    }

                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .clickable {
                                                    activePhotoQuestionId = q.id
                                                    photoPickerLauncher.launch("image/*")
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AttachFile,
                                                contentDescription = "Attach image",
                                                tint = if (hasError) colors.error else secondaryTextColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Attach image",
                                                fontSize = 15.sp,
                                                color = if (hasError) colors.error else secondaryTextColor
                                            )
                                        }

                                        if (photoUris.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                            ) {
                                                photoUris.forEachIndexed { pIdx, uriStr ->
                                                    Box(modifier = Modifier.size(72.dp)) {
                                                        AsyncImage(
                                                            model = uriStr,
                                                            contentDescription = "Photo thumbnail",
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(RoundedCornerShape(12.dp)),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                        Surface(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(2.dp)
                                                                .size(20.dp)
                                                                .clickable {
                                                                    val updatedList = photoUris.toMutableList()
                                                                    updatedList.removeAt(pIdx)
                                                                    answersMap = answersMap + (q.id to updatedList.joinToString(","))
                                                                },
                                                            shape = CircleShape,
                                                            color = Color.Black.copy(alpha = 0.60f)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Close,
                                                                    contentDescription = "Remove photo",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
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
                                    DimLineTextField(
                                        value = currentAns,
                                        onValueChange = { newText ->
                                            answersMap = answersMap + (q.id to newText)
                                            if (newText.isNotBlank()) validationErrorIds = validationErrorIds - q.id
                                        },
                                        isError = hasError,
                                        isSingleLine = true
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
            isMultiple = true,
            members = groupMembers,
            initialSelected = selectedWorkers,
            onDone = { signers ->
                selectedWorkers = signers
                showSelectWorkersSheet = false

                // Check if applicant included themselves and needs to sign
                val applicantWorker = signers.find { it.uid == submitterUid }
                if (applicantWorker != null && applicantWorker.stamp == null) {
                    if (profileData.signatureUrl.isBlank()) {
                        showNoSignatureDialog = true
                    } else {
                        showApplicantYourSignatureSheet = true
                    }
                }
            },
            onDismiss = { showSelectWorkersSheet = false }
        )
    }

    if (showSelectSupervisorSheet) {
        SelectSignersSheet(
            isMultiple = false,
            members = groupMembers,
            initialSelected = if (selectedSupervisor != null) listOf(selectedSupervisor!!) else emptyList(),
            onDone = { signers ->
                val sup = signers.firstOrNull()
                selectedSupervisor = sup
                showSelectSupervisorSheet = false

                if (sup != null && sup.uid == submitterUid && sup.stamp == null) {
                    if (profileData.signatureUrl.isBlank()) {
                        showNoSignatureDialog = true
                    } else {
                        showApplicantYourSignatureSheet = true
                    }
                }
            },
            onDismiss = { showSelectSupervisorSheet = false }
        )
    }

    if (showApplicantYourSignatureSheet) {
        val userFullName = profileData.firstName + if (profileData.lastName.isNotBlank()) " ${profileData.lastName}" else ""

        val tzOffsetStr = remember {
            ZoneId.systemDefault().rules.getOffset(Instant.now()).id.let {
                if (it == "Z") "GMT+0" else "GMT$it"
            }
        }

        ApplicantYourSignatureSheet(
            formName = groupForm.title,
            fieldLabel = activeSignatureFieldLabel.ifBlank { "Signature" },
            profileFullName = userFullName.ifBlank { submitterName },
            profileBadgeNumber = profileData.badgeNumber,
            profileSignatureUrl = profileData.signatureUrl,
            onSignConfirmed = { stamp ->
                showApplicantYourSignatureSheet = false

                // Apply stamp to applicant in selectedWorkers or selectedSupervisor
                selectedWorkers = selectedWorkers.map { w ->
                    if (w.uid == submitterUid) w.copy(stamp = stamp) else w
                }
                if (selectedSupervisor?.uid == submitterUid) {
                    selectedSupervisor = selectedSupervisor?.copy(stamp = stamp)
                }

                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Signed")
                }
            },
            onNotNow = { showApplicantYourSignatureSheet = false },
            onDismiss = { showApplicantYourSignatureSheet = false }
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

    if (showNoSignatureDialog) {
        AlertDialog(
            onDismissRequest = { showNoSignatureDialog = false },
            title = { Text("Add your signature", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor) },
            text = { Text("Create your signature in Profile once. It becomes your stamp on every form you sign.", fontSize = 14.sp, color = secondaryTextColor) },
            confirmButton = {
                TextButton(onClick = {
                    showNoSignatureDialog = false
                }) {
                    Text("Open Profile", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tealAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoSignatureDialog = false }) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
