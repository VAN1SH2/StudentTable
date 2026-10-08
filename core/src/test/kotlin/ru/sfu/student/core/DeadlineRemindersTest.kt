package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DeadlineRemindersTest {
    @Test fun multiSelectionAndNoneAreMutuallyExclusive() {
        var values = DeadlineReminders.toggle(emptyList(), 1440)
        values = DeadlineReminders.toggle(values, 60)
        values = DeadlineReminders.toggle(values, 0)
        assertEquals(listOf(1440, 60, 0), values)
        assertEquals(listOf(1440, 0), DeadlineReminders.toggle(values, 60))
        assertTrue(DeadlineReminders.toggle(values, -1).isEmpty())
        assertEquals(listOf(2880), DeadlineReminders.toggle(emptyList(), 2880))
    }
    @Test fun legacyRemindersAndAnExplicitEmptyListRemainDifferent() {
        assertEquals(listOf(60), DeadlineReminders.resolve(null, 60))
        assertTrue(DeadlineReminders.resolve(null, -1).isEmpty())
        assertTrue(DeadlineReminders.resolve(emptyList(), 60).isEmpty())
        val values = listOf(60, 1440, 2880, 60, -1)
        assertEquals(listOf(2880, 1440, 60), DeadlineReminders.decode(DeadlineReminders.encode(values)))
        assertTrue(DeadlineReminders.decode("").isEmpty())
    }
    @Test fun createsSeparateJobsInTimeOrderAndSkipsPastReminderTimes() {
        val now = Instant.parse("2026-10-01T10:00:00Z")
        val due = Instant.parse("2026-10-02T12:00:00Z").toEpochMilli()
        val plan = DeadlineReminders.pending(7, due, listOf(2880, 1440, 60, 0, 60), now)
        assertEquals(listOf(1440, 60, 0), plan.map { it.minutes })
        assertEquals(listOf("2026-10-01T12:00:00Z", "2026-10-02T11:00:00Z", "2026-10-02T12:00:00Z"), plan.map { it.at.toString() })
        assertEquals(3, plan.map { it.key }.distinct().size)
        assertTrue(DeadlineReminders.pending(7, due, listOf(0, 60), Instant.ofEpochMilli(due)).isEmpty())
        assertTrue(DeadlineReminders.pending(7, due, emptyList(), now).isEmpty())
    }
}
