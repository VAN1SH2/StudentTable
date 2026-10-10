package ru.sfu.student.ui

import ru.sfu.student.core.*
import ru.sfu.student.data.StudentTask
import ru.sfu.student.data.weekReference
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

fun StudentState.upcomingLessons(groupId: Long?, subjectId: Long?, subject: String, now: Instant,
    zone: ZoneId = ScheduleCycle.zone): List<LessonOccurrence> {
    val group = groups.firstOrNull { it.groupId == groupId } ?: return emptyList()
    val reference = group.weekReference(settings)
    val anchor = LocalDate.parse(reference.monday)
    return (UpcomingLessons.forSubject(regularLessonsForGroup(groupId), subjectId, subject, now, anchor, reference.week, zone) +
        CustomSchedule.upcoming(customForGroup(groupId), subjectId, subject, now, anchor, reference.week, zone))
        .sortedWith(compareBy({ it.startsAt }, { it.lesson.type }, { it.lesson.id }))
}

fun StudentState.initialTaskOccurrence(groupId: Long?, lesson: Lesson, now: Instant,
    zone: ZoneId = ScheduleCycle.zone, date: LocalDate? = null): LessonOccurrence? =
    if (date != null) lessonOccurrence(groupId, lesson.id, date.toString(), zone, LessonBinding.key(lesson))
    else upcomingLessons(groupId, lesson.subjectId, lesson.subject, now, zone).firstOrNull()

/** Keep the selected class visible even outside the monthly suggestion window. */
fun lessonDeadlineChoices(upcoming: List<LessonOccurrence>, picked: LessonOccurrence?): List<LessonOccurrence> =
    if (picked == null) upcoming
    else (listOf(picked) + upcoming).distinctBy { it.key }
        .sortedWith(compareBy({ it.startsAt }, { it.lesson.type }, { it.lesson.id }))

fun StudentState.lessonOccurrence(groupId: Long?, lessonId: Long?, lessonDate: String?,
    zone: ZoneId = ScheduleCycle.zone, bindingKey: String? = null): LessonOccurrence? {
    if (groupId == null || lessonId == null || lessonDate == null) return null
    if (lessonId < 0) {
        val group = groups.firstOrNull { it.groupId == groupId } ?: return null
        val reference = group.weekReference(settings)
        val custom = customLessons.firstOrNull { it.id == -lessonId && it.groupId == groupId }?.core(customExclusions) ?: return null
        return runCatching {
            val date = LocalDate.parse(lessonDate)
            val anchor = LocalDate.parse(reference.monday)
            if (!custom.occurs(date, anchor, reference.week)) null
            else LessonOccurrence(custom.lesson(date, anchor, reference.week), date,
                date.atTime(LocalTime.parse(custom.data.startTime)).atZone(zone).toInstant())
        }.getOrNull()
    }
    val lesson = LessonBinding.resolve(lessonId, bindingKey, lessons.filter { it.groupId == groupId }.map { it.data }) ?: return null
    return runCatching {
        val date = LocalDate.parse(lessonDate)
        LessonOccurrence(lesson, date, date.atTime(LocalTime.parse(lesson.startTime)).atZone(zone).toInstant())
    }.getOrNull()
}

fun tasksForLesson(lesson: Lesson, groupId: Long, tasks: List<StudentTask>, date: LocalDate? = null): List<StudentTask> =
    tasks.filter { task ->
        val sameSubject = if (task.subjectId != null && lesson.subjectId != null) task.subjectId == lesson.subjectId
            else task.subject.trim().equals(lesson.subject.trim(), ignoreCase = true)
        // The undated two-week overview matches the class; the daily screen also matches its occurrence.
        val sameOccurrence = task.lessonId == null || task.lessonId == lesson.id &&
            (task.lessonBindingKey == null || task.lessonBindingKey == LessonBinding.key(lesson)) &&
            (task.lessonDate == null || date == null || task.lessonDate == date.toString())
        !task.done && task.groupId == groupId && task.subject.isNotBlank() && sameSubject && sameOccurrence
    }.sortedBy { it.dueAt }
