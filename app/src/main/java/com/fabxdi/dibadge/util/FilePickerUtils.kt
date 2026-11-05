package com.fabxdi.dibadge.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.Locale

object FilePickerUtils {
    const val MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024
    private const val TASK_DIR = "task_attachments"

    /**
     * Copies a picked file into app storage (filesDir/task_attachments/<id>/<name>) so it
     * survives the source being moved or the picker permission expiring.
     * Returns null if the file is unreadable or bigger than [MAX_ATTACHMENT_BYTES].
     * Call from a background thread.
     */
    fun importToAppStorage(context: Context, source: Uri): Uri? {
        if (FileBlocklistValidator.isFileBlocked(getFileName(context, source))) return null
        val dir = File(File(context.filesDir, TASK_DIR), UUID.randomUUID().toString())
        return try {
            dir.mkdirs()
            val name = getFileName(context, source)
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .ifBlank { "file" }
            val dest = File(dir, name)
            var total = 0L
            val input = context.contentResolver.openInputStream(source)
                ?: throw IOException("Cannot open file")
            input.use { ins ->
                dest.outputStream().use { out ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = ins.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_ATTACHMENT_BYTES) throw IOException("File too large")
                        out.write(buffer, 0, read)
                    }
                }
            }
            Uri.fromFile(dest)
        } catch (e: Exception) {
            dir.deleteRecursively()
            null
        }
    }

    fun isAppStored(context: Context, uri: Uri): Boolean {
        val path = uri.path ?: return false
        return uri.scheme == "file" && path.startsWith(File(context.filesDir, TASK_DIR).path + File.separator)
    }

    /** Deletes a file previously created by [importToAppStorage]; anything else is left alone. */
    fun deleteAppStored(context: Context, uri: Uri) {
        if (!isAppStored(context, uri)) return
        try {
            val file = File(uri.path!!)
            file.delete()
            file.parentFile?.takeIf { it.name != TASK_DIR && it.listFiles().isNullOrEmpty() }?.delete()
        } catch (_: Exception) {
        }
    }

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
        if (uri.scheme == "file") return uri.path?.let { File(it).length() } ?: 0L
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

    fun openFileInExternalApp(context: Context, uri: Uri, fileName: String): Boolean {
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
            else -> {
                val fromExt = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                fromExt ?: try { context.contentResolver.getType(uri) ?: "*/*" } catch (e: Exception) { "*/*" }
            }
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

        return try {
            val chooserIntent = Intent.createChooser(intent, "Open with")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
            true
        } catch (e: Exception) {
            // No app can open this file type
            android.widget.Toast.makeText(context, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show()
            false
        }
    }
}
