package com.fabxdi.dibadge.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * The signed-in person's own badge number, read from their profile (users/{uid}.badgeNumber).
 * It is empty until it has loaded. It is the code inside "My QR code".
 */
@Composable
fun rememberMyBadgeNumber(): String {
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var code by remember(uid) { mutableStateOf("") }
    DisposableEffect(uid) {
        if (uid.isBlank()) return@DisposableEffect onDispose { }
        val reg = FirebaseFirestore.getInstance().collection("users").document(uid)
            .addSnapshotListener { snap, _ -> code = snap?.getString("badgeNumber").orEmpty() }
        onDispose { reg.remove() }
    }
    return code
}