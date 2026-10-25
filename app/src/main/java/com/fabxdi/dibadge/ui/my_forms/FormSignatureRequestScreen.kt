package com.fabxdi.dibadge.ui.my_forms

import android.content.res.Configuration
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.FormLogosData
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.data.FormStamp
import com.fabxdi.dibadge.data.FormSubmissionEntity
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.profile.parseSignatureImageBitmap
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.FormViewModel
import com.fabxdi.dibadge.viewmodel.ProfileViewModel
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

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val dimColor = diBadgeColors.tabInactive

    val formTitle = groupForm?.title ?: "Signature Request"
    val questions = remember(groupForm?.questionsJson) {
        if (groupForm != null) parseQuestionsFromJson(groupForm.questionsJson) else emptyList()
    }
    val answersMap = remember(currentSubmission.answersJson) {
        parseMapFromJson(currentSubmission.answersJson)
    }

    val workers = remember(currentSubmission.workersJson) {
        parseSignersFromJson(currentSubmission.workersJson)
    }

    val supervisor = remember(currentSubmission.supervisorJson) {
        parseSingleSignerFromJson(currentSubmission.supervisorJson)
    }

    val signedWorkersCount = workers.count { it.stamp != null }
    val allWorkersSigned = workers.isNotEmpty() && workers.all { it.stamp != null }

    val isUserWorker = workers.any { it.uid == currentUserId }
    val userWorker = workers.find { it.uid == currentUserId }
    val hasWorkerSigned = userWorker?.stamp != null

    val isUserSupervisor = supervisor?.uid == currentUserId
    val hasSupervisorSigned = supervisor?.stamp != null

    fun handleSignWorker() {
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
            fullName = userFullName.ifBlank { submitterNameForUser(currentUser?.displayName) },
            badgeNumber = profileData.badgeNumber,
            signedAtMs = System.currentTimeMillis(),
            tzOffset = tzOffsetStr
        )

        val updatedWorkers = workers.map { w ->
            if (w.uid == currentUserId) w.copy(stamp = stamp) else w
        }

        val updatedSubmission = currentSubmission.copy(
            workersJson = encodeSignersToJson(updatedWorkers)
        )
        currentSubmission = updatedSubmission

        coroutineScope.launch {
            snackbarHostState.showSnackbar("Signed")
        }
    }

    fun handleSignAndSubmitSupervisor() {
        if (!allWorkersSigned) return
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
            fullName = userFullName.ifBlank { submitterNameForUser(currentUser?.displayName) },
            badgeNumber = profileData.badgeNumber,
            signedAtMs = System.currentTimeMillis(),
            tzOffset = tzOffsetStr
        )

        val updatedSupervisor = supervisor?.copy(stamp = stamp)
        val updatedSubmission = currentSubmission.copy(
            supervisorJson = encodeSingleSignerToJson(updatedSupervisor),
            status = "submitted",
            submittedAtMs = System.currentTimeMillis()
        )
        currentSubmission = updatedSubmission

        coroutineScope.launch {
            snackbarHostState.showSnackbar("Form signed and submitted")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = formTitle, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                        Text(text = "${submission.submitterName} asked for your signature", fontSize = 12.sp, color = secondaryTextColor)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryTextColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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

            // Read-only fields
            questions.forEachIndexed { idx, q ->
                val ans = answersMap[q.id]?.ifBlank { "N/A" } ?: "N/A"
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

            Spacer(modifier = Modifier.height(16.dp))

            // Workers Block
            Text("Workers", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
            Spacer(modifier = Modifier.height(8.dp))

            workers.forEach { worker ->
                val isYou = worker.uid == currentUserId
                val displayName = if (isYou) "You" else worker.name

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
                                Text("Signature pending", fontSize = 12.sp, color = secondaryTextColor)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Supervisor Block
            Text("Supervisor", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
            Spacer(modifier = Modifier.height(8.dp))

            if (supervisor != null) {
                val isYou = supervisor.uid == currentUserId
                val displayName = if (isYou) "You" else supervisor.name

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
                        val avatarColor = remember(supervisor.name) { UserColorUtils.getColorForName(supervisor.name) }
                        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = avatarColor) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(supervisor.name.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryTextColor)
                            Spacer(modifier = Modifier.height(2.dp))
                            if (supervisor.stamp != null) {
                                val dateStr = remember(supervisor.stamp.signedAtMs) {
                                    val dt = Instant.ofEpochMilli(supervisor.stamp.signedAtMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
                                    dt.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))
                                }
                                Text("${supervisor.stamp.fullName} · ${supervisor.stamp.badgeNumber} · $dateStr ${supervisor.stamp.tzOffset}", fontSize = 12.sp, color = secondaryTextColor)
                            } else if (!allWorkersSigned) {
                                Text("Opens after all workers have signed ($signedWorkersCount of ${workers.size})", fontSize = 12.sp, color = secondaryTextColor)
                            } else {
                                Text("Ready to sign", fontSize = 12.sp, color = secondaryTextColor)
                            }
                        }

                        if (supervisor.stamp != null && supervisor.stamp.signatureUrl.isNotBlank()) {
                            val sigBitmap = remember(supervisor.stamp.signatureUrl) { parseSignatureImageBitmap(supervisor.stamp.signatureUrl) }
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

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            if (isUserWorker && !hasWorkerSigned) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Sign",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable { handleSignWorker() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            } else if (isUserSupervisor && !hasSupervisorSigned) {
                val canSubmit = allWorkersSigned
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Sign and submit",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSubmit) tealAccent else dimColor.copy(alpha = 0.40f),
                        modifier = Modifier
                            .clickable(enabled = canSubmit) { handleSignAndSubmitSupervisor() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
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

private fun submitterNameForUser(displayName: String?): String {
    return displayName?.ifBlank { null } ?: "Livin Varghese"
}

// ---------------------------------------------------------------------------
// PREVIEWS
// ---------------------------------------------------------------------------

@Preview(name = "Signature Request Worker Ready - Light", widthDp = 412, showBackground = true)
@Composable
fun FormSignatureRequestWorkerReadyLightPreview() {
    val sampleSubmission = FormSubmissionEntity(
        id = "sub1",
        groupId = "g1",
        formId = "f1",
        submitterUid = "owner",
        submitterName = "Project Admin",
        status = "awaitingSignatures",
        workersJson = "[{\"uid\":\"me\",\"name\":\"Livin Varghese\",\"isSupervisor\":false}]",
        supervisorJson = "{\"uid\":\"sup1\",\"name\":\"John Director\",\"isSupervisor\":true}"
    )

    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FormSignatureRequestScreen(
                submission = sampleSubmission,
                groupForm = null,
                onBack = {}
            )
        }
    }
}

@Preview(name = "Signature Request Supervisor Waiting - Light", widthDp = 412, showBackground = true)
@Composable
fun FormSignatureRequestSupervisorWaitingLightPreview() {
    val sampleSubmission = FormSubmissionEntity(
        id = "sub2",
        groupId = "g1",
        formId = "f1",
        submitterUid = "owner",
        submitterName = "Project Admin",
        status = "awaitingSignatures",
        workersJson = "[{\"uid\":\"w1\",\"name\":\"Worker One\",\"isSupervisor\":false}]",
        supervisorJson = "{\"uid\":\"me\",\"name\":\"Livin Varghese\",\"isSupervisor\":true}"
    )

    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FormSignatureRequestScreen(
                submission = sampleSubmission,
                groupForm = null,
                onBack = {}
            )
        }
    }
}

@Preview(
    name = "Signature Request - Dark",
    widthDp = 412,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun FormSignatureRequestDarkPreview() {
    val sampleSubmission = FormSubmissionEntity(
        id = "sub3",
        groupId = "g1",
        formId = "f1",
        submitterUid = "owner",
        submitterName = "Project Admin",
        status = "awaitingSignatures",
        workersJson = "[{\"uid\":\"w1\",\"name\":\"Worker One\",\"isSupervisor\":false}]",
        supervisorJson = "{\"uid\":\"sup1\",\"name\":\"John Director\",\"isSupervisor\":true}"
    )

    DiBadgeTheme(themeMode = AppThemeMode.DARK) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FormSignatureRequestScreen(
                submission = sampleSubmission,
                groupForm = null,
                onBack = {}
            )
        }
    }
}
