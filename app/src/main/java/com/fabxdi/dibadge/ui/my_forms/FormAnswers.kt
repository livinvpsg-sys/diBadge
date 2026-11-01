package com.fabxdi.dibadge.ui.my_forms

import com.fabxdi.dibadge.data.FormQuestionData
import org.json.JSONArray
import org.json.JSONObject

/** One checklist item's answer. status is "ok", "notok", "na" or "" (not answered). */
data class ChecklistAnswer(
    val status: String = "",
    val remark: String = "",
    val photos: List<String> = emptyList()
)

/**
 * Shared rules for turning question answers into the String values stored in answersJson,
 * deciding which questions are visible (show-if), validating, and printing answers.
 *
 * Stored formats (all plain Strings, so answersJson keeps its Map<String, String> shape):
 *  - yesno            "yes" | "no"
 *  - declaration      "true" when ticked
 *  - multiple_choice  JSON array of option texts
 *  - checklist        JSON object: itemId -> {"s": status, "r": remark, "p": [photoUri]}
 *  - everything else  unchanged
 */
object FormAnswers {

    val signatureTypes = listOf("signatures", "single_signature", "multiple_signatures")

    // ---------------- show-if ----------------

    /** Ids of questions that should be shown, given the current answers. A question whose source is hidden is hidden too. */
    fun visibleIds(questions: List<FormQuestionData>, answers: Map<String, String>): Set<String> {
        val visible = LinkedHashSet<String>()
        for (q in questions) {
            val rule = q.showIf
            val show = rule == null ||
                    (rule.questionId in visible &&
                            (answers[rule.questionId] ?: "").equals(rule.equals, ignoreCase = true))
            if (show) visible.add(q.id)
        }
        return visible
    }

    // ---------------- multiple choice ----------------

    fun parseMulti(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it) }
        } catch (e: Exception) {
            listOf(raw) // older plain-text answer
        }
    }

    fun encodeMulti(list: List<String>): String =
        if (list.isEmpty()) "" else JSONArray(list).toString()

    // ---------------- checklist ----------------

    fun parseChecklist(raw: String): Map<String, ChecklistAnswer> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = LinkedHashMap<String, ChecklistAnswer>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val o = obj.optJSONObject(key) ?: continue
                val photos = mutableListOf<String>()
                o.optJSONArray("p")?.let { arr ->
                    for (i in 0 until arr.length()) photos.add(arr.optString(i))
                }
                out[key] = ChecklistAnswer(o.optString("s"), o.optString("r"), photos)
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun encodeChecklist(map: Map<String, ChecklistAnswer>): String {
        val obj = JSONObject()
        map.forEach { (id, a) ->
            if (a.status.isNotBlank()) {
                val o = JSONObject()
                o.put("s", a.status)
                if (a.remark.isNotBlank()) o.put("r", a.remark)
                if (a.photos.isNotEmpty()) o.put("p", JSONArray(a.photos))
                obj.put(id, o)
            }
        }
        return if (obj.length() == 0) "" else obj.toString()
    }

    fun statusLabel(status: String): String = when (status) {
        "ok" -> "OK"
        "notok" -> "Not OK"
        "na" -> "N/A"
        else -> "-"
    }

    // ---------------- validation ----------------

    /** null when the answer is fine, otherwise the message to show under the question. */
    fun problem(q: FormQuestionData, raw: String): String? {
        if (q.type == "instruction" || q.type in signatureTypes) return null
        return when (q.type) {
            "checklist" -> {
                val answers = parseChecklist(raw)
                when {
                    q.isRequired && q.items.any { (answers[it.id]?.status ?: "").isBlank() } ->
                        "Answer every item"
                    q.requireRemarkOnNotOk && q.items.any {
                        answers[it.id]?.status == "notok" && answers[it.id]?.remark.isNullOrBlank()
                    } -> "Add a remark for items marked Not OK"
                    else -> null
                }
            }
            "declaration" -> if (q.isRequired && raw != "true") "Required" else null
            "multiple_choice" -> if (q.isRequired && parseMulti(raw).isEmpty()) "Required" else null
            else -> if (q.isRequired && raw.isBlank()) "Required" else null
        }
    }

    // ---------------- printing (detail screen and PDF) ----------------

    fun displayText(q: FormQuestionData, raw: String): String = when (q.type) {
        "yesno" -> when (raw.lowercase()) {
            "yes" -> "Yes"
            "no" -> "No"
            else -> raw
        }
        "declaration" -> if (raw == "true") "Agreed" else ""
        "multiple_choice" -> parseMulti(raw).joinToString(", ")
        "checklist" -> {
            val answers = parseChecklist(raw)
            if (answers.isEmpty()) "" else q.items.mapIndexed { i, item ->
                val a = answers[item.id] ?: ChecklistAnswer()
                val name = item.name.ifBlank { "Item ${i + 1}" }
                val extra = buildString {
                    if (a.remark.isNotBlank()) append(": ${a.remark}")
                    if (a.photos.isNotEmpty()) append(" (${a.photos.size} ${if (a.photos.size == 1) "photo" else "photos"})")
                }
                "$name - ${statusLabel(a.status)}$extra"
            }.joinToString("\n")
        }
        else -> raw
    }
}
