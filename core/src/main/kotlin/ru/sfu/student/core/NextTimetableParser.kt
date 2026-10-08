package ru.sfu.student.core

import com.google.gson.*
import java.time.LocalTime

class NextTimetableParser(private val groupId: Long) {
    fun parse(html: String): List<Lesson> = parseSchedule(html).lessons
    fun parseSchedule(html: String): ScheduleData {
        val content = NextResponse.content(html)
        val arrays = NextResponse.arrays(content, "timetables")
        if (arrays.isEmpty() && content.contains("Нам не удалось найти расписание")) throw ScheduleUnavailableException()
        require(arrays.isNotEmpty()) { "Не удалось прочитать расписание СФУ: формат страницы изменился" }
        val periods = NextResponse.arrays(content, "timetablePeriods").flatMap { it.toList() }.map { el ->
            val o = el.asJsonObject
            TimetablePeriod(o.get("id").asInt, time(o.get("beginTime").asString), time(o.get("endTime").asString))
        }.distinctBy { it.id }
        val records = arrays.flatMap { it.toList() }.map { it.asJsonObject }
            .filter { it.getAsJsonObject("group")?.get("id")?.asLong == groupId }
        require(records.isNotEmpty()) { "В ответе СФУ нет занятий выбранной группы. Старое расписание сохранено" }
        val lessons = records.map { lesson(it, periods) }
        require(lessons.map { it.id }.distinct().size == lessons.distinct().size) { "Противоречивые записи расписания" }
        return ScheduleData(lessons.distinct().sortedWith(compareBy({ it.week }, { it.dayOfWeek }, { it.startTime })), periods)
    }
    private fun time(value: String): String = LocalTime.parse(value.trim().replace('.', ':').take(5)).toString()
    private fun lesson(o: JsonObject, periods: List<TimetablePeriod>): Lesson {
        fun text(key: String): String = o.get(key)?.takeUnless { it.isJsonNull }?.let {
            if (it.isJsonObject) it.asJsonObject.get("name")?.asString.orEmpty() else it.asString
        }.orEmpty()
        val range = Regex("(\\d{1,2})[.:](\\d{2})\\s*[-–—]\\s*(\\d{1,2})[.:](\\d{2})").matchEntire(text("time").trim())
        val periodId = o.get("timetablePeriodId")?.takeUnless { it.isJsonNull }?.asInt
            ?: text("time").toIntOrNull()
        val period = periods.firstOrNull { it.id == periodId }
        val start = range?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
            ?: period?.let { LocalTime.parse(it.beginTime) } ?: error("Неизвестный формат времени: ${text("time")}")
        val end = range?.let { LocalTime.of(it.groupValues[3].toInt(), it.groupValues[4].toInt()) }
            ?: period?.let { LocalTime.parse(it.endTime) } ?: error("Неизвестное время окончания")
        val id = o.get("id").asLong; val week = o.get("week").asInt; val day = o.get("dayOfWeek").asInt
        require(id > 0 && week in 1..2 && day in 1..7 && end.isAfter(start) && text("subject").isNotBlank()) { "Некорректная запись расписания $id" }
        require(o.get("date") == null || o.get("date").isJsonNull) { "Получено разовое расписание вместо регулярного" }
        return Lesson(id, week, day, start.toString(), end.toString(), text("subject"), text("teacher"), text("type"),
            text("building"), text("room"), text("description"),
            o.getAsJsonObject("subject")?.get("id")?.takeUnless { it.isJsonNull }?.asLong, subgroup = null)
    }
}
