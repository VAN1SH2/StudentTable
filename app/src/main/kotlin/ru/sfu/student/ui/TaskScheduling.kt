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
    return UpcomingLessons.forSubject(lessonsForGroup(groupId), subjectId, subject, now,
        LocalDate.parse(reference.monday), reference.week, zone)
}

fun StudentState.nextOccurrenceForLesson(groupId: Long?, lesson: Lesson, now: Instant,
    zone: ZoneId = ScheduleCycle.zone): LessonOccurrence? =
    upcomingLessons(groupId, lesson.subjectId, lesson.subject, now, zone).firstOrNull()

fun StudentState.lessonOccurrence(groupId: Long?, lessonId: Long?, lessonDate: String?,
    zone: ZoneId = ScheduleCycle.zone, bindingKey: String? = null): LessonOccurrence? {
    if (groupId == null || lessonId == null || lessonDate == null) return null
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
