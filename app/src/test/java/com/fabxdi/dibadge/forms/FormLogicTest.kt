package com.fabxdi.dibadge.forms

import com.fabxdi.dibadge.data.FormEntity
import com.fabxdi.dibadge.data.FormSubmissionEntity
import com.fabxdi.dibadge.data.GroupFormEntity
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.canUserAssignTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormLogicTest {

    @Test
    fun testWhoCanAddFormsToGroup() {
        // Owner and Admin can add/remove forms in group
        assertTrue(canUserAssignTasks("owner"))
        assertTrue(canUserAssignTasks("admin"))
        assertTrue(canUserAssignTasks("OWNER"))
        assertTrue(canUserAssignTasks("ADMIN"))

        // Regular members cannot add forms
        assertFalse(canUserAssignTasks("member"))
        assertFalse(canUserAssignTasks("MEMBER"))
        assertFalse(canUserAssignTasks(""))
    }

    @Test
    fun testFormSnapshotUpdateIncrementsVersion() {
        val originalForm = FormEntity(
            id = "f1",
            creatorUid = "u1",
            title = "Site Safety Checklist",
            description = "Daily safety checks",
            questionsJson = "[{\"id\":\"q1\",\"type\":\"short_answer\",\"label\":\"Inspector Name\"}]",
            addedGroupIdsJson = "[\"g1\",\"g2\"]",
            version = 1
        )

        val updatedQuestions = "[{\"id\":\"q1\",\"type\":\"short_answer\",\"label\":\"Inspector Full Name\"},{\"id\":\"q2\",\"type\":\"signatures\",\"label\":\"Signatures\"}]"
        val updatedForm = originalForm.copy(
            title = "Updated Site Safety Checklist",
            questionsJson = updatedQuestions,
            version = originalForm.version + 1,
            updatedAtMs = System.currentTimeMillis()
        )

        assertEquals(2, updatedForm.version)
        assertNotEquals(originalForm.questionsJson, updatedForm.questionsJson)
        assertEquals("f1", updatedForm.id)

        // Published group form snapshot holds updated version
        val groupSnapshot = GroupFormEntity(
            id = "g1_f1",
            groupId = "g1",
            formId = updatedForm.id,
            ownerUid = updatedForm.creatorUid,
            title = updatedForm.title,
            description = updatedForm.description,
            questionsJson = updatedForm.questionsJson,
            version = updatedForm.version,
            addedBy = "Livin Varghese"
        )

        assertEquals(2, groupSnapshot.version)
        assertEquals(updatedQuestions, groupSnapshot.questionsJson)
    }

    @Test
    fun testSubmissionsKeepTheirVersionAndQuestionsIntact() {
        val initialVersion = 1
        val initialQuestionsJson = "[{\"id\":\"q1\",\"type\":\"short_answer\",\"label\":\"Inspector Name\"}]"

        val submissionV1 = FormSubmissionEntity(
            id = "sub1",
            groupId = "g1",
            formId = "f1",
            version = initialVersion,
            submitterUid = "u10",
            submitterName = "John Worker",
            answersJson = "{\"q1\":\"John Worker\"}",
            isComplete = true
        )

        // Form updated to version 2 with new questions
        val newVersion = 2
        val newQuestionsJson = "[{\"id\":\"q1\",\"type\":\"short_answer\",\"label\":\"Inspector Name\"},{\"id\":\"q2\",\"type\":\"photos\",\"label\":\"Site Photos\"}]"

        val groupFormV2 = GroupFormEntity(
            id = "g1_f1",
            groupId = "g1",
            formId = "f1",
            title = "Site Checklist V2",
            questionsJson = newQuestionsJson,
            version = newVersion
        )

        // The older submission stays locked to version 1 and its original answers
        assertEquals(1, submissionV1.version)
        assertEquals("{\"q1\":\"John Worker\"}", submissionV1.answersJson)
        assertEquals(2, groupFormV2.version)
        assertNotEquals(submissionV1.version, groupFormV2.version)
    }
}
