package com.fabxdi.dibadge.util

import android.content.Context
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserSubscription(
    val isPro: Boolean = false,
    val planType: String = "Free", // "Free", "Yearly", "Monthly"
    val renewalDateText: String = "",
    val isCancelled: Boolean = false,
    val storageUsedBytes: Long = 0L,
    val storageLimitBytes: Long = 1024L * 1024L * 1024L // 1 GB
)

object SubscriptionManager {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _subscriptionState = MutableStateFlow(UserSubscription())
    val subscriptionState: StateFlow<UserSubscription> = _subscriptionState.asStateFlow()

    fun listenToSubscription(context: Context) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val plan = snapshot.getString("subscriptionPlan") ?: "Free"
                val isPro = plan.equals("Pro", ignoreCase = true) || snapshot.getBoolean("isPro") == true
                val planType = snapshot.getString("planType") ?: if (isPro) "Yearly" else "Free"
                val isCancelled = snapshot.getBoolean("subscriptionCancelled") == true

                val expiresTimestamp = snapshot.getTimestamp("subscriptionExpiresAt")?.toDate()?.time
                    ?: snapshot.getLong("subscriptionExpiresAt")
                    ?: (System.currentTimeMillis() + 365L * 24 * 3600 * 1000)

                val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                val renewalDateText = dateFmt.format(Date(expiresTimestamp))

                val storageUsed = snapshot.getLong("storageUsedBytes") ?: 0L

                _subscriptionState.value = UserSubscription(
                    isPro = isPro,
                    planType = planType,
                    renewalDateText = renewalDateText,
                    isCancelled = isCancelled,
                    storageUsedBytes = storageUsed
                )
            }
    }

    fun subscribeUserToPro(context: Context, planType: String, onDone: () -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val oneYearMs = 365L * 24 * 3600 * 1000
        val oneMonthMs = 30L * 24 * 3600 * 1000
        val durationMs = if (planType.equals("Monthly", ignoreCase = true)) oneMonthMs else oneYearMs
        val expiresAt = System.currentTimeMillis() + durationMs

        val subData = mapOf(
            "isPro" to true,
            "subscriptionPlan" to "Pro",
            "planType" to planType,
            "subscriptionCancelled" to false,
            "subscriptionExpiresAt" to expiresAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("users").document(uid)
            .set(subData, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(context, "You are now on Pro", Toast.LENGTH_SHORT).show()
                onDone()
            }
    }

    fun restorePurchases(context: Context, onDone: () -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val isPro = snapshot.getString("subscriptionPlan")?.equals("Pro", ignoreCase = true) == true
                if (isPro) {
                    Toast.makeText(context, "Purchases restored", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No purchases to restore", Toast.LENGTH_SHORT).show()
                }
                onDone()
            }
            .addOnFailureListener {
                Toast.makeText(context, "No purchases to restore", Toast.LENGTH_SHORT).show()
                onDone()
            }
    }
}
