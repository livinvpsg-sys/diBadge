package com.fabxdi.dibadge.viewmodel

import com.fabxdi.dibadge.util.generateBadgeNumber
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Makes sure a signed-in account has its profile record at users/{uid}. Search reads this record,
 * so an account without it can sign in but nobody can find it.
 *
 * Only missing fields are filled in, never overwritten, and it runs as one transaction so the
 * badge number is created once even if two screens ask at the same moment.
 *
 * @return the account's badge number
 */
suspend fun ensureUserProfileDoc(user: FirebaseUser): String {
    val firestore = FirebaseFirestore.getInstance()
    val ref = firestore.collection("users").document(user.uid)
    val email = user.email.orEmpty().trim()
    val authName = user.displayName.orEmpty().trim()

    return firestore.runTransaction { tx ->
        val snap = tx.get(ref)
        val updates = mutableMapOf<String, Any>()

        if (!snap.exists()) {
            updates["uid"] = user.uid
            updates["createdAt"] = System.currentTimeMillis()
        }

        var badge = snap.getString("badgeNumber").orEmpty()
        if (badge.isBlank()) {
            badge = generateBadgeNumber()
            updates["badgeNumber"] = badge
        }

        if (email.isNotBlank()) {
            if (snap.getString("email").isNullOrBlank()) updates["email"] = email
            if (snap.getString("emailLower") != email.lowercase()) updates["emailLower"] = email.lowercase()
        }

        val savedName = snap.getString("displayName").orEmpty().trim()
        val nameToUse = savedName.ifBlank { authName }
        if (nameToUse.isNotBlank()) {
            if (savedName.isBlank()) updates["displayName"] = nameToUse
            if (snap.getString("displayNameLower") != nameToUse.lowercase()) {
                updates["displayNameLower"] = nameToUse.lowercase()
            }
        }

        if (updates.isNotEmpty()) tx.set(ref, updates, SetOptions.merge())
        badge
    }.await()
}
