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

data class ChatMessage(
    val id: String,
    val text: String,
    val senderName: String,
    val isSentByMe: Boolean,
    val attachments: List<String> = emptyList(),
    val attachmentDurations: Map<String, Long> = emptyMap(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val replyingTo: ChatMessage? = null,
    val replyingToAttachmentUri: String? = null,
    val isEdited: Boolean = false,
    val isGroupMention: Boolean = false,
    val threadId: String? = null,
    val commentCount: Int = 0,
    val unreadCommentCount: Int = 0,
    val sendingGroupName: String? = null,
    val isCrossPostFailed: Boolean = false,
    val mentionedGroupNames: List<String> = emptyList(),
    val mentionedGroupDetails: List<GroupDeliveryStatus> = emptyList(),
    val latestCommentAuthor: String? = null,
    val latestCommentText: String? = null,
    val msgType: String = "text",
    val libraryFileId: String? = null,
    val libraryCategoryId: String? = null,
    val libraryCategoryName: String? = null,
    val libraryFileName: String? = null,
    val libraryCount: Int = 1,
    val libraryActorUid: String? = null,
    val libraryActorName: String? = null,
    val libraryRevision: String? = null,
    val locationLat: Double = 0.0,
    val locationLng: Double = 0.0,
    val locationAccuracy: Int = 0,
    val locationPlaceName: String? = null,
    val isLiveLocation: Boolean = false,
    val liveExpiresAt: Long = 0L,
    val isLiveEnded: Boolean = false,
    val pollQuestion: String = "",
    val pollOptions: List<PollOptionItem> = emptyList(),
    val pollAllowMultiple: Boolean = false,
    val pollShowVoters: Boolean = true,
    val isPollClosed: Boolean = false,
    val pollEndsAt: Long = 0L,   // 0 = no end date
    val userVotedOptionIds: Set<String> = emptySet(),
    val pollVoters: Map<String, List<String>> = emptyMap(), // option id -> voter names
    val isVoiceNote: Boolean = false,
    val isListened: Boolean = false,
    val waveformArray: List<Int> = emptyList(),
    val isForwarded: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedByRole: String? = null,
    val isHiddenForMe: Boolean = false,
    val sendStatus: String = "SENT"
)

/** Where the files waiting in the message box came from. The left button follows the latest one. */
enum class AttachSource { PICKER, CAMERA, STAMP, SCAN }

data class PollOptionItem(
    val id: String,
    val text: String,
    val voteCount: Int = 0 )
