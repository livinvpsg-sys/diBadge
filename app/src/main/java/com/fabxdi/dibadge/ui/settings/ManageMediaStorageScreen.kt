package com.fabxdi.dibadge.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.util.FilePickerUtils
import java.io.File

data class ChatMediaStorageItem(
    val chatId: String,
    val chatTitle: String,
    val sizeBytes: Long
)

@Composable
fun ManageMediaStorageScreen(
    homeEntries: List<HomeEntryEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(enabled = true, onBack = onBack)

    var chatStorageItems by remember { mutableStateOf<List<ChatMediaStorageItem>>(emptyList()) }
    var selectedChatItemForDelete by remember { mutableStateOf<ChatMediaStorageItem?>(null) }

    LaunchedEffect(homeEntries) {
        val items = homeEntries.map { entry ->
            val mediaDir = File(context.filesDir, "media/${entry.id}")
            val size = if (mediaDir.exists()) {
                mediaDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            } else 0L
            ChatMediaStorageItem(
                chatId = entry.id.toString(),
                chatTitle = entry.title,
                sizeBytes = size
            )
        }.filter { it.sizeBytes > 0L }.sortedByDescending { it.sizeBytes }

        chatStorageItems = items
    }

    val totalSizeBytes = remember(chatStorageItems) { chatStorageItems.sumOf { it.sizeBytes } }

    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val errorColor = colors.error

    Scaffold(
        topBar = {
            SettingsHeader(
                title = "Media on this phone",
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Total Size Card
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total media size",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                    Text(
                        text = FilePickerUtils.formatFileSize(totalSizeBytes),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (chatStorageItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "No chat media saved on this phone",
                        fontSize = 15.sp,
                        color = secondaryTextColor
                    )
                }
            } else {
                SettingsSectionLabel("Chats by size")

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    items(chatStorageItems, key = { it.chatId }) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = cardBgColor
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.chatTitle,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = FilePickerUtils.formatFileSize(item.sizeBytes),
                                        fontSize = 13.sp,
                                        color = secondaryTextColor
                                    )
                                }

                                Text(
                                    text = "Delete media",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = errorColor,
                                    modifier = Modifier
                                        .clickable { selectedChatItemForDelete = item }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog
    if (selectedChatItemForDelete != null) {
        val targetItem = selectedChatItemForDelete!!
        AlertDialog(
            onDismissRequest = { selectedChatItemForDelete = null },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Delete media from ${targetItem.chatTitle}?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "These files will be removed from this phone. They stay in your Google Drive backup if it includes them.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedChatItemForDelete = null
                        val mediaDir = File(context.filesDir, "media/${targetItem.chatId}")
                        if (mediaDir.exists()) {
                            mediaDir.deleteRecursively()
                        }
                        chatStorageItems = chatStorageItems.filter { it.chatId != targetItem.chatId }
                        Toast.makeText(context, "Media deleted from phone", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "Delete",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = errorColor
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedChatItemForDelete = null }) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        color = secondaryTextColor
                    )
                }
            }
        )
    }
}
