package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

data class PollOptionInput(
    val id: String,
    var text: String
)

private const val MAX_POLL_OPTIONS = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPollScreen(
    isDark: Boolean,
    onCreatePoll: (question: String, options: List<String>, allowMultiple: Boolean, showVoters: Boolean, endsAtMillis: Long) -> Unit,
    onClose: () -> Unit
) {
    var questionText by remember { mutableStateOf("") }
    var optionsList by remember {
        mutableStateOf(
            listOf(
                PollOptionInput(java.util.UUID.randomUUID().toString(), ""),
                PollOptionInput(java.util.UUID.randomUUID().toString(), "")
            )
        )
    }
    var allowMultiple by remember { mutableStateOf(false) }
    var showVoters by remember { mutableStateOf(true) }
    var endsAt by remember { mutableStateOf(0L) }          // 0 = no end date
    var endChoice by remember { mutableStateOf(EndChoice.NONE) }
    var showEndSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pageBg = if (isDark) Color(0xFF0F1415) else Color(0xFFF5F8F8)
    val card = if (isDark) Color(0xFF1A2123) else Color.White
    val line = if (isDark) Color(0xFF2B3638) else Color(0xFFDCE5E5)
    val textMain = if (isDark) Color(0xFFE6ECEC) else Color(0xFF10201F)
    val textSub = if (isDark) Color(0xFF8FA0A0) else Color(0xFF6B7B7B)
    val teal = if (isDark) Color(0xFF2EC4B6) else Color(0xFF0F9D8F)
    val onTeal = if (isDark) Color(0xFF06201D) else Color.White

    val validOptionCount = optionsList.count { it.text.trim().isNotBlank() }
    val canSend = questionText.trim().isNotBlank() && validOptionCount >= 2

    androidx.activity.compose.BackHandler(onBack = onClose)

    Surface(modifier = Modifier.fillMaxSize(), color = pageBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ---- Top bar: close, title, Send pill ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(card)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textMain, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "New poll",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textMain,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (canSend) teal else line)
                        .clickable(enabled = canSend) {
                            val validOptions = optionsList.map { it.text.trim() }.filter { it.isNotBlank() }
                            onCreatePoll(questionText.trim(), validOptions, allowMultiple, showVoters, endsAt)
                            onClose()
                        }
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Send",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSend) onTeal else textSub
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ---- Question (no length limit) ----
                item { SectionLabel("QUESTION", textSub) }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = card,
                        border = BorderStroke(1.dp, line)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (questionText.isEmpty()) {
                                Text("Ask a question", fontSize = 18.sp, color = textSub)
                            }
                            BasicTextField(
                                value = questionText,
                                onValueChange = { questionText = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, color = textMain),
                                cursorBrush = SolidColor(teal),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ---- Options ----
                item { SectionLabel("OPTIONS", textSub) }
                itemsIndexed(optionsList, key = { _, o -> o.id }) { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(card)
                            .border(1.dp, line, RoundedCornerShape(14.dp))
                            .heightIn(min = 52.dp)
                            .padding(start = 16.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (option.text.isEmpty()) {
                                Text("Option ${index + 1}", fontSize = 16.sp, color = textSub)
                            }
                            BasicTextField(
                                value = option.text,
                                onValueChange = { newText ->
                                    optionsList = optionsList.toMutableList().apply {
                                        this[index] = this[index].copy(text = newText)
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, color = textMain),
                                cursorBrush = SolidColor(teal),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (optionsList.size > 2) {
                            IconButton(
                                onClick = { optionsList = optionsList.toMutableList().apply { removeAt(index) } },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove option",
                                    tint = textSub,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (optionsList.size < MAX_POLL_OPTIONS) {
                    item {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    optionsList = optionsList + PollOptionInput(java.util.UUID.randomUUID().toString(), "")
                                }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = teal, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add option", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = teal)
                        }
                    }
                }

                // ---- Settings ----
                item { SectionLabel("SETTINGS", textSub) }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = card,
                        border = BorderStroke(1.dp, line)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            SettingRow(
                                icon = Icons.Default.DoneAll,
                                title = "Allow multiple answers",
                                checked = allowMultiple,
                                onChange = { allowMultiple = it },
                                teal = teal, onTeal = onTeal, line = line, textMain = textMain, textSub = textSub
                            )
                            HorizontalDivider(color = line, thickness = 1.dp)
                            SettingRow(
                                icon = Icons.Default.Groups,
                                title = "Show who voted",
                                checked = showVoters,
                                onChange = { showVoters = it },
                                teal = teal, onTeal = onTeal, line = line, textMain = textMain, textSub = textSub
                            )
                            HorizontalDivider(color = line, thickness = 1.dp)
                            EndDateRow(
                                valueText = if (endsAt > 0L) formatPollEnd(endsAt) else "No end date",
                                hasValue = endsAt > 0L,
                                onClick = { showEndSheet = true },
                                teal = teal, textMain = textMain, textSub = textSub
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showEndSheet) {
        EndDateSheet(
            choice = endChoice,
            card = card, textMain = textMain, textSub = textSub, teal = teal,
            onPick = { choice, millis ->
                endChoice = choice
                endsAt = millis
                showEndSheet = false
            },
            onCustom = {
                showEndSheet = false
                val cal = Calendar.getInstance()
                cal.timeInMillis = if (endsAt > 0L) endsAt else System.currentTimeMillis() + 24L * 3600_000L
                val dateDialog = DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val picked = Calendar.getInstance().apply {
                                    set(year, month, day, hour, minute, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                if (picked <= System.currentTimeMillis() + 60_000L) {
                                    Toast.makeText(context, "Choose a time in the future", Toast.LENGTH_SHORT).show()
                                } else {
                                    endsAt = picked
                                    endChoice = EndChoice.CUSTOM
                                }
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                dateDialog.datePicker.minDate = System.currentTimeMillis() - 1000L
                dateDialog.show()
            },
            onDismiss = { showEndSheet = false }
        )
    }
}

private enum class EndChoice { NONE, H1, H24, D3, W1, CUSTOM }

/** "Today, 5:00 pm", "Tomorrow, 6:00 pm" or "12 Nov, 6:00 pm". Also used by the poll card in the chat. */
fun formatPollEnd(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    val today = LocalDate.now(zone)
    val time = dt.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).lowercase()
    val day = when (dt.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> dt.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
    return "$day, $time"
}

@Composable
private fun EndDateRow(
    valueText: String,
    hasValue: Boolean,
    onClick: () -> Unit,
    teal: Color,
    textMain: Color,
    textSub: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(textSub.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = textSub, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("End date", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textMain, modifier = Modifier.weight(1f))
        Text(
            text = valueText,
            fontSize = 13.sp,
            fontWeight = if (hasValue) FontWeight.SemiBold else FontWeight.Normal,
            color = if (hasValue) teal else textSub,
            maxLines = 1
        )
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = textSub, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndDateSheet(
    choice: EndChoice,
    card: Color,
    textMain: Color,
    textSub: Color,
    teal: Color,
    onPick: (EndChoice, Long) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val hour = 3600_000L
    val options = listOf(
        Triple(EndChoice.NONE, "No end date", 0L),
        Triple(EndChoice.H1, "In 1 hour", now + hour),
        Triple(EndChoice.H24, "In 24 hours", now + 24 * hour),
        Triple(EndChoice.D3, "In 3 days", now + 72 * hour),
        Triple(EndChoice.W1, "In 1 week", now + 168 * hour)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = card) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                "End date",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textMain,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            options.forEach { (c, label, millis) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(c, millis) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontSize = 15.sp, color = textMain)
                    if (millis > 0L) {
                        Spacer(Modifier.width(10.dp))
                        Text(formatPollEnd(millis), fontSize = 12.sp, color = textSub)
                    }
                    Spacer(Modifier.weight(1f))
                    RadioButton(
                        selected = choice == c,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = teal, unselectedColor = textSub)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCustom)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pick date and time\u2026", fontSize = 15.sp, color = textMain)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = textSub, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    teal: Color,
    onTeal: Color,
    line: Color,
    textMain: Color,
    textSub: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(textSub.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = textSub, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textMain, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = teal,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = line,
                uncheckedBorderColor = line
            )
        )
    }
}