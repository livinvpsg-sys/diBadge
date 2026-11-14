package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import com.fabxdi.dibadge.viewmodel.GroupMember
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Reads the members of another (linked) group so a task can be assigned to all of them. */
suspend fun fetchGroupMembersForTask(groupId: String): List<GroupMember> = withContext(Dispatchers.IO) {
    try {
        val db = FirebaseFirestore.getInstance()
        val groupDoc = db.collection("personalGroups").document(groupId).get().await()
        val uids = (groupDoc.get("members") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        uids.mapNotNull { uid ->
            try {
                val u = db.collection("users").document(uid).get().await()
                if (!u.exists()) return@mapNotNull null
                val first = u.getString("firstName") ?: ""
                val last = u.getString("lastName") ?: ""
                val nick = u.getString("nickname") ?: ""
                val name = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")
                    .ifBlank { nick.ifBlank { u.getString("displayName") ?: "User" } }
                GroupMember(uid = uid, displayName = name, badgeNumber = u.getString("badgeNumber") ?: "")
            } catch (e: Exception) {
                null
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}