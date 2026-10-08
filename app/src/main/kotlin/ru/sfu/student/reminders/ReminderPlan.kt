package ru.sfu.student.reminders

import ru.sfu.student.core.DeadlineReminders
import ru.sfu.student.data.*

val StudentTask.reminderLeads: List<Int> get() = DeadlineReminders.resolve(reminderMinutes, remindMinutes)

object LessonReminderPlan {
    fun select(settings: Settings, groups: List<SavedGroup>, lessons: List<StoredLesson>): List<StoredLesson> {
        if (!settings.lessonNotifications) return emptyList()
        val primary = groups.firstOrNull { it.groupId == settings.primaryGroupId } ?: return emptyList()
        if (!primary.hasConfirmedWeek(settings)) return emptyList()
        return lessons.filter { it.groupId == primary.groupId && primary.acceptsLesson(it.data) }
    }
}
