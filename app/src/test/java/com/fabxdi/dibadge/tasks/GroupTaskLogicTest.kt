package com.fabxdi.dibadge.tasks

import com.fabxdi.dibadge.data.GroupTaskEntity
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.TaskStatusCategory
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.calculateTaskStatusCategory
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.canUserAssignTasks
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.createTasksForAssignees
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.isTaskVisibleInAssignedToMe
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.resolveUserRole
import com.fabxdi.dibadge.ui.home.home_entry.chat_details.sortGroupTasks
import com.fabxdi.dibadge.viewmodel.GroupMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GroupTaskLogicTest {

    private val today = LocalDate.of(2026, 10, 26)

    @Test
    fun testCreatorWithNoSavedRoleIsTreatedAsOwner() {
        // Group creator with no explicit role set in member object or single member group
        val creatorMember = GroupMember(uid = "u1", displayName = "Livin Varghese (You)", role = "")
        val role = resolveUserRole(
            members = listOf(creatorMember),
            currentUserId = "u1",
            isAdmin = false,
            creatorId = "u1"
        )
        assertEquals("owner", role)
        assertTrue(canUserAssignTasks(role))
    }

    @Test
    fun testOwnerWithEmptyListSeesAssignTask() {
        val soleOwner = GroupMember(uid = "me", displayName = "Livin Varghese (You)", isYou = true)
        val role = resolveUserRole(
            members = listOf(soleOwner),
            currentUserId = "me",
            isAdmin = true
        )
        assertEquals("owner", role)
        assertTrue(canUserAssignTasks(role))
    }

    @Test
    fun testSoleOwnerCanAssignToThemselves() {
        val soleOwner = GroupMember(uid = "u_me", displayName = "Livin Varghese (You)", isYou = true)
        val tasks = createTasksForAssignees(
            title = "Personal group goal",
            groupId = "g1",
            assignees = listOf(soleOwner),
            assignerName = "Livin Varghese",
            assignerUid = "u_me",
            dueDate = LocalDate.of(2026, 11, 1),
            dueTime = "5:00 PM",
            note = "Self assigned"
        )

        assertEquals(1, tasks.size)
        assertEquals("u_me", tasks[0].assignedToUid)
        assertEquals("Livin Varghese (You)", tasks[0].assignedTo)
        assertTrue(isTaskVisibleInAssignedToMe(tasks[0], "u_me"))
    }

    @Test
    fun testMemberDoesNotSeeAssignTaskAction() {
        val regularMember = GroupMember(uid = "u2", displayName = "Rahul Verma", role = "member")
        val ownerMember = GroupMember(uid = "u1", displayName = "Anita Sharma", role = "owner")

        val role = resolveUserRole(
            members = listOf(ownerMember, regularMember),
            currentUserId = "u2",
            isAdmin = false,
            creatorId = "u1"
        )

        assertEquals("member", role)
        assertFalse(canUserAssignTasks(role))
    }

    @Test
    fun testTaskWithoutDueDateIsNeverOverdue() {
        val taskNoDueDate = GroupTaskEntity(
            id = "1",
            groupId = "g1",
            title = "Task with no due date",
            assignedTo = "Anita Sharma",
            assignedToUid = "u1",
            assignedBy = "Livin Varghese",
            assignedByUid = "u0",
            dueDate = null,
            isCompleted = false
        )

        val status = calculateTaskStatusCategory(taskNoDueDate, today)
        assertEquals(TaskStatusCategory.PENDING, status)
    }

    @Test
    fun testOverdueAndPendingAndDoneStatus() {
        val overdueTask = GroupTaskEntity(
            id = "1",
            groupId = "g1",
            title = "Overdue task",
            assignedTo = "Anita",
            assignedToUid = "u1",
            assignedBy = "Livin",
            assignedByUid = "u0",
            dueDate = LocalDate.of(2026, 10, 20),
            isCompleted = false
        )

        val pendingTask = GroupTaskEntity(
            id = "2",
            groupId = "g1",
            title = "Pending task",
            assignedTo = "Anita",
            assignedToUid = "u1",
            assignedBy = "Livin",
            assignedByUid = "u0",
            dueDate = LocalDate.of(2026, 10, 28),
            isCompleted = false
        )

        val doneTask = GroupTaskEntity(
            id = "3",
            groupId = "g1",
            title = "Done task",
            assignedTo = "Anita",
            assignedToUid = "u1",
            assignedBy = "Livin",
            assignedByUid = "u0",
            dueDate = LocalDate.of(2026, 10, 15),
            isCompleted = true
        )

        assertEquals(TaskStatusCategory.OVERDUE, calculateTaskStatusCategory(overdueTask, today))
        assertEquals(TaskStatusCategory.PENDING, calculateTaskStatusCategory(pendingTask, today))
        assertEquals(TaskStatusCategory.DONE, calculateTaskStatusCategory(doneTask, today))
    }

    @Test
    fun testTaskOrderingOverdueThenPendingThenDone() {
        val overdue1 = GroupTaskEntity(
            id = "o1",
            groupId = "g1",
            title = "Older overdue",
            assignedTo = "Anita",
            assignedToUid = "u1",
            assignedBy = "Livin",
            assignedByUid = "u0",
            dueDate = LocalDate.of(2026, 10, 10),
            isCompleted = false
        )

        val overdue2 = GroupTaskEntity(
            id = "o2",
            groupId = "g1",
            title = "Recent overdue",
            assignedTo = "Anita",
            assignedToUid = "u1",
            assignedBy = "Livin",
            assignedByUid = "u0",
            dueDate = LocalDate.of(2026, 10, 24),
            isCompleted = false
        )

        val pending = GroupTaskEntity(
            id = "p1",
            groupId = "g1",
            title = "Pending",
            assignedTo = "Anita",
            assignedToUid = "u1",
            assignedBy = "Livin",
            assignedByUid = "u0",
            dueDate = LocalDate.of(2026, 10, 30),
            isCompleted = false
        )

        val done = GroupTaskEntity(
            id = "d1",
            groupId = "g1",
            title = "Done",
            assignedTo = "Anita",
            assignedToUid = "u1",
            assignedBy = "Livin",
            assignedByUid = "u0",
            dueDate = LocalDate.of(2026, 10, 5),
            isCompleted = true
        )

        val unsorted = listOf(pending, done, overdue2, overdue1)
        val sorted = sortGroupTasks(unsorted, today)

        assertEquals(listOf("o1", "o2", "p1", "d1"), sorted.map { it.id })
    }

    @Test
    fun testWhoCanAssign() {
        assertTrue(canUserAssignTasks("owner"))
        assertTrue(canUserAssignTasks("admin"))
        assertTrue(canUserAssignTasks("OWNER"))
        assertTrue(canUserAssignTasks("ADMIN"))

        assertFalse(canUserAssignTasks("member"))
        assertFalse(canUserAssignTasks("MEMBER"))
        assertFalse(canUserAssignTasks(""))
    }

    @Test
    fun testOneTaskPerChosenPerson() {
        val assignees = listOf(
            GroupMember("u1", "Anita Sharma"),
            GroupMember("u2", "Rahul Verma"),
            GroupMember("u3", "Livin Varghese")
        )

        val tasks = createTasksForAssignees(
            title = "Prepare quarterly deck",
            groupId = "g100",
            assignees = assignees,
            assignerName = "Livin Varghese",
            assignerUid = "u3",
            dueDate = LocalDate.of(2026, 10, 30),
            dueTime = "5:00 PM",
            note = "Include financial charts"
        )

        assertEquals(3, tasks.size)
        assertEquals("u1", tasks[0].assignedToUid)
        assertEquals("Anita Sharma", tasks[0].assignedTo)
        assertEquals("u2", tasks[1].assignedToUid)
        assertEquals("Rahul Verma", tasks[1].assignedTo)
        assertEquals("u3", tasks[2].assignedToUid)
        assertEquals("Livin Varghese", tasks[2].assignedTo)

        for (t in tasks) {
            assertEquals("Prepare quarterly deck", t.title)
            assertEquals("g100", t.groupId)
            assertEquals("Livin Varghese", t.assignedBy)
            assertEquals("Include financial charts", t.note)
            assertFalse(t.isCompleted)
        }
    }

    @Test
    fun testVisibilityInAssignedToMe() {
        val taskForMe = GroupTaskEntity(
            id = "t1",
            groupId = "g1",
            title = "Task for me",
            assignedTo = "Livin Varghese",
            assignedToUid = "u3",
            assignedBy = "Anita",
            assignedByUid = "u1"
        )

        val taskForOther = GroupTaskEntity(
            id = "t2",
            groupId = "g1",
            title = "Task for someone else",
            assignedTo = "Rahul Verma",
            assignedToUid = "u2",
            assignedBy = "Anita",
            assignedByUid = "u1"
        )

        assertTrue(isTaskVisibleInAssignedToMe(taskForMe, "u3"))
        assertFalse(isTaskVisibleInAssignedToMe(taskForOther, "u3"))
    }
}
