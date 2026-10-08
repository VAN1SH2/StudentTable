package ru.sfu.student.data

import org.junit.Assert.*
import org.junit.Test
import ru.sfu.student.core.*
import java.time.*

class TaskLessonBindingsTest {
    private val zone = ZoneOffset.ofHours(7)
    private val date = LocalDate.of(2026, 10, 8)
    private val reference = WeekReference("2026-09-28", 1)
    private val lesson = Lesson(12, 2, 4, "14:10", "15:45", "Предмет", "", "Практика", "", "", "", 214)
    private val task = StudentTask(7, "Задание", lesson.subject, dueAt = date.atTime(14, 10).atZone(zone).toInstant().toEpochMilli(),
        groupId = 11, subjectId = 214, lessonId = 12, lessonDate = date.toString(), reminderMinutes = listOf(1440, 60))

    @Test fun capturedLinkSurvivesRemoteIdsAndManualDeadlineChanges() {
        val saved = TaskLessonBindings.capture(task.copy(dueAt = 100), listOf(lesson))
        val updated = lesson.copy(id = 900, subjectId = 600, room = "410")
        val repaired = TaskLessonBindings.reconcile(saved, listOf(updated), reference, zone)
        assertEquals(900L, repaired.lessonId)
        assertEquals(600L, repaired.subjectId)
        assertEquals(task.lessonDate, repaired.lessonDate)
        assertEquals(100L, repaired.dueAt)
        assertEquals(task.reminderMinutes, repaired.reminderMinutes)
    }
    @Test fun repairsAnAlreadyOrphanedLegacyLinkOnlyAtAnUnambiguousDatedStart() {
        val updated = lesson.copy(id = 900)
        val fixed = TaskLessonBindings.reconcile(task, listOf(updated, updated.copy(id = 901, type = "Лекция", startTime = "12:00")), reference, zone)
        assertEquals(900L, fixed.lessonId)
        assertNotNull(fixed.lessonBindingKey)
        assertEquals(task, TaskLessonBindings.reconcile(task.copy(dueAt = task.dueAt - 60_000), listOf(updated), reference, zone).copy(dueAt = task.dueAt))
        assertEquals(task, TaskLessonBindings.reconcile(task, listOf(updated, updated.copy(id = 901, subgroup = 2)), reference, zone))
    }
    @Test fun anUnboundSubjectTaskIsNeverConvertedIntoAClassTask() {
        val general = task.copy(lessonId = null, lessonDate = null)
        assertEquals(general, TaskLessonBindings.reconcile(general, listOf(lesson), reference, zone))
    }
    @Test fun missingClassRetainsItsIdentityAndReturnsOnALaterRefresh() {
        val captured = TaskLessonBindings.capture(task, listOf(lesson))
        assertEquals(captured, TaskLessonBindings.reconcile(captured, emptyList(), reference, zone))
        val restored = TaskLessonBindings.reconcile(captured, listOf(lesson.copy(id = 900)), reference, zone)
        assertEquals(900L, restored.lessonId)
    }
}
