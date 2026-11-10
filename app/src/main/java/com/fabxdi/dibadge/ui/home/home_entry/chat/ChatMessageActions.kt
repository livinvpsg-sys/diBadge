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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Shown when a message is long-pressed. The rest of the screen is dimmed but the pressed bubble
 * stays bright where it is, and the actions take the place of the typing box at the bottom.
 */
@Composable
fun MessageActionsOverlay(
    targetMsg: ChatMessage,
    bubbleBounds: Rect?,
    isDark: Boolean,
    cardColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    errorColor: Color,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    val isMine = targetMsg.isSentByMe
    val hasText = targetMsg.text.isNotBlank()
    val canEdit = isMine && !targetMsg.isDeleted && !targetMsg.isForwarded && !targetMsg.isVoiceNote
    val bubble = bubbleBounds?.translate(-rootOrigin)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                rootOrigin = it.positionInWindow()
                rootSize = it.size
            }
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        // Dim everything except the pressed bubble
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                if (bubble != null) {
                    val pad = 2.dp.toPx()
                    addRoundRect(
                        RoundRect(
                            Rect(bubble.left - pad, bubble.top - pad, bubble.right + pad, bubble.bottom + pad),
                            CornerRadius(14.dp.toPx())
                        )
                    )
                }
            }
            drawPath(path, Color.Black.copy(alpha = if (isDark) 0.55f else 0.35f))
        }

        // Actions, in the place of the typing box
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            color = cardColor,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                MessageActionItem(
                    icon = Icons.AutoMirrored.Filled.Reply, label = "Reply",
                    tint = primaryTextColor, onClick = onReply, modifier = Modifier.weight(1f)
                )
                MessageActionItem(
                    icon = Icons.AutoMirrored.Filled.Reply, label = "Forward",
                    tint = primaryTextColor, onClick = onForward, mirrored = true, modifier = Modifier.weight(1f)
                )
                if (hasText) {
                    MessageActionItem(
                        icon = Icons.Default.ContentCopy, label = "Copy", tint = primaryTextColor,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("chat_text", targetMsg.text))
                            Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (canEdit) {
                    MessageActionItem(
                        icon = Icons.Outlined.Edit, label = "Edit",
                        tint = primaryTextColor, onClick = onEdit, modifier = Modifier.weight(1f)
                    )
                }
                if (isMine) {
                    MessageActionItem(
                        icon = Icons.Default.Info, label = "Info",
                        tint = primaryTextColor, onClick = onInfo, modifier = Modifier.weight(1f)
                    )
                }
                MessageActionItem(
                    icon = Icons.Default.CheckCircle, label = "Select",
                    tint = primaryTextColor, onClick = onSelect, modifier = Modifier.weight(1f)
                )
                if (isMine) {
                    MessageActionItem(
                        icon = Icons.Default.Delete, label = "Delete",
                        tint = errorColor, onClick = onDelete, modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer(scaleX = if (mirrored) -1f else 1f)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = label, fontSize = 11.sp, color = tint, maxLines = 1)
    }
}
