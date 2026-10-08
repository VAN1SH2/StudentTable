package ru.sfu.student.ui

import org.junit.Assert.*
import org.junit.Test
import ru.sfu.student.core.*
import ru.sfu.student.data.*
import java.time.*

class TaskSchedulingTest {
    private val zone = ZoneOffset.ofHours(7)
    private val now = LocalDate.of(2026, 10, 1).atTime(9, 0).atZone(zone).toInstant()
    private fun lesson(id: Long, subgroup: Int? = null) =
        Lesson(id, 2, 4, "13:45", "15:15", "Предмет", "", "Лабораторная работа", "", "", "", subgroup = subgroup)

    @Test fun selectedGroupUsesItsOwnWeekCycleAndSubgroupEvenWhenAnotherGroupIsActive() {
        val sfu = SavedGroup(11, "СФУ", weekAnchorMonday = "2026-09-28", weekAnchorWeek = 1, weekConfirmed = true)
        val irnitu = SavedGroup(-11, "ИРНИТУ", universityCode = "irnitu", weekAnchorMonday = "2026-09-28",
            weekAnchorWeek = 2, weekConfirmed = true, selectedSubgroup = 1)
        val classes = listOf(StoredLesson(11, 1, lesson(1).copy(week = 1)),
            StoredLesson(-11, 2, lesson(2)), StoredLesson(-11, 3, lesson(3, 1)), StoredLesson(-11, 4, lesson(4, 2)))
        val state = StudentState(Settings(activeGroupId = 11, primaryGroupId = 11), listOf(sfu, irnitu), classes)
        val choices = state.upcomingLessons(-11, null, "Предмет", now, zone)
        assertEquals(setOf(2L, 3L), choices.map { it.lesson.id }.toSet())
        assertEquals(LocalDate.of(2026, 10, 1), choices.first().date)
        assertEquals(setOf(1L), state.upcomingLessons(11, null, "Предмет", now, zone).map { it.lesson.id }.toSet())
        assertEquals(1, sfu.weekAnchorWeek)
        assertEquals(1, irnitu.selectedSubgroup)
    }

    @Test fun unassignedOrRemovedGroupsOfferNoLessonDeadline() {
        val state = StudentState()
        assertTrue(state.upcomingLessons(null, null, "Предмет", now, zone).isEmpty())
        assertTrue(state.upcomingLessons(999, null, "Предмет", now, zone).isEmpty())
    }

    @Test fun openingAPracticeCardDoesNotSelectAnEarlierLecture() {
        val group = SavedGroup(11, "СФУ", weekAnchorMonday = "2026-09-28", weekAnchorWeek = 1, weekConfirmed = true)
        val practice = lesson(2).copy(week = 1, startTime = "14:10", type = "пр. занятие")
        val lecture = practice.copy(id = 1, startTime = "12:00", type = "лекция")
        val state = StudentState(groups = listOf(group), lessons = listOf(StoredLesson(11, 1, lecture), StoredLesson(11, 2, practice)))
        val chosen = state.nextOccurrenceForLesson(11, practice, now, zone)!!
        assertEquals(2L, chosen.lesson.id)
        assertEquals(LocalDate.of(2026, 10, 1), chosen.date)
        assertEquals(LocalTime.of(14, 10), chosen.startsAt.atZone(zone).toLocalTime())
    }

    @Test fun savedOccurrenceCanBeRestoredAfterItsStartWithoutUsingTheDeadline() {
        val practice = lesson(2)
        val state = StudentState(lessons = listOf(StoredLesson(11, 2, practice)))
        val chosen = state.lessonOccurrence(11, 2, "2026-09-17", zone)!!
        assertEquals(2L, chosen.lesson.id)
        assertEquals(LocalDate.of(2026, 9, 17), chosen.date)
        assertEquals(LocalTime.of(13, 45), chosen.startsAt.atZone(zone).toLocalTime())
        assertNull(state.lessonOccurrence(12, 2, "2026-09-17", zone))
        assertNull(state.lessonOccurrence(11, 2, "invalid", zone))
        assertNull(state.lessonOccurrence(11, null, null, zone))
    }
}
