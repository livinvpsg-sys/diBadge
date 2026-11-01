package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.util.TimeZone
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class CaptureMode { PHOTO, VIDEO }

@Composable
fun CameraCaptureScreen(
    isStampCamera: Boolean = true,
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isStampEnabled = isStampCamera
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var captureMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableLongStateOf(0L) }
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    val preview = remember { Preview.Builder().build() }
    
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    val currentRecording = rememberUpdatedState(activeRecording)
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // Real location for the stamp. The clock ticks so the viewfinder never shows a stale time.
    val locationState = rememberLocationState(enabled = isStampEnabled)
    val deviceLocation = locationState.location
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowTick = System.currentTimeMillis()
        }
    }
    val liveStamp = remember(nowTick, deviceLocation) { buildStampText(nowTick, deviceLocation) }

    // If photo is captured, show Review Screen
    if (capturedPhotoUri != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = capturedPhotoUri,
                contentDescription = "Review captured photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Review Bottom Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Retake",
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier.clickable { capturedPhotoUri = null }
                )

                Text(
                    text = "Use photo",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.clickable {
                        onImageCaptured(capturedPhotoUri!!)
                    }
                )
            }
        }
        return
    }

    LaunchedEffect(lensFacing, flashMode, captureMode) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            preview.surfaceProvider = previewView.surfaceProvider
            
            imageCapture.flashMode = flashMode
            
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                val useCases = mutableListOf<UseCase>(preview)
                if (captureMode == CaptureMode.PHOTO) {
                    useCases.add(imageCapture)
                } else {
                    useCases.add(videoCapture)
                }
                
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )
            } catch (e: Exception) {
                Log.e("CameraCapture", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                recordingDuration += 1
            }
        } else {
            recordingDuration = 0
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentRecording.value?.stop()
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        camera?.let { cam ->
                            val zoomState = cam.cameraInfo.zoomState.value
                            val currentZoomRatio = zoomState?.zoomRatio ?: 1f
                            cam.cameraControl.setZoomRatio(currentZoomRatio * zoom)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        camera?.let { cam ->
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            cam.cameraControl.startFocusAndMetering(action)
                        }
                    }
                }
        )

        // Viewfinder Stamp Overlay at Bottom-Left
        if (isStampEnabled && captureMode == CaptureMode.PHOTO) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 120.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = liveStamp.dateTime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = liveStamp.zoneAndPlace,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                    if (liveStamp.coords != null) {
                        Text(
                            text = liveStamp.coords,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    } else if (locationState.status == LocationStatus.NeedsPermission) {
                        Text(
                            text = "Tap to add location",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.clickable { locationState.requestPermission() }
                        )
                    }
                }
            }
        }

        // Top Bar (Close at left, Flash at right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            if (captureMode == CaptureMode.PHOTO) {
                IconButton(onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }) {
                    Icon(
                        imageVector = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                            else -> Icons.Default.FlashOff
                        },
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Selector
            if (!isRecording) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(CaptureMode.PHOTO, CaptureMode.VIDEO)
                    modes.forEach { mode ->
                        val isSelected = captureMode == mode
                        Surface(
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { captureMode = mode }
                        ) {
                            Text(
                                text = mode.name,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                color = if (isSelected) Color.Black else Color.White,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Shutter Button
                Surface(
                    onClick = {
                        if (captureMode == CaptureMode.PHOTO) {
                            takePhoto(
                                context = context,
                                imageCapture = imageCapture,
                                executor = cameraExecutor,
                                isStampEnabled = isStampEnabled,
                                // Time is read at the moment of capture, not when the screen opened.
                                stamp = buildStampText(System.currentTimeMillis(), deviceLocation)
                            ) { uri ->
                                capturedPhotoUri = uri
                            }
                        } else {
                            if (isRecording) {
                                activeRecording?.stop()
                                activeRecording = null
                                isRecording = false
                            } else {
                                isRecording = true
                                activeRecording = startVideoRecording(context, videoCapture, ContextCompat.getMainExecutor(context)) { uri ->
                                    onImageCaptured(uri)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .border(BorderStroke(4.dp, Color.White), CircleShape),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.5f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .background(if (captureMode == CaptureMode.VIDEO && isRecording) Color.Red else Color.White, if (captureMode == CaptureMode.VIDEO && isRecording) RoundedCornerShape(8.dp) else CircleShape)
                    )
                }

                // Flip Camera
                if (!isRecording) {
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 32.dp)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun startVideoRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    executor: Executor,
    onVideoCaptured: (Uri) -> Unit
): Recording {
    val videoFile = File(
        context.cacheDir,
        "VID_${System.currentTimeMillis()}.mp4"
    )

    val outputOptions = FileOutputOptions.Builder(videoFile).build()

    return videoCapture.output
        .prepareRecording(context, outputOptions)
        .apply {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                withAudioEnabled()
            }
        }
        .start(executor) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    if (!event.hasError()) {
                        try {
                            val savedUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                videoFile
                            )
                            onVideoCaptured(savedUri)
                        } catch (e: Exception) {
                            Log.e("CameraCapture", "Error sharing video", e)
                        }
                    } else if (event.error != VideoRecordEvent.Finalize.ERROR_RECORDING_GARBAGE_COLLECTED) {
                        Log.e("CameraCapture", "Video recording error: ${event.error}")
                    }
                }
            }
        }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    isStampEnabled: Boolean,
    stamp: StampText,
    onImageCaptured: (Uri) -> Unit
) {
    val photoFile = File(
        context.cacheDir,
        "IMG_${System.currentTimeMillis()}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    if (isStampEnabled) {
                        val decoded = BitmapFactory.decodeFile(photoFile.absolutePath)
                        if (decoded != null) {
                            // Apply the EXIF rotation first so the stamp sits at the bottom of
                            // the photo as the user sees it, not the sensor's bottom.
                            val originalBitmap = rotateByExif(decoded, photoFile.absolutePath)
                            val stampedBitmap = drawStampOnBitmap(originalBitmap, stamp)
                            FileOutputStream(photoFile).use { out ->
                                stampedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                            }
                        }
                    }
                    val savedUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    onImageCaptured(savedUri)
                } catch (e: Exception) {
                    Log.e("CameraCapture", "Error burning stamp onto photo", e)
                }
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraCapture", "Photo capture failed: ${exc.message}", exc)
            }
        }
    )
}

