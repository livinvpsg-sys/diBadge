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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.ActivityLogEntity
import com.fabxdi.dibadge.data.AppDatabase
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
import com.fabxdi.dibadge.viewmodel.parseLogosFromJson
import com.fabxdi.dibadge.viewmodel.parseQuestionsFromJson
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

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
    val context = LocalContext.current
    val questions = remember(groupForm.questionsJson) {
        parseQuestionsFromJson(groupForm.questionsJson)
    }

    val sigQuestions = remember(questions) {
        questions.filter { it.type in listOf("signatures", "single_signature", "multiple_signatures") }
    }

    val hasSignaturesQuestion = sigQuestions.isNotEmpty()

    val groupMembers by detailsViewModel.members.collectAsState(initial = emptyList())
    val profileData by profileViewModel.profileData.collectAsState()

    var answersMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var signatureSelectionsMap by remember { mutableStateOf<Map<String, List<FormSigner>>>(emptyMap()) }

    var showSelectSignersSheet by remember { mutableStateOf(false) }
    var showApplicantYourSignatureSheet by remember { mutableStateOf(false) }
    var showNoSignatureDialog by remember { mutableStateOf(false) }

    var activeSignatureQuestionId by remember { mutableStateOf<String?>(null) }
    var activeSignatureFieldLabel by remember { mutableStateOf("Signature") }
    var activeIsMultiple by remember { mutableStateOf(true) }

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

    val hasFillerSigned = remember(signatureSelectionsMap, submitterUid) {
        signatureSelectionsMap.values.flatten().any { it.uid == submitterUid && it.stamp != null }
    }

    val hasFilledEntries = remember(answersMap, signatureSelectionsMap) {
        answersMap.values.any { it.isNotBlank() } || signatureSelectionsMap.values.any { it.isNotEmpty() }
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
        if (hasSignaturesQuestion && !hasFillerSigned) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Sign first to submit")
            }
            return
        }

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

            val allSigners = signatureSelectionsMap.values.flatten()
            val allSigned = allSigners.isNotEmpty() && allSigners.all { it.stamp != null }
            val submissionStatus = if (hasSignaturesQuestion && !allSigned) "inProgress" else "completed"

            formViewModel.submitFormWithSignatures(
                groupId = groupForm.groupId,
                formId = groupForm.formId,
                formTitle = groupForm.title,
                version = groupForm.version,
                submitterUid = submitterUid,
                submitterName = submitterName,
                answersMap = answersMap,
                status = submissionStatus,
                workersJson = encodeSignersToJson(allSigners),
                supervisorJson = "{}",
                onSubmitted = {
                    onSubmitted()
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
                    val sigIdx = sigQuestions.indexOfFirst { it.id == q.id }
                    val signers = signatureSelectionsMap[q.id] ?: emptyList()

                    val isCompleted = signers.isNotEmpty() && signers.all { it.stamp != null }
                    val isEnabled = sigIdx == 0 || (sigQuestions[sigIdx - 1].let { prevQ ->
                        val prevSigners = signatureSelectionsMap[prevQ.id] ?: emptyList()
                        prevSigners.isNotEmpty() && prevSigners.all { it.stamp != null }
                    })
                    val isDim = !isEnabled && !isCompleted
                    val isTappable = isEnabled || (isDim && hasFillerSigned)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .alpha(if (isDim) 0.45f else 1.0f),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBgColor,
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = q.label.ifBlank { "Signature" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDim) secondaryTextColor else primaryTextColor
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (signers.isEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clickable(enabled = isTappable) {
                                            activeSignatureQuestionId = q.id
                                            activeSignatureFieldLabel = q.label.ifBlank { "Signature" }
                                            activeIsMultiple = q.type != "single_signature"
                                            showSelectSignersSheet = true
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.background,
                                    border = BorderStroke(1.dp, outlineColor)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = tealAccent, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (q.type == "single_signature") "Select member" else "Select members",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tealAccent
                                        )
                                    }
                                }
                            } else {
                                Column {
                                    signers.forEach { signer ->
                                        val isApplicantSigner = signer.uid == submitterUid
                                        val isSigned = signer.stamp != null

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
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
                                                    } else {
                                                        Text("Waiting", fontSize = 12.sp, color = secondaryTextColor)
                                                    }
                                                }
                                            }

                                            // Small teal tick (18dp) at the right only when that person has signed
                                            if (isSigned) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Signed",
                                                    tint = tealAccent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else if (isApplicantSigner && isEnabled) {
                                                Text(
                                                    text = "Sign",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tealAccent,
                                                    modifier = Modifier
                                                        .clickable {
                                                            activeSignatureFieldLabel = q.label.ifBlank { "Signature" }
                                                            if (profileData.signatureUrl.isBlank()) {
                                                                showNoSignatureDialog = true
                                                            } else {
                                                                showApplicantYourSignatureSheet = true
                                                            }
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (isTappable) {
                                        Text(
                                            text = "Change members",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tealAccent,
                                            modifier = Modifier
                                                .clickable {
                                                    activeSignatureQuestionId = q.id
                                                    activeSignatureFieldLabel = q.label.ifBlank { "Signature" }
                                                    activeIsMultiple = q.type != "single_signature"
                                                    showSelectSignersSheet = true
                                                }
                                                .padding(vertical = 4.dp)
                                        )
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

            val isSubmitEnabled = !hasSignaturesQuestion || hasFillerSigned

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Submit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSubmitEnabled) tealAccent else dimColor,
                    modifier = Modifier
                        .clickable {
                            if (!isSubmitEnabled) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Sign first to submit")
                                }
                            } else {
                                handleSubmit()
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (showSelectSignersSheet && activeSignatureQuestionId != null) {
        val qId = activeSignatureQuestionId!!
        val currentSigners = signatureSelectionsMap[qId] ?: emptyList()

        SelectSignersSheet(
            isMultiple = activeIsMultiple,
            members = groupMembers,
            initialSelected = currentSigners,
            onDone = { updatedSigners ->
                signatureSelectionsMap = signatureSelectionsMap + (qId to updatedSigners)
                showSelectSignersSheet = false
                activeSignatureQuestionId = null
            },
            onDismiss = {
                showSelectSignersSheet = false
                activeSignatureQuestionId = null
            }
        )
    }

    if (showApplicantYourSignatureSheet) {
        val userFullName = profileData.firstName + if (profileData.lastName.isNotBlank()) " ${profileData.lastName}" else ""

        ApplicantYourSignatureSheet(
            formName = groupForm.title,
            fieldLabel = activeSignatureFieldLabel.ifBlank { "Signature" },
            profileFullName = userFullName.ifBlank { submitterName },
            profileBadgeNumber = profileData.badgeNumber,
            profileSignatureUrl = profileData.signatureUrl,
            onSignConfirmed = { stamp ->
                showApplicantYourSignatureSheet = false

                val updatedMap = signatureSelectionsMap.mapValues { (_, signers) ->
                    signers.map { s ->
                        if (s.uid == submitterUid && s.stamp == null) s.copy(stamp = stamp) else s
                    }
                }
                signatureSelectionsMap = updatedMap

                coroutineScope.launch(Dispatchers.IO) {
                    val timeStr = DateTimeFormatter.ofPattern("h:mm a").format(LocalTime.now())
                    val activityLog = ActivityLogEntity(
                        id = UUID.randomUUID().toString(),
                        result = "Signed",
                        kind = "form",
                        title = "You signed ${groupForm.title}",
                        source = groupForm.orgName.ifBlank { groupForm.addedBy },
                        reference = "$activeSignatureFieldLabel · $timeStr",
                        relatedId = groupForm.formId,
                        timestamp = System.currentTimeMillis()
                    )
                    AppDatabase.getDatabase(context).activityLogDao().insertActivityLog(activityLog)
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
