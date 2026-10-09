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

    @Test fun openingAPracticeCardSelectsTheNearestSubjectClassEvenIfItIsALecture() {
        val group = SavedGroup(11, "СФУ", weekAnchorMonday = "2026-09-28", weekAnchorWeek = 1, weekConfirmed = true)
        val practice = lesson(2).copy(week = 1, startTime = "14:10", type = "пр. занятие")
        val lecture = practice.copy(id = 1, startTime = "12:00", type = "лекция")
        val state = StudentState(groups = listOf(group), lessons = listOf(StoredLesson(11, 1, lecture), StoredLesson(11, 2, practice)))
        val chosen = state.initialTaskOccurrence(11, practice, now, zone)!!
        assertEquals(1L, chosen.lesson.id)
        assertEquals(LocalDate.of(2026, 10, 1), chosen.date)
        assertEquals(LocalTime.of(12, 0), chosen.startsAt.atZone(zone).toLocalTime())
    }

    @Test fun selectsNextWeeksClassInsteadOfRepeatingTheClickedIdTwoWeeksLater() {
        val group = SavedGroup(11, "СФУ", weekAnchorMonday = "2026-09-28", weekAnchorWeek = 1, weekConfirmed = true)
        val current = lesson(1).copy(week = 1)
        val nextWeek = current.copy(id = 2, week = 2)
        val unrelated = current.copy(id = 3, subject = "Другой предмет", startTime = "16:00")
        val state = StudentState(groups = listOf(group), lessons = listOf(StoredLesson(11, 1, current),
            StoredLesson(11, 2, nextWeek), StoredLesson(11, 3, unrelated)))
        val afterCurrent = LocalDate.of(2026, 10, 1).atTime(15, 30).atZone(zone).toInstant()
        val chosen = state.initialTaskOccurrence(11, current, afterCurrent, zone)!!
        assertEquals(2L, chosen.lesson.id)
        assertEquals(LocalDate.of(2026, 10, 8), chosen.date)
        assertEquals(LocalTime.of(13, 45), chosen.startsAt.atZone(zone).toLocalTime())
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

    @Test fun calendarCreationKeepsTheClickedPracticeAndSelectedDateInsteadOfTheNearestLecture() {
        val group = SavedGroup(11, "СФУ", weekAnchorMonday = "2026-09-28", weekAnchorWeek = 1, weekConfirmed = true)
        val practice = lesson(2).copy(week = 1, startTime = "14:10", type = "пр. занятие")
        val lecture = practice.copy(id = 1, startTime = "12:00", type = "лекция")
        val state = StudentState(groups = listOf(group), lessons = listOf(StoredLesson(11, 1, lecture), StoredLesson(11, 2, practice)))
        val selectedDate = LocalDate.of(2026, 10, 15)
        val chosen = state.initialTaskOccurrence(11, practice, now, zone, selectedDate)!!
        assertEquals(2L, chosen.lesson.id)
        assertEquals(selectedDate, chosen.date)
        assertEquals(selectedDate.atTime(14, 10).atZone(zone).toInstant(), chosen.startsAt)
        assertEquals(1L, state.initialTaskOccurrence(11, practice, now, zone)!!.lesson.id)
        assertEquals(LocalDate.of(2026, 10, 1), state.initialTaskOccurrence(11, practice, now, zone)!!.date)
    }

    @Test fun calendarCreationAcceptsPastAndDistantDatesAndUsesDeviceLessonTime() {
        val group = SavedGroup(11, "СФУ", weekAnchorMonday = "2026-09-28", weekAnchorWeek = 1, weekConfirmed = true)
        val practice = lesson(2).copy(week = 1)
        val state = StudentState(groups = listOf(group), lessons = listOf(StoredLesson(11, 2, practice)))
        listOf(LocalDate.of(2026, 9, 17), LocalDate.of(2027, 1, 21)).forEach { date ->
            listOf(ZoneOffset.ofHours(-5), zone).forEach { deviceZone ->
                val chosen = state.initialTaskOccurrence(11, practice, now, deviceZone, date)!!
                assertEquals(date, chosen.date)
                assertEquals(2L, chosen.lesson.id)
                assertEquals(date.atTime(13, 45).atZone(deviceZone).toInstant(), chosen.startsAt)
            }
        }
    }

    @Test fun deadlinePickerIncludesTheSelectedCalendarClassOutsideTheUpcomingMonth() {
        val group = SavedGroup(11, "СФУ", weekAnchorMonday = "2026-09-28", weekAnchorWeek = 1, weekConfirmed = true)
        val practice = lesson(2).copy(week = 1)
        val state = StudentState(groups = listOf(group), lessons = listOf(StoredLesson(11, 2, practice)))
        val upcoming = state.upcomingLessons(11, null, "Предмет", now, zone)
        assertFalse(upcoming.isEmpty())
        listOf(LocalDate.of(2026, 9, 17), LocalDate.of(2027, 1, 21)).forEach { date ->
            val selected = state.initialTaskOccurrence(11, practice, now, zone, date)!!
            assertFalse(upcoming.any { it.key == selected.key })
            val choices = lessonDeadlineChoices(upcoming, selected)
            assertEquals(upcoming.size + 1, choices.size)
            assertEquals(selected, choices.single { it.key == selected.key })
            assertTrue(choices.containsAll(upcoming))
            assertEquals(choices.map { it.startsAt }.sorted(), choices.map { it.startsAt })
        }
    }

    @Test fun deadlinePickerDoesNotDuplicateTheSelectedClassAndKeepsItWhenNoSuggestionsExist() {
        val practice = lesson(2)
        val date = LocalDate.of(2027, 1, 21)
        val selected = LessonOccurrence(practice, date, date.atTime(13, 45).atZone(zone).toInstant())
        assertEquals(listOf(selected), lessonDeadlineChoices(listOf(selected), selected))
        assertEquals(listOf(selected), lessonDeadlineChoices(emptyList(), selected))
        assertEquals(listOf(selected), lessonDeadlineChoices(listOf(selected), null))
        assertTrue(lessonDeadlineChoices(emptyList(), null).isEmpty())
    }
}
