package com.fabxdi.dibadge.ui.my_forms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AttachFile
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
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.FormQuestionData
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.components.DiBadgeTimePicker
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Shown to the person who sends the form, in place of the questions each selected member answers
 * themselves (a Multiple signatures block with "Members fill the form").
 */
@Composable
fun MembersFillPlaceholder(questionCount: Int) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Filled by each member",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (questionCount == 1) {
                    "1 question is answered by the members you select, then they sign"
                } else {
                    "$questionCount questions are answered by the members you select, then they sign"
                },
                fontSize = 13.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/**
 * Editable cards for the questions of a members-fill block, answered by the signed-in member.
 *
 * @param allQuestions every question of the form (show-if rules may point outside the block)
 * @param blockIds     ids of the questions to draw
 * @param answers      answers keyed by plain question id: shared answers plus this member's own
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberQuestionCards(
    allQuestions: List<FormQuestionData>,
    blockIds: Set<String>,
    answers: Map<String, String>,
    errors: Map<String, String>,
    onAnswerChange: (questionId: String, value: String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tealAccent = DiBadgeTheme.colors.accent
    val dimColor = DiBadgeTheme.colors.tabInactive
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val outlineColor = colors.outline
    val isDark = primaryTextColor.red > 0.5f

    val visible = remember(allQuestions, answers) { FormAnswers.visibleIds(allQuestions, answers) }
    val shown = remember(allQuestions, blockIds, visible) {
        allQuestions.filter { it.id in blockIds && it.id in visible }
    }

    var datePickerFor by remember { mutableStateOf<String?>(null) }
    var timePickerFor by remember { mutableStateOf<String?>(null) }
    var photoFor by remember { mutableStateOf<String?>(null) }
    var checklistPhotoFor by remember { mutableStateOf<Pair<String, String>?>(null) } // questionId, itemId

    fun updateChecklistItem(q: FormQuestionData, itemId: String, transform: (ChecklistAnswer) -> ChecklistAnswer) {
        val current = FormAnswers.parseChecklist(answers[q.id] ?: "").toMutableMap()
        current[itemId] = transform(current[itemId] ?: ChecklistAnswer())
        onAnswerChange(q.id, FormAnswers.encodeChecklist(current))
    }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            val qId = photoFor
            if (uris.isNotEmpty() && qId != null) {
                val existingStr = answers[qId] ?: ""
                val existing = if (existingStr.isNotBlank()) existingStr.split(",") else emptyList()
                onAnswerChange(qId, (existing + uris.map { it.toString() }).distinct().joinToString(","))
            }
        }
    )

    val checklistPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            val target = checklistPhotoFor
            val q = allQuestions.firstOrNull { it.id == target?.first }
            if (uri != null && target != null && q != null) {
                updateChecklistItem(q, target.second) { it.copy(photos = (it.photos + uri.toString()).distinct()) }
            }
        }
    )

    var number = 0
    shown.forEach { q ->
        key(q.id) {
            if (q.type == "instruction") {
                Text(
                    text = q.label,
                    fontSize = 15.sp,
                    color = primaryTextColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            } else {
                number += 1
                val currentAns = answers[q.id] ?: ""
                val errorMessage = errors[q.id]
                val hasError = errorMessage != null

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
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
                            if (q.isRequired) Text(" *", fontSize = 16.sp, color = colors.error)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when (q.type) {
                            "long_answer" -> DimLineTextField(
                                value = currentAns,
                                onValueChange = { onAnswerChange(q.id, it) },
                                isError = hasError,
                                isSingleLine = false
                            )
                            "number" -> DimLineTextField(
                                value = currentAns,
                                onValueChange = { onAnswerChange(q.id, filterNumberInput(it)) },
                                isError = hasError,
                                isSingleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            "photos" -> {
                                val photoUris = if (currentAns.isNotBlank()) currentAns.split(",") else emptyList()
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .clickable {
                                                photoFor = q.id
                                                photoLauncher.launch("image/*")
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
                                                onAnswerChange(q.id, updated.joinToString(","))
                                            }
                                        )
                                    }
                                }
                            }
                            "date" -> {
                                val shownDate = currentAns.ifBlank {
                                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clickable { datePickerFor = q.id },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(text = shownDate, fontSize = 15.sp, color = primaryTextColor)
                                }
                            }
                            "time" -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable { timePickerFor = q.id },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = currentAns.ifBlank { "Select time" },
                                    fontSize = 15.sp,
                                    color = if (currentAns.isBlank()) secondaryTextColor else primaryTextColor
                                )
                            }
                            "single_choice" -> Column {
                                q.options.filter { it.isNotBlank() }.forEach { optionText ->
                                    ChoicePill(
                                        text = optionText,
                                        selected = currentAns == optionText,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        onClick = { onAnswerChange(q.id, optionText) }
                                    )
                                }
                            }
                            "multiple_choice" -> {
                                val picked = FormAnswers.parseMulti(currentAns)
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
                                                onAnswerChange(q.id, FormAnswers.encodeMulti(q.options.filter { it in updated }))
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
                                                    onAnswerChange(q.id, optionText)
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
                            "yesno" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChoicePill("Yes", currentAns.equals("yes", true)) { onAnswerChange(q.id, "yes") }
                                ChoicePill("No", currentAns.equals("no", true)) { onAnswerChange(q.id, "no") }
                            }
                            "declaration" -> {
                                val ticked = currentAns == "true"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAnswerChange(q.id, if (ticked) "" else "true") }
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
                                val checklist = FormAnswers.parseChecklist(currentAns)
                                q.items.forEachIndexed { i, item ->
                                    val a = checklist[item.id] ?: ChecklistAnswer()
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = item.name.ifBlank { "Item ${i + 1}" },
                                            fontSize = 15.sp,
                                            color = primaryTextColor
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("ok" to "OK", "notok" to "Not OK", "na" to "N/A").forEach { (statusKey, label) ->
                                                ChoicePill(
                                                    text = label,
                                                    selected = a.status == statusKey,
                                                    selectedBorder = if (statusKey == "notok") colors.error else null
                                                ) {
                                                    updateChecklistItem(q, item.id) {
                                                        if (statusKey == "notok") it.copy(status = statusKey)
                                                        else it.copy(status = statusKey, remark = "", photos = emptyList())
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
                                                        checklistPhotoFor = q.id to item.id
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
                            // short_answer and anything else
                            else -> DimLineTextField(
                                value = currentAns,
                                onValueChange = { onAnswerChange(q.id, it) },
                                isError = hasError,
                                isSingleLine = true
                            )
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

    val dateQ = datePickerFor
    if (dateQ != null) {
        DiBadgeDatePicker(
            selectedDate = LocalDate.now(),
            onDateSelected = { date ->
                onAnswerChange(dateQ, date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                datePickerFor = null
            },
            onDismiss = { datePickerFor = null }
        )
    }

    val timeQ = timePickerFor
    if (timeQ != null) {
        DiBadgeTimePicker(
            initialTime = LocalTime.now(),
            title = "Select Time",
            onTimeSelected = { time ->
                onAnswerChange(timeQ, time.format(DateTimeFormatter.ofPattern("h:mm a")))
                timePickerFor = null
            },
            onDismiss = { timePickerFor = null }
        )
    }
}

/**
 * Read-only answers of each member for one members-fill signature block.
 *
 * @param showWaiting true for the person who sent the form (sees members who have not answered yet);
 *                    false for everyone else (only members who have signed are listed)
 */
