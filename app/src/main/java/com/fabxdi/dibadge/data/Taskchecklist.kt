package com.fabxdi.dibadge.data

import org.json.JSONArray
import org.json.JSONObject

/** One checklist line of a group task. */
data class TaskCheckItem(val text: String, val done: Boolean = false)

fun parseTaskChecklist(json: String?): List<TaskCheckItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val t = o.optString("t")
            if (t.isBlank()) null else TaskCheckItem(t, o.optBoolean("d", false))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun encodeTaskChecklist(items: List<TaskCheckItem>): String {
    val arr = JSONArray()
    items.filter { it.text.isNotBlank() }.forEach {
        arr.put(JSONObject().put("t", it.text.trim()).put("d", it.done))
    }
    return arr.toString()
}