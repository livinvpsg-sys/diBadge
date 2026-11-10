package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.fabxdi.dibadge.util.ScanNameValidator
import com.fabxdi.dibadge.util.UserColorUtils
import java.io.FileOutputStream
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.ui.res.painterResource
import com.fabxdi.dibadge.R
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.components.AttachmentList
import com.fabxdi.dibadge.ui.home.AvatarPhotoCache
import com.fabxdi.dibadge.ui.home.ChatAvatar
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.ChatDetailsScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.AudioMerger
import com.fabxdi.dibadge.util.AudioPlayer
import com.fabxdi.dibadge.util.AudioRecorder
import com.fabxdi.dibadge.util.FileBlocklistValidator
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.MediaCompressor
import com.fabxdi.dibadge.viewmodel.ChatViewModel
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import com.fabxdi.dibadge.viewmodel.GroupDetailsViewModel
import com.fabxdi.dibadge.viewmodel.GroupMember
import com.fabxdi.dibadge.viewmodel.GroupConnectionItem
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The top bar of the chat screen. It shows one of three bars: the message selection bar,
 * the search bar, or the normal title bar. All state lives in ChatScreen; this only draws it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatTopBar(
    currentEntry: HomeEntryEntity,
    messages: List<ChatMessage>,
    selectedMessages: Set<String>,
    isOneToOne: Boolean,
    otherUserNickname: String,
    otherUserPhotoUrl: String,
    otherUserName: String,
    isSearchOpen: Boolean,
    searchQuery: String,
    searchMatchCount: Int,
    searchIndex: Int,
    searchFocusRequester: androidx.compose.ui.focus.FocusRequester,
    cardColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    errorColor: Color,
    onBack: () -> Unit,
    onClearSelection: () -> Unit,
    onReply: (ChatMessage) -> Unit,
    onForward: () -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onInfo: (ChatMessage) -> Unit,
    onDelete: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchOlder: () -> Unit,
    onSearchNewer: () -> Unit
) {
    val context = LocalContext.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor)
                    .statusBarsPadding()
            ) {
                if (selectedMessages.isNotEmpty()) {
                    val selectedList = remember(selectedMessages, messages) {
                        messages.filter { selectedMessages.contains(it.id) }
                    }
                    val isSingleSelected = selectedMessages.size == 1
                    val firstSelectedMsg = selectedList.firstOrNull()
                    val hasTextToCopy = selectedList.any { it.text.isNotBlank() }
                    val allOwnMessages = selectedList.all { it.isSentByMe }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onClearSelection() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection",
                                tint = primaryTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${selectedMessages.size} selected",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor,
                            modifier = Modifier.weight(1f)
                        )

                        // Reply Icon (Only if 1 selected)
                        if (isSingleSelected && firstSelectedMsg != null) {
                            IconButton(
                                onClick = {
                                    onReply(firstSelectedMsg)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = "Reply",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Forward Icon (If any selected)
                        IconButton(
                            onClick = {
                                onForward()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = "Forward",
                                tint = primaryTextColor,
                                modifier = Modifier.size(22.dp).graphicsLayer(scaleX = -1f)
                            )
                        }

                        // Edit Icon (Only one own, editable message)
                        val canEditSelected = isSingleSelected && firstSelectedMsg != null && firstSelectedMsg.isSentByMe &&
                                !firstSelectedMsg.isDeleted && !firstSelectedMsg.isForwarded && !firstSelectedMsg.isVoiceNote
                        if (canEditSelected && firstSelectedMsg != null) {
                            IconButton(
                                onClick = {
                                    onEdit(firstSelectedMsg)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Copy Icon (If text present)
                        if (hasTextToCopy) {
                            IconButton(
                                onClick = {
                                    val combinedText = selectedList.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("chat_text", combinedText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
                                    onClearSelection()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy text",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Info Icon (Only if 1 selected and sent by me)
                        if (isSingleSelected && firstSelectedMsg?.isSentByMe == true) {
                            IconButton(
                                onClick = {
                                    onInfo(firstSelectedMsg)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Message info",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Delete Icon (Only if ALL selected messages were sent by user)
                        if (allOwnMessages) {
                            IconButton(
                                onClick = {
                                    onDelete()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = errorColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                } else if (isSearchOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onCloseSearch() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search",
                                tint = primaryTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(primaryTextColor.copy(alpha = 0.08f))
                                .padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search messages",
                                        fontSize = 15.sp,
                                        color = secondaryTextColor,
                                        maxLines = 1
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { onSearchQueryChange(it) },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 15.sp,
                                        color = primaryTextColor
                                    ),
                                    cursorBrush = SolidColor(primaryTextColor),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                    ),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                        onSearch = { onSearchOlder() }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                )
                            }
                            if (searchQuery.isNotBlank()) {
                                Text(
                                    text = if (searchMatchCount == 0) "No results" else "${searchIndex + 1} of $searchMatchCount",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor,
                                    maxLines = 1
                                )
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        val hasMatches = searchMatchCount > 0
                        IconButton(
                            onClick = { onSearchOlder() },
                            enabled = hasMatches,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Previous match",
                                tint = primaryTextColor.copy(alpha = if (hasMatches) 1f else 0.3f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = { onSearchNewer() },
                            enabled = hasMatches,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Next match",
                                tint = primaryTextColor.copy(alpha = if (hasMatches) 1f else 0.3f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // Back Arrow (24dp icon in 48dp touch area, 12dp from left edge)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 40dp Round Avatar + Group Name / Subtitle
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenDetails() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val entryKey = currentEntry.id.toString()
                        val cachedPhoto = AvatarPhotoCache.photoFor(entryKey)
                        val headerPhoto = if (isOneToOne) otherUserPhotoUrl.ifBlank { cachedPhoto } else cachedPhoto
                        // Same name the home list shows, so the letter matches on every screen
                        val headerTitle = if (isOneToOne && otherUserName.isNotBlank()) otherUserName else currentEntry.title

                        LaunchedEffect(otherUserPhotoUrl, isOneToOne) {
                            if (isOneToOne && otherUserPhotoUrl.isNotBlank()) {
                                AvatarPhotoCache.put(context, entryKey, otherUserPhotoUrl)
                            }
                        }

                        ChatAvatar(
                            title = currentEntry.title,
                            idForColor = entryKey,
                            size = 40.dp,
                            photoUrl = headerPhoto
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = headerTitle,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isOneToOne) {
                                if (otherUserNickname.isNotBlank()) {
                                    Text(
                                        text = otherUserNickname,
                                        fontSize = 13.sp,
                                        color = secondaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                val rawSub = currentEntry.subtitle.trim()
                                val subLower = rawSub.lowercase()
                                val isMemberCount = subLower.endsWith("member") || subLower.endsWith("members")
                                val isPlaceholder = subLower == "test" || subLower == "add group description"
                                val descriptionText = if (isMemberCount || isPlaceholder) "" else rawSub

                                if (descriptionText.isNotBlank()) {
                                    Text(
                                        text = descriptionText,
                                        fontSize = 13.sp,
                                        color = secondaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Search this chat (group details open from the title)
                    IconButton(
                        onClick = { onOpenSearch() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search chat",
                            tint = primaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
}
