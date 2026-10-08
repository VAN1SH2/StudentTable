package ru.sfu.student.core

import java.time.Instant

data class DeadlineReminder(val minutes: Int, val at: Instant, val key: String)

object DeadlineReminders {
    val choices = listOf(0, 15, 60, 1440, 2880)
    fun normalize(values: List<Int>): List<Int> = values.filter { it >= 0 }.distinct().sortedDescending()
    fun resolve(values: List<Int>?, legacy: Int): List<Int> = normalize(values ?: listOf(legacy))
    fun toggle(values: List<Int>, minutes: Int): List<Int> = when {
        minutes < 0 -> emptyList()
        minutes in values -> normalize(values.filterNot { it == minutes })
        else -> normalize(values + minutes)
    }
    fun encode(values: List<Int>): String = normalize(values).joinToString(",")
    fun decode(value: String): List<Int> = normalize(value.split(',').mapNotNull { it.toIntOrNull() })
    fun legacyKey(id: Long, dueAt: Long): String = "task:$id:$dueAt"
    fun key(id: Long, dueAt: Long, minutes: Int): String = "${legacyKey(id, dueAt)}:$minutes"
    fun pending(id: Long, dueAt: Long, values: List<Int>, now: Instant): List<DeadlineReminder> {
        val due = Instant.ofEpochMilli(dueAt)
        if (!due.isAfter(now)) return emptyList()
        return normalize(values).map { minutes ->
            DeadlineReminder(minutes, due.minusSeconds(minutes * 60L), key(id, dueAt, minutes))
        }.filter { !it.at.isBefore(now) }
    }
}
