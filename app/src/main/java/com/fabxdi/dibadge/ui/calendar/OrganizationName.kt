package com.fabxdi.dibadge.ui.calendar

import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** The signed-in account's own organisation from its profile, or "" when it has none. */
@Composable
fun rememberOrganizationName(): String {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var org by remember(uid) { mutableStateOf("") }
    LaunchedEffect(uid) {
        org = ""
        if (uid == null) return@LaunchedEffect
        try {
            val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
            org = doc.getString("organization") ?: doc.getString("company") ?: ""
        } catch (e: Exception) {
        }
    }
    return org
}
