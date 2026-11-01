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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.FormEntity
import com.fabxdi.dibadge.data.FormLogosData
import com.fabxdi.dibadge.data.FormQuestionData
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
    QuestionTypeInfo("single_signature", "Single signature", Icons.Outlined.Draw),
    QuestionTypeInfo("multiple_signatures", "Multiple signatures", Icons.Outlined.Gesture)
)

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

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent

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
                    // Preview text action
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

                    // Save text action
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
            // Top Header: Add logos / Header strip
            FormHeaderLogosStrip(
                logos = logosData,
                onClick = { showLogosSheet = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Form Title Input
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
                    cursorBrush = SolidColor(tealAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = outlineColor)
            Spacer(modifier = Modifier.height(12.dp))

            // Description Input
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
                    cursorBrush = SolidColor(tealAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Add question button row
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

                questions.forEachIndexed { index, question ->
                    val isExpanded = expandedQuestionId == question.id

                    QuestionCardItem(
                        index = index + 1,
                        question = question,
                        isExpanded = isExpanded,
                        onCardClick = {
                            expandedQuestionId = if (isExpanded) null else question.id
                        },
                        onUpdateQuestion = { updatedQ ->
                            questions = questions.map { if (it.id == updatedQ.id) updatedQ else it }
                        },
                        onDuplicate = {
                            val dup = question.copy(id = UUID.randomUUID().toString())
                            questions = questions + dup
                        },
                        onDelete = {
                            questions = questions.filter { it.id != question.id }
                            if (expandedQuestionId == question.id) expandedQuestionId = null
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
                val newQ = FormQuestionData(
                    id = UUID.randomUUID().toString(),
                    type = selectedTypeInfo.type,
                    label = "",
                    isRequired = false,
                    options = if (selectedTypeInfo.type in listOf("single_choice", "multiple_choice", "dropdown", "checklist")) {
                        listOf("Option 1")
                    } else emptyList()
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
fun QuestionCardItem(
    index: Int,
    question: FormQuestionData,
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
                // Collapsed View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = CircleShape,
                            color = colors.background,
                            border = BorderStroke(1.dp, outlineColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$index",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = question.label.ifBlank { "Untitled question" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val typeLabel = availableQuestionTypes.find { it.type == question.type }?.label ?: question.type
                            val reqSuffix = if (question.isRequired) " · Required" else ""
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
                // Expanded View (Editing mode)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = CircleShape,
                        color = colors.background,
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$index",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedTextField(
                        value = question.label,
                        onValueChange = { onUpdateQuestion(question.copy(label = it)) },
                        placeholder = { Text("Question text") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Question options for choice types
                if (question.type in listOf("single_choice", "multiple_choice", "dropdown", "checklist")) {
                    Column(modifier = Modifier.padding(start = 34.dp)) {
                        question.options.forEachIndexed { optIdx, optText ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = optText,
                                    onValueChange = { newOpt ->
                                        val updatedOpts = question.options.toMutableList()
                                        updatedOpts[optIdx] = newOpt
                                        onUpdateQuestion(question.copy(options = updatedOpts))
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(onClick = {
                                    val updatedOpts = question.options.toMutableList()
                                    updatedOpts.removeAt(optIdx)
                                    onUpdateQuestion(question.copy(options = updatedOpts))
                                }) {
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
                                    val nextOptNumber = question.options.size + 1
                                    onUpdateQuestion(question.copy(options = question.options + "Option $nextOptNumber"))
                                }
                                .padding(vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom bar inside card: Required switch, Copy, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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

            // 2-Column Grid of 52dp Rows (Clean text rows without outer borders)
            val chunkedTypes = availableQuestionTypes.chunked(2)
            chunkedTypes.forEach { pair ->
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

            questions.forEachIndexed { idx, q ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row {
                            Text("${idx + 1}. ${q.label.ifBlank { q.type }}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                            if (q.isRequired) Text(" *", fontSize = 16.sp, color = colors.error)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when (q.type) {
                            "short_answer", "long_answer", "number" -> {
                                Box(modifier = Modifier.fillMaxWidth().height(28.dp))
                                HorizontalDivider(thickness = 1.dp, color = colors.outline.copy(alpha = 0.60f))
                            }
                            "photos" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Attach image", fontSize = 14.sp, color = secondaryTextColor)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
