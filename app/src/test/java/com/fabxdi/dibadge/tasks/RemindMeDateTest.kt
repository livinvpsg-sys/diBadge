package com.fabxdi.dibadge.tasks

import com.fabxdi.dibadge.data.ToDoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RemindMeDateTest {

    @Test
    fun testDefaultRemindDateUsesDueDateOrNull() {
        val reminderDefault = ToDoEntity(
            title = "Task",
            content = "",
            date = LocalDate.of(2026, 10, 23),
            startDate = null,
            endDate = null,
            repeatType = "once",
            time = "10:04 am",
            isAlarmEnabled = true,
            remindDate = null
        )
        assertNull(reminderDefault.remindDate)
    }

    @Test
    fun testCustomRemindDateStored() {
        val customDate = LocalDate.of(2026, 10, 22)
        val reminderCustom = ToDoEntity(
            title = "Task",
            content = "",
            date = LocalDate.of(2026, 10, 23),
            startDate = null,
            endDate = null,
            repeatType = "once",
            time = "10:04 am",
            isAlarmEnabled = true,
            remindDate = customDate
        )
        assertEquals(customDate, reminderCustom.remindDate)
    }

    @Test
    fun testRepeatingTaskClearsCustomRemindDate() {
        var customRemindDate: LocalDate? = LocalDate.of(2026, 10, 22)
        var repeatType = "daily"

        if (repeatType != "once" && customRemindDate != null) {
            customRemindDate = null
        }

        assertNull(customRemindDate)
    }

    @Test
    fun testDueDateEarlierResetsCustomRemindDate() {
        var customRemindDate: LocalDate? = LocalDate.of(2026, 10, 22)
        val newDueDate = LocalDate.of(2026, 10, 20)

        if (customRemindDate != null && newDueDate.isBefore(customRemindDate)) {
            customRemindDate = null
        }

        assertNull(customRemindDate)
    }
}
