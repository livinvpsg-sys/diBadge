package com.fabxdi.dibadge.ui.my_forms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.FormLogosData
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.data.FormStamp
import com.fabxdi.dibadge.data.FormSubmissionEntity
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.inbox.RejectRequestDialog
import com.fabxdi.dibadge.ui.profile.parseSignatureImageBitmap
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.FormViewModel
import com.fabxdi.dibadge.viewmodel.ProfileViewModel
import com.fabxdi.dibadge.viewmodel.encodeMapToJson
import com.fabxdi.dibadge.viewmodel.encodeSignersToJson
import com.fabxdi.dibadge.viewmodel.encodeSingleSignerToJson
import com.fabxdi.dibadge.viewmodel.parseLogosFromJson
import com.fabxdi.dibadge.viewmodel.parseMapFromJson
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
fun FormSignatureRequestScreen(
    submission: FormSubmissionEntity,
    groupForm: GroupFormEntity?,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit = {},
    formViewModel: FormViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel()
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val profileData by profileViewModel.profileData.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUserId = currentUser?.uid ?: "me"

    var currentSubmission by remember { mutableStateOf(submission) }
    var showNoSignatureDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error

    val formTitle = groupForm?.title ?: "Signature Request"
    val questions = remember(groupForm?.questionsJson) {
        if (groupForm != null) parseQuestionsFromJson(groupForm.questionsJson) else emptyList()
    }
    val answersMap = remember(currentSubmission.answersJson) {
        parseMapFromJson(currentSubmission.answersJson)
    }

    val isApplicant = currentSubmission.submitterUid == currentUserId

    // Members-fill blocks this person was chosen for: they answer those questions here, then sign
    val memberFilledIds = remember(questions) { FormAnswers.blockOf(questions).keys }
    val mySigBlocks = remember(questions, answersMap, currentUserId) {
        questions.filter {
            FormAnswers.isMembersFill(it) && currentUserId in FormAnswers.signerUids(answersMap, it.id)
        }
    }
    val myBlockIds = remember(mySigBlocks, questions) {
        mySigBlocks.flatMap { FormAnswers.blockQuestions(questions, it.id) }.map { it.id }.toSet()
    }
    var memberAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var memberErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // shared answers + what this member has stored before + what they are typing now
    val memberView = remember(answersMap, memberAnswers, myBlockIds) {
        FormAnswers.memberView(answersMap, currentUserId, myBlockIds) + memberAnswers
    }

    val firstSigIndex = remember(questions) {
        questions.indexOfFirst { it.type in listOf("signatures", "single_signature", "multiple_signatures") }
    }
    val visibleIds = remember(questions, answersMap) { FormAnswers.visibleIds(questions, answersMap) }
    val visibleQuestions = remember(questions, isApplicant, firstSigIndex, visibleIds, memberFilledIds) {
        (if (isApplicant || firstSigIndex == -1) questions else questions.take(firstSigIndex))
            .filter { it.id in visibleIds && it.id !in memberFilledIds }
    }

    val workers = remember(currentSubmission.workersJson) {
        parseSignersFromJson(currentSubmission.workersJson)
    }

    val supervisor = remember(currentSubmission.supervisorJson) {
        parseSingleSignerFromJson(currentSubmission.supervisorJson)
    }

    val isUserWorker = workers.any { it.uid == currentUserId }
    val isUserSupervisor = supervisor?.uid == currentUserId

    fun handleSignerSign() {
        // answers to this member's own block come first
        val ownVisible = FormAnswers.visibleIds(questions, memberView)
        if (myBlockIds.isNotEmpty()) {
            val problems = questions
                .filter { it.id in myBlockIds && it.id in ownVisible }
                .mapNotNull { bq -> FormAnswers.problem(bq, memberView[bq.id] ?: "")?.let { bq.id to it } }
                .toMap()
            if (problems.isNotEmpty()) {
                memberErrors = problems
                coroutineScope.launch { snackbarHostState.showSnackbar("Answer your questions first") }
                return
            }
        }

        if (profileData.signatureUrl.isBlank()) {
            showNoSignatureDialog = true
            return
        }

        val tzOffsetStr = ZoneId.systemDefault().rules.getOffset(Instant.now()).id.let {
            if (it == "Z") "GMT+0" else "GMT$it"
        }

        val userFullName = profileData.firstName + if (profileData.lastName.isNotBlank()) " ${profileData.lastName}" else ""

        val stamp = FormStamp(
            signatureUrl = profileData.signatureUrl,
            fullName = userFullName.ifBlank { currentUser?.displayName ?: "User" },
            badgeNumber = profileData.badgeNumber,
            signedAtMs = System.currentTimeMillis(),
            tzOffset = tzOffsetStr
        )

        var updatedWorkers = workers
        var updatedSupervisor = supervisor

        if (isUserWorker) {
            updatedWorkers = workers.map { w ->
                if (w.uid == currentUserId) w.copy(stamp = stamp, isRejected = false) else w
            }
        } else if (isUserSupervisor) {
            updatedSupervisor = supervisor?.copy(stamp = stamp, isRejected = false)
        }

        val checkWorkersSigned = updatedWorkers.isEmpty() || updatedWorkers.all { it.stamp != null && !it.isRejected }
        val checkSupervisorSigned = updatedSupervisor == null || (updatedSupervisor.stamp != null && !updatedSupervisor.isRejected)
        val newStatus = if (checkWorkersSigned && checkSupervisorSigned) "submitted" else "awaitingSignatures"

        // this member's answers are saved under their own key; answers of hidden questions are dropped
        val savedAnswers = answersMap.toMutableMap()
        for (id in myBlockIds) {
            val key = FormAnswers.memberKey(currentUserId, id)
            if (id in ownVisible) savedAnswers[key] = memberView[id] ?: "" else savedAnswers.remove(key)
        }

        val updatedSubmission = currentSubmission.copy(
            answersJson = if (myBlockIds.isNotEmpty()) encodeMapToJson(savedAnswers) else currentSubmission.answersJson,
            workersJson = encodeSignersToJson(updatedWorkers),
            supervisorJson = encodeSingleSignerToJson(updatedSupervisor),
            status = newStatus,
            submittedAtMs = if (newStatus == "submitted") System.currentTimeMillis() else currentSubmission.submittedAtMs
        )
        currentSubmission = updatedSubmission
        formViewModel.updateSubmission(updatedSubmission)

        coroutineScope.launch {
            val msg = if (newStatus == "submitted") "Form submitted" else "Signed"
            snackbarHostState.showSnackbar(msg)
            onBack()
        }
    }

    fun handleReject(reason: String) {
        if (isUserWorker) {
            val updatedWorkers = workers.map { w ->
                if (w.uid == currentUserId) w.copy(isRejected = true, rejectionReason = reason, handledAtMs = System.currentTimeMillis()) else w
            }
            currentSubmission = currentSubmission.copy(workersJson = encodeSignersToJson(updatedWorkers))
        } else if (isUserSupervisor) {
            val updatedSupervisor = supervisor?.copy(isRejected = true, rejectionReason = reason, handledAtMs = System.currentTimeMillis())
            currentSubmission = currentSubmission.copy(supervisorJson = encodeSingleSignerToJson(updatedSupervisor))
        }
        formViewModel.updateSubmission(currentSubmission)

        coroutineScope.launch {
            snackbarHostState.showSnackbar("Request rejected")
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = formTitle, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                        Text(text = "Requested by ${submission.submitterName}", fontSize = 12.sp, color = secondaryTextColor)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = primaryTextColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (!isApplicant) {
                // Bottom bar for signer: "Accept signs with your Profile stamp", Reject & Accept
                Surface(
                    color = colors.surface,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Accept signs with your Profile stamp",
                            fontSize = 12.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reject",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = errorColor,
                                modifier = Modifier
                                    .clickable { showRejectDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            Text(
                                text = "Accept",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tealAccent,
                                modifier = Modifier
                                    .clickable { handleSignerSign() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val logosData = remember(groupForm?.logosJson) {
            if (groupForm != null) parseLogosFromJson(groupForm.logosJson)
            else FormLogosData()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            FormHeaderLogosStrip(logos = logosData)

            // Form fields above signature question for signers, or full form for applicant
            var shownNumber = 0
            visibleQuestions.forEach { q ->
                if (q.type == "instruction") {
                    Text(
                        text = q.label,
                        fontSize = 15.sp,
                        color = primaryTextColor,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                    return@forEach
                }
                shownNumber += 1
                val idx = shownNumber - 1
                val ans = FormAnswers.displayText(q, answersMap[q.id] ?: "").ifBlank { "N/A" }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${idx + 1}. ${q.label.ifBlank { q.type }}", fontSize = 14.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(ans, fontSize = 16.sp, color = primaryTextColor)
                    }
                }
            }

            // Members chosen for a "Members fill the form" block answer their own questions here
            if (!isApplicant && mySigBlocks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your answers",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                MemberQuestionCards(
                    allQuestions = questions,
                    blockIds = myBlockIds,
                    answers = memberView,
                    errors = memberErrors,
                    onAnswerChange = { qId, value ->
                        memberAnswers = memberAnswers + (qId to value)
                        if (value.isNotBlank()) memberErrors = memberErrors - qId
                    }
                )
            }

            // Signer states rendered ONLY for Applicant
            if (isApplicant) {
                Spacer(modifier = Modifier.height(16.dp))

                // what each chosen member answered so far (members-fill blocks)
                questions.filter { FormAnswers.isMembersFill(it) && it.id in visibleIds }.forEach { sig ->
                    val chosen = FormAnswers.signerUids(answersMap, sig.id)
                    val sigMembers = if (chosen.isEmpty()) workers else workers.filter { it.uid in chosen }
                    MemberAnswersSection(
                        questions = questions,
                        sigQuestion = sig,
                        answersMap = answersMap,
                        members = sigMembers,
                        showWaiting = true
                    )
                }

                Text("Workers", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                Spacer(modifier = Modifier.height(8.dp))

                workers.forEach { worker ->
                    val isYou = worker.uid == currentUserId
                    val displayName = if (isYou) "${worker.name} (You)" else worker.name

                    if (worker.isRejected) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = cardBgColor,
                            border = BorderStroke(1.dp, errorColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("$displayName · Rejected", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = errorColor)
                                if (worker.rejectionReason.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Reason: ${worker.rejectionReason}", fontSize = 12.sp, color = secondaryTextColor)
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = cardBgColor,
                            border = BorderStroke(1.dp, outlineColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val avatarColor = remember(worker.name) { UserColorUtils.getColorForName(worker.name) }
                                Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = avatarColor) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(worker.name.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryTextColor)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (worker.stamp != null) {
                                        val dateStr = remember(worker.stamp.signedAtMs) {
                                            val dt = Instant.ofEpochMilli(worker.stamp.signedAtMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
                                            dt.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))
                                        }
                                        Text("${worker.stamp.fullName} · ${worker.stamp.badgeNumber} · $dateStr ${worker.stamp.tzOffset}", fontSize = 12.sp, color = secondaryTextColor)
                                    } else {
                                        Text("$displayName · Waiting", fontSize = 12.sp, color = secondaryTextColor)
                                    }
                                }

                                if (worker.stamp != null && worker.stamp.signatureUrl.isNotBlank()) {
                                    val sigBitmap = remember(worker.stamp.signatureUrl) { parseSignatureImageBitmap(worker.stamp.signatureUrl) }
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
    }

    if (showRejectDialog) {
        RejectRequestDialog(
            requesterName = submission.submitterName,
            onConfirmReject = { reason ->
                showRejectDialog = false
                handleReject(reason)
            },
            onDismiss = { showRejectDialog = false }
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
                    onOpenProfile()
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
