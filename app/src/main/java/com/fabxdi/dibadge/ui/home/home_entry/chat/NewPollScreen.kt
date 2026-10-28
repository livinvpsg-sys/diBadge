package com.fabxdi.dibadge.ui.home.home_entry.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

data class PollOptionInput(
    val id: String,
    var text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPollScreen(
    isDark: Boolean,
    onCreatePoll: (question: String, options: List<String>, allowMultiple: Boolean, showVoters: Boolean) -> Unit,
    onClose: () -> Unit
) {
    var questionText by remember { mutableStateOf("") }
    var optionsList by remember {
        mutableStateOf(
            listOf(
                PollOptionInput("1", ""),
                PollOptionInput("2", "")
            )
        )
    }
    var allowMultiple by remember { mutableStateOf(false) }
    var showVoters by remember { mutableStateOf(true) }

    val pageBgColor = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF)
    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)
    val tealAccent = if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)

    val validOptionCount = optionsList.count { it.text.trim().isNotBlank() }
    val canSend = questionText.trim().isNotBlank() && validOptionCount >= 2

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = pageBgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = primaryTextColor
                    )
                }

                Text(
                    text = "New poll",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )

                Text(
                    text = "Send",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canSend) tealAccent else dimTextColor,
                    modifier = Modifier.clickable(enabled = canSend) {
                        val validOptions = optionsList.map { it.text.trim() }.filter { it.isNotBlank() }
                        onCreatePoll(questionText.trim(), validOptions, allowMultiple, showVoters)
                        onClose()
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Question Section
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Question",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (questionText.isEmpty()) {
                                Text(
                                    text = "Ask a question",
                                    fontSize = 16.sp,
                                    color = dimTextColor
                                )
                            }
                            BasicTextField(
                                value = questionText,
                                onValueChange = { questionText = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    color = primaryTextColor
                                ),
                                cursorBrush = SolidColor(primaryTextColor),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        HorizontalDivider(color = outlineColor, thickness = 1.dp)
                    }
                }

                // Options Section
                item {
                    Text(
                        text = "Options",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )
                }

                itemsIndexed(optionsList) { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (option.text.isEmpty()) {
                                Text(
                                    text = "Option ${index + 1}",
                                    fontSize = 15.sp,
                                    color = dimTextColor
                                )
                            }
                            BasicTextField(
                                value = option.text,
                                onValueChange = { newText ->
                                    optionsList = optionsList.toMutableList().apply {
                                        this[index] = this[index].copy(text = newText)
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    color = primaryTextColor
                                ),
                                cursorBrush = SolidColor(primaryTextColor),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Remove Option Button (hidden if only 2 options)
                        if (optionsList.size > 2) {
                            IconButton(onClick = {
                                optionsList = optionsList.toMutableList().apply { removeAt(index) }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove option",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Add Option Button (max 10)
                if (optionsList.size < 10) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    optionsList = optionsList + PollOptionInput((optionsList.size + 1).toString(), "")
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add option",
                                tint = tealAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add option",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tealAccent
                            )
                        }
                    }
                }

                // Settings Switch Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = cardColor,
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Allow multiple answers",
                                    fontSize = 15.sp,
                                    color = primaryTextColor
                                )
                                Switch(
                                    checked = allowMultiple,
                                    onCheckedChange = { allowMultiple = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = tealAccent
                                    )
                                )
                            }

                            HorizontalDivider(
                                color = outlineColor,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Show who voted",
                                        fontSize = 15.sp,
                                        color = primaryTextColor
                                    )
                                    Text(
                                        text = "Members can see each person's vote",
                                        fontSize = 12.sp,
                                        color = secondaryTextColor
                                    )
                                }
                                Switch(
                                    checked = showVoters,
                                    onCheckedChange = { showVoters = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = tealAccent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
