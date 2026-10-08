package ru.sfu.student.data

import ru.sfu.student.core.*
import java.time.*

/** Capture the old class before replacing the cache; never fall back to the whole subject. */
object TaskLessonBindings {
    fun capture(task: StudentTask, lessons: List<Lesson>): StudentTask {
        if (task.lessonId == null || task.lessonBindingKey != null) return task
        val lesson = lessons.firstOrNull { it.id == task.lessonId && sameSubject(task, it) } ?: return task
        return task.copy(lessonBindingKey = LessonBinding.key(lesson))
    }

    fun reconcile(task: StudentTask, lessons: List<Lesson>, reference: WeekReference,
        zone: ZoneId = ScheduleCycle.zone): StudentTask {
        if (task.lessonId == null) return task
        val captured = capture(task, lessons)
        val found = if (captured.lessonBindingKey != null)
            LessonBinding.resolve(captured.lessonId, captured.lessonBindingKey, lessons)
        else recoverLegacy(captured, lessons, reference, zone)
        return if (found == null) captured else captured.copy(lessonId = found.id,
            lessonBindingKey = LessonBinding.key(found), subjectId = found.subjectId)
    }

    private fun sameSubject(task: StudentTask, lesson: Lesson): Boolean = task.subject.isNotBlank() &&
        (task.subjectId != null && task.subjectId == lesson.subjectId ||
            LessonBinding.normalized(task.subject) == LessonBinding.normalized(lesson.subject))

    private fun recoverLegacy(task: StudentTask, lessons: List<Lesson>, reference: WeekReference, zone: ZoneId): Lesson? {
        // An already orphaned old task can be repaired only when its deadline is the selected class start.
        // With a manual deadline or ambiguous parallel classes there is not enough information to guess.
        val date = runCatching { LocalDate.parse(task.lessonDate) }.getOrNull() ?: return null
        val due = Instant.ofEpochMilli(task.dueAt).atZone(zone)
        if (due.toLocalDate() != date) return null
        val week = ScheduleCycle.week(date, LocalDate.parse(reference.monday), reference.week)
        return lessons.filter { sameSubject(task, it) && it.week == week && it.dayOfWeek == date.dayOfWeek.value &&
            date.atTime(LocalTime.parse(it.startTime)).atZone(zone).toInstant().toEpochMilli() == task.dueAt }.singleOrNull()
    }
}
