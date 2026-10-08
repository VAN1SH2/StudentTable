package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.TimeZone

class ScheduleCycleTest {
    private val anchor = LocalDate.of(2026, 9, 28)
    @Test fun alternatesAcrossYearBoundaryAndBackwards() {
        assertEquals(1, ScheduleCycle.week(anchor.plusDays(6), anchor, 1))
        assertEquals(2, ScheduleCycle.week(anchor.plusDays(7), anchor, 1))
        assertEquals(2, ScheduleCycle.week(anchor.minusDays(1), anchor, 1))
        assertEquals(1, ScheduleCycle.week(LocalDate.of(2027, 1, 4), anchor, 1))
    }
    @Test fun nextLessonRespectsLeadAndLocalTime() {
        val zone = ZoneOffset.ofHours(3)
        val lesson = Lesson(1, 1, 1, "15:55", "17:30", "Test", "", "", "", "", "")
        val before = ZonedDateTime.of(2026, 9, 28, 15, 30, 0, 0, zone).toInstant()
        assertEquals("2026-09-28T12:55:00Z", ScheduleCycle.nextStart(lesson, before, anchor, 1, 15, zone).toString())
        val after = before.plusSeconds(20 * 60)
        assertEquals("2026-10-12T12:55:00Z", ScheduleCycle.nextStart(lesson, after, anchor, 1, 15, zone).toString())
    }
    @Test fun deviceZoneIsReadAgainAfterTimezoneChanges() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
            assertEquals(ZoneOffset.ofHours(3), ScheduleCycle.zone.rules.getOffset(Instant.EPOCH))
            TimeZone.setDefault(TimeZone.getTimeZone("GMT-05:00"))
            assertEquals(ZoneOffset.ofHours(-5), ScheduleCycle.zone.rules.getOffset(Instant.EPOCH))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test fun lessonKeepsItsWallClockTimeInDifferentZones() {
        val lesson = Lesson(1, 1, 1, "14:10", "15:45", "Test", "", "", "", "", "")
        listOf(-5, 0, 3, 7).forEach { offset ->
            val zone = ZoneOffset.ofHours(offset)
            val before = anchor.atTime(14, 0).atZone(zone).toInstant()
            assertEquals(anchor.atTime(14, 10), ScheduleCycle.nextStart(lesson, before, anchor, 1, 0, zone).atZone(zone).toLocalDateTime())
        }
    }

    @Test fun remainingMinutesExcludeLessonBoundariesAndRoundUp() {
        val lesson = Lesson(1, 1, 1, "14:10", "15:45", "Test", "", "", "", "", "")
        val zone = ZoneOffset.ofHours(3)
        fun at(hour: Int, minute: Int, second: Int = 0) = anchor.atTime(hour, minute, second).atZone(zone).toInstant()
        assertNull(ScheduleCycle.remainingMinutes(lesson, anchor, at(14, 9), zone))
        assertEquals(95L, ScheduleCycle.remainingMinutes(lesson, anchor, at(14, 10), zone))
        assertEquals(35L, ScheduleCycle.remainingMinutes(lesson, anchor, at(15, 10, 20), zone))
        assertEquals(1L, ScheduleCycle.remainingMinutes(lesson, anchor, at(15, 44, 59), zone))
        assertNull(ScheduleCycle.remainingMinutes(lesson, anchor, at(15, 45), zone))
    }

    @Test fun startingSoonAppearsOnlyWithinTheLastSixtyMinutes() {
        val lesson = Lesson(1, 1, 1, "14:10", "15:45", "Test", "", "", "", "", "")
        listOf(-5, 0, 3, 7).forEach { offset ->
            val zone = ZoneOffset.ofHours(offset)
            fun at(hour: Int, minute: Int, second: Int = 0) = anchor.atTime(hour, minute, second).atZone(zone).toInstant()
            assertNull(ScheduleCycle.startingSoonMinutes(lesson, anchor, at(0, 8), zone))
            assertNull(ScheduleCycle.startingSoonMinutes(lesson, anchor, at(13, 9, 59), zone))
            assertEquals(60L, ScheduleCycle.startingSoonMinutes(lesson, anchor, at(13, 10), zone))
            assertEquals(60L, ScheduleCycle.startingSoonMinutes(lesson, anchor, at(13, 10, 1), zone))
            assertEquals(59L, ScheduleCycle.startingSoonMinutes(lesson, anchor, at(13, 11), zone))
            assertEquals(42L, ScheduleCycle.startingSoonMinutes(lesson, anchor, at(13, 28), zone))
            assertEquals(1L, ScheduleCycle.startingSoonMinutes(lesson, anchor, at(14, 9, 59), zone))
            assertNull(ScheduleCycle.startingSoonMinutes(lesson, anchor, at(14, 10), zone))
            assertNull(ScheduleCycle.startingSoonMinutes(lesson, anchor, at(15, 45), zone))
        }
    }
}
