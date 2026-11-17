package com.fabxdi.dibadge.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When each chat was last on screen on this phone. The home list counts messages newer than this as
 * unread. A chat seen for the first time starts as "all read", so old messages are not counted.
 */
object ChatSeen {
    private const val PREFS = "chat_seen"
    private val _version = MutableStateFlow(0)

    /** Goes up each time a seen time changes, so the home list can recount. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, groupId: String): Long = prefs(context).getLong(groupId, 0L)

    /** Makes sure a chat has a seen time (now) the first time it is listed. */
    fun ensure(context: Context, groupId: String) {
        val p = prefs(context)
        if (!p.contains(groupId)) p.edit().putLong(groupId, System.currentTimeMillis()).apply()
    }

    fun markNow(context: Context, groupId: String) {
        prefs(context).edit().putLong(groupId, System.currentTimeMillis()).apply()
        _version.value = _version.value + 1
    }
}