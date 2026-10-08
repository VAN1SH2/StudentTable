package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class ScheduleCalendarTest {
    private val anchor = LocalDate.of(2026, 9, 28)
    private fun lesson(id: Long, week: Int, day: Int, start: String = "14:10") =
        Lesson(id, week, day, start, "15:45", "Предмет", "", "", "", "", "")

    @Test fun arbitraryPastAndFutureDatesUseTheSameAlternatingCycle() {
        val lessons = listOf(lesson(1, 1, 1), lesson(2, 2, 1))
        val past = ScheduleCalendar.day(lessons, LocalDate.of(2026, 9, 21), anchor, 1)
        val future = ScheduleCalendar.day(lessons, LocalDate.of(2027, 1, 11), anchor, 1)
        assertEquals(2, past.week)
        assertEquals(listOf(2L), past.lessons.map { it.id })
        assertEquals(2, future.week)
        assertEquals(listOf(2L), future.lessons.map { it.id })
        assertEquals(1, ScheduleCalendar.day(lessons, LocalDate.of(2027, 1, 18), anchor, 1).week)
    }

    @Test fun selectedDateShowsOnlyItsWeekAndWeekdayInTimeOrder() {
        val lessons = listOf(lesson(1, 1, 4), lesson(2, 1, 4, "09:00"), lesson(3, 2, 4), lesson(4, 1, 5))
        val day = ScheduleCalendar.day(lessons, LocalDate.of(2026, 10, 1), anchor, 1)
        assertEquals(listOf(2L, 1L), day.lessons.map { it.id })
        assertTrue(ScheduleCalendar.day(lessons, LocalDate.of(2026, 10, 4), anchor, 1).lessons.isEmpty())
    }

    @Test fun leapFebruaryIncludesThe29thAndCompleteCalendarRows() {
        val dates = ScheduleCalendar.monthDates(YearMonth.of(2028, 2))
        assertEquals(35, dates.size)
        assertEquals(LocalDate.of(2028, 1, 31), dates.first())
        assertEquals(LocalDate.of(2028, 3, 5), dates.last())
        assertTrue(LocalDate.of(2028, 2, 29) in dates)
        assertEquals(DayOfWeek.MONDAY, dates.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, dates.last().dayOfWeek)
    }

    @Test fun sixRowMonthsAndTheYearBoundaryKeepEveryDateOnce() {
        val march = ScheduleCalendar.monthDates(YearMonth.of(2026, 3))
        assertEquals(42, march.size)
        assertEquals(LocalDate.of(2026, 2, 23), march.first())
        assertEquals(LocalDate.of(2026, 4, 5), march.last())
        assertEquals(31, march.count { it.monthValue == 3 })
        assertEquals(march.size, march.distinct().size)
        val january = ScheduleCalendar.monthDates(YearMonth.of(2027, 1))
        assertEquals(LocalDate.of(2026, 12, 28), january.first())
        assertEquals(LocalDate.of(2027, 1, 31), january.last())
    }
}
