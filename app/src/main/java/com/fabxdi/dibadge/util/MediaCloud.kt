package com.fabxdi.dibadge.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Sends chat files to Cloudflare R2 through the diBadge media Worker and removes them again.
 *
 * Every file becomes a normal https link, so the rest of the app (images, video, documents)
 * keeps working with the same link strings it used with Firebase Storage. Old messages that
 * still hold Firebase links keep working too.
 */
object MediaCloud {

    /** Set this to the PUBLIC_BASE_URL in worker/wrangler.toml (no trailing slash). */
    const val BASE_URL = "https://dibadge-media.dibadge.workers.dev"

    private const val TAG = "MediaCloud"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** True when this link points at our own media server. */
    fun isOurs(url: String): Boolean = url.startsWith("$BASE_URL/f/")

    /**
     * Uploads [uri] for the chat [groupId] / message [msgId] and returns the public link.
     * Throws if the upload fails, so the caller can mark the message as failed instead of
     * sending a message with a file nobody else can open.
     */
    suspend fun upload(
        context: Context,
        uri: Uri,
        groupId: String,
        msgId: String,
        fileName: String,
        mimeType: String? = null
    ): String = withContext(Dispatchers.IO) {
        val resolver = context.applicationContext.contentResolver
        val type = mimeType ?: resolver.getType(uri) ?: "application/octet-stream"

        // The server needs the exact size up front. Some sources do not report it, so those are
        // copied to a temporary file first.
        var tempCopy: File? = null
        var sourceUri = uri
        var size = try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (e: Exception) {
            -1L
        }
        if (size <= 0L) {
            val tmp = File(context.cacheDir, "upload_${System.nanoTime()}.tmp")
            resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: throw IOException("Cannot read the file")
            tempCopy = tmp
            sourceUri = Uri.fromFile(tmp)
            size = tmp.length()
        }
        if (size <= 0L) throw IOException("The file is empty")

        val endpoint = "$BASE_URL/upload/${enc(groupId)}/${enc(msgId)}/${enc(fileName)}"
        val fileSize = size
        val fileUri = sourceUri

        try {
            var forceRefresh = false
            var attempt = 0
            while (true) {
                attempt++
                val token = FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(forceRefresh)?.await()?.token
                    ?: throw IOException("Not signed in")

                val body = object : RequestBody() {
                    override fun contentType() = type.toMediaTypeOrNull()
                    override fun contentLength() = fileSize
                    override fun writeTo(sink: BufferedSink) {
                        val input = resolver.openInputStream(fileUri) ?: throw IOException("Cannot read the file")
                        input.source().use { sink.writeAll(it) }
                    }
                }
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer $token")
                    .put(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        return@withContext JSONObject(text).getString("url")
                    }
                    if (response.code == 401 && attempt == 1) {
                        forceRefresh = true // token may have just expired: get a fresh one once
                        return@use
                    }
                    Log.w(TAG, "Upload failed ${response.code}: $text")
                    throw IOException("Upload failed (${response.code})")
                }
            }
            // Never reached; the loop above always returns or throws. Kotlin needs a final value.
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        } finally {
            tempCopy?.delete()
        }
    }

    /** Removes a file this account uploaded. Quietly does nothing for other links. */
    suspend fun delete(url: String): Boolean = withContext(Dispatchers.IO) {
        if (!isOurs(url)) return@withContext false
        try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                ?: return@withContext false
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "Delete failed: ${e.message}")
            false
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
