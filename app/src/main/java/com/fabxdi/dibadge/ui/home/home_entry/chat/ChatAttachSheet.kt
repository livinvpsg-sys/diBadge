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

/** The paper-clip sheet: 4 columns, 2 rows. ChatScreen decides what each choice does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachOptionsSheet(
    attachedFiles: List<Uri>,
    isGroupChat: Boolean,
    isDark: Boolean,
    cardColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onDismiss: () -> Unit,
    onStamp: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onAudio: () -> Unit,
    onDocuments: () -> Unit,
    onScan: () -> Unit,
    onLocation: () -> Unit,
    onPoll: () -> Unit
) {
    val context = LocalContext.current
    val activeCategory = remember(attachedFiles) {
        if (attachedFiles.isNotEmpty()) getAttachmentCategory(context, attachedFiles.first()) else null
    }
    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
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
                    color = secondaryTextColor.copy(alpha = 0.40f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            // Row 1: Stamp, Photo, Video, Audio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AttachColumnItem(
                        icon = Icons.Default.CameraAlt,
                        label = "Stamp",
                        isDark = isDark,
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = activeCategory == null || activeCategory == AttachmentCategory.IMAGE
                    ) {
                        onStamp()
                    }
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AttachColumnItem(
                        icon = Icons.Outlined.Image,
                        label = "Photo",
                        isDark = isDark,
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = activeCategory == null || activeCategory == AttachmentCategory.IMAGE
                    ) {
                        onPhoto()
                    }
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AttachColumnItem(
                        icon = Icons.Outlined.VideoLibrary,
                        label = "Video",
                        isDark = isDark,
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = activeCategory == null || activeCategory == AttachmentCategory.VIDEO
                    ) {
                        onVideo()
                    }
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AttachColumnItem(
                        icon = Icons.Outlined.AudioFile,
                        label = "Audio",
                        isDark = isDark,
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = activeCategory == null || activeCategory == AttachmentCategory.AUDIO
                    ) {
                        onAudio()
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Row 2: Documents, Scan, Location, Poll
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AttachColumnItem(
                        icon = Icons.Outlined.Description,
                        label = "Documents",
                        isDark = isDark,
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = activeCategory == null || activeCategory == AttachmentCategory.DOCUMENT
                    ) {
                        onDocuments()
                    }
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AttachColumnItem(
                        icon = Icons.Outlined.DocumentScanner,
                        label = "Scan",
                        isDark = isDark,
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = activeCategory == null || activeCategory == AttachmentCategory.DOCUMENT
                    ) {
                        onScan()
                    }
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AttachColumnItem(
                        icon = Icons.Default.Place,
                        label = "Location",
                        isDark = isDark,
                        outlineColor = outlineColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryTextColor = primaryTextColor,
                        enabled = true
                    ) {
                        onLocation()
                    }
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    if (isGroupChat) {
                        AttachColumnItem(
                            icon = Icons.Default.BarChart,
                            label = "Poll",
                            isDark = isDark,
                            outlineColor = outlineColor,
                            secondaryTextColor = secondaryTextColor,
                            primaryTextColor = primaryTextColor,
                            enabled = true
                        ) {
                            onPoll()
                        }
                    }
                }
            }

            if (activeCategory != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Finish or remove your current attachments first",
                    fontSize = 12.sp,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
