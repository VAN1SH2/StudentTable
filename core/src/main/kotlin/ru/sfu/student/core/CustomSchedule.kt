package ru.sfu.student.core

import java.time.*

/** A local class: 0 = once, 1/2 = cycle week, 3 = every week. */
data class CustomLessonDetails(
    val subject: String, val startTime: String, val endTime: String, val startDate: String,
    val repeat: Int = 0, val untilDate: String? = null, val type: String = "",
    val teacher: String = "", val building: String = "", val room: String = "",
    val subjectId: Long? = null, val subgroup: Int? = null
) {
    fun validate() {
        require(subject.isNotBlank() && subject.length <= 200) { "Укажите предмет" }
        require(repeat in 0..3) { "Выберите повторение" }
        val date = LocalDate.parse(startDate)
        require(untilDate == null || !LocalDate.parse(untilDate).isBefore(date)) { "Окончание повторения раньше первой пары" }
        val start = LocalTime.parse(startTime)
        val end = LocalTime.parse(endTime)
        require(start.second == 0 && start.nano == 0 && end.second == 0 && end.nano == 0 && end.isAfter(start)) {
            "Время окончания должно быть позже начала в тот же день"
        }
        require(subgroup == null || subgroup > 0) { "Укажите положительный номер подгруппы" }
    }
}

data class CustomLesson(val id: Long, val data: CustomLessonDetails, val excludedDates: Set<LocalDate> = emptySet()) {
    val lessonId: Long get() = -id
    fun occurs(date: LocalDate, anchor: LocalDate, anchorWeek: Int): Boolean {
        val first = LocalDate.parse(data.startDate)
        if (date in excludedDates || date.isBefore(first) || data.untilDate?.let { date.isAfter(LocalDate.parse(it)) } == true) return false
        if (data.repeat == 0) return date == first
        return date.dayOfWeek == first.dayOfWeek && (data.repeat == 3 || ScheduleCycle.week(date, anchor, anchorWeek) == data.repeat)
    }
    fun lesson(date: LocalDate, anchor: LocalDate, anchorWeek: Int): Lesson = Lesson(
        lessonId, ScheduleCycle.week(date, anchor, anchorWeek), date.dayOfWeek.value,
        data.startTime, data.endTime, data.subject, data.teacher, data.type, data.building, data.room, "",
        data.subjectId, data.subgroup
    )
    fun template(): Lesson = lesson(LocalDate.parse(data.startDate), LocalDate.parse(data.startDate), data.repeat.takeIf { it in 1..2 } ?: 1)
    fun nextStart(now: Instant, anchor: LocalDate, anchorWeek: Int, zone: ZoneId = ScheduleCycle.zone): Instant? {
        var date = maxOf(now.atZone(zone).toLocalDate(), LocalDate.parse(data.startDate))
        // Exclusions are finite. One full cycle after the last exclusion is sufficient.
        val end = maxOf(date, excludedDates.maxOrNull() ?: date).plusDays(14)
        while (!date.isAfter(end)) {
            if (data.untilDate?.let { date.isAfter(LocalDate.parse(it)) } == true) return null
            if (occurs(date, anchor, anchorWeek)) {
                val at = date.atTime(LocalTime.parse(data.startTime)).atZone(zone).toInstant()
                if (at.isAfter(now)) return at
            }
            if (data.repeat == 0) return null
            date = date.plusDays(1)
        }
        return null
    }
}

object CustomSchedule {
    fun day(regular: List<Lesson>, custom: List<CustomLesson>, date: LocalDate, anchor: LocalDate, anchorWeek: Int): ScheduleDay {
        val day = ScheduleCalendar.day(regular, date, anchor, anchorWeek)
        return day.copy(lessons = (day.lessons + custom.filter { it.occurs(date, anchor, anchorWeek) }.map { it.lesson(date, anchor, anchorWeek) })
            .sortedWith(compareBy({ it.startTime }, { it.id })))
    }
    fun upcoming(custom: List<CustomLesson>, subjectId: Long?, subject: String, now: Instant,
        anchor: LocalDate, anchorWeek: Int, zone: ZoneId = ScheduleCycle.zone): List<LessonOccurrence> {
        val until = now.atZone(zone).plusMonths(1).toInstant()
        val matches = custom.filter { if (subjectId != null && it.data.subjectId != null) subjectId == it.data.subjectId
            else LessonBinding.normalized(subject) == LessonBinding.normalized(it.data.subject) }
        val result = mutableListOf<LessonOccurrence>()
        var date = now.atZone(zone).toLocalDate()
        while (!date.isAfter(until.atZone(zone).toLocalDate())) {
            matches.filter { it.occurs(date, anchor, anchorWeek) }.forEach {
                val at = date.atTime(LocalTime.parse(it.data.startTime)).atZone(zone).toInstant()
                if (at.isAfter(now) && !at.isAfter(until)) result += LessonOccurrence(it.lesson(date, anchor, anchorWeek), date, at)
            }
            date = date.plusDays(1)
        }
        return result.sortedBy { it.startsAt }
    }
}
