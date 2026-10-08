package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleWindowTest {
    private fun lesson(id: Long, week: Int, day: Int) = Lesson(id, week, day, "14:10", "15:45", "Предмет", "", "", "", "", "")
    @Test fun showsCurrentAndNextCalendarWeekWithTheRightCounts() {
        val today = LocalDate.of(2026, 10, 4)
        val days = ScheduleWindow.days(listOf(lesson(1, 1, 4), lesson(2, 1, 4), lesson(3, 2, 4)), today, LocalDate.of(2026, 9, 28), 1)
        assertEquals(14, days.size)
        assertEquals(LocalDate.of(2026, 9, 28), days.first().date)
        assertEquals(LocalDate.of(2026, 10, 11), days.last().date)
        assertEquals(List(7) { 1 } + List(7) { 2 }, days.map { it.week })
        assertEquals(2, days[3].lessons.size)
        assertEquals(listOf(3L), days[10].lessons.map { it.id })
        assertTrue(days[6].lessons.isEmpty())
    }
    @Test fun datesAndWeekParityCrossTheYearBoundary() {
        val days = ScheduleWindow.days(emptyList(), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 28), 2)
        assertEquals(LocalDate.of(2027, 1, 4), days[7].date)
        assertEquals(1, days[7].week)
        assertEquals(2, days.first().week)
    }
}
