package ru.sfu.student.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object ScheduleCalendar {
    fun day(lessons: List<Lesson>, date: LocalDate, anchorMonday: LocalDate, anchorWeek: Int): ScheduleDay {
        val week = ScheduleCycle.week(date, anchorMonday, anchorWeek)
        return ScheduleDay(date, week, lessons.filter { it.week == week && it.dayOfWeek == date.dayOfWeek.value }
            .sortedWith(compareBy({ it.startTime }, { it.id })))
    }

    /** Whole Monday-to-Sunday rows, including adjacent-month dates. */
    fun monthDates(month: YearMonth): List<LocalDate> {
        val first = ScheduleCycle.monday(month.atDay(1))
        val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return (0L..ChronoUnit.DAYS.between(first, last)).map(first::plusDays)
    }
}
