package com.fabxdi.dibadge.ui.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FlashOn
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
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun LinkedDevicesScannerScreen(
    userUid: String,
    onBack: () -> Unit,
    onDeviceLinkedSuccessfully: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isProcessingScan by remember { mutableStateOf(false) }

    var pendingSessionId by remember { mutableStateOf<String?>(null) }
    var pendingDeviceName by remember { mutableStateOf("Chrome on Windows") }
    var pendingCity by remember { mutableStateOf("Singapore") }
    var showLinkConfirmationDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val tealAccent = diBadgeColors.accent
    val cardBgColor = colors.surface
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview + Image Analysis
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val executor = Executors.newSingleThreadExecutor()
                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            @OptIn(ExperimentalGetImage::class)
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && !isProcessingScan && !showLinkConfirmationDialog) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                val scanner = BarcodeScanning.getClient()
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val code = barcodes.firstOrNull()?.rawValue
                                        if (!code.isNullOrBlank() && !isProcessingScan && !showLinkConfirmationDialog) {
                                            isProcessingScan = true

                                            // Vibrate lightly
                                            val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                            } else {
                                                vibrator?.vibrate(50)
                                            }

                                            // Validate QR format
                                            val isValidQr = code.startsWith("dibadge-web:") || code.startsWith("SESS_") || code.contains("sessionId")
                                            if (isValidQr) {
                                                val parsedSessionId = code.substringAfter("dibadge-web:").substringAfter("SESS_").take(40)
                                                pendingSessionId = parsedSessionId
                                                pendingDeviceName = "Chrome on Windows"
                                                pendingCity = "Singapore"
                                                showLinkConfirmationDialog = true
                                            } else {
                                                Toast.makeText(ctx, "This QR code is no longer valid. Refresh it on the web and try again", Toast.LENGTH_SHORT).show()
                                                coroutineScope.launch {
                                                    delay(2000)
                                                    isProcessingScan = false
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera.cameraControl

                    } catch (e: Exception) {
                        Log.e("LinkedDevicesScanner", "Camera init failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Mask with Square Viewfinder Cutout
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSize = 260.dp.toPx()
            val left = (size.width - boxSize) / 2
            val top = (size.height - boxSize) / 2 - 40.dp.toPx()

            val rectPath = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }
            val holePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, left + boxSize, top + boxSize),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                    )
                )
            }
            val cutout = Path.combine(PathOperation.Difference, rectPath, holePath)
            drawPath(path = cutout, color = Color.Black.copy(alpha = 0.65f))

            // Rounded White Corners Viewfinder Border
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Top Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = "Scan QR code",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    isFlashOn = !isFlashOn
                    cameraControl?.enableTorch(isFlashOn)
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.FlashOn,
                    contentDescription = "Toggle flash",
                    tint = if (isFlashOn) tealAccent else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Instruction Text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 70.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Open diBadge on a computer and point the camera at the QR code",
                fontSize = 14.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        // Confirmation Dialog: "Link this device?"
        if (showLinkConfirmationDialog) {
            AlertDialog(
                onDismissRequest = {
                    showLinkConfirmationDialog = false
                    isProcessingScan = false
                },
                containerColor = cardBgColor,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "Link this device?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )
                },
                text = {
                    Text(
                        text = "$pendingDeviceName, $pendingCity",
                        fontSize = 14.sp,
                        color = secondaryTextColor
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLinkConfirmationDialog = false
                            val sessId = pendingSessionId.orEmpty().ifBlank { "SESS_${System.currentTimeMillis()}" }
                            val db = FirebaseFirestore.getInstance()

                            // 1. Approval doc for web page
                            val webApprovalData = mapOf(
                                "approved" to true,
                                "uid" to userUid,
                                "approvedAt" to FieldValue.serverTimestamp()
                            )
                            db.collection("webLogins").document(sessId)
                                .set(webApprovalData, SetOptions.merge())

                            // 2. Linked Device record under user
                            val linkedDevData = mapOf(
                                "id" to sessId,
                                "deviceName" to pendingDeviceName,
                                "city" to pendingCity,
                                "linkedAt" to FieldValue.serverTimestamp(),
                                "lastActiveAt" to FieldValue.serverTimestamp()
                            )
                            db.collection("users").document(userUid)
                                .collection("linkedDevices").document(sessId)
                                .set(linkedDevData, SetOptions.merge())

                            Toast.makeText(context, "New device linked", Toast.LENGTH_SHORT).show()
                            onDeviceLinkedSuccessfully()
                        }
                    ) {
                        Text(
                            text = "Link",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tealAccent
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showLinkConfirmationDialog = false
                            isProcessingScan = false
                        }
                    ) {
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
}
