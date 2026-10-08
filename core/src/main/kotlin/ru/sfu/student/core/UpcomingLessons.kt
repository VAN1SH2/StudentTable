package ru.sfu.student.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class LessonOccurrence(val lesson: Lesson, val date: LocalDate, val startsAt: Instant) {
    val key: String get() = "$date:${lesson.id}"
}

/** Expand the regular two-week cycle into dated classes for one calendar month. */
object UpcomingLessons {
    fun forSubject(lessons: List<Lesson>, subjectId: Long?, subject: String, now: Instant,
        anchorMonday: LocalDate, anchorWeek: Int, zone: ZoneId = ScheduleCycle.zone): List<LessonOccurrence> {
        if (subject.isBlank()) return emptyList()
        val matching = lessons.filter { lesson ->
            if (subjectId != null && lesson.subjectId != null) lesson.subjectId == subjectId
            else lesson.subject.trim().equals(subject.trim(), ignoreCase = true)
        }
        val localNow = now.atZone(zone)
        val until = localNow.plusMonths(1).toInstant()
        val result = mutableListOf<LessonOccurrence>()
        var date = localNow.toLocalDate()
        while (!date.isAfter(until.atZone(zone).toLocalDate())) {
            val week = ScheduleCycle.week(date, anchorMonday, anchorWeek)
            matching.filter { it.week == week && it.dayOfWeek == date.dayOfWeek.value }.forEach { lesson ->
                val start = date.atTime(LocalTime.parse(lesson.startTime)).atZone(zone).toInstant()
                if (start.isAfter(now) && !start.isAfter(until)) result += LessonOccurrence(lesson, date, start)
            }
            date = date.plusDays(1)
        }
        return result.distinctBy { it.key }.sortedWith(compareBy({ it.startsAt }, { it.lesson.type }, { it.lesson.id }))
    }
}
