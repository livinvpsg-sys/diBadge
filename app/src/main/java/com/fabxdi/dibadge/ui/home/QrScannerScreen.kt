package com.fabxdi.dibadge.ui.home

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.data.InboxEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    userCode: String = "7K2M9QX4B",
    onBack: () -> Unit,
    onConnectionRequestSent: (String) -> Unit = {},
    onAttendanceMarked: (title: String, orgName: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var scannedCode by remember { mutableStateOf<String?>(null) }
    var lastWrongCode by remember { mutableStateOf<String?>(null) }
    var lastWrongTime by remember { mutableLongStateOf(0L) }

    var showPersonSheet by remember { mutableStateOf(false) }
    var showAttendanceSheet by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasCameraPermission = isGranted }
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val code = barcodes.firstOrNull()?.rawValue
                        if (code != null) {
                            processScannedCode(code, userCode, context,
                                onOwnCode = {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("This is your own QR code") }
                                },
                                onPersonCode = {
                                    scannedCode = code
                                    showPersonSheet = true
                                },
                                onAttendanceCode = {
                                    scannedCode = code
                                    showAttendanceSheet = true
                                },
                                onWrongCode = {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("This is not a diBadge QR code. Try another one.") }
                                }
                            )
                        } else {
                            coroutineScope.launch { snackbarHostState.showSnackbar("No QR code found in image") }
                        }
                    }
            } catch (e: Exception) {
                coroutineScope.launch { snackbarHostState.showSnackbar("Failed to read image") }
            }
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val tealAccent = diBadgeColors.accent

    if (!hasCameraPermission) {
        // Permission Denied Screen
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background)
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryTextColor, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Scan QR code", fontSize = 20.sp, fontWeight = FontWeight.Normal, color = primaryTextColor)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(colors.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 40.dp)
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = diBadgeColors.tabInactive, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Allow the camera to scan", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("The camera is used only to read QR codes", fontSize = 13.sp, color = secondaryTextColor, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))

                    // Allow camera Text Button
                    Text(
                        text = "Allow camera",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable { launcher.launch(Manifest.permission.CAMERA) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
        return
    }

    // Full Camera Scanner View
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        CameraXScannerPreview(
            onCameraControlReady = { cameraControl = it },
            onCodeScanned = { rawCode ->
                val now = System.currentTimeMillis()
                if (rawCode == lastWrongCode && now - lastWrongTime < 3000L) {
                    return@CameraXScannerPreview
                }

                // Vibrate haptic
                triggerHapticFeedback(context)

                processScannedCode(
                    rawCode = rawCode,
                    userCode = userCode,
                    context = context,
                    onOwnCode = {
                        coroutineScope.launch { snackbarHostState.showSnackbar("This is your own QR code") }
                    },
                    onPersonCode = {
                        scannedCode = rawCode
                        showPersonSheet = true
                    },
                    onAttendanceCode = {
                        scannedCode = rawCode
                        showAttendanceSheet = true
                    },
                    onWrongCode = {
                        lastWrongCode = rawCode
                        lastWrongTime = now
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("This is not a diBadge QR code. Try another one.")
                        }
                    }
                )
            }
        )

        // Viewfinder Mask Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSizePx = 260.dp.toPx()
            val left = (size.width - boxSizePx) / 2f
            val top = (size.height - boxSizePx) / 2f - 40.dp.toPx()
            val right = left + boxSizePx
            val bottom = top + boxSizePx

            // Dim 50% outside viewfinder
            val path = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                    )
                )
                fillType = PathFillType.EvenOdd
            }
            drawPath(path = path, color = Color.Black.copy(alpha = 0.50f))

            // White Corner Brackets
            val strokeWidth = 4.dp.toPx()
            val cornerLength = 32.dp.toPx()
            val bracketColor = Color(0xFFF0F3F5)

            // Top-Left
            drawLine(color = bracketColor, start = Offset(left, top + cornerLength), end = Offset(left, top), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color = bracketColor, start = Offset(left, top), end = Offset(left + cornerLength, top), strokeWidth = strokeWidth, cap = StrokeCap.Round)

            // Top-Right
            drawLine(color = bracketColor, start = Offset(right - cornerLength, top), end = Offset(right, top), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color = bracketColor, start = Offset(right, top), end = Offset(right, top + cornerLength), strokeWidth = strokeWidth, cap = StrokeCap.Round)

            // Bottom-Left
            drawLine(color = bracketColor, start = Offset(left, bottom - cornerLength), end = Offset(left, bottom), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color = bracketColor, start = Offset(left, bottom), end = Offset(left + cornerLength, bottom), strokeWidth = strokeWidth, cap = StrokeCap.Round)

            // Bottom-Right
            drawLine(color = bracketColor, start = Offset(right - cornerLength, bottom), end = Offset(right, bottom), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color = bracketColor, start = Offset(right, bottom - 1f), end = Offset(right, bottom - cornerLength), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }

        // Header Over Camera
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFF0F3F5), modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Scan QR code", fontSize = 20.sp, fontWeight = FontWeight.Normal, color = Color(0xFFF0F3F5))
            }

            IconButton(
                onClick = {
                    isFlashOn = !isFlashOn
                    cameraControl?.enableTorch(isFlashOn)
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Outlined.FlashOn, contentDescription = "Torch", tint = Color(0xFFF0F3F5), modifier = Modifier.size(22.dp))
            }
        }

        // Instruction Text Under Viewfinder
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Point the camera at a diBadge QR code",
                fontSize = 15.sp,
                color = Color(0xFFF0F3F5),
                textAlign = TextAlign.Center
            )
        }

        // Bottom Outlined Pill: Choose from gallery
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier
                    .height(44.dp)
                    .wrapContentWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = Color(0xFFF0F3F5), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Choose from gallery", fontSize = 14.sp, color = Color(0xFFF0F3F5))
                }
            }
        }

        // Snackbar Host
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 116.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            SnackbarHost(snackbarHostState)
        }
    }

    // Person Scan Result Sheet
    if (showPersonSheet && scannedCode != null) {
        val extractedCode = scannedCode!!.replace("dibadge:", "")
        val personName = remember(extractedCode) { "Alex Morgan" }

        ModalBottomSheet(
            onDismissRequest = {
                showPersonSheet = false
                scannedCode = null
            },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = cardBgColor,
            scrimColor = Color.Black.copy(alpha = 0.45f),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 64dp Round Avatar
                val avatarColor = remember(personName) { UserColorUtils.getColorForName(personName) }
                val outlineColor = colors.outline
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = avatarColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(personName.take(1), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Person Name (20sp Medium, NO badge number text)
                Text(personName, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)

                Spacer(modifier = Modifier.height(24.dp))

                // Send Connection Request Text Button
                Text(
                    text = "Send connection request",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tealAccent,
                    modifier = Modifier
                        .clickable {
                            showPersonSheet = false
                            scannedCode = null
                            onConnectionRequestSent(personName)
                            onBack()
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Cancel Text Button
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor,
                    modifier = Modifier
                        .clickable {
                            showPersonSheet = false
                            scannedCode = null
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Attendance Scan Result Sheet
    if (showAttendanceSheet && scannedCode != null) {
        val eventTitle = "Site Safety Briefing"
        val orgName = "diBadge Inc."
        val placeName = "Project Site B"
        val dateTimeStr = "Today, 9:00 AM"

        ModalBottomSheet(
            onDismissRequest = {
                showAttendanceSheet = false
                scannedCode = null
            },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = cardBgColor,
            scrimColor = Color.Black.copy(alpha = 0.45f),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("ATTENDANCE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(eventTitle, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text("$orgName · $placeName", fontSize = 14.sp, color = secondaryTextColor)
                Spacer(modifier = Modifier.height(2.dp))
                Text(dateTimeStr, fontSize = 14.sp, color = secondaryTextColor)

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Mark attendance",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable {
                                showAttendanceSheet = false
                                scannedCode = null
                                onAttendanceMarked(eventTitle, orgName)
                                onBack()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryTextColor,
                        modifier = Modifier
                            .clickable {
                                showAttendanceSheet = false
                                scannedCode = null
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private fun processScannedCode(
    rawCode: String,
    userCode: String,
    context: Context,
    onOwnCode: () -> Unit,
    onPersonCode: () -> Unit,
    onAttendanceCode: () -> Unit,
    onWrongCode: () -> Unit
) {
    val clean = rawCode.trim()
    if (!clean.startsWith("dibadge:", ignoreCase = true)) {
        onWrongCode()
        return
    }

    if (clean.startsWith("dibadge:att:", ignoreCase = true)) {
        onAttendanceCode()
        return
    }

    val extractedCode = clean.replace("dibadge:", "", ignoreCase = true)
    if (extractedCode.equals(userCode, ignoreCase = true)) {
        onOwnCode()
        return
    }

    if (extractedCode.length == 9) {
        onPersonCode()
    } else {
        onWrongCode()
    }
}

private fun triggerHapticFeedback(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50L)
            }
        }
    } catch (e: Exception) {}
}

@Composable
fun CameraXScannerPreview(
    onCameraControlReady: (CameraControl) -> Unit,
    onCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val scanner = BarcodeScanning.getClient()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val raw = barcodes.firstOrNull()?.rawValue
                            if (raw != null) {
                                onCodeScanned(raw)
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                onCameraControlReady(camera.cameraControl)
            } catch (e: Exception) {
                Log.e("QrScanner", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}
