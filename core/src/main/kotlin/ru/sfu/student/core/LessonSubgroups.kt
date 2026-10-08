package ru.sfu.student.core

/** No subgroup means a common class; no selection means showing all classes. */
object LessonSubgroups {
    private val legacyLabel = Regex("^подгруппа\\s+(\\d+)$", RegexOption.IGNORE_CASE)

    fun number(lesson: Lesson): Int? = lesson.subgroup?.takeIf { it > 0 }
        ?: legacyLabel.matchEntire(lesson.description.trim())?.groupValues?.get(1)
            ?.toIntOrNull()?.takeIf { it > 0 }

    fun available(lessons: List<Lesson>): List<Int> = lessons.mapNotNull(::number).distinct().sorted()

    fun includes(lesson: Lesson, selected: Int?): Boolean {
        val number = number(lesson)
        return selected == null || number == null || number == selected
    }
}
