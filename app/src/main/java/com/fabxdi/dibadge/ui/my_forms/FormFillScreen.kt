package com.fabxdi.dibadge.ui.my_forms

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.ArrowDropDown
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
import com.fabxdi.dibadge.data.FormQuestionData
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.components.DiBadgeTimePicker
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

    // Members-fill blocks: question id -> id of the Multiple signatures question that collects it
    val blockOf = remember(questions) { FormAnswers.blockOf(questions) }

    val groupMembers by detailsViewModel.members.collectAsState(initial = emptyList())
    val profileData by profileViewModel.profileData.collectAsState()

    var answersMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var signatureSelectionsMap by remember { mutableStateOf<Map<String, List<FormSigner>>>(emptyMap()) }

    // Show-if: only questions whose rule is met are drawn, checked and submitted.
    val visibleIds = remember(questions, answersMap) { FormAnswers.visibleIds(questions, answersMap) }

    val sigQuestions = remember(questions, visibleIds) {
        questions.filter { it.type in FormAnswers.signatureTypes && it.id in visibleIds }
    }
    val hasSignaturesQuestion = sigQuestions.isNotEmpty()

    var showSelectSignersSheet by remember { mutableStateOf(false) }
    var showApplicantYourSignatureSheet by remember { mutableStateOf(false) }
    var showNoSignatureDialog by remember { mutableStateOf(false) }

    var activeSignatureQuestionId by remember { mutableStateOf<String?>(null) }
    var activeSignatureFieldLabel by remember { mutableStateOf("Signature") }
    var activeIsMultiple by remember { mutableStateOf(true) }

    // questionId -> message shown under the question
    var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    var activeDatePickerQuestionId by remember { mutableStateOf<String?>(null) }
    var activeTimePickerQuestionId by remember { mutableStateOf<String?>(null) }
    var activePhotoQuestionId by remember { mutableStateOf<String?>(null) }
    var checklistPhotoTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // questionId, itemId

    fun setAnswer(questionId: String, value: String) {
        answersMap = answersMap + (questionId to value)
        if (value.isNotBlank()) validationErrors = validationErrors - questionId
    }

    fun updateChecklistItem(q: FormQuestionData, itemId: String, transform: (ChecklistAnswer) -> ChecklistAnswer) {
        val current = FormAnswers.parseChecklist(answersMap[q.id] ?: "").toMutableMap()
        current[itemId] = transform(current[itemId] ?: ChecklistAnswer())
        answersMap = answersMap + (q.id to FormAnswers.encodeChecklist(current))
        validationErrors = validationErrors - q.id
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            val qId = activePhotoQuestionId
            if (uris.isNotEmpty() && qId != null) {
                val existingListStr = answersMap[qId] ?: ""
                val existingList = if (existingListStr.isNotBlank()) existingListStr.split(",") else emptyList()
                val combined = (existingList + uris.map { it.toString() }).distinct()
                setAnswer(qId, combined.joinToString(","))
            }
        }
    )

    val checklistPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            val target = checklistPhotoTarget
            val q = questions.firstOrNull { it.id == target?.first }
            if (uri != null && target != null && q != null) {
                updateChecklistItem(q, target.second) { it.copy(photos = (it.photos + uri.toString()).distinct()) }
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
    val isDark = primaryTextColor.red > 0.5f

    val currentUser = FirebaseAuth.getInstance().currentUser
    val submitterUid = currentUser?.uid ?: "me"
    val submitterName = currentUser?.displayName?.ifBlank { null } ?: "Applicant"

    val hasFillerSigned = remember(signatureSelectionsMap, submitterUid) {
        signatureSelectionsMap.values.flatten().any { it.uid == submitterUid && it.stamp != null }
    }

    // The sender only has to sign when some signature block is not "members fill".
    val needsFillerSign = sigQuestions.any { !FormAnswers.isMembersFill(it) }

    fun senderIsInBlock(sigId: String): Boolean =
        (signatureSelectionsMap[sigId] ?: emptyList()).any { it.uid == submitterUid }

    /** False for questions that other members answer (the sender is not one of them). */
    fun sendersTurn(q: FormQuestionData): Boolean {
        val sigId = blockOf[q.id] ?: return true
        return senderIsInBlock(sigId)
    }

    val hasFilledEntries = remember(answersMap, signatureSelectionsMap) {
        answersMap.values.any { it.isNotBlank() } || signatureSelectionsMap.values.any { it.isNotEmpty() }
    }

    fun handleClose() {
        if (hasFilledEntries) showDiscardDialog = true else onClose()
    }

    BackHandler { handleClose() }

    fun buildSubmitAnswers(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        answersMap.forEach { (id, value) ->
            if (id !in visibleIds) return@forEach
            val sigId = blockOf[id]
            if (sigId == null) {
                out[id] = value
            } else if (senderIsInBlock(sigId)) {
                out[FormAnswers.memberKey(submitterUid, id)] = value
            }
        }
        sigQuestions.filter { FormAnswers.isMembersFill(it) }.forEach { sig ->
            out[FormAnswers.signerKey(sig.id)] =
                (signatureSelectionsMap[sig.id] ?: emptyList()).joinToString(",") { it.uid }
        }
        return out
    }

    fun handleSubmit() {
        if (needsFillerSign && !hasFillerSigned) {
            coroutineScope.launch { snackbarHostState.showSnackbar("Sign first to submit") }
            return
        }

        val emptyMembersBlock = sigQuestions.firstOrNull {
            FormAnswers.isMembersFill(it) && (signatureSelectionsMap[it.id] ?: emptyList()).isEmpty()
        }
        if (emptyMembersBlock != null) {
            val blockName = emptyMembersBlock.label.ifBlank { "the signatures" }
            coroutineScope.launch { snackbarHostState.showSnackbar("Select members for $blockName") }
            return
        }

        val errors = mutableMapOf<String, String>()
        for (q in questions) {
            if (q.id !in visibleIds) continue
            if (!sendersTurn(q)) continue
            val message = FormAnswers.problem(q, answersMap[q.id] ?: "")
            if (message != null) errors[q.id] = message
        }

        if (errors.isNotEmpty()) {
            validationErrors = errors
            // jump to the first problem so it is not missed on long forms
            coroutineScope.launch { scrollState.animateScrollTo(0) }
            return
        }

        validationErrors = emptyMap()

        val allSigners = signatureSelectionsMap
            .filterKeys { it in visibleIds }
            .values.flatten()
        val allSigned = allSigners.isNotEmpty() && allSigners.all { it.stamp != null }
        val submissionStatus = if (hasSignaturesQuestion && !allSigned) "inProgress" else "completed"

        formViewModel.submitFormWithSignatures(
            groupId = groupForm.groupId,
            formId = groupForm.formId,
            formTitle = groupForm.title,
            version = groupForm.version,
            submitterUid = submitterUid,
            submitterName = submitterName,
            // answers of questions hidden by a show-if rule are not saved;
            // the sender's own members-fill answers are stored under their member key,
            // and each members-fill block remembers which members were chosen
            answersMap = buildSubmitAnswers(),
            status = submissionStatus,
            workersJson = encodeSignersToJson(allSigners),
            supervisorJson = "{}",
            onSubmitted = { onSubmitted() }
        )
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
                        Text(text = sublineStr, fontSize = 12.sp, color = secondaryTextColor)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleClose() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = primaryTextColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
                Text(text = groupForm.description, fontSize = 14.sp, color = secondaryTextColor)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Instruction text and signature cards are not numbered.
            var questionNumber = 0

            questions.forEach { q ->
                if (q.id !in visibleIds) return@forEach

                // Questions other members answer are replaced by one note, shown at the start of the block
                val blockSigId = blockOf[q.id]
                if (blockSigId != null && !senderIsInBlock(blockSigId)) {
                    val blockList = FormAnswers.blockQuestions(questions, blockSigId)
                    if (blockList.firstOrNull()?.id == q.id) {
                        MembersFillPlaceholder(questionCount = blockList.count { it.type != "instruction" })
                    }
                    return@forEach
                }

                key(q.id) {
                    val errorMessage = validationErrors[q.id]
                    val hasError = errorMessage != null
                    val currentAns = answersMap[q.id] ?: ""

                    val displayAns = if (q.type == "date" && currentAns.isBlank()) {
                        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    } else currentAns

                    if (q.type == "instruction") {
                        Text(
                            text = q.label,
                            fontSize = 15.sp,
                            color = primaryTextColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )
                    } else if (q.type in FormAnswers.signatureTypes) {
                        val sigIdx = sigQuestions.indexOfFirst { it.id == q.id }
                        val signers = signatureSelectionsMap[q.id] ?: emptyList()

                        val isCompleted = signers.isNotEmpty() && signers.all { it.stamp != null }
                        val isEnabled = sigIdx == 0 || (sigQuestions[sigIdx - 1].let { prevQ ->
                            val prevSigners = signatureSelectionsMap[prevQ.id] ?: emptyList()
                            prevSigners.isNotEmpty() && prevSigners.all { it.stamp != null }
                        })
                        val isMembersFillBlock = FormAnswers.isMembersFill(q)
                        val isDim = !isEnabled && !isCompleted && !isMembersFillBlock
                        val isTappable = isEnabled || isMembersFillBlock || (isDim && hasFillerSigned)

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
                                Text(
                                    text = q.label.ifBlank { "Signature" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDim) secondaryTextColor else primaryTextColor
                                )

                                if (isMembersFillBlock) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Each member answers the questions above, then signs",
                                        fontSize = 12.sp,
                                        color = secondaryTextColor
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

                                                if (isSigned) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Signed",
                                                        tint = tealAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                } else if (isApplicantSigner && (isEnabled || isMembersFillBlock)) {
                                                    Text(
                                                        text = "Sign",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = tealAccent,
                                                        modifier = Modifier
                                                            .clickable {
                                                                activeSignatureFieldLabel = q.label.ifBlank { "Signature" }
                                                                // the sender's own members-fill answers must be complete before signing
                                                                val ownProblems = if (isMembersFillBlock) {
                                                                    FormAnswers.blockQuestions(questions, q.id)
                                                                        .filter { it.id in visibleIds }
                                                                        .mapNotNull { bq ->
                                                                            FormAnswers.problem(bq, answersMap[bq.id] ?: "")?.let { bq.id to it }
                                                                        }
                                                                        .toMap()
                                                                } else emptyMap()
                                                                if (ownProblems.isNotEmpty()) {
                                                                    validationErrors = validationErrors + ownProblems
                                                                    coroutineScope.launch { snackbarHostState.showSnackbar("Answer your questions first") }
                                                                } else if (profileData.signatureUrl.isBlank()) {
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
                        questionNumber += 1
                        val number = questionNumber

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
                                        text = "$number. ${q.label.ifBlank { q.type }}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        modifier = Modifier.weight(1f, fill = false)
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
                                            onValueChange = { setAnswer(q.id, it) },
                                            isError = hasError,
                                            isSingleLine = true
                                        )
                                    }
                                    "long_answer" -> {
                                        DimLineTextField(
                                            value = currentAns,
                                            onValueChange = { setAnswer(q.id, it) },
                                            isError = hasError,
                                            isSingleLine = false
                                        )
                                    }
                                    "number" -> {
                                        DimLineTextField(
                                            value = currentAns,
                                            onValueChange = { setAnswer(q.id, filterNumberInput(it)) },
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
                                                PhotoStrip(
                                                    uris = photoUris,
                                                    onRemove = { index ->
                                                        val updated = photoUris.toMutableList()
                                                        updated.removeAt(index)
                                                        answersMap = answersMap + (q.id to updated.joinToString(","))
                                                    }
                                                )
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
                                            q.options.filter { it.isNotBlank() }.forEach { optionText ->
                                                ChoicePill(
                                                    text = optionText,
                                                    selected = currentAns == optionText,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    onClick = { setAnswer(q.id, optionText) }
                                                )
                                            }
                                        }
                                    }
                                    "multiple_choice" -> {
                                        val picked = remember(currentAns) { FormAnswers.parseMulti(currentAns) }
                                        Column {
                                            q.options.filter { it.isNotBlank() }.forEach { optionText ->
                                                val isPicked = optionText in picked
                                                ChoicePill(
                                                    text = optionText,
                                                    selected = isPicked,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    onClick = {
                                                        val updated = if (isPicked) picked - optionText else picked + optionText
                                                        val ordered = q.options.filter { it in updated }
                                                        setAnswer(q.id, FormAnswers.encodeMulti(ordered))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    "dropdown" -> {
                                        var expanded by remember { mutableStateOf(false) }
                                        Box {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(44.dp)
                                                    .clickable { expanded = true },
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = currentAns.ifBlank { "Choose" },
                                                    fontSize = 15.sp,
                                                    color = if (currentAns.isBlank()) dimColor else primaryTextColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = secondaryTextColor)
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                q.options.filter { it.isNotBlank() }.forEach { optionText ->
                                                    DropdownMenuItem(
                                                        text = { Text(optionText, fontSize = 15.sp, color = primaryTextColor) },
                                                        onClick = {
                                                            setAnswer(q.id, optionText)
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(
                                            thickness = 1.dp,
                                            color = if (hasError) colors.error else outlineColor.copy(alpha = 0.60f)
                                        )
                                    }
                                    "yesno" -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ChoicePill("Yes", currentAns.equals("yes", true)) { setAnswer(q.id, "yes") }
                                            ChoicePill("No", currentAns.equals("no", true)) { setAnswer(q.id, "no") }
                                        }
                                    }
                                    "declaration" -> {
                                        val ticked = currentAns == "true"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { setAnswer(q.id, if (ticked) "" else "true") }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(24.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (ticked) tealAccent else Color.Transparent,
                                                border = BorderStroke(
                                                    1.5.dp,
                                                    when {
                                                        ticked -> tealAccent
                                                        hasError -> colors.error
                                                        else -> secondaryTextColor
                                                    }
                                                )
                                            ) {
                                                if (ticked) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Agreed",
                                                            tint = if (isDark) Color(0xFF0E1114) else Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("I agree", fontSize = 15.sp, color = primaryTextColor)
                                        }
                                    }
                                    "checklist" -> {
                                        val checklist = remember(currentAns) { FormAnswers.parseChecklist(currentAns) }
                                        q.items.forEachIndexed { i, item ->
                                            val a = checklist[item.id] ?: ChecklistAnswer()
                                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                                Text(
                                                    text = item.name.ifBlank { "Item ${i + 1}" },
                                                    fontSize = 15.sp,
                                                    color = primaryTextColor
                                                )
                                                if (item.note.isNotBlank()) {
                                                    Text(item.note, fontSize = 12.sp, color = secondaryTextColor)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf("ok" to "OK", "notok" to "Not OK", "na" to "N/A").forEach { (key, label) ->
                                                        ChoicePill(
                                                            text = label,
                                                            selected = a.status == key,
                                                            selectedBorder = if (key == "notok") colors.error else null
                                                        ) {
                                                            updateChecklistItem(q, item.id) {
                                                                if (key == "notok") it.copy(status = key)
                                                                else it.copy(status = key, remark = "", photos = emptyList())
                                                            }
                                                        }
                                                    }
                                                }

                                                if (a.status == "notok" && q.requireRemarkOnNotOk) {
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    DimLineTextField(
                                                        value = a.remark,
                                                        onValueChange = { v -> updateChecklistItem(q, item.id) { it.copy(remark = v) } },
                                                        placeholder = "Remark",
                                                        isError = hasError && a.remark.isBlank(),
                                                        isSingleLine = false
                                                    )
                                                    Row(
                                                        modifier = Modifier
                                                            .clickable {
                                                                checklistPhotoTarget = q.id to item.id
                                                                checklistPhotoLauncher.launch("image/*")
                                                            }
                                                            .padding(vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Outlined.AttachFile, contentDescription = "Attach image", tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Add photo (optional)", fontSize = 14.sp, color = secondaryTextColor)
                                                    }
                                                    if (a.photos.isNotEmpty()) {
                                                        PhotoStrip(
                                                            uris = a.photos,
                                                            onRemove = { index ->
                                                                updateChecklistItem(q, item.id) {
                                                                    it.copy(photos = it.photos.toMutableList().also { list -> list.removeAt(index) })
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            if (i < q.items.size - 1) {
                                                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.60f))
                                            }
                                        }
                                    }
                                    else -> {
                                        DimLineTextField(
                                            value = currentAns,
                                            onValueChange = { setAnswer(q.id, it) },
                                            isError = hasError,
                                            isSingleLine = true
                                        )
                                    }
                                }

                                if (hasError) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = errorMessage ?: "Required", fontSize = 12.sp, color = colors.error)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val isSubmitEnabled = !needsFillerSign || hasFillerSigned

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
                                coroutineScope.launch { snackbarHostState.showSnackbar("Sign first to submit") }
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

                signatureSelectionsMap = signatureSelectionsMap.mapValues { (_, signers) ->
                    signers.map { s ->
                        if (s.uid == submitterUid && s.stamp == null) s.copy(stamp = stamp) else s
                    }
                }

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

                coroutineScope.launch { snackbarHostState.showSnackbar("Signed") }
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
                setAnswer(qId, date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
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
                setAnswer(qId, time.format(DateTimeFormatter.ofPattern("h:mm a")))
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
                TextButton(onClick = { showDiscardDialog = false; onClose() }) {
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
                TextButton(onClick = { showNoSignatureDialog = false }) {
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

/** Rounded choice chip. Selected = bold text and a text-coloured border (never teal). */
@Composable
internal fun ChoicePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selectedBorder: Color? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.background else Color.Transparent,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) (selectedBorder ?: colors.onBackground) else colors.outline
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = colors.onBackground
            )
        }
    }
}

@Composable
internal fun PhotoStrip(uris: List<String>, onRemove: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        uris.forEachIndexed { index, uriStr ->
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
                        .clickable { onRemove(index) },
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
