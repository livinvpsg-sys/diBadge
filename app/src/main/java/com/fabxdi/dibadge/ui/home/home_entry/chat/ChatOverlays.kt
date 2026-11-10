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
 * Overlays that ChatScreen opens on top of the chat: share location, end poll, poll votes,
 * PDF viewer, document info and the failed message sheet. All state stays in ChatScreen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatOverlays(
    chatViewModel: ChatViewModel,
    currentEntry: HomeEntryEntity,
    isDark: Boolean,
    currentUserId: String,
    showShareLocationSheet: Boolean,
    onDismissShareLocation: () -> Unit,
    pollToEnd: ChatMessage?,
    onDismissPollToEnd: () -> Unit,
    selectedPollForVotes: ChatMessage?,
    onDismissVotes: () -> Unit,
    selectedPdfUri: Uri?,
    onClosePdf: () -> Unit,
    selectedDocInfoUri: Uri?,
    isDocInfoPreSend: Boolean,
    onCloseDocInfo: () -> Unit,
    attachedFiles: List<Uri>,
    onRemoveAttached: (Uri) -> Unit,
    selectedFailedMessage: ChatMessage?,
    onDismissFailed: () -> Unit
) {
    val context = LocalContext.current

    if (showShareLocationSheet) {
        ShareLocationSheet(
            isDark = isDark,
            onSendCurrentLocation = { lat, lng, place, accuracy ->
                chatViewModel.sendLocationMessage(
                    context = context,
                    groupId = currentEntry.id.toString(),
                    lat = lat,
                    lng = lng,
                    placeName = place,
                    accuracy = accuracy,
                    isLive = false
                )
            },
            onSendLiveLocation = { lat, lng, place, durationMins, accuracy ->
                chatViewModel.sendLocationMessage(
                    context = context,
                    groupId = currentEntry.id.toString(),
                    lat = lat,
                    lng = lng,
                    placeName = place,
                    accuracy = accuracy,
                    isLive = true,
                    durationMins = durationMins
                )
            },
            onDismiss = { onDismissShareLocation() }
        )
    }

    pollToEnd?.let { poll ->
        AlertDialog(
            onDismissRequest = { onDismissPollToEnd() },
            title = { Text("End this poll?") },
            text = { Text("Nobody can vote after this. The results stay visible.") },
            confirmButton = {
                TextButton(onClick = {
                    chatViewModel.closePoll(currentEntry.id.toString(), poll)
                    onDismissPollToEnd()
                }) { Text("End poll", color = Color(0xFFD93025)) }
            },
            dismissButton = {
                TextButton(onClick = { onDismissPollToEnd() }) { Text("Cancel") }
            }
        )
    }

    if (selectedPollForVotes != null) {
        val targetPoll = selectedPollForVotes!!
        val optionGroups = targetPoll.pollOptions.map { opt ->
            val iVoted = targetPoll.userVotedOptionIds.contains(opt.id)
            PollVoterOptionGroup(
                optionText = opt.text,
                voters = if (iVoted) listOf(PollVoter(currentUserId, "You")) else emptyList(),
                otherVoterCount = (opt.voteCount - if (iVoted) 1 else 0).coerceAtLeast(0)
            )
        }

        VotesSheet(
            optionGroups = optionGroups,
            isDark = isDark,
            onDismiss = { onDismissVotes() }
        )
    }

    if (selectedPdfUri != null) {
        val pdfUri = selectedPdfUri!!
        PdfViewerScreen(
            fileUri = pdfUri,
            fileName = FilePickerUtils.getFileName(context, pdfUri),
            isDark = isDark,
            onClose = { onClosePdf() },
            onDelete = if (attachedFiles.contains(pdfUri)) {
                {
                    onRemoveAttached(pdfUri)
                    onClosePdf()
                }
            } else null
        )
    }

    if (selectedDocInfoUri != null) {
        val uri = selectedDocInfoUri!!
        val fileName = FilePickerUtils.getFileName(context, uri)
        InAppDocInfoScreen(
            fileUri = uri,
            fileName = fileName,
            isPreSend = isDocInfoPreSend,
            isDark = isDark,
            onClose = { onCloseDocInfo() },
            onDelete = {
                onRemoveAttached(uri)
                onCloseDocInfo()
            }
        )
    }

    selectedFailedMessage?.let { failedMessage ->
        FailedMessageSheet(
            message = failedMessage,
            onResend = {
                chatViewModel.resendMessage(context, currentEntry.id.toString(), failedMessage.id)
                onDismissFailed()
            },
            onDelete = {
                chatViewModel.deleteForMe(context, currentEntry.id.toString(), setOf(failedMessage.id))
                onDismissFailed()
            },
            onDismiss = { onDismissFailed() }
        )
    }
}
