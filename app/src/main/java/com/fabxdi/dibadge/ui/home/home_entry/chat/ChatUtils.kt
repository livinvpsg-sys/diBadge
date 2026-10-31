package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.util.FileBlocklistValidator
import com.fabxdi.dibadge.util.FilePickerUtils
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

enum class AttachmentCategory {
    IMAGE, VIDEO, AUDIO, DOCUMENT
}

enum class VoiceRecordState { NONE, HOLD, LOCKED, PAUSED }

fun stripGroupMention(text: String, groupName: String? = null): String {
    if (text.isBlank()) return ""
    var result = text
    if (groupName != null && groupName.isNotBlank()) {
        result = result.replace("@$groupName", "", ignoreCase = true)
    }
    result = result.replace("@All linked groups", "", ignoreCase = true)
    result = result.replace("@All", "", ignoreCase = true)
    return result.trim()
}

fun normalizeWaveformTo64(amplitudes: List<Int>): List<Int> {
    if (amplitudes.isEmpty()) return List(64) { 4 }
    val maxAmp = amplitudes.maxOrNull()?.coerceAtLeast(1) ?: 1
    val result = mutableListOf<Int>()
    val step = amplitudes.size / 64f

    for (i in 0 until 64) {
        val index = (i * step).toInt().coerceIn(0, amplitudes.size - 1)
        val raw = amplitudes[index]
        val normalized = ((raw.toFloat() / maxAmp) * 31f).toInt().coerceIn(2, 31)
        result.add(normalized)
    }
    return result
}

fun getAttachmentCategory(context: Context, uri: Uri): AttachmentCategory {
    val fileName = FilePickerUtils.getFileName(context, uri).lowercase()
    val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
    val ext = fileName.substringAfterLast('.', "")

    return when {
        mimeType?.startsWith("image/") == true || ext in listOf("jpg", "jpeg", "png") -> AttachmentCategory.IMAGE
        mimeType?.startsWith("video/") == true || ext == "mp4" -> AttachmentCategory.VIDEO
        mimeType?.startsWith("audio/") == true || ext in listOf("m4a", "mp3") -> AttachmentCategory.AUDIO
        else -> AttachmentCategory.DOCUMENT
    }
}

fun launchDocumentScanner(context: Context, launcher: ActivityResultLauncher<IntentSenderRequest>) {
    try {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        val activity = context as? Activity
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Scanning isn't available on this phone", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "Scanning isn't available on this phone", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Scanning isn't available on this phone", Toast.LENGTH_SHORT).show()
    }
}

fun processNewUris(newUris: List<Uri>, currentFiles: List<Uri>, context: Context): List<Uri> {
    if (currentFiles.size >= 10) {
        Toast.makeText(context, "You can attach up to 10 files", Toast.LENGTH_SHORT).show()
        return currentFiles
    }

    val currentCat = if (currentFiles.isNotEmpty()) getAttachmentCategory(context, currentFiles.first()) else null

    val availableSlotCount = 10 - currentFiles.size
    val limitedUris = newUris.take(availableSlotCount)
    if (newUris.size > availableSlotCount) {
        Toast.makeText(context, "You can attach up to 10 files", Toast.LENGTH_SHORT).show()
    }

    val validUris = mutableListOf<Uri>()
    var sizeExceeded = false
    var isBlockedType = false

    for (uri in limitedUris) {
        val fileName = FilePickerUtils.getFileName(context, uri)
        if (FileBlocklistValidator.isFileBlocked(fileName)) {
            isBlockedType = true
            continue
        }

        val cat = getAttachmentCategory(context, uri)
        if (currentCat != null && cat != currentCat) {
            continue
        }
        val sizeBytes = FilePickerUtils.getFileSizeBytes(context, uri)
        if (sizeBytes > 25 * 1024 * 1024) {
            sizeExceeded = true
        } else {
            validUris.add(uri)
        }
    }

    if (isBlockedType) {
        Toast.makeText(context, "This file type can't be sent", Toast.LENGTH_SHORT).show()
    }

    if (sizeExceeded) {
        Toast.makeText(context, "This file is over 25 MB", Toast.LENGTH_SHORT).show()
    }

    return currentFiles + validUris
}

@Composable
fun AttachColumnItem(
    icon: ImageVector,
    label: String,
    isDark: Boolean,
    outlineColor: Color,
    secondaryTextColor: Color,
    primaryTextColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val raisedColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (!enabled) Modifier.alpha(0.35f) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = raisedColor,
            border = BorderStroke(1.dp, outlineColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = primaryTextColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = primaryTextColor
        )
    }
}
