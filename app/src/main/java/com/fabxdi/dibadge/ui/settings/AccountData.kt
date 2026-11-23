package com.fabxdi.dibadge.ui.settings

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.Dispatchers
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/**
 * "Download my data": reads the signed-in person's own records with their own permissions
 * and turns them into one JSON text. Nothing goes through a server.
 */
object AccountDataExport {

    /** Everything stored under users/{uid} that belongs to the person. */
    private val USER_COLLECTIONS = listOf(
        "experience", "nicknames", "userConnections", "tasks", "leaves", "overtime",
        "claims", "inbox", "documents", "activity_logs", "linkedDevices", "mutedChats", "groupCallLog",
        "verifiedOrganizations"
    )

    /** Calls [onDone] on the main thread with the JSON text, or null when nothing could be read. */
    fun build(uid: String, email: String, onDone: (String?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val result = JSONObject()
        val skipped = JSONArray()
        val total = 2 + USER_COLLECTIONS.size // profile + blocked list + collections
        val pending = AtomicInteger(total)
        val readOk = AtomicInteger(0)

        fun oneDone() {
            if (pending.decrementAndGet() == 0) {
                if (readOk.get() == 0) {
                    onDone(null)
                } else {
                    result.put("skipped", skipped)
                    onDone(result.toString(2))
                }
            }
        }

        result.put("app", "diBadge")
        result.put("exportedAt", isoTime(Date()))
        result.put("accountId", uid)
        result.put("email", email)

        val userRef = db.collection("users").document(uid)

        userRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                readOk.incrementAndGet()
                result.put("profile", docJson(task.result))
            } else {
                skipped.put("profile")
            }
            oneDone()
        }

        db.collection("blockedUsers").document(uid).collection("blocked").get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                readOk.incrementAndGet()
                result.put("blockedUsers", JSONArray().also { arr -> task.result.documents.forEach { arr.put(docJson(it)) } })
            } else {
                skipped.put("blockedUsers")
            }
            oneDone()
        }

        USER_COLLECTIONS.forEach { name ->
            userRef.collection(name).get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    readOk.incrementAndGet()
                    result.put(name, JSONArray().also { arr -> task.result.documents.forEach { arr.put(docJson(it)) } })
                } else {
                    skipped.put(name)
                }
                oneDone()
            }
        }
    }

    private fun docJson(doc: DocumentSnapshot): JSONObject {
        val o = JSONObject()
        o.put("id", doc.id)
        doc.data?.forEach { (k, v) -> o.put(k, toJson(v)) }
        return o
    }

    private fun toJson(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), toJson(x)) } }
        is Iterable<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it)) } }
        is Timestamp -> isoTime(v.toDate())
        is GeoPoint -> JSONObject().put("latitude", v.latitude).put("longitude", v.longitude)
        is DocumentReference -> v.path
        is Boolean, is Number, is String -> v
        else -> v.toString()
    }

    private fun isoTime(d: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(d)
}

/** "Delete account": asks the Worker, which removes the data and the sign-in on the server. */
object AccountDeletion {
    private const val WORKER_BASE = "https://dibadge-push.dibadge.workers.dev"

    /**
     * Deletes every file stored for this person in Firebase Storage (users/{uid}/...: photo, signature, anything else).
     * Must run BEFORE the server request, because afterwards the person can no longer sign in to Storage.
     * A missing file or a refused listing is ignored.
     */
    suspend fun deleteStorageFiles(uid: String) {
        val storage = FirebaseStorage.getInstance()
        suspend fun wipe(ref: StorageReference, depth: Int) {
            if (depth > 4) return
            val listing = try { ref.listAll().await() } catch (e: Exception) { return }
            listing.items.forEach { item -> try { item.delete().await() } catch (e: Exception) { } }
            listing.prefixes.forEach { wipe(it, depth + 1) }
        }
        wipe(storage.reference.child("users/$uid"), 0)
        // In case listing is not allowed by the rules, the two known files are removed by name too
        listOf("profile_photo.jpg", "signature.png").forEach { name ->
            try { storage.reference.child("users/$uid/$name").delete().await() } catch (e: Exception) { }
        }
    }

    /** HTTP status and body. Status -1 means the server could not be reached. */
    suspend fun request(idToken: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = URL("$WORKER_BASE/account/delete").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write("{}".toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to body
        } catch (e: Exception) {
            -1 to ""
        } finally {
            conn.disconnect()
        }
    }
}