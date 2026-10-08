package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test

class LessonSubgroupsTest {
    private fun lesson(number: Int? = null, description: String = "") = Lesson(
        1, 1, 1, "11:45", "13:15", "Предмет", "", "", "", "", description, subgroup = number)

    @Test fun chosenSubgroupIncludesCommonLessonsButHidesOtherSubgroups() {
        assertTrue(LessonSubgroups.includes(lesson(), 1))
        assertTrue(LessonSubgroups.includes(lesson(1), 1))
        assertFalse(LessonSubgroups.includes(lesson(2), 1))
        assertTrue(LessonSubgroups.includes(lesson(2), null))
    }

    @Test fun discoversSubgroupsFromBothWeeksWithoutAssumingOnlyTwoExist() {
        val lessons = listOf(lesson(3), lesson(), lesson(2), lesson(1), lesson(2).copy(week = 2))
        assertEquals(listOf(1, 2, 3), LessonSubgroups.available(lessons))
    }

    @Test fun cachedLegacyMarkersWorkWithoutDownloadingScheduleAgain() {
        val cached = lesson(description = "Подгруппа 2")
        assertEquals(2, LessonSubgroups.number(cached))
        assertFalse(LessonSubgroups.includes(cached, 1))
        assertTrue(LessonSubgroups.includes(cached, 2))
        assertEquals(1, LessonSubgroups.number(lesson(1, "Подгруппа 2")))
        assertNull(LessonSubgroups.number(lesson(description = "Подгруппа 0")))
    }
}
