package com.fabxdi.dibadge.ui.my_forms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.ChecklistItem
import com.fabxdi.dibadge.data.FormEntity
import com.fabxdi.dibadge.data.FormLogosData
import com.fabxdi.dibadge.data.FormQuestionData
import com.fabxdi.dibadge.data.FormQuestionShowIf
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.FormViewModel
import com.fabxdi.dibadge.viewmodel.encodeLogosToJson
import com.fabxdi.dibadge.viewmodel.encodeQuestionsToJson
import com.fabxdi.dibadge.viewmodel.parseLogosFromJson
import com.fabxdi.dibadge.viewmodel.parseQuestionsFromJson
import java.util.UUID

data class QuestionTypeInfo(
    val type: String,
    val label: String,
    val icon: ImageVector
)

// "Auto-filled" is intentionally not offered any more.
val availableQuestionTypes = listOf(
    QuestionTypeInfo("short_answer", "Short answer", Icons.AutoMirrored.Outlined.ShortText),
    QuestionTypeInfo("long_answer", "Long answer", Icons.AutoMirrored.Outlined.Subject),
    QuestionTypeInfo("number", "Number", Icons.Outlined.Tag),
    QuestionTypeInfo("date", "Date", Icons.Outlined.CalendarToday),
    QuestionTypeInfo("time", "Time", Icons.Outlined.Schedule),
    QuestionTypeInfo("single_choice", "Single choice", Icons.Outlined.RadioButtonChecked),
    QuestionTypeInfo("multiple_choice", "Multiple choice", Icons.Outlined.CheckBox),
    QuestionTypeInfo("dropdown", "Dropdown", Icons.Outlined.IndeterminateCheckBox),
    QuestionTypeInfo("checklist", "Checklist", Icons.Outlined.Checklist),
    QuestionTypeInfo("photos", "Photos", Icons.Outlined.Image),
    QuestionTypeInfo("yesno", "Yes / No", Icons.Outlined.ToggleOn),
    QuestionTypeInfo("declaration", "Declaration", Icons.AutoMirrored.Outlined.FactCheck),
    QuestionTypeInfo("instruction", "Instruction text", Icons.Outlined.Info),
    QuestionTypeInfo("single_signature", "Single signature", Icons.Outlined.Draw),
    QuestionTypeInfo("multiple_signatures", "Multiple signatures", Icons.Outlined.Gesture)
)

/** Types that use the plain "Option 1 / Add option" editor. Checklist is NOT one of them. */
private val choiceTypes = listOf("single_choice", "multiple_choice", "dropdown")

/** Types that can be used as the source of a "Show only if" rule. */
private val showIfSourceTypes = listOf("yesno", "single_choice", "dropdown")

private fun newChecklistItem() = ChecklistItem(id = UUID.randomUUID().toString(), name = "", note = "")

