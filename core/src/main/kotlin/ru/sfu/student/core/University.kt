package ru.sfu.student.core

enum class University(val code: String, val label: String, val scheduleUrl: String, val groupExample: String) {
    SFU("sfu", "СФУ", "https://sfu.ru/ru/education/schedule", "КИ25-20"),
    IRNITU("irnitu", "ИРНИТУ", "https://www.istu.edu/raspisanie", "ИСТб-25");

    // Keep existing SFU primary keys and foreign keys intact; the second provider has its own namespace.
    fun localGroupId(remoteId: Long): Long {
        require(remoteId > 0) { "Некорректный ID группы" }
        return if (this == SFU) remoteId else -remoteId
    }

    fun remoteGroupId(localId: Long): Long {
        require(if (this == SFU) localId > 0 else localId < 0 && localId != Long.MIN_VALUE) { "Группа принадлежит другому источнику" }
        return if (this == SFU) localId else -localId
    }

    companion object {
        fun fromCode(code: String): University = entries.firstOrNull { it.code == code }
            ?: error("Неизвестный источник расписания: $code")
    }
}

data class WeekReference(val monday: String, val week: Int)
