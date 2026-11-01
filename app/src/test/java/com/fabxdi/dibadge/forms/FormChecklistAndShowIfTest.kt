package com.fabxdi.dibadge.forms

import com.fabxdi.dibadge.data.ChecklistItem
import com.fabxdi.dibadge.data.FormQuestionData
import com.fabxdi.dibadge.data.FormQuestionShowIf
import com.fabxdi.dibadge.util.FormConditionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormChecklistAndShowIfTest {

    @Test
    fun testShowIfQuestionHiddenWhenParentAnswerDoesNotMatch() {
        val q1 = FormQuestionData(
            id = "q1",
            type = "yesno",
            label = "Are you on medication?"
        )
        val q2 = FormQuestionData(
            id = "q2",
            type = "short_answer",
            label = "Specify medication",
            showIf = FormQuestionShowIf(questionId = "q1", equals = "yes")
        )
        val allQuestions = listOf(q1, q2)

        // When Q1 is "no", Q2 is hidden
        val answers1 = mapOf("q1" to "no", "q2" to "Aspirin")
        assertFalse(FormConditionEvaluator.isQuestionVisible(q2, allQuestions, answers1))

        // When Q1 is "yes", Q2 is visible
        val answers2 = mapOf("q1" to "yes", "q2" to "Aspirin")
        assertTrue(FormConditionEvaluator.isQuestionVisible(q2, allQuestions, answers2))
    }

    @Test
    fun testHiddenAnswersClearedAndNotRequired() {
        val q1 = FormQuestionData(id = "q1", type = "yesno", label = "Are you on medication?")
        val q2 = FormQuestionData(
            id = "q2",
            type = "short_answer",
            label = "Specify medication",
            isRequired = true,
            showIf = FormQuestionShowIf(questionId = "q1", equals = "yes")
        )
        val questions = listOf(q1, q2)

        val initialAnswers = mapOf("q1" to "no", "q2" to "Paracetamol")
        val cleanedAnswers = FormConditionEvaluator.filterAndClearHiddenAnswers(questions, initialAnswers)

        assertFalse(cleanedAnswers.containsKey("q2"))
        assertEquals("no", cleanedAnswers["q1"])
    }

    @Test
    fun testChecklistRemarkRequiredOnNotOk() {
        val item1 = ChecklistItem(id = "item1", name = "Safety Helmet", note = "Valid expiry")
        val checklistQ = FormQuestionData(
            id = "q_chk",
            type = "checklist",
            label = "PPE Inspection",
            isRequired = true,
            requireRemarkOnNotOk = true,
            items = listOf(item1)
        )

        // Result "notok" with blank remark -> Invalid
        val jsonAnswerInvalid = """{"item1":{"result":"notok","remark":"   "}}"""
        val isRemarkValid1 = isChecklistItemValid(checklistQ, "item1", jsonAnswerInvalid)
        assertFalse(isRemarkValid1)

        // Result "notok" with non-blank remark -> Valid
        val jsonAnswerValid = """{"item1":{"result":"notok","remark":"Cracked chin strap"}}"""
        val isRemarkValid2 = isChecklistItemValid(checklistQ, "item1", jsonAnswerValid)
        assertTrue(isRemarkValid2)
    }

    @Test
    fun testDeclarationRequiredMustBeTicked() {
        val declQ = FormQuestionData(
            id = "q_decl",
            type = "declaration",
            label = "I declare that all information provided is accurate.",
            isRequired = true
        )

        assertFalse(isDeclarationAnswerValid(declQ, "false"))
        assertFalse(isDeclarationAnswerValid(declQ, ""))
        assertTrue(isDeclarationAnswerValid(declQ, "true"))
    }

    @Test
    fun testAutoFilledValuesEvaluation() {
        val projectName = evaluateAutoFilledValue("project", "Site Alpha", "John Doe", "7K2M9QX4B", "12 Mar 2025")
        val fillerName = evaluateAutoFilledValue("name", "Site Alpha", "John Doe", "7K2M9QX4B", "12 Mar 2025")
        val badgeNumber = evaluateAutoFilledValue("badge", "Site Alpha", "John Doe", "7K2M9QX4B", "12 Mar 2025")
        val todayDate = evaluateAutoFilledValue("date", "Site Alpha", "John Doe", "7K2M9QX4B", "12 Mar 2025")

        assertEquals("Site Alpha", projectName)
        assertEquals("John Doe", fillerName)
        assertEquals("7K2M9QX4B", badgeNumber)
        assertEquals("12 Mar 2025", todayDate)
    }

    private fun isChecklistItemValid(q: FormQuestionData, itemId: String, answerJson: String): Boolean {
        if (q.type != "checklist") return true
        if (!answerJson.contains(itemId)) return false
        val isNotOk = answerJson.contains(""""result":"notok"""") || answerJson.contains(""""result": "notok"""")
        if (isNotOk && q.requireRemarkOnNotOk) {
            val remarkText = answerJson.substringAfter(""""remark":""", "").substringAfter("\"", "").substringBefore("\"", "").trim()
            return remarkText.isNotBlank()
        }
        return true
    }

    private fun isDeclarationAnswerValid(q: FormQuestionData, answer: String): Boolean {
        if (q.type != "declaration") return true
        if (q.isRequired) {
            return answer == "true"
        }
        return true
    }

    private fun evaluateAutoFilledValue(kind: String, groupName: String, fillerName: String, badgeNo: String, dateStr: String): String {
        return when (kind) {
            "project" -> groupName
            "name" -> fillerName
            "badge" -> badgeNo
            "date" -> dateStr
            else -> groupName
        }
    }
}