@Composable
fun MemberAnswersSection(
    questions: List<FormQuestionData>,
    sigQuestion: FormQuestionData,
    answersMap: Map<String, String>,
    members: List<FormSigner>,
    showWaiting: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val outlineColor = colors.outline

    val blockQs = remember(questions, sigQuestion.id) { FormAnswers.blockQuestions(questions, sigQuestion.id) }
    val blockIds = remember(blockQs) { blockQs.map { it.id }.toSet() }
    val listed = if (showWaiting) members else members.filter { it.stamp != null }
    if (blockQs.none { it.type != "instruction" } || listed.isEmpty()) return

    Text(
        text = "Answers from members",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = primaryTextColor,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    listed.forEach { member ->
        val view = FormAnswers.memberView(answersMap, member.uid, blockIds)
        val visible = FormAnswers.visibleIds(questions, view)
        val answered = blockIds.any { answersMap.containsKey(FormAnswers.memberKey(member.uid, it)) }
        val statusText = when {
            member.isRejected -> "Rejected"
            member.stamp != null -> "Signed"
            answered -> "Answered"
            else -> "Waiting"
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, if (member.isRejected) colors.error else outlineColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = member.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = if (member.isRejected) colors.error else secondaryTextColor
                    )
                }

                if (!answered) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("No answers yet", fontSize = 13.sp, color = secondaryTextColor)
                } else {
                    var n = 0
                    blockQs.filter { it.id in visible }.forEach { q ->
                        if (q.type == "instruction") return@forEach
                        n += 1
                        val raw = view[q.id] ?: ""
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "$n. ${q.label.ifBlank { q.type }}",
                            fontSize = 13.sp,
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (q.type == "photos" && raw.isNotBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                raw.split(",").forEach { photoUrl ->
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
                                text = FormAnswers.displayText(q, raw).ifBlank { "N/A" },
                                fontSize = 15.sp,
                                color = primaryTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}
