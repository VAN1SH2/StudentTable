package ru.sfu.student.reminders

import org.junit.Assert.*
import org.junit.Test
import ru.sfu.student.core.Lesson
import ru.sfu.student.core.CustomLessonDetails
import java.time.*
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
    @Test fun localClassRemindersUsePrimaryGroupSubgroupAndExcludeDeletedOccurrences() {
        val primary = SavedGroup(-1, "Основная", universityCode = "irnitu", weekAnchorMonday = "2026-10-05", weekConfirmed = true, selectedSubgroup = 2)
        val settings = Settings(activeGroupId = 2, primaryGroupId = -1, lessonNotifications = true)
        val data = CustomLessonDetails("Своя", "10:00", "11:30", "2026-10-05", 3, subgroup = 2)
        val own = listOf(StoredCustomLesson(1, -1, data), StoredCustomLesson(2, -1, data.copy(subgroup = 1)),
            StoredCustomLesson(3, 2, data), StoredCustomLesson(4, -1, data.copy(repeat = 0, startDate = "2028-04-03", subgroup = null)))
        val now = Instant.parse("2026-10-05T09:00:00Z")
        val plan = LessonReminderPlan.upcoming(settings, listOf(primary), emptyList(), own, listOf(CustomLessonExclusion(1, "2026-10-05")), now, ZoneOffset.UTC)
        assertEquals(listOf(-1L, -4L), plan.map { it.lesson.id })
        assertEquals(listOf(Instant.parse("2026-10-12T10:00:00Z"), Instant.parse("2028-04-03T10:00:00Z")), plan.map { it.startsAt })
        assertTrue(LessonReminderPlan.upcoming(settings, listOf(primary), emptyList(), emptyList(), emptyList(), now, ZoneOffset.UTC).isEmpty())
        assertTrue(LessonReminderPlan.upcoming(settings.copy(lessonNotifications = false), listOf(primary), emptyList(), own, emptyList(), now, ZoneOffset.UTC).isEmpty())
        assertTrue(LessonReminderPlan.upcoming(settings, listOf(primary.copy(weekConfirmed = false)), emptyList(), own, emptyList(), now, ZoneOffset.UTC).isEmpty())
    }
    @Test fun endedAndPastOnceClassesDoNotGetReminderJobs() {
        val group = SavedGroup(1, "Группа", weekAnchorMonday = "2026-10-05", weekConfirmed = true)
        val settings = Settings(primaryGroupId = 1, lessonNotifications = true)
        val once = CustomLessonDetails("Своя", "10:00", "11:30", "2026-10-05")
        val own = listOf(StoredCustomLesson(1, 1, once), StoredCustomLesson(2, 1, once.copy(repeat = 3, untilDate = "2026-10-05")))
        assertTrue(LessonReminderPlan.upcoming(settings, listOf(group), emptyList(), own, emptyList(), Instant.parse("2026-10-05T10:01:00Z"), ZoneOffset.UTC).isEmpty())
    }
}
