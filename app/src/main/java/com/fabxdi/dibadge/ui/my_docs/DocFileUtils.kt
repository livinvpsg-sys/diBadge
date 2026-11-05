package com.fabxdi.dibadge.ui.my_docs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.fabxdi.dibadge.ui.calendar.AttachmentFile
import java.io.File
import java.util.UUID

/**
 * My Docs keeps its own copy of every file in the app's private storage. A file picked from the
 * gallery or file manager is only readable for a short time, so without a copy the document
 * could not be opened again later.
 *
 * For opening and sharing, add this line inside <paths> in res/xml/file_paths.xml:
 *     <files-path name="documents" path="documents/" />
 */
object DocFileUtils {
    const val MAX_FILE_BYTES = 5L * 1024 * 1024

    private fun docsDir(context: Context): File =
        File(context.filesDir, "documents").apply { mkdirs() }

    /**
     * Reads the real name and size of a picked file and copies it into app storage.
     * A file that is over the size limit is returned without being copied; the caller checks sizeBytes.
     * Returns null when the file cannot be read.
     */
    fun importPicked(context: Context, uri: Uri, isPdf: Boolean): AttachmentFile? {
        var name: String? = null
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n != -1) name = c.getString(n)
                    if (s != -1 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        } catch (e: Exception) {
        }
        val mime = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        val pdf = isPdf || mime == "application/pdf"
        val displayName = name?.takeIf { it.isNotBlank() } ?: if (pdf) "Document.pdf" else "Photo.jpg"
        val described = AttachmentFile(uri.toString(), displayName, size, pdf)
        if (size > MAX_FILE_BYTES) return described
        return persist(context, described)
    }

    /** Copies a file into app storage (if it is not there already) and returns the stored copy. */
    fun persist(context: Context, file: AttachmentFile): AttachmentFile? {
        val src = Uri.parse(file.uriString)
        if (src.scheme == "file") return file
        val safe = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dest = File(docsDir(context), "${UUID.randomUUID()}_$safe")
        return try {
            val input = context.contentResolver.openInputStream(src) ?: return null
            input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            file.copy(uriString = Uri.fromFile(dest).toString(), sizeBytes = dest.length())
        } catch (e: Exception) {
            dest.delete()
            null
        }
    }

    /** Removes a stored copy. Files that are not in app storage are left alone. */
    fun deleteStored(context: Context, stored: String) {
        try {
            val u = Uri.parse(stored)
            if (u.scheme != "file") return
            val f = File(u.path ?: return)
            if (f.exists() && f.canonicalPath.startsWith(docsDir(context).canonicalPath)) f.delete()
        } catch (e: Exception) {
        }
    }

    fun discard(context: Context, file: AttachmentFile) = deleteStored(context, file.uriString)

    /** A link other apps can read, or null when the file is not available. */
    fun toShareUri(context: Context, stored: String): Uri? {
        val u = Uri.parse(stored)
        return when (u.scheme) {
            "file" -> try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(u.path ?: return null))
            } catch (e: Exception) {
                null
            }
            "content" -> u
            else -> null
        }
    }

    fun openFile(context: Context, stored: String, isPdf: Boolean): Boolean {
        val uri = toShareUri(context, stored) ?: return false
        val mime = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
            ?: if (isPdf) "application/pdf" else "image/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
