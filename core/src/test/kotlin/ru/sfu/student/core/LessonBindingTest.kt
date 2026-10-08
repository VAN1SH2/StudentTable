package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test

class LessonBindingTest {
    private val practice = Lesson(12, 2, 4, "14:10", "15:45", "Предмет", "Иванов", "Практика", "17", "301", "", 214, 1)
    @Test fun remoteIdsRoomTeacherAndWhitespaceDoNotChangeTheBinding() {
        val updated = practice.copy(id = 900, subjectId = 600, room = "410", teacher = "Петров", subject = "  ПРЕДМЕТ  ", type = " практика ")
        assertEquals(LessonBinding.key(practice), LessonBinding.key(updated))
        assertEquals(updated, LessonBinding.resolve(12, LessonBinding.key(practice), listOf(updated)))
    }
    @Test fun distinguishesLectureTimeParityWeekdayAndSubgroup() {
        listOf(practice.copy(type = "Лекция"), practice.copy(startTime = "12:00"), practice.copy(week = 1),
            practice.copy(dayOfWeek = 3), practice.copy(subgroup = 2)).forEach {
            assertNotEquals(LessonBinding.key(practice), LessonBinding.key(it))
        }
    }
    @Test fun neverChoosesAnAmbiguousReplacementOrReusedRemoteId() {
        val first = practice.copy(id = 900)
        val second = practice.copy(id = 901)
        assertNull(LessonBinding.resolve(12, LessonBinding.key(practice), listOf(first, second)))
        assertEquals(first, LessonBinding.resolve(900, LessonBinding.key(practice), listOf(first, second)))
        assertNull(LessonBinding.resolve(12, LessonBinding.key(practice), listOf(practice.copy(type = "Лекция"))))
    }
}
