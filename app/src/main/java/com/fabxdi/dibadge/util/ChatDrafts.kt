package com.fabxdi.dibadge.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Text typed in a chat but not sent yet. It is kept on this phone, brought back when the chat is
 * opened again, and shown as "Draft: ..." in the home list.
 */
object ChatDrafts {
    private const val PREFS = "chat_drafts"
    private var loaded = false
    private val _drafts = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Chat id -> unsent text (only chats that have a draft). */
    val drafts: StateFlow<Map<String, String>> = _drafts.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val map = HashMap<String, String>()
        for ((k, v) in prefs(context).all) {
            if (v is String && v.isNotBlank()) map[k] = v
        }
        _drafts.value = map
    }

    fun get(context: Context, chatId: String): String {
        load(context)
        return _drafts.value[chatId].orEmpty()
    }

    /** Saves the draft, or removes it when the text is empty. */
    fun set(context: Context, chatId: String, text: String) {
        load(context)
        val editor = prefs(context).edit()
        val next = HashMap(_drafts.value)
        if (text.isBlank()) {
            if (!next.containsKey(chatId)) return
            next.remove(chatId)
            editor.remove(chatId)
        } else {
            if (next[chatId] == text) return
            next[chatId] = text
            editor.putString(chatId, text)
        }
        editor.apply()
        _drafts.value = next
    }
}