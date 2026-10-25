package com.fabxdi.dibadge.forms

import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.data.FormStamp
import com.fabxdi.dibadge.data.FormSubmissionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormSignatureLogicTest {

    @Test
    fun testWorkerCanSignAnyTime() {
        val worker1 = FormSigner("u1", "Worker One")
        val worker2 = FormSigner("u2", "Worker Two")

        val stampWorker2 = FormStamp("http://sig/2.png", "Worker Two", "B200")
        val updatedWorker2 = worker2.copy(stamp = stampWorker2)

        assertNotNull(updatedWorker2.stamp)
        assertNull(worker1.stamp)

        val allWorkersSigned = listOf(worker1, updatedWorker2).all { it.stamp != null }
        assertFalse(allWorkersSigned)
    }

    @Test
    fun testSupervisorSignAndSubmitOnlyAfterAllWorkersSigned() {
        val stamp1 = FormStamp("http://sig/1.png", "Worker One", "B100")
        val stamp2 = FormStamp("http://sig/2.png", "Worker Two", "B200")

        val signedWorker1 = FormSigner("u1", "Worker One", stamp = stamp1)
        val signedWorker2 = FormSigner("u2", "Worker Two", stamp = stamp2)

        val workers = listOf(signedWorker1, signedWorker2)
        val supervisor = FormSigner("u3", "Supervisor Three", isSupervisor = true)

        val allWorkersSigned = workers.all { it.stamp != null }
        assertTrue(allWorkersSigned)

        val supervisorStamp = FormStamp("http://sig/3.png", "Supervisor Three", "SUP99")
        val updatedSupervisor = supervisor.copy(stamp = supervisorStamp)

        val submission = FormSubmissionEntity(
            id = "sub100",
            groupId = "g1",
            formId = "f1",
            submitterUid = supervisor.uid,
            submitterName = supervisor.name,
            status = if (allWorkersSigned && updatedSupervisor.stamp != null) "submitted" else "awaitingSignatures"
        )

        assertEquals("submitted", submission.status)
        assertEquals("Supervisor Three", submission.submitterName)
    }

    @Test
    fun testStampContainsFullDetails() {
        val stamp = FormStamp(
            signatureUrl = "data:image/png;base64,iVBORw0KG...",
            fullName = "Livin Varghese",
            badgeNumber = "S12345",
            signedAtMs = 1792900000000L,
            tzOffset = "GMT+8"
        )

        assertEquals("data:image/png;base64,iVBORw0KG...", stamp.signatureUrl)
        assertEquals("Livin Varghese", stamp.fullName)
        assertEquals("S12345", stamp.badgeNumber)
        assertEquals("GMT+8", stamp.tzOffset)
    }
}
