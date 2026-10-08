package ru.sfu.student.core

object ScheduleRefresh {
    const val MAX_AGE_MILLIS = 12L * 60 * 60 * 1000
    fun isStale(lastUpdated: Long, now: Long): Boolean = lastUpdated <= 0 ||
        now > lastUpdated && now - lastUpdated > MAX_AGE_MILLIS
}
