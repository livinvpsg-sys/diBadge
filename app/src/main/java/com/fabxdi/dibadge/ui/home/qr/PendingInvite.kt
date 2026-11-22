package com.fabxdi.dibadge.ui.home.qr

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The invite code from a link the person opened (dibadge://u/CODE). The Home screen shows who it is
 * and offers "Send connection request", then clears it.
 */
object PendingInvite {
    val code = MutableStateFlow<String?>(null)
}
