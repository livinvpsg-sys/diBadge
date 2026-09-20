package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.net.Uri
import android.view.WindowManager
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.fabxdi.dibadge.viewmodel.GroupMediaItem
import com.fabxdi.dibadge.viewmodel.GroupMember

@Composable
fun EditNameDialog(
    show: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = null,
        text = {
            Column {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = "Name",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    thickness = 1.dp,
                    color = Color.LightGray.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8ECEF),
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFFF0F0F0),
                    disabledContentColor = Color.Gray
                ),
                shape = CircleShape,
                enabled = input.isNotBlank()
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun EditDescriptionDialog(
    show: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = null,
        text = {
            Column {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = "Details",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    thickness = 1.dp,
                    color = Color.LightGray.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8ECEF),
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFFF0F0F0),
                    disabledContentColor = Color.Gray
                ),
                shape = CircleShape
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun GroupCodeDialog(
    show: Boolean,
    groupCode: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    if (!show) return
    val displayCode = groupCode.ifBlank { "A7B9-K2%X" }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = null,
        text = {
            Column {
                TextField(
                    value = displayCode,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    thickness = 1.dp,
                    color = Color.LightGray.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCopy(displayCode) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8ECEF),
                    contentColor = Color.Black
                ),
                shape = CircleShape
            ) {
                Text("Copy", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun AddMemberDialog(
    show: Boolean,
    isAdmin: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    candidates: List<GroupMember>,
    onAddCandidate: (GroupMember) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Member") },
        text = {
            Column {
                Text(
                    text = if (isAdmin) "Search by phone number or email:" else "Search your personal connections:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Phone or email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                candidates.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddCandidate(candidate) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = candidate.displayName,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DeleteMediaDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForAll: () -> Unit
) {
    if (!show) return
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(220.dp)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onDeleteForMe,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE8ECEF),
                            contentColor = Color.Black
                        ),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Delete for me", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDeleteForAll,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE8ECEF),
                            contentColor = Color.Black
                        ),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Delete for all", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarCropDialog(
    pendingAvatarUri: Uri?,
    avatarRotationAngle: Float,
    onRotate: () -> Unit,
    onCancel: () -> Unit,
    onDone: (Uri) -> Unit
) {
    if (pendingAvatarUri == null) return

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val ringColor = MaterialTheme.colorScheme.primary
    val maskColor = Color.Black.copy(alpha = 0.55f)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
        ) {
            // 1. CENTER CROP AREA (Responsive Theme Background)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Background Photo with Pan & Zoom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.8f, 5f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = pendingAvatarUri,
                        contentDescription = "Crop Image",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                                rotationZ = avatarRotationAngle
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                // Dim Mask Overlay & Ring
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val circleRadius = size.minDimension * 0.35f
                    val circleCenter = Offset(size.width / 2f, size.height / 2f)

                    clipPath(
                        path = Path().apply {
                            addOval(Rect(circleCenter, circleRadius))
                        },
                        clipOp = ClipOp.Difference
                    ) {
                        drawRect(color = maskColor)
                    }

                    drawCircle(
                        color = ringColor,
                        radius = circleRadius,
                        center = circleCenter,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            // 2. THEMED BOTTOM TOOLBAR (Cancel, Rotate, Done)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel Button
                    TextButton(onClick = onCancel) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Rotate 90° Button
                    IconButton(onClick = onRotate) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Rotate",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Done Button
                    Button(
                        onClick = { onDone(pendingAvatarUri) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = CircleShape
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FullscreenAvatarViewer(
    show: Boolean,
    groupPhotoUrl: String,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    if (!show || groupPhotoUrl.isBlank()) return

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = groupPhotoUrl,
                    contentDescription = "Group Photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 60.dp),
                    contentScale = ContentScale.Fit
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Group Photo",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Photo",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Photo",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullscreenMediaViewer(
    viewerItem: GroupMediaItem?,
    onClose: () -> Unit,
    onReply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onShare: (GroupMediaItem) -> Unit,
    onForward: (GroupMediaItem) -> Unit,
    onOpenInChat: (String) -> Unit
) {
    if (viewerItem == null) return

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = viewerItem.url,
                        contentDescription = viewerItem.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = viewerItem.name.ifBlank { "Media Viewer" },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )

                    IconButton(onClick = { onReply(viewerItem.url) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { onDelete(viewerItem.id) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { onShare(viewerItem) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { onForward(viewerItem) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Forward",
                            tint = Color.White,
                            modifier = Modifier.graphicsLayer { scaleX = -1f }
                        )
                    }

                    IconButton(onClick = { onOpenInChat(viewerItem.url) }) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Open in chat",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
