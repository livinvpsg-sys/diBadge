package com.fabxdi.dibadge.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

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

    fun getFileSizeBytes(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "File"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
            else -> "$bytes B"
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
        val fileName = getFileName(context, uri)
        openFileInExternalApp(context, uri, fileName)
    }

    fun openFileInExternalApp(context: Context, uri: Uri, fileName: String) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mimeType = when (ext) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "rtf" -> "application/rtf"
            else -> try { context.contentResolver.getType(uri) ?: "*/*" } catch (e: Exception) { "*/*" }
        }

        val safeUri = if (uri.scheme == "file" && uri.path != null) {
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(uri.path!!)
                )
            } catch (e: Exception) {
                uri
            }
        } else {
            uri
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(safeUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            val chooserIntent = Intent.createChooser(intent, "Open with")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }
}
