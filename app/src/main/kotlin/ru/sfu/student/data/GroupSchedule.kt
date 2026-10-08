package ru.sfu.student.data

import ru.sfu.student.core.*
import java.time.LocalDate

val SavedGroup.university: University get() = University.fromCode(universityCode)
fun SavedGroup.displayName(): String = "${university.label} · $groupName" +
    if (university == University.IRNITU && selectedSubgroup != null) " · $selectedSubgroup подгруппа" else ""

fun SavedGroup.acceptsLesson(lesson: Lesson): Boolean =
    university != University.IRNITU || LessonSubgroups.includes(lesson, selectedSubgroup)

fun SavedGroup.weekReference(settings: Settings): WeekReference = when {
    weekAnchorMonday.isNotBlank() -> WeekReference(weekAnchorMonday, weekAnchorWeek)
    university == University.SFU -> WeekReference(settings.anchorMonday, settings.anchorWeek)
    else -> WeekReference(ScheduleCycle.monday(LocalDate.now(ScheduleCycle.zone)).toString(), 1)
}

fun SavedGroup.hasConfirmedWeek(settings: Settings): Boolean =
    if (weekAnchorMonday.isNotBlank()) weekConfirmed else university == University.SFU && settings.weekConfirmed
