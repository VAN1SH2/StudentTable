package ru.sfu.student.reminders

import ru.sfu.student.core.DeadlineReminders
import ru.sfu.student.core.*
import ru.sfu.student.data.*
import java.time.*

val StudentTask.reminderLeads: List<Int> get() = DeadlineReminders.resolve(reminderMinutes, remindMinutes)

object LessonReminderPlan {
    fun select(settings: Settings, groups: List<SavedGroup>, lessons: List<StoredLesson>): List<StoredLesson> {
        if (!settings.lessonNotifications) return emptyList()
        val primary = groups.firstOrNull { it.groupId == settings.primaryGroupId } ?: return emptyList()
        if (!primary.hasConfirmedWeek(settings)) return emptyList()
        return lessons.filter { it.groupId == primary.groupId && primary.acceptsLesson(it.data) }
    }
    fun upcoming(settings: Settings, groups: List<SavedGroup>, lessons: List<StoredLesson>,
        custom: List<StoredCustomLesson>, exclusions: List<CustomLessonExclusion>, now: Instant,
        zone: ZoneId = ScheduleCycle.zone): List<PlannedLesson> {
        if (!settings.lessonNotifications) return emptyList()
        val primary = groups.firstOrNull { it.groupId == settings.primaryGroupId } ?: return emptyList()
        if (!primary.hasConfirmedWeek(settings)) return emptyList()
        val reference = primary.weekReference(settings)
        val anchor = LocalDate.parse(reference.monday)
        val regular = select(settings, groups, lessons).map {
            PlannedLesson(it.groupId, it.data, ScheduleCycle.nextStart(it.data, now, anchor, reference.week, 0, zone))
        }
        return regular + custom.filter { it.groupId == primary.groupId }.mapNotNull { stored ->
            val own = stored.core(exclusions)
            if (!primary.acceptsLesson(own.template())) null
            else own.nextStart(now, anchor, reference.week, zone)?.let { start ->
                PlannedLesson(stored.groupId, own.lesson(start.atZone(zone).toLocalDate(), anchor, reference.week), start)
            }
        }
    }
}

data class PlannedLesson(val groupId: Long, val lesson: Lesson, val startsAt: Instant)
