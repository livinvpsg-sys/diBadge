package com.fabxdi.dibadge.util

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Chats the person pinned to the top of the Home list. Kept on this phone (per account).
 */
object ChatPins {
    private val _pins = MutableStateFlow<Set<String>>(emptySet())
    private var loadedFor: String? = null

    /** Ids of the pinned chats. */
    val pins: StateFlow<Set<String>> = _pins.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("chat_pins", Context.MODE_PRIVATE)

    private fun uid() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun load(context: Context) {
        val me = uid()
        if (loadedFor == me) return
        loadedFor = me
        _pins.value = if (me.isBlank()) emptySet() else prefs(context).getStringSet("pins_$me", emptySet()).orEmpty().toSet()
    }

    /** Pins (or unpins) all of [chatIds]. */
    fun set(context: Context, chatIds: Collection<String>, pinned: Boolean) {
        load(context)
        val me = uid()
        if (me.isBlank()) return
        val next = if (pinned) _pins.value + chatIds else _pins.value - chatIds.toSet()
        _pins.value = next
        prefs(context).edit().putStringSet("pins_$me", next).apply()
    }
}

/**
 * Chats the person muted. It is the same list the chat's own "Mute notifications" uses
 * (users/{uid}/mutedChats), so muting here or there is the same thing.
 */
object ChatMutes {
    private val _muted = MutableStateFlow<Set<String>>(emptySet())
    private var reg: ListenerRegistration? = null
    private var listeningFor: String? = null

    /** Ids of the chats that are muted right now. */
    val muted: StateFlow<Set<String>> = _muted.asStateFlow()

    private fun uid() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun start() {
        val me = uid()
        if (listeningFor == me) return
        reg?.remove(); reg = null
        listeningFor = me
        _muted.value = emptySet()
        if (me.isBlank()) return
        reg = FirebaseFirestore.getInstance().collection("users").document(me).collection("mutedChats")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val now = System.currentTimeMillis()
                _muted.value = snap.documents.filter { (it.getLong("until") ?: 0L) > now }.map { it.id }.toSet()
            }
    }

    /** Mutes all of [chatIds] until they are unmuted. */
    fun mute(chatIds: Collection<String>) {
        val me = uid()
        if (me.isBlank()) return
        val col = FirebaseFirestore.getInstance().collection("users").document(me).collection("mutedChats")
        chatIds.forEach { col.document(it).set(mapOf("until" to Long.MAX_VALUE)) }
    }

    fun unmute(chatIds: Collection<String>) {
        val me = uid()
        if (me.isBlank()) return
        val col = FirebaseFirestore.getInstance().collection("users").document(me).collection("mutedChats")
        chatIds.forEach { col.document(it).delete() }
    }
}