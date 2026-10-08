package ru.sfu.student.core

import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class Lesson(
    val id: Long,
    val week: Int,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val subject: String,
    val teacher: String,
    val type: String,
    val building: String,
    val room: String,
    val description: String,
    val subjectId: Long? = null,
    val subgroup: Int? = null
)

data class StudyGroup(val groupId: Long, val groupName: String, val course: Int = 0,
    val educationLevel: String = "", val isSession: Boolean = false, val university: University = University.SFU)
data class TimetablePeriod(val id: Int, val beginTime: String, val endTime: String)
data class ScheduleData(val lessons: List<Lesson>, val periods: List<TimetablePeriod>)
data class ScheduleSnapshot(val group: StudyGroup, val lessons: List<Lesson>, val periods: List<TimetablePeriod> = emptyList(),
    val weekReference: WeekReference? = null)

interface ScheduleSource {
    suspend fun search(query: String): List<StudyGroup>
    suspend fun search(query: String, university: University): List<StudyGroup> {
        require(university == University.SFU) { "Источник выбранного вуза не подключён" }
        return search(query)
    }
    suspend fun fetch(group: StudyGroup): ScheduleSnapshot
}

object ScheduleCycle {
    // Read on demand so changing the device timezone never leaves a cached zone.
    val zone: ZoneId get() = ZoneId.systemDefault()
    fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun week(date: LocalDate, anchorMonday: LocalDate, anchorWeek: Int): Int =
        Math.floorMod(ChronoUnit.WEEKS.between(monday(anchorMonday), monday(date)) + anchorWeek - 1, 2).toInt() + 1

    fun remainingMinutes(lesson: Lesson, date: LocalDate, now: Instant, zone: ZoneId = ScheduleCycle.zone): Long? {
        val start = date.atTime(LocalTime.parse(lesson.startTime)).atZone(zone).toInstant()
        val end = date.atTime(LocalTime.parse(lesson.endTime)).atZone(zone).toInstant()
        if (now.isBefore(start) || !now.isBefore(end)) return null
        // Round up: during the last minute the status must still show one minute.
        return (Duration.between(now, end).toMillis() + 59_999) / 60_000
    }

    fun startingSoonMinutes(lesson: Lesson, date: LocalDate, now: Instant, zone: ZoneId = ScheduleCycle.zone): Long? {
        val start = date.atTime(LocalTime.parse(lesson.startTime)).atZone(zone).toInstant()
        if (!now.isBefore(start) || now.isBefore(start.minusSeconds(60 * 60L))) return null
        return ((Duration.between(now, start).toMillis() + 59_999) / 60_000).coerceAtLeast(1)
    }

    fun nextStart(lesson: Lesson, now: Instant, anchor: LocalDate, anchorWeek: Int, leadMinutes: Int,
        zone: ZoneId = ScheduleCycle.zone): Instant {
        val today = now.atZone(zone).toLocalDate()
        for (offset in 0L..28L) {
            val date = today.plusDays(offset)
            val start = date.atTime(LocalTime.parse(lesson.startTime)).atZone(zone).toInstant()
            if (date.dayOfWeek.value == lesson.dayOfWeek && week(date, anchor, anchorWeek) == lesson.week &&
                start.minusSeconds(leadMinutes * 60L).isAfter(now)) return start
        }
        error("Не удалось определить следующую пару")
    }
}
