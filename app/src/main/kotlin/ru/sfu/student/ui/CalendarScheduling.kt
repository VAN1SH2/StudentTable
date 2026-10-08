package ru.sfu.student.ui

import ru.sfu.student.core.ScheduleCalendar
import ru.sfu.student.core.ScheduleCycle
import ru.sfu.student.core.ScheduleDay
import ru.sfu.student.data.StudentTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CalendarDay(val schedule: ScheduleDay, val deadlines: List<StudentTask>)

fun StudentState.calendarDeadlines(zone: ZoneId = ScheduleCycle.zone): Map<LocalDate, List<StudentTask>> =
    activeTasks.filterNot { it.done }.sortedBy { it.dueAt }
        .groupBy { Instant.ofEpochMilli(it.dueAt).atZone(zone).toLocalDate() }

fun StudentState.calendarDay(date: LocalDate, zone: ZoneId = ScheduleCycle.zone): CalendarDay {
    val reference = weekReference
    return CalendarDay(ScheduleCalendar.day(activeLessons, date, LocalDate.parse(reference.monday), reference.week),
        calendarDeadlines(zone)[date].orEmpty())
}