// ---------------------------------------------------------------------------
// Shared dim-line text field (no box, 1dp bottom line, neutral cursor)
// ---------------------------------------------------------------------------
@Composable
fun DimLineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    fontSize: Int = 16
) {
    val colors = MaterialTheme.colorScheme
    val dimColor = DiBadgeTheme.colors.tabInactive
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = fontSize.sp, color = dimColor)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = TextStyle(fontSize = fontSize.sp, color = colors.onBackground),
                cursorBrush = SolidColor(colors.onBackground), // never teal
                modifier = Modifier.fillMaxWidth()
            )
        }
        HorizontalDivider(thickness = 1.dp, color = colors.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormBuilderScreen(
    formToEdit: FormEntity? = null,
    onClose: () -> Unit,
    onSave: (FormEntity) -> Unit,
    formViewModel: FormViewModel = viewModel()
) {
    var title by remember { mutableStateOf(formToEdit?.title ?: "") }
    var description by remember { mutableStateOf(formToEdit?.description ?: "") }
    var questions by remember {
        mutableStateOf(
            if (formToEdit != null) parseQuestionsFromJson(formToEdit.questionsJson) else emptyList()
        )
    }
    var logosData by remember {
        mutableStateOf(
            if (formToEdit != null) parseLogosFromJson(formToEdit.logosJson) else FormLogosData()
        )
    }

    var expandedQuestionId by remember { mutableStateOf<String?>(null) }
    var showAddQuestionSheet by remember { mutableStateOf(false) }
    var showLogosSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var isPreviewMode by remember { mutableStateOf(false) }
    var showIfRemovedMessage by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showIfRemovedMessage) {
        if (showIfRemovedMessage) {
            snackbarHostState.showSnackbar("Show-if rule removed")
            showIfRemovedMessage = false
        }
    }

    val isValid = title.isNotBlank() && questions.isNotEmpty()

    val hasUnsavedChanges = remember(title, description, questions, logosData, formToEdit) {
        if (formToEdit == null) {
            title.isNotBlank() || description.isNotBlank() || questions.isNotEmpty() || logosData.hasLogos()
        } else {
            title != formToEdit.title || description != formToEdit.description ||
                    encodeQuestionsToJson(questions) != formToEdit.questionsJson ||
                    encodeLogosToJson(logosData) != formToEdit.logosJson
        }
    }

    fun handleClose() {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler { handleClose() }

    if (isPreviewMode) {
        FormBuilderPreviewScreen(
            title = title,
            description = description,
            questions = questions,
            logosData = logosData,
            onClosePreview = { isPreviewMode = false }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (formToEdit == null) "New form" else "Edit form",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor
                    )
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
                actions = {
                    Text(
                        text = "Preview",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isValid) tealAccent else dimColor,
                        modifier = Modifier
                            .clickable(enabled = isValid) { isPreviewMode = true }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Save",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isValid) tealAccent else dimColor,
                        modifier = Modifier
                            .clickable(enabled = isValid) {
                                formViewModel.saveUserForm(
                                    formId = formToEdit?.id,
                                    title = title,
                                    description = description,
                                    questions = questions,
                                    onSaved = onSave
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            FormHeaderLogosStrip(
                logos = logosData,
                onClick = { showLogosSheet = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Form title
            Box(modifier = Modifier.fillMaxWidth()) {
                if (title.isEmpty()) {
                    Text("Form title", fontSize = 22.sp, color = dimColor)
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor
                    ),
                    cursorBrush = SolidColor(primaryTextColor), // never teal
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = outlineColor)
            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Box(modifier = Modifier.fillMaxWidth()) {
                if (description.isEmpty()) {
                    Text("Description (optional)", fontSize = 15.sp, color = dimColor)
                }
                BasicTextField(
                    value = description,
                    onValueChange = { description = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        color = primaryTextColor
                    ),
                    cursorBrush = SolidColor(primaryTextColor), // never teal
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAddQuestionSheet = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = tealAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add question",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealAccent
                )
            }

            if (questions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                // Instruction text rows are not counted in the question numbers.
                var counter = 0
                questions.forEachIndexed { index, question ->
                    val isExpanded = expandedQuestionId == question.id
                    val displayIndex: Int? = if (question.type == "instruction") null else {
                        counter += 1
                        counter
                    }
                    val earlier = questions.take(index)

                    QuestionCardItem(
                        index = displayIndex,
                        question = question,
                        earlierQuestions = earlier,
                        isExpanded = isExpanded,
                        onCardClick = {
                            expandedQuestionId = if (isExpanded) null else question.id
                        },
                        onUpdateQuestion = { updatedQ ->
                            questions = questions.map { if (it.id == updatedQ.id) updatedQ else it }
                        },
                        onDuplicate = {
                            val dup = question.copy(
                                id = UUID.randomUUID().toString(),
                                items = question.items.map { it.copy(id = UUID.randomUUID().toString()) }
                            )
                            questions = questions + dup
                        },
                        onDelete = {
                            val deletedId = question.id
                            var removedRule = false
                            questions = questions
                                .filter { it.id != deletedId }
                                .map {
                                    if (it.showIf?.questionId == deletedId) {
                                        removedRule = true
                                        it.copy(showIf = null)
                                    } else it
                                }
                            if (removedRule) showIfRemovedMessage = true
                            if (expandedQuestionId == deletedId) expandedQuestionId = null
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showLogosSheet) {
        FormLogosSheet(
            initialLogos = logosData,
            onDone = { updatedLogos ->
                logosData = updatedLogos
                showLogosSheet = false
            },
            onDismiss = { showLogosSheet = false }
        )
    }

    if (showAddQuestionSheet) {
        AddQuestionTypeSheet(
            onSelectType = { selectedTypeInfo ->
                showAddQuestionSheet = false
                val type = selectedTypeInfo.type
                val newQ = FormQuestionData(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    label = "",
                    isRequired = false,
                    options = if (type in choiceTypes) listOf("Option 1") else emptyList(),
                    requireRemarkOnNotOk = type == "checklist",
                    items = if (type == "checklist") listOf(newChecklistItem()) else emptyList()
                )
                questions = questions + newQ
                expandedQuestionId = newQ.id
            },
            onDismiss = { showAddQuestionSheet = false }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this form?", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor) },
            text = { Text("What you changed will be lost.", fontSize = 14.sp, color = secondaryTextColor) },
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

@Composable
private fun NumberCircle(index: Int?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(22.dp),
        shape = CircleShape,
        color = colors.background,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (index == null) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Instruction",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            } else {
                Text(
                    text = "$index",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val tealAccent = DiBadgeTheme.colors.accent
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = tealAccent
            )
        )
    }
}

@Composable
fun QuestionCardItem(
    index: Int?,
    question: FormQuestionData,
    earlierQuestions: List<FormQuestionData>,
    isExpanded: Boolean,
    onCardClick: () -> Unit,
    onUpdateQuestion: (FormQuestionData) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = DiBadgeTheme.colors.accent
    val isInstruction = question.type == "instruction"

    var showShowIfSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(14.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!isExpanded) {
                // ---------------- Collapsed ----------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        NumberCircle(index)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = question.label.ifBlank {
                                    if (isInstruction) "Instruction" else "Untitled question"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val baseLabel = availableQuestionTypes.find { it.type == question.type }?.label ?: question.type
                            val typeLabel = if (question.type == "checklist") {
                                val n = question.items.size
                                "$baseLabel · $n ${if (n == 1) "item" else "items"}"
                            } else if (question.type == "multiple_signatures" && question.membersFill) {
                                "$baseLabel · Members fill"
                            } else baseLabel
                            val reqSuffix = if (question.isRequired && !isInstruction) " · Required" else ""
                            Text(
                                text = "$typeLabel$reqSuffix",
                                fontSize = 13.sp,
                                color = secondaryTextColor
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // ---------------- Expanded ----------------
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.padding(top = 12.dp)) { NumberCircle(index) }
                    Spacer(modifier = Modifier.width(12.dp))

                    when (question.type) {
                        "declaration" -> DimLineField(
                            value = question.label,
                            onValueChange = { onUpdateQuestion(question.copy(label = it)) },
                            placeholder = "Write the statement",
                            singleLine = false,
                            minLines = 3,
                            modifier = Modifier.weight(1f)
                        )
                        "instruction" -> DimLineField(
                            value = question.label,
                            onValueChange = { onUpdateQuestion(question.copy(label = it)) },
                            placeholder = "Write the instruction",
                            singleLine = false,
                            minLines = 3,
                            modifier = Modifier.weight(1f)
                        )
                        else -> DimLineField(
                            value = question.label,
                            onValueChange = { onUpdateQuestion(question.copy(label = it)) },
                            placeholder = "Question text",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.padding(start = 34.dp)) {
                    when (question.type) {
                        // ---- choice types: options editor ----
                        in choiceTypes -> {
                            question.options.forEachIndexed { optIdx, optText ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DimLineField(
                                        value = optText,
                                        onValueChange = { newOpt ->
                                            val updated = question.options.toMutableList()
                                            updated[optIdx] = newOpt
                                            onUpdateQuestion(question.copy(options = updated))
                                        },
                                        placeholder = "Option ${optIdx + 1}",
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        enabled = question.options.size > 1,
                                        onClick = {
                                            val updated = question.options.toMutableList()
                                            updated.removeAt(optIdx)
                                            onUpdateQuestion(question.copy(options = updated))
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove option", tint = secondaryTextColor)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add option",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = tealAccent,
                                modifier = Modifier
                                    .clickable {
                                        val n = question.options.size + 1
                                        onUpdateQuestion(question.copy(options = question.options + "Option $n"))
                                    }
                                    .padding(vertical = 6.dp)
                            )
                        }

                        // ---- checklist: items with name + note ----
                        "checklist" -> {
                            question.items.forEachIndexed { itemIdx, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        DimLineField(
                                            value = item.name,
                                            onValueChange = { v ->
                                                onUpdateQuestion(
                                                    question.copy(
                                                        items = question.items.map { if (it.id == item.id) it.copy(name = v) else it }
                                                    )
                                                )
                                            },
                                            placeholder = "Item name"
                                        )
                                    }
                                    IconButton(
                                        enabled = question.items.size > 1,
                                        onClick = {
                                            onUpdateQuestion(question.copy(items = question.items.filter { it.id != item.id }))
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove item",
                                            tint = if (question.items.size > 1) secondaryTextColor else secondaryTextColor.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                text = "Add item",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = tealAccent,
                                modifier = Modifier
                                    .clickable { onUpdateQuestion(question.copy(items = question.items + newChecklistItem())) }
                                    .padding(vertical = 6.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SwitchRow(
                                label = "Ask for a remark and photo when Not OK",
                                checked = question.requireRemarkOnNotOk,
                                onChange = { onUpdateQuestion(question.copy(requireRemarkOnNotOk = it)) }
                            )
                        }

                        "yesno" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PreviewChip("Yes")
                                PreviewChip("No")
                            }
                        }

                        "declaration" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(24.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.5.dp, secondaryTextColor)
                                ) {}
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Tick box shown when filling", fontSize = 13.sp, color = secondaryTextColor)
                            }
                        }

                        // ---- multiple signatures: members answer the questions above themselves ----
                        "multiple_signatures" -> {
                            SwitchRow(
                                label = "Members fill the form",
                                checked = question.membersFill,
                                onChange = { onUpdateQuestion(question.copy(membersFill = it)) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (question.membersFill) {
                                    "The questions above, back to the previous signature block, are answered by each selected member separately, then they sign"
                                } else {
                                    "Members only sign. The questions above are filled by the person who sends the form"
                                },
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                        }

                        "auto" -> {
                            Text(
                                "Auto-filled is no longer available. Delete this question or keep it as it is.",
                                fontSize = 13.sp,
                                color = secondaryTextColor
                            )
                        }

                        else -> {}
                    }

                    // ---- Show only if ----
                    if (!isInstruction && earlierQuestions.any { it.type in showIfSourceTypes }) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showShowIfSheet = true }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Show only if", fontSize = 14.sp, color = primaryTextColor, modifier = Modifier.weight(1f))
                            val rule = question.showIf
                            val ruleText = if (rule == null) "Not set" else {
                                val src = earlierQuestions.firstOrNull { it.id == rule.questionId }
                                "${src?.label?.ifBlank { "Question" } ?: "Question"} is ${rule.equals}"
                            }
                            Text(ruleText, fontSize = 13.sp, color = secondaryTextColor, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Bottom bar: Required (not for instruction), copy, delete ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isInstruction) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Required", fontSize = 14.sp, color = primaryTextColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = question.isRequired,
                                onCheckedChange = { onUpdateQuestion(question.copy(isRequired = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = tealAccent
                                )
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDuplicate) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate question", tint = secondaryTextColor)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete question", tint = colors.error)
                        }
                    }
                }
            }
        }
    }

    if (showShowIfSheet) {
        ShowIfSheet(
            earlierQuestions = earlierQuestions.filter { it.type in showIfSourceTypes },
            current = question.showIf,
            onSelect = { rule ->
                onUpdateQuestion(question.copy(showIf = rule))
                showShowIfSheet = false
            },
            onDismiss = { showShowIfSheet = false }
        )
    }
}

@Composable
private fun PreviewChip(text: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = colors.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Show only if: pick an earlier question, then the answer that shows this one
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowIfSheet(
    earlierQuestions: List<FormQuestionData>,
    current: FormQuestionShowIf?,
    onSelect: (FormQuestionShowIf?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tealAccent = DiBadgeTheme.colors.accent
    var chosen by remember { mutableStateOf<FormQuestionData?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.surface,
        scrimColor = Color.Black.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            val q = chosen
            if (q == null) {
                Text("Show only if", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = colors.onBackground)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Choose an earlier question", fontSize = 13.sp, color = colors.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                earlierQuestions.forEach { eq ->
                    Text(
                        text = eq.label.ifBlank { "Untitled question" },
                        fontSize = 16.sp,
                        color = colors.onBackground,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable { chosen = eq }
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }
                if (current != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Remove rule",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.error,
                        modifier = Modifier.clickable { onSelect(null) }.padding(vertical = 8.dp)
                    )
                }
            } else {
                Text(q.label.ifBlank { "Untitled question" }, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = colors.onBackground, maxLines = 1)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Show this question when the answer is", fontSize = 13.sp, color = colors.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                val answers: List<Pair<String, String>> = when (q.type) {
                    "yesno" -> listOf("Yes" to "yes", "No" to "no")
                    else -> q.options.filter { it.isNotBlank() }.map { it to it }
                }
                answers.forEach { (shown, stored) ->
                    Text(
                        text = shown,
                        fontSize = 16.sp,
                        color = if (current?.questionId == q.id && current.equals == stored) tealAccent else colors.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable { onSelect(FormQuestionShowIf(questionId = q.id, equals = stored)) }
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }
                Text(
                    text = "Back",
                    fontSize = 15.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.clickable { chosen = null }.padding(vertical = 10.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddQuestionTypeSheet(
    onSelectType: (QuestionTypeInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val cardBgColor = colors.surface
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant

    // Open fully so the whole list of question types is visible (scrolls on small screens)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = secondaryTextColor.copy(alpha = 0.40f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Add question",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            availableQuestionTypes.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    pair.forEach { typeInfo ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clickable { onSelectType(typeInfo) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = typeInfo.icon,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = typeInfo.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor,
                                maxLines = 1
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview: draws every question type the way the fill screen will show it
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormBuilderPreviewScreen(
    title: String,
    description: String,
    questions: List<FormQuestionData>,
    logosData: FormLogosData,
    onClosePreview: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val dimColor = diBadgeColors.tabInactive

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { "Form Preview" }, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor) },
                navigationIcon = {
                    IconButton(onClick = onClosePreview) {
                        Icon(Icons.Default.Close, contentDescription = "Close preview", tint = primaryTextColor)
                    }
                },
                actions = {
                    Text("Preview", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = dimColor, modifier = Modifier.padding(end = 16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            FormHeaderLogosStrip(logos = logosData)

            Spacer(modifier = Modifier.height(16.dp))

            if (description.isNotBlank()) {
                Text(description, fontSize = 14.sp, color = secondaryTextColor)
                Spacer(modifier = Modifier.height(16.dp))
            }

            var counter = 0
            questions.forEach { q ->
                if (q.type == "instruction") {
                    Text(
                        text = q.label,
                        fontSize = 15.sp,
                        color = primaryTextColor,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                    return@forEach
                }
                counter += 1
                val number = counter

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row {
                            Text("$number. ${q.label.ifBlank { q.type }}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                            if (q.isRequired) Text(" *", fontSize = 16.sp, color = colors.error)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when (q.type) {
                            "short_answer", "long_answer", "number" -> {
                                Box(modifier = Modifier.fillMaxWidth().height(28.dp))
                                HorizontalDivider(thickness = 1.dp, color = colors.outline.copy(alpha = 0.60f))
                            }
                            "date", "time" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (q.type == "date") Icons.Outlined.CalendarToday else Icons.Outlined.Schedule,
                                        contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (q.type == "date") "Select date" else "Select time", fontSize = 14.sp, color = dimColor)
                                }
                            }
                            "single_choice", "multiple_choice" -> {
                                q.options.forEach { opt ->
                                    Row(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (q.type == "single_choice") Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckBoxOutlineBlank,
                                            contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(opt, fontSize = 15.sp, color = primaryTextColor)
                                    }
                                }
                            }
                            "dropdown" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Choose", fontSize = 15.sp, color = dimColor, modifier = Modifier.weight(1f))
                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = secondaryTextColor)
                                }
                                HorizontalDivider(thickness = 1.dp, color = colors.outline.copy(alpha = 0.60f))
                            }
                            "checklist" -> {
                                q.items.forEachIndexed { i, item ->
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(item.name.ifBlank { "Item ${i + 1}" }, fontSize = 15.sp, color = primaryTextColor)
                                        if (item.note.isNotBlank()) {
                                            Text(item.note, fontSize = 12.sp, color = secondaryTextColor)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            PreviewChip("OK")
                                            PreviewChip("Not OK")
                                            PreviewChip("N/A")
                                        }
                                    }
                                    if (i < q.items.size - 1) {
                                        HorizontalDivider(thickness = 1.dp, color = colors.outline.copy(alpha = 0.60f))
                                    }
                                }
                                if (q.requireRemarkOnNotOk) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("A remark and photo open when an item is Not OK", fontSize = 12.sp, color = secondaryTextColor)
                                }
                            }
                            "yesno" -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PreviewChip("Yes")
                                    PreviewChip("No")
                                }
                            }
                            "declaration" -> {
                                Row(verticalAlignment = Alignment.Top) {
                                    Surface(
                                        modifier = Modifier.size(24.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(1.5.dp, secondaryTextColor)
                                    ) {}
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("I agree with the statement above", fontSize = 14.sp, color = secondaryTextColor)
                                }
                            }
                            "photos" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Attach image", fontSize = 14.sp, color = secondaryTextColor)
                                }
                            }
                            "single_signature", "multiple_signatures" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Draw, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tap to sign", fontSize = 14.sp, color = dimColor)
                                }
                            }
                            else -> {}
                        }

                        val rule = q.showIf
                        if (rule != null) {
                            val src = questions.firstOrNull { it.id == rule.questionId }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Shown only if ${src?.label?.ifBlank { "an earlier question" } ?: "an earlier question"} is ${rule.equals}",
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}
