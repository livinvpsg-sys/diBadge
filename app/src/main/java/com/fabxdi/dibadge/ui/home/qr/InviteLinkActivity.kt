package com.fabxdi.dibadge.ui.home.qr

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.fabxdi.dibadge.MainActivity

/**
 * Catches invite links (dibadge://u/CODE, or the web link from the Worker), remembers the code and opens the app.
 * It has no screen of its own.
 */
class InviteLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val code = data?.lastPathSegment.orEmpty().trim().uppercase()
        if (Regex("^[A-Z0-9]{9}$").matches(code)) {
            PendingInvite.code.value = code
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
