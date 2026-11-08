package com.fabxdi.dibadge.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.first

/**
 * Single place that writes activity logs, so every log is saved on the phone (Room)
 * AND in the cloud (Firestore: users/{uid}/activity_logs/{id}).
 * Always use this instead of calling activityLogDao().insertActivityLog directly.
 */
object ActivityLogRepository {

    private fun collection(uid: String) = FirebaseFirestore.getInstance()
        .collection("users").document(uid).collection("activity_logs")

    /** Save locally, then push to the cloud. Safe to call from any thread. */
    suspend fun record(context: Context, log: ActivityLogEntity) {
        AppDatabase.getDatabase(context).activityLogDao().insertActivityLog(log)
        push(log)
    }

    /** Fire-and-forget: Firestore queues the write and sends it when back online. */
    fun push(log: ActivityLogEntity) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            collection(uid).document(log.id).set(log.toMap(), SetOptions.merge())
        } catch (_: Exception) {
        }
    }

    /** Upload any local logs that the cloud does not have yet (e.g. made before this fix). */
    suspend fun uploadLocalOnly(context: Context, cloudIds: Set<String>) {
        val local = AppDatabase.getDatabase(context).activityLogDao()
            .getAllActivityLogs().first()
        local.filter { it.id !in cloudIds && !it.id.startsWith("act_") }.forEach { push(it) }
    }
}

fun ActivityLogEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "result" to result,
    "kind" to kind,
    "title" to title,
    "source" to source,
    "reference" to reference,
    "relatedId" to relatedId,
    "timestamp" to timestamp
)

fun Map<String, Any?>.toActivityLog(documentId: String): ActivityLogEntity = ActivityLogEntity(
    id = this["id"] as? String ?: documentId,
    result = this["result"] as? String ?: "Confirmed",
    kind = this["kind"] as? String ?: "message",
    title = this["title"] as? String ?: "Activity",
    source = this["source"] as? String ?: "Organisation",
    reference = this["reference"] as? String ?: "Letter",
    relatedId = this["relatedId"] as? String ?: "",
    timestamp = (this["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
)