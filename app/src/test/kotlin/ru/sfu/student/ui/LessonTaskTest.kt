package ru.sfu.student.ui

import org.junit.Assert.*
import org.junit.Test
import ru.sfu.student.core.Lesson
import ru.sfu.student.core.LessonBinding
import ru.sfu.student.data.StudentTask
import java.time.LocalDate

class LessonTaskTest {
    private val lesson = Lesson(12, 1, 3, "14:10", "15:45", "Операционные системы", "", "", "", "", "", 214)
    @Test fun showsAllPendingSubjectTasksOfOnlySelectedGroup() {
        val matching = StudentTask(1, "Лабораторная", "Операционные системы", dueAt = 100, groupId = 108257, subjectId = 214)
        val overdue = matching.copy(id = 2, title = "Отчёт", dueAt = 50)
        val candidates = listOf(matching, overdue, matching.copy(id = 3, done = true), matching.copy(id = 4, groupId = 108256), matching.copy(id = 5, subject = "", subjectId = null))
        assertEquals(listOf(2L, 1L), tasksForLesson(lesson, 108257, candidates).map { it.id })
    }
    @Test fun keepsLegacyNameLinksWhenSubjectIdIsAbsent() {
        val legacy = StudentTask(9, "Старое задание", "Операционные системы", dueAt = 100, groupId = 108257)
        assertEquals(listOf(legacy), tasksForLesson(lesson, 108257, listOf(legacy)))
        assertTrue(tasksForLesson(lesson, 108257, listOf(legacy.copy(subjectId = 999))).isEmpty())
    }

    @Test fun practiceTaskIsNotShownOnLectureOrAnotherPracticeOfTheSameSubject() {
        val date = LocalDate.of(2026, 10, 1)
        val practice = lesson.copy(type = "пр. занятие")
        val lecture = practice.copy(id = 11, type = "лекция", startTime = "12:00")
        val task = StudentTask(1, "Прекоммит", practice.subject, dueAt = 100, groupId = 108257,
            subjectId = 214, lessonId = practice.id, lessonDate = date.toString())
        assertEquals(listOf(task), tasksForLesson(practice, 108257, listOf(task), date))
        assertTrue(tasksForLesson(lecture, 108257, listOf(task), date).isEmpty())
        assertTrue(tasksForLesson(practice.copy(id = 13), 108257, listOf(task), date).isEmpty())
        assertTrue(tasksForLesson(practice, 108256, listOf(task), date).isEmpty())
        assertTrue(tasksForLesson(practice, 108257, listOf(task.copy(done = true)), date).isEmpty())
    }

    @Test fun datedTaskIsNotRepeatedWhenTheSameLessonReturnsTwoWeeksLater() {
        val date = LocalDate.of(2026, 10, 1)
        val task = StudentTask(1, "Практика", lesson.subject, dueAt = 100, groupId = 108257,
            subjectId = 214, lessonId = lesson.id, lessonDate = date.toString())
        assertEquals(listOf(task), tasksForLesson(lesson, 108257, listOf(task), date))
        assertTrue(tasksForLesson(lesson, 108257, listOf(task), date.plusWeeks(2)).isEmpty())
        // The two-week overview has no calendar dates, but still distinguishes lecture and practice IDs.
        assertEquals(listOf(task), tasksForLesson(lesson, 108257, listOf(task)))
        assertTrue(tasksForLesson(lesson.copy(id = 11), 108257, listOf(task)).isEmpty())
    }

    @Test fun changingTheDeadlineDoesNotMoveATaskToAnotherClass() {
        val date = LocalDate.of(2026, 10, 1)
        val task = StudentTask(1, "Практика", lesson.subject, dueAt = 100, groupId = 108257,
            subjectId = 214, lessonId = lesson.id, lessonDate = date.toString()).copy(dueAt = 50)
        assertEquals(listOf(task), tasksForLesson(lesson, 108257, listOf(task), date))
        assertTrue(tasksForLesson(lesson.copy(id = 11, startTime = "12:00"), 108257, listOf(task), date).isEmpty())
    }

    @Test fun generalSubjectTasksRemainVisibleOnBothKindsOfClass() {
        val task = StudentTask(9, "Общее задание", lesson.subject, dueAt = 100, groupId = 108257, subjectId = 214)
        val date = LocalDate.of(2026, 10, 1)
        assertEquals(listOf(task), tasksForLesson(lesson, 108257, listOf(task), date))
        assertEquals(listOf(task), tasksForLesson(lesson.copy(id = 11, type = "лекция"), 108257, listOf(task), date))
    }

    @Test fun reusedRemoteIdDoesNotMoveADatedTaskToADifferentTypeOfClass() {
        val practice = lesson.copy(type = "Практика")
        val date = LocalDate.of(2026, 10, 8)
        val task = StudentTask(9, "Практика", practice.subject, dueAt = 100, groupId = 108257, subjectId = 214,
            lessonId = practice.id, lessonDate = date.toString(), lessonBindingKey = LessonBinding.key(practice))
        assertEquals(listOf(task), tasksForLesson(practice, 108257, listOf(task), date))
        assertTrue(tasksForLesson(practice.copy(type = "Лекция"), 108257, listOf(task), date).isEmpty())
    }
}
