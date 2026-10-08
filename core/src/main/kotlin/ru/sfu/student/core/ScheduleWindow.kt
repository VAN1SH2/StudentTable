package ru.sfu.student.core

import java.time.LocalDate

data class ScheduleDay(val date: LocalDate, val week: Int, val lessons: List<Lesson>)

object ScheduleWindow {
    fun days(lessons: List<Lesson>, today: LocalDate, anchorMonday: LocalDate, anchorWeek: Int): List<ScheduleDay> {
        val monday = ScheduleCycle.monday(today)
        return (0L..13L).map { offset ->
            ScheduleCalendar.day(lessons, monday.plusDays(offset), anchorMonday, anchorWeek)
        }
    }
}
