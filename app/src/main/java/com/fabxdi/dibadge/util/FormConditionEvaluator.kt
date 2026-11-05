package com.fabxdi.dibadge.util

import com.fabxdi.dibadge.data.FormQuestionData
import com.fabxdi.dibadge.ui.my_forms.FormAnswers

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
            "multiple_choice" -> FormAnswers.parseMulti(rawAnswer).contains(showIf.equals)
            "checklist" -> {
                // Stored as itemId -> {"s": "ok" | "notok" | "na", "r": remark, "p": [photos]}
                val itemId = showIf.itemId ?: ""
                val status = FormAnswers.parseChecklist(rawAnswer)[itemId]?.status ?: ""
                val expected = showIf.equals.trim().lowercase()
                val wanted = when {
                    expected == "notok" || expected.contains("not ok") || expected.contains("not_ok") -> "notok"
                    expected == "na" || expected.contains("n/a") -> "na"
                    expected == "ok" -> "ok"
                    else -> expected
                }
                status == wanted
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
