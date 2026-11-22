package com.fabxdi.dibadge.ui.home.qr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.security.SecureRandom

/**
 * The secret behind a person's QR code and invite link (users/{uid}.qrCode).
 * It is made once, and "Reset my QR code" replaces it, which stops every old QR image and link from working.
 */
object MyQrCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0, O, 1, I

    fun newCode(): String {
        val r = SecureRandom()
        return (1..9).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    /** Replaces the code with a new one. */
    fun reset() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("qrCode" to newCode()), SetOptions.merge())
    }

    /** The link that opens the diBadge app on this person (page served by the Worker). */
    fun linkFor(code: String): String = "https://dibadge-push.dibadge.workers.dev/u/$code"
}

/**
 * The signed-in person's QR code. It is empty until it has loaded, and it is made here the first time.
 */
@Composable
fun rememberMyQrCode(): String {
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var code by remember(uid) { mutableStateOf("") }
    DisposableEffect(uid) {
        if (uid.isBlank()) return@DisposableEffect onDispose { }
        val ref = FirebaseFirestore.getInstance().collection("users").document(uid)
        val reg = ref.addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val saved = snap.getString("qrCode").orEmpty()
            if (saved.isBlank() && !snap.metadata.isFromCache) {
                ref.set(mapOf("qrCode" to MyQrCode.newCode()), SetOptions.merge())
            } else {
                code = saved
            }
        }
        onDispose { reg.remove() }
    }
    return code
}
