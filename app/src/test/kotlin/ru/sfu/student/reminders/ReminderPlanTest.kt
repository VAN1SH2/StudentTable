package ru.sfu.student.reminders

import org.junit.Assert.*
import org.junit.Test
import ru.sfu.student.core.Lesson
import ru.sfu.student.data.*
import ru.sfu.student.data.database.TaskReminderConverters

class ReminderPlanTest {
    private fun lesson(id: Long, subgroup: Int? = null) = Lesson(id, 1, 4, "14:10", "15:45", "Предмет", "", "", "", "", "", subgroup = subgroup)
    @Test fun activeGroupDoesNotChangeLessonNotificationsOfThePrimaryGroup() {
        val primary = SavedGroup(-1, "Основная", universityCode = "irnitu", weekAnchorMonday = "2026-09-28",
            weekConfirmed = true, selectedSubgroup = 2)
        val other = SavedGroup(2, "Активная", weekAnchorMonday = "2026-09-28", weekConfirmed = true)
        val classes = listOf(StoredLesson(-1, 1, lesson(1)), StoredLesson(-1, 2, lesson(2, 1)),
            StoredLesson(-1, 3, lesson(3, 2)), StoredLesson(2, 4, lesson(4)))
        val settings = Settings(activeGroupId = 2, primaryGroupId = -1, lessonNotifications = true)
        assertEquals(setOf(1L, 3L), LessonReminderPlan.select(settings, listOf(primary, other), classes).map { it.id }.toSet())
        assertEquals(setOf(1L, 3L), LessonReminderPlan.select(settings.copy(activeGroupId = -1), listOf(primary, other), classes).map { it.id }.toSet())
        assertEquals(listOf(4L), LessonReminderPlan.select(settings.copy(primaryGroupId = 2), listOf(primary, other), classes).map { it.id })
        assertTrue(LessonReminderPlan.select(settings.copy(lessonNotifications = false), listOf(primary, other), classes).isEmpty())
        assertTrue(LessonReminderPlan.select(settings, listOf(other), classes).isEmpty())
    }
    @Test fun convertsMultipleRemindersAndKeepsLegacyOrDisabledChoices() {
        val converter = TaskReminderConverters()
        assertNull(converter.fromStorage(null))
        assertEquals(emptyList<Int>(), converter.fromStorage(converter.toStorage(emptyList())))
        assertEquals(listOf(2880, 60, 0), converter.fromStorage(converter.toStorage(listOf(0, 60, 2880))))
        val task = StudentTask(title = "Задание", dueAt = 100)
        assertEquals(listOf(1440), task.reminderLeads)
        assertEquals(listOf(60), task.copy(remindMinutes = 60).reminderLeads)
        assertTrue(task.copy(reminderMinutes = emptyList()).reminderLeads.isEmpty())
        assertEquals(listOf(2880, 60), task.copy(reminderMinutes = listOf(60, 2880)).reminderLeads)
    }
}
