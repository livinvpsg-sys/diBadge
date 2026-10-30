package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardToSheet(
    recentChats: List<HomeEntryEntity>,
    groups: List<HomeEntryEntity>,
    isDark: Boolean,
    onSendForward: (List<HomeEntryEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val selectedTargets = remember { mutableStateListOf<HomeEntryEntity>() }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val cardFillColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)
    val tealAccent = if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)
    val pageBgColor = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF)

    val filteredRecent = remember(recentChats, searchQuery) {
        if (searchQuery.isBlank()) recentChats else recentChats.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    val filteredGroups = remember(groups, searchQuery) {
        if (searchQuery.isBlank()) groups else groups.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = cardColor,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = outlineColor
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryTextColor
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Forward to",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { isSearchActive = !isSearchActive },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = primaryTextColor
                    )
                }
            }

            if (isSearchActive) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(40.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = pageBgColor
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text("Search by name...", fontSize = 14.sp, color = dimTextColor)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = primaryTextColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Target List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 380.dp)
            ) {
                if (filteredRecent.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent chats",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(filteredRecent) { entry ->
                        val isSelected = selectedTargets.contains(entry)
                        ForwardTargetRow(
                            entry = entry,
                            isSelected = isSelected,
                            isGroup = false,
                            isDark = isDark,
                            cardFillColor = cardFillColor,
                            outlineColor = outlineColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            pageBgColor = pageBgColor,
                            onToggle = {
                                if (isSelected) {
                                    selectedTargets.remove(entry)
                                } else {
                                    selectedTargets.add(entry)
                                }
                            }
                        )
                    }
                }

                if (filteredGroups.isNotEmpty()) {
                    item {
                        Text(
                            text = "Groups",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(filteredGroups) { entry ->
                        val isSelected = selectedTargets.contains(entry)
                        ForwardTargetRow(
                            entry = entry,
                            isSelected = isSelected,
                            isGroup = true,
                            isDark = isDark,
                            cardFillColor = cardFillColor,
                            outlineColor = outlineColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            pageBgColor = pageBgColor,
                            onToggle = {
                                if (isSelected) {
                                    selectedTargets.remove(entry)
                                } else {
                                    selectedTargets.add(entry)
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = cardColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selectedNamesText = if (selectedTargets.isNotEmpty()) {
                        selectedTargets.joinToString(", ") { it.title }
                    } else "Select chats to forward"

                    Text(
                        text = selectedNamesText,
                        fontSize = 14.sp,
                        color = if (selectedTargets.isNotEmpty()) primaryTextColor else dimTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    val canSend = selectedTargets.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(enabled = canSend) {
                                if (canSend) {
                                    onSendForward(selectedTargets.toList())
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send forward",
                            tint = if (canSend) tealAccent else dimTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardTargetRow(
    entry: HomeEntryEntity,
    isSelected: Boolean,
    isGroup: Boolean,
    isDark: Boolean,
    cardFillColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    pageBgColor: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onToggle() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatarShape = if (isGroup) RoundedCornerShape(10.dp) else CircleShape
        Surface(
            modifier = Modifier.size(36.dp),
            shape = avatarShape,
            color = cardFillColor,
            border = BorderStroke(1.dp, outlineColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = entry.title.take(1).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isGroup) "Group · ${entry.subtitle}" else "Chat",
                fontSize = 12.sp,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isSelected) {
            val tealAccent = if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = tealAccent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (isDark) Color(0xFF0E1114) else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Color.Transparent, CircleShape)
                    .border(BorderStroke(1.5.dp, outlineColor), CircleShape)
            )
        }
    }
}
