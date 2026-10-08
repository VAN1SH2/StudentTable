package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test

class ScheduleRefreshTest {
    @Test fun refreshesOnlyAfterTwelveHoursOrWhenNeverLoaded() {
        val last = 1_000_000L
        assertFalse(ScheduleRefresh.isStale(last, last + ScheduleRefresh.MAX_AGE_MILLIS))
        assertTrue(ScheduleRefresh.isStale(last, last + ScheduleRefresh.MAX_AGE_MILLIS + 1))
        assertTrue(ScheduleRefresh.isStale(0, last))
        assertFalse(ScheduleRefresh.isStale(last, last - 1))
    }
}
