package com.fabxdi.dibadge.ui.my_forms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.FormLogosData
import com.fabxdi.dibadge.data.FormQuestionData
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.data.FormSubmissionEntity
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.profile.parseSignatureImageBitmap
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.PdfSharingUtils
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.FormViewModel
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.encodeMapToJson
import com.fabxdi.dibadge.viewmodel.encodeSignersToJson
import com.fabxdi.dibadge.viewmodel.parseLogosFromJson
import com.fabxdi.dibadge.viewmodel.parseMapFromJson
import com.fabxdi.dibadge.viewmodel.parseQuestionsFromJson
import com.fabxdi.dibadge.viewmodel.parseSignersFromJson
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSubmissionDetailScreen(
    submission: FormSubmissionEntity,
    groupForm: GroupFormEntity?,
    onBack: () -> Unit,
    formViewModel: FormViewModel = viewModel(),
    detailsViewModel: GroupDetailsViewModel = viewModel()
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val groupMembers by detailsViewModel.members.collectAsState(initial = emptyList())
    var currentSubmission by remember { mutableStateOf(submission) }

    var isExportingPdf by remember { mutableStateOf(false) }
    var showSelectSignersSheet by remember { mutableStateOf(false) }
    var activeSignatureQuestionId by remember { mutableStateOf<String?>(null) }
    var activeIsMultiple by remember { mutableStateOf(true) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error
    val cardBgColor = colors.surface

    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUserId = currentUser?.uid ?: "me"
    val isFiller = currentSubmission.submitterUid == currentUserId

    val formTitle = groupForm?.title ?: "Submitted Form"
    val questions = remember(groupForm?.questionsJson) {
        if (groupForm != null) parseQuestionsFromJson(groupForm.questionsJson) else emptyList()
    }

    val answersMap = remember(currentSubmission.answersJson) {
        parseMapFromJson(currentSubmission.answersJson)
    }

    val visibleIds = remember(questions, answersMap) { FormAnswers.visibleIds(questions, answersMap) }

    val sigQuestions = remember(questions, visibleIds) {
        questions.filter { it.type in FormAnswers.signatureTypes && it.id in visibleIds }
    }
    val hasSignaturesQuestion = sigQuestions.isNotEmpty()

    val workers = remember(currentSubmission.workersJson) {
        parseSignersFromJson(currentSubmission.workersJson)
    }

    // Members-fill blocks: those questions are shown per member, not in the main list
    val memberFilledIds = remember(questions) { FormAnswers.blockOf(questions).keys }

    /** Signers of one signature block. Members-fill blocks remember who was chosen; others share the full list. */
    fun workersFor(q: FormQuestionData): List<FormSigner> {
        if (!FormAnswers.isMembersFill(q)) return workers
        val chosen = FormAnswers.signerUids(answersMap, q.id)
        return if (chosen.isEmpty()) workers else workers.filter { it.uid in chosen }
    }

    val isCompleted = currentSubmission.status == "completed" || currentSubmission.status == "submitted" ||
            (workers.isNotEmpty() && workers.all { it.stamp != null && !it.isRejected })

    val statusText = if (hasSignaturesQuestion) {
        if (isCompleted) "Completed" else "In progress"
    } else ""

    val submittedTimeStr = remember(currentSubmission.submittedAtMs) {
        val dt = Instant.ofEpochMilli(currentSubmission.submittedAtMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
        dt.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))
    }

    val sublineInfo = "Submitted by ${currentSubmission.submitterName} · $submittedTimeStr"

    fun handleExportPdf() {
        isExportingPdf = true
        // PDF gets only the questions that were shown, with readable answers
        val pdfQuestions = mutableListOf<FormQuestionData>()
        val pdfAnswers = mutableMapOf<String, String>()
        questions.filter { it.id in visibleIds && it.type != "instruction" && it.id !in memberFilledIds }.forEach {
            pdfQuestions.add(it)
            pdfAnswers[it.id] = FormAnswers.displayText(it, answersMap[it.id] ?: "")
        }
        // each member's own answers follow, labelled with their name
        sigQuestions.filter { FormAnswers.isMembersFill(it) }.forEach { sig ->
            val blockQs = FormAnswers.blockQuestions(questions, sig.id)
            val blockIdSet = blockQs.map { it.id }.toSet()
            workersFor(sig).forEach { member ->
                val view = FormAnswers.memberView(answersMap, member.uid, blockIdSet)
                val visibleForMember = FormAnswers.visibleIds(questions, view)
                blockQs.filter { it.type != "instruction" && it.id in visibleForMember }.forEach { bq ->
                    val key = FormAnswers.memberKey(member.uid, bq.id)
                    pdfQuestions.add(bq.copy(id = key, label = "${bq.label.ifBlank { bq.type }} (${member.name})", showIf = null))
                    pdfAnswers[key] = FormAnswers.displayText(bq, view[bq.id] ?: "")
                }
            }
        }
        coroutineScope.launch {
            val file = PdfSharingUtils.exportFormSubmissionPdf(
                context = context,
                formTitle = formTitle,
                sublineInfo = sublineInfo,
                questions = pdfQuestions,
                answersMap = pdfAnswers,
                workers = workers
            )
            isExportingPdf = false

            if (file != null) {
                val result = snackbarHostState.showSnackbar(
                    message = "${file.name} saved",
                    actionLabel = "Open",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    PdfSharingUtils.openPdfFile(context, file)
                }
            } else {
                snackbarHostState.showSnackbar("Failed to export PDF")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = formTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        val fullSubline = if (statusText.isNotBlank()) "$sublineInfo · $statusText" else sublineInfo
                        Text(
                            text = fullSubline,
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
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
                    if (isExportingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 12.dp),
                            strokeWidth = 2.dp,
                            color = tealAccent
                        )
                    } else {
                        Text(
                            text = "Export PDF",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tealAccent,
                            modifier = Modifier
                                .clickable { handleExportPdf() }
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

            // Form Fields (Read-only, dim line style for text answers, photo thumbnails)
            var shownNumber = 0
            questions.filter { it.type !in FormAnswers.signatureTypes && it.id in visibleIds && it.id !in memberFilledIds }.forEach { q ->
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
                val rawAnswer = answersMap[q.id] ?: ""
                val answer = FormAnswers.displayText(q, rawAnswer).ifBlank { "N/A" }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${idx + 1}. ${q.label.ifBlank { q.type }}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryTextColor
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (q.type == "photos" && rawAnswer.isNotBlank()) {
                            val photoList = rawAnswer.split(",")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                photoList.forEach { photoUrl ->
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = "Submitted photo",
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = answer,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            )
                        }
                    }
                }
            }

            // Signature Sections display rules:
            // - Filler sees every signature section with member names, ticks, stamps and rejections. Can choose members for empty/rejected section.
            // - Other group members see stamps once Completed; never waiting or rejected entries.
            if (isFiller) {
                sigQuestions.forEach { q ->
                    val cardWorkers = workersFor(q)

                    if (FormAnswers.isMembersFill(q)) {
                        MemberAnswersSection(
                            questions = questions,
                            sigQuestion = q,
                            answersMap = answersMap,
                            members = cardWorkers,
                            showWaiting = true
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBgColor,
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = q.label.ifBlank { "Signature" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            if (cardWorkers.isEmpty()) {
                                Text(
                                    text = "No members chosen yet",
                                    fontSize = 14.sp,
                                    color = secondaryTextColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Select members",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tealAccent,
                                    modifier = Modifier
                                        .clickable {
                                            activeSignatureQuestionId = q.id
                                            activeIsMultiple = q.type != "single_signature"
                                            showSelectSignersSheet = true
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            } else {
                                Column {
                                    cardWorkers.forEach { signer ->
                                        val isSigned = signer.stamp != null
                                        val isRejected = signer.isRejected

                                        if (isRejected) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                color = cardBgColor,
                                                border = BorderStroke(1.dp, errorColor)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("${signer.name} · Rejected", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = errorColor)
                                                    if (signer.rejectionReason.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text("Reason: ${signer.rejectionReason}", fontSize = 12.sp, color = secondaryTextColor)
                                                    }
                                                }
                                            }
                                        } else {
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
                                                    val avatarColor = UserColorUtils.getColorForName(signer.name)
                                                    Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = avatarColor) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(signer.name.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(12.dp))

                                                    Column {
                                                        Text(signer.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
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
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Select members",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tealAccent,
                                        modifier = Modifier
                                            .clickable {
                                                activeSignatureQuestionId = q.id
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
            } else if (isCompleted && workers.isNotEmpty()) {
                // Answers of each member who signed (members-fill blocks)
                sigQuestions.filter { FormAnswers.isMembersFill(it) }.forEach { sig ->
                    MemberAnswersSection(
                        questions = questions,
                        sigQuestion = sig,
                        answersMap = answersMap,
                        members = workersFor(sig),
                        showWaiting = false
                    )
                }

                // Other group members see stamps once Completed; never waiting or rejected entries
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Signatures",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        workers.filter { it.stamp != null }.forEach { signer ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(signer.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                                    Text("${signer.stamp!!.fullName} · ${signer.stamp.badgeNumber}", fontSize = 12.sp, color = secondaryTextColor)
                                }
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Signed",
                                    tint = tealAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSelectSignersSheet && activeSignatureQuestionId != null) {
        val qId = activeSignatureQuestionId!!
        val activeQ = questions.firstOrNull { it.id == qId }
        val isMembersBlock = activeQ != null && FormAnswers.isMembersFill(activeQ)
        val blockWorkers = if (activeQ != null) workersFor(activeQ) else workers
        SelectSignersSheet(
            isMultiple = activeIsMultiple,
            members = groupMembers,
            initialSelected = blockWorkers,
            onDone = { updatedSigners ->
                val updatedSub = if (isMembersBlock) {
                    // only this block changes; its chosen members are remembered, and anyone newly
                    // added gets the request to answer and sign
                    val keepUids = blockWorkers.map { it.uid }.toSet()
                    val merged = workers.filter { it.uid !in keepUids } + updatedSigners
                    val allSigned = merged.isNotEmpty() && merged.all { it.stamp != null && !it.isRejected }
                    val newAnswers = answersMap + (FormAnswers.signerKey(qId) to updatedSigners.joinToString(",") { it.uid })
                    currentSubmission.copy(
                        answersJson = encodeMapToJson(newAnswers),
                        workersJson = encodeSignersToJson(merged),
                        status = if (allSigned) "submitted" else "awaitingSignatures"
                    )
                } else {
                    currentSubmission.copy(workersJson = encodeSignersToJson(updatedSigners))
                }
                currentSubmission = updatedSub
                formViewModel.updateSubmission(updatedSub)
                showSelectSignersSheet = false
                activeSignatureQuestionId = null
            },
            onDismiss = {
                showSelectSignersSheet = false
                activeSignatureQuestionId = null
            }
        )
    }
}
