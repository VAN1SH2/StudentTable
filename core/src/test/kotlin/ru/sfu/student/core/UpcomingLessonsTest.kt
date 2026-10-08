package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class UpcomingLessonsTest {
    private val anchor = LocalDate.of(2026, 9, 28)
    private val zone = ZoneOffset.ofHours(7)
    private fun lesson(id: Long, week: Int, start: String = "13:45", subject: String = "Предмет", subjectId: Long? = 214) =
        Lesson(id, week, 4, start, "17:00", subject, "", "Практическое занятие", "", "", "", subjectId)
    private fun at(hour: Int, minute: Int = 0) = LocalDate.of(2026, 10, 1).atTime(hour, minute).atZone(zone).toInstant()

    @Test fun expandsBothWeeksAndShowsOnlyTheChosenSubjectInDateOrder() {
        val classes = listOf(lesson(2, 2, "15:30"), lesson(1, 1), lesson(3, 1, subjectId = 999),
            lesson(4, 1, subject = "Другой предмет", subjectId = 1000))
        val choices = UpcomingLessons.forSubject(classes, 214, "Предмет", at(9), anchor, 1, zone)
        assertEquals(listOf("2026-10-01", "2026-10-08", "2026-10-15", "2026-10-22", "2026-10-29"), choices.map { it.date.toString() })
        assertEquals(listOf(1L, 2L, 1L, 2L, 1L), choices.map { it.lesson.id })
        assertEquals(choices.size, choices.map { it.key }.distinct().size)
        assertEquals(LocalDateTime.of(2026, 10, 1, 13, 45), choices.first().startsAt.atZone(zone).toLocalDateTime())
    }

    @Test fun startedClassesAreExcludedAndLegacyNamesStillMatch() {
        val choices = UpcomingLessons.forSubject(listOf(lesson(1, 1, subject = " ПРЕДМЕТ ", subjectId = null)),
            214, "Предмет", at(13, 45), anchor, 1, zone)
        assertEquals(LocalDate.of(2026, 10, 15), choices.first().date)
        assertTrue(choices.all { it.startsAt.isAfter(at(13, 45)) })
        assertTrue(UpcomingLessons.forSubject(listOf(lesson(1, 1)), null, "", at(9), anchor, 1, zone).isEmpty())
    }

    @Test fun calendarMonthClampsAtFebruaryAndExcludesTimesBeyondTheWindow() {
        val now = LocalDate.of(2027, 1, 31).atTime(17, 0).atZone(zone).toInstant()
        val classes = (1..2).map { week -> lesson(week.toLong(), week, "18:00").copy(dayOfWeek = 7, endTime = "19:30") }
        val choices = UpcomingLessons.forSubject(classes, 214, "Предмет", now, LocalDate.of(2027, 1, 25), 1, zone)
        assertEquals(listOf("2027-01-31", "2027-02-07", "2027-02-14", "2027-02-21"), choices.map { it.date.toString() })
        assertTrue(choices.all { !it.startsAt.isAfter(now.atZone(zone).plusMonths(1).toInstant()) })
    }

    @Test fun deadlinesKeepLessonWallClockTimeInDifferentDeviceZones() {
        listOf(-5, 0, 3, 7).forEach { offset ->
            val localZone = ZoneOffset.ofHours(offset)
            val now = LocalDate.of(2026, 10, 1).atTime(9, 0).atZone(localZone).toInstant()
            val first = UpcomingLessons.forSubject(listOf(lesson(1, 1)), 214, "Предмет", now, anchor, 1, localZone).first()
            assertEquals(LocalTime.of(13, 45), first.startsAt.atZone(localZone).toLocalTime())
        }
    }
}
