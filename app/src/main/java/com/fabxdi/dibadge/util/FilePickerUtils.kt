package com.fabxdi.dibadge.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import java.io.File

object FilePickerUtils {
    fun getFileName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            } ?: uri.path?.split("/")?.last() ?: "Unknown File"
        } catch (e: Exception) {
            uri.path?.split("/")?.last() ?: "Unknown File"
        }
    }

    fun getFileIcon(context: Context, uri: Uri): ImageVector {
        val fileName = getFileName(context, uri).lowercase()
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }
        
        return when {
            mimeType?.startsWith("image/") == true || fileName.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") } -> Icons.Default.Image
            mimeType?.startsWith("video/") == true || fileName.endsWith(".mp4") -> Icons.Default.VideoFile
            mimeType?.startsWith("audio/") == true || fileName.endsWith(".m4a") || fileName.endsWith(".mp3") -> Icons.Default.AudioFile
            mimeType == "application/pdf" || fileName.endsWith(".pdf") -> Icons.Default.PictureAsPdf
            mimeType == "application/vnd.ms-excel" || 
            mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
            fileName.endsWith(".xls") || fileName.endsWith(".xlsx") -> Icons.Default.TableChart
            mimeType == "application/msword" || 
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            fileName.endsWith(".doc") || fileName.endsWith(".docx") -> Icons.Default.Description
            mimeType?.contains("presentation") == true || fileName.endsWith(".ppt") || fileName.endsWith(".pptx") -> Icons.Default.Slideshow
            fileName.endsWith(".zip") || fileName.endsWith(".rar") || fileName.endsWith(".7z") -> Icons.Default.FolderZip
            else -> Icons.Default.Description
        }
    }

    fun createImageUri(context: Context): Uri? {
        val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun openFile(context: Context, uri: Uri) {
        val mimeType = context.contentResolver.getType(uri) ?: when (uri.path?.substringAfterLast('.')) {
            "jpg", "jpeg", "png" -> "image/*"
            "pdf" -> "application/pdf"
            "mp4" -> "video/*"
            "m4a", "mp3" -> "audio/*"
            else -> "*/*"
        }

        val safeUri = if (uri.scheme == "file") {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(uri.path!!)
            )
        } else {
            uri
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(safeUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Handle error (e.g., no app found to open file)
            Log.e("FilePickerUtils", "Error opening file", e)
        }
    }
}
