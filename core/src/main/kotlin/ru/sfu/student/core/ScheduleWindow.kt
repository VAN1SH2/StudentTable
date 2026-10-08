package ru.sfu.student.core

import java.time.LocalDate

data class ScheduleDay(val date: LocalDate, val week: Int, val lessons: List<Lesson>)

object ScheduleWindow {
    fun days(lessons: List<Lesson>, today: LocalDate, anchorMonday: LocalDate, anchorWeek: Int): List<ScheduleDay> {
        val monday = ScheduleCycle.monday(today)
        return (0L..13L).map { offset ->
            val date = monday.plusDays(offset)
            val week = ScheduleCycle.week(date, anchorMonday, anchorWeek)
            ScheduleDay(date, week, lessons.filter { it.week == week && it.dayOfWeek == date.dayOfWeek.value }
                .sortedWith(compareBy({ it.startTime }, { it.id })))
        }
    }
}
