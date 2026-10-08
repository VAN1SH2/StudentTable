package ru.sfu.student.core

import java.time.LocalTime
import java.util.Locale

/** Identifies a recurring class without university-generated IDs or mutable location details. */
object LessonBinding {
    fun normalized(value: String): String = value.replace('\u00a0', ' ').trim()
        .replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    fun key(lesson: Lesson): String = "v1|" + listOf(
        lesson.week.toString(), lesson.dayOfWeek.toString(), LocalTime.parse(lesson.startTime).toString(),
        normalized(lesson.subject), normalized(lesson.type), lesson.subgroup?.toString().orEmpty()
    ).joinToString("") { "${it.length}:$it" }

    fun resolve(id: Long?, key: String?, lessons: List<Lesson>): Lesson? {
        if (key == null) return lessons.firstOrNull { it.id == id }
        val matching = lessons.filter { this.key(it) == key }
        return matching.firstOrNull { it.id == id } ?: matching.singleOrNull()
    }
}
