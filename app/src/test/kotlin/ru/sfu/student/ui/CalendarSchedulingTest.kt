package ru.sfu.student.ui

import org.junit.Assert.*
import org.junit.Test
import ru.sfu.student.core.Lesson
import ru.sfu.student.data.*
import java.time.*

class CalendarSchedulingTest {
    private val zone = ZoneOffset.ofHours(7)
    private val date = LocalDate.of(2026, 10, 9)
    private val group = SavedGroup(-11, "ИРНИТУ", universityCode = "irnitu", weekAnchorMonday = "2026-09-28",
        weekAnchorWeek = 2, weekConfirmed = true, selectedSubgroup = 1)
    private fun task(id: Long, date: LocalDate = this.date, hour: Int = 18) =
        StudentTask(id, "Задание", dueAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), groupId = -11)

    @Test fun arbitraryCalendarDateUsesTheActiveGroupsCycleAndSubgroup() {
        val common = Lesson(1, 1, 5, "12:00", "13:30", "Предмет", "", "", "", "", "")
        val lessons = listOf(StoredLesson(-11, 1, common), StoredLesson(-11, 2, common.copy(id = 2, subgroup = 1)),
            StoredLesson(-11, 3, common.copy(id = 3, subgroup = 2)), StoredLesson(-11, 4, common.copy(id = 4, week = 2)),
            StoredLesson(12, 5, common.copy(id = 5)))
        val state = StudentState(Settings(activeGroupId = -11, anchorWeek = 1), listOf(group), lessons)
        val day = state.calendarDay(date.plusWeeks(8), zone)
        assertEquals(1, day.schedule.week)
        assertEquals(listOf(1L, 2L), day.schedule.lessons.map { it.id })
        assertEquals(listOf(4L), state.calendarDay(date.plusWeeks(9), zone).schedule.lessons.map { it.id })
    }

    @Test fun deadlinesUseDeviceDatesAndActualDeadlinesRatherThanBoundLessonDates() {
        val bound = task(1, hour = 0).copy(lessonDate = date.plusDays(3).toString(), lessonId = 99)
        val endOfDay = task(2, hour = 23)
        val tomorrow = task(3, date.plusDays(1), 0)
        val state = StudentState(Settings(activeGroupId = -11), listOf(group), tasks = listOf(tomorrow, endOfDay, bound))
        assertEquals(listOf(1L, 2L), state.calendarDay(date, zone).deadlines.map { it.id })
        assertEquals(listOf(3L), state.calendarDay(date.plusDays(1), zone).deadlines.map { it.id })
        assertEquals(listOf(1L), state.calendarDay(date.minusDays(1), ZoneOffset.UTC).deadlines.map { it.id })
        assertTrue(state.calendarDay(date.plusDays(3), zone).deadlines.isEmpty())
    }

    @Test fun pendingDeadlinesIncludeUngroupedTasksAndExcludeOtherGroupsAndCompletedTasks() {
        val active = task(1)
        val ungrouped = task(2, hour = 12).copy(groupId = null)
        val state = StudentState(Settings(activeGroupId = -11), listOf(group), tasks = listOf(
            active, ungrouped, task(3).copy(groupId = 12), task(4).copy(done = true)))
        assertEquals(listOf(2L, 1L), state.calendarDay(date, zone).deadlines.map { it.id })
        assertEquals(listOf(2L), state.copy(settings = Settings()).calendarDay(date, zone).deadlines.map { it.id })
        assertTrue(state.copy(tasks = state.tasks.map { it.copy(done = true) }).calendarDeadlines(zone).isEmpty())
    }
}