class StampText(val dateTime: String, val zoneAndPlace: String, val coords: String?)

private fun buildStampText(millis: Long, loc: DeviceLocation?): StampText {
    val tz = TimeZone.getDefault()
    val dateTime = java.text.SimpleDateFormat("d MMM yyyy · h:mm a", Locale.getDefault())
        .apply { timeZone = tz }
        .format(java.util.Date(millis))
    val zone = tz.getDisplayName(tz.inDaylightTime(java.util.Date(millis)), TimeZone.SHORT, Locale.getDefault())
    val zoneAndPlace = if (loc != null && loc.placeName.isNotBlank()) "$zone · ${loc.placeName}" else zone
    val coords = loc?.let {
        val ns = if (it.lat >= 0) "N" else "S"
        val ew = if (it.lng >= 0) "E" else "W"
        String.format(Locale.US, "%.5f° %s, %.5f° %s", kotlin.math.abs(it.lat), ns, kotlin.math.abs(it.lng), ew)
    }
    return StampText(dateTime, zoneAndPlace, coords)
}

private fun rotateByExif(bitmap: Bitmap, path: String): Bitmap {
    val degrees = try {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
    if (degrees == 0f) return bitmap
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
}

private fun drawStampOnBitmap(bitmap: Bitmap, stamp: StampText): Bitmap {
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    val width = canvas.width
    val height = canvas.height

    val line1Size = width * 0.032f
    val line2Size = width * 0.026f
    val line3Size = width * 0.022f
    val hasCoords = stamp.coords != null

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
    }

    val margin = width * 0.04f
    val startX = margin
    val textBlock = line1Size * 1.3f + line2Size * 1.3f + if (hasCoords) line3Size else 0f
    var startY = height - margin - textBlock + line1Size

    val bgPaint = Paint().apply {
        color = android.graphics.Color.argb(140, 0, 0, 0)
    }
    paint.textSize = line2Size
    val widest = maxOf(
        paint.measureText(stamp.zoneAndPlace),
        paint.measureText(stamp.coords ?: ""),
        width * 0.30f
    ).coerceAtMost(width * 0.92f - startX)
    val rect = RectF(
        startX - 16f,
        startY - line1Size - 16f,
        startX + widest + 16f,
        height - margin + 16f
    )
    canvas.drawRoundRect(rect, 16f, 16f, bgPaint)

    paint.textSize = line1Size
    paint.isFakeBoldText = true
    canvas.drawText(stamp.dateTime, startX, startY, paint)

    startY += line1Size * 1.3f
    paint.textSize = line2Size
    paint.isFakeBoldText = false
    canvas.drawText(stamp.zoneAndPlace, startX, startY, paint)

    if (hasCoords) {
        startY += line2Size * 1.3f
        paint.textSize = line3Size
        canvas.drawText(stamp.coords!!, startX, startY, paint)
    }

    return mutableBitmap
}
