package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.fabxdi.dibadge.util.FilePickerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * One brush stroke. Points are stored as fractions (0..1) of the photo itself, so the stroke
 * lands in the same place on screen and in the saved file, whatever size either one is.
 */
data class DrawPathData(
    val points: List<Offset>,
    val color: Color,
    /** Brush width as a fraction of the photo's width. */
    val strokeWidthFraction: Float = 0.008f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    mediaUris: List<Uri>,
    initialIndex: Int = 0,
    isDark: Boolean = isSystemInDarkTheme(),
    onClose: () -> Unit,
    onRemovePhoto: (Int) -> Unit = {},
    onSaveEditedPhoto: (Int, Uri) -> Unit = { _, _ -> }
) {
    // Closing is a side effect, so it runs in an effect and not during composition.
    LaunchedEffect(mediaUris.isEmpty()) {
        if (mediaUris.isEmpty()) onClose()
    }
    if (mediaUris.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, mediaUris.size - 1)) { mediaUris.size }

    var isDrawMode by remember { mutableStateOf(false) }
    var currentDrawColor by remember { mutableStateOf(Color(0xFFFF3B30)) } // Red default
    val drawPaths = remember { mutableStateListOf<DrawPathData>() }

    val pageBgColor = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF)
    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val tealAccent = if (isDark) Color(0xFF8F9CD8) else Color(0xFF3A4694)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)

    BackHandler {
        if (isDrawMode) {
            drawPaths.clear()
            isDrawMode = false
        } else {
            onClose()
        }
    }

    val currentPage = pagerState.currentPage.coerceIn(0, mediaUris.size - 1)
    val currentUri = mediaUris[currentPage]
    val currentIsVideo = remember(currentUri) { isVideoUri(context, currentUri) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBgColor)
    ) {
        if (isDrawMode) {
            // DRAW MODE SCREEN
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Draw Mode Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(cardColor)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close "X" at left (discards drawing)
                    IconButton(onClick = {
                        drawPaths.clear()
                        isDrawMode = false
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Discard drawing",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Undo Icon in center
                    IconButton(
                        onClick = {
                            if (drawPaths.isNotEmpty()) {
                                drawPaths.removeAt(drawPaths.size - 1)
                            }
                        },
                        enabled = drawPaths.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo stroke",
                            tint = if (drawPaths.isNotEmpty()) primaryTextColor else secondaryTextColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Teal "Done" text action
                    Text(
                        text = "Done",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable {
                                if (drawPaths.isEmpty()) {
                                    isDrawMode = false
                                } else {
                                    val strokes = drawPaths.toList()
                                    scope.launch {
                                        val newUri = saveEditedPhotoWithPaths(context, currentUri, strokes)
                                        if (newUri != null) {
                                            onSaveEditedPhoto(currentPage, newUri)
                                        }
                                        drawPaths.clear()
                                        isDrawMode = false
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // Interactive Drawing Canvas over Photo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = currentUri,
                        contentDescription = "Photo to draw on",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // The photo's real (rotated) size, so strokes can be tied to the picture
                    // and not to the screen.
                    var photoSize by remember(currentUri) { mutableStateOf<IntSize?>(null) }
                    LaunchedEffect(currentUri) {
                        photoSize = withContext(Dispatchers.IO) { readOrientedSize(context, currentUri) }
                    }
                    var livePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentDrawColor, photoSize) {
                                val photo = photoSize ?: return@pointerInput
                                val rect = fitRect(size.width.toFloat(), size.height.toFloat(), photo)
                                fun toFraction(o: Offset) = Offset(
                                    ((o.x - rect.left) / rect.width).coerceIn(0f, 1f),
                                    ((o.y - rect.top) / rect.height).coerceIn(0f, 1f)
                                )
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val f = toFraction(offset)
                                        livePoints = listOf(f, f)
                                    },
                                    onDrag = { change, _ ->
                                        livePoints = livePoints + toFraction(change.position)
                                    },
                                    onDragEnd = {
                                        if (livePoints.isNotEmpty()) {
                                            drawPaths.add(DrawPathData(livePoints, currentDrawColor))
                                        }
                                        livePoints = emptyList()
                                    },
                                    onDragCancel = { livePoints = emptyList() }
                                )
                            }
                    ) {
                        val photo = photoSize ?: return@Canvas
                        val rect = fitRect(size.width, size.height, photo)
                        fun strokePath(points: List<Offset>): Path = Path().apply {
                            points.forEachIndexed { i, f ->
                                val x = rect.left + f.x * rect.width
                                val y = rect.top + f.y * rect.height
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }

                        drawPaths.forEach { drawData ->
                            drawPath(
                                path = strokePath(drawData.points),
                                color = drawData.color,
                                style = Stroke(
                                    width = drawData.strokeWidthFraction * rect.width,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        if (livePoints.isNotEmpty()) {
                            drawPath(
                                path = strokePath(livePoints),
                                color = currentDrawColor,
                                style = Stroke(
                                    width = 0.008f * rect.width,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                // Bottom Color Dots Bar (5 Color Dots: White, Red, Yellow, Blue, Green)
                val colorOptions = listOf(
                    Color(0xFFFFFFFF) to "White",
                    Color(0xFFFF3B30) to "Red",
                    Color(0xFFFFCC00) to "Yellow",
                    Color(0xFF007AFF) to "Blue",
                    Color(0xFF34C759) to "Green"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(cardColor),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorOptions.forEach { (color, name) ->
                        val isSelected = currentDrawColor == color
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { currentDrawColor = color },
                            shape = CircleShape,
                            color = color,
                            border = BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) primaryTextColor else outlineColor)
                        ) {}
                    }
                }
            }
        } else {
            // NORMAL IN-APP PHOTO VIEWER
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Bar (Card Color background)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(cardColor)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Arrow (24dp icon in 48dp touch area)
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // "{n} of {total}" centered
                    Text(
                        text = "${currentPage + 1} of ${mediaUris.size}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pencil icon to draw (photos only)
                        if (!currentIsVideo) {
                            IconButton(
                                onClick = { isDrawMode = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Draw",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Delete / Trash icon (errorColor)
                        IconButton(
                            onClick = { onRemovePhoto(currentPage) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete photo",
                                tint = errorColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Main Photo Pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val uri = mediaUris[page]
                    val isVideo = remember(uri) { isVideoUri(context, uri) }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Photo preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        if (isVideo) {
                            IconButton(
                                onClick = { FilePickerUtils.openFile(context, uri) },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Bar: Centered row of 44dp mini thumbnails
                if (mediaUris.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(cardColor)
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(mediaUris) { index, thumbUri ->
                            val isCurrent = index == currentPage
                            Surface(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                shape = RoundedCornerShape(9.dp),
                                color = cardColor,
                                border = BorderStroke(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) primaryTextColor else outlineColor
                                )
                            ) {
                                AsyncImage(
                                    model = thumbUri,
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(9.dp))
                                )

                                val mimeType = try { context.contentResolver.getType(thumbUri) } catch (e: Exception) { null }
                                val ext = FilePickerUtils.getFileName(context, thumbUri).substringAfterLast('.', "").lowercase()
                                val isVideo = mimeType?.startsWith("video/") == true || ext == "mp4"
                                if (isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** Where a photo ends up inside a box of the given size under ContentScale.Fit. */
private fun fitRect(boxW: Float, boxH: Float, photo: IntSize): FitRect {
    val scale = minOf(boxW / photo.width, boxH / photo.height)
    val w = photo.width * scale
    val h = photo.height * scale
    return FitRect((boxW - w) / 2f, (boxH - h) / 2f, w, h)
}

private fun isVideoUri(context: Context, uri: Uri): Boolean {
    val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
    return mimeType?.startsWith("video/") == true ||
            FilePickerUtils.getFileName(context, uri).lowercase().endsWith(".mp4")
}

private fun readExifDegrees(context: Context, uri: Uri): Int = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        when (android.media.ExifInterface(input).getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL
        )) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } ?: 0
} catch (e: Exception) {
    0
}

/** Photo size as the user sees it (after EXIF rotation), read without loading the pixels. */
private fun readOrientedSize(context: Context, uri: Uri): IntSize? = try {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    if (opts.outWidth <= 0 || opts.outHeight <= 0) {
        null
    } else if (readExifDegrees(context, uri) % 180 != 0) {
        IntSize(opts.outHeight, opts.outWidth)
    } else {
        IntSize(opts.outWidth, opts.outHeight)
    }
} catch (e: Exception) {
    null
}

private suspend fun saveEditedPhotoWithPaths(
    context: Context,
    originalUri: Uri,
    strokes: List<DrawPathData>
): Uri? = withContext(Dispatchers.IO) {
    try {
        // Decode at a sensible size so a 50 MP photo doesn't exhaust memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(originalUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(originalUri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return@withContext null

        val degrees = readExifDegrees(context, originalUri)
        val upright = if (degrees != 0) {
            val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        } else {
            decoded
        }

        val mutableBitmap = upright.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)
        val w = mutableBitmap.width.toFloat()
        val h = mutableBitmap.height.toFloat()

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { stroke ->
            val path = android.graphics.Path()
            stroke.points.forEachIndexed { i, f ->
                if (i == 0) path.moveTo(f.x * w, f.y * h) else path.lineTo(f.x * w, f.y * h)
            }
            paint.color = stroke.color.toArgb()
            paint.strokeWidth = stroke.strokeWidthFraction * w
            canvas.drawPath(path, paint)
        }

        val editedFile = File(context.cacheDir, "EDITED_${System.currentTimeMillis()}.jpg")
        FileOutputStream(editedFile).use { out ->
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            editedFile
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}