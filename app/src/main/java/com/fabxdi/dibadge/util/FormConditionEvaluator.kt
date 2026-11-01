package com.fabxdi.dibadge.util

import com.fabxdi.dibadge.data.FormQuestionData
import org.json.JSONObject

object FormConditionEvaluator {

    fun isQuestionVisible(
        q: FormQuestionData,
        allQuestions: List<FormQuestionData>,
        answersMap: Map<String, String>
    ): Boolean {
        val showIf = q.showIf ?: return true
        val parentIndex = allQuestions.indexOfFirst { it.id == showIf.questionId }
        val currentIndex = allQuestions.indexOfFirst { it.id == q.id }

        if (parentIndex == -1 || (currentIndex != -1 && parentIndex >= currentIndex)) {
            return true
        }

        val parentQ = allQuestions[parentIndex]
        if (!isQuestionVisible(parentQ, allQuestions, answersMap)) {
            return false
        }

        val rawAnswer = answersMap[parentQ.id] ?: return false

        return when (parentQ.type) {
            "yesno" -> {
                val expected = showIf.equals.lowercase()
                rawAnswer.lowercase() == expected
            }
            "single_choice", "dropdown" -> {
                rawAnswer == showIf.equals
            }
            "checklist" -> {
                val itemId = showIf.itemId ?: ""
                try {
                    val jsonObj = JSONObject(rawAnswer)
                    val itemObj = jsonObj.optJSONObject(itemId)
                    val result = itemObj?.optString("result", "ok") ?: "ok"
                    val expected = showIf.equals.lowercase()
                    if (expected.contains("not ok") || expected == "notok") {
                        result.equals("notok", ignoreCase = true)
                    } else if (expected.contains("n/a") || expected == "na") {
                        result.equals("na", ignoreCase = true)
                    } else if (expected == "ok" || expected.endsWith("ok")) {
                        result.equals("ok", ignoreCase = true)
                    } else {
                        result.equals(expected, ignoreCase = true)
                    }
                } catch (e: Exception) {
                    false
                }
            }
            else -> true
        }
    }

    fun filterAndClearHiddenAnswers(
        questions: List<FormQuestionData>,
        answersMap: Map<String, String>
    ): Map<String, String> {
        val result = answersMap.toMutableMap()
        var changed = true
        while (changed) {
            changed = false
            for (q in questions) {
                if (result.containsKey(q.id)) {
                    if (!isQuestionVisible(q, questions, result)) {
                        result.remove(q.id)
                        changed = true
                    }
                }
            }
        }
        return result
    }
}
