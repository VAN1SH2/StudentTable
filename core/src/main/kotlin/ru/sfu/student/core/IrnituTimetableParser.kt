package ru.sfu.student.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The HTML layout of the official IRNITU site is isolated from local storage and Compose. */
class IrnituTimetableParser {
    private val groupPath = Regex("/raspisanie/grup/(\\d+)(?:/|$|[?#])")
    private val datePattern = Regex("\\d{2}\\.\\d{2}\\.\\d{4}")
    private val timePattern = Regex("\\b\\d{1,2}:\\d{2}\\b")
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    private val timeFormat = DateTimeFormatter.ofPattern("H:mm")
    private val subgroupPattern = Regex("подгруппа\\s+(\\d+)", RegexOption.IGNORE_CASE)

    fun search(html: String, query: String): List<StudyGroup> {
        val page = Jsoup.parse(html)
        check(page.selectFirst("input[name=zapros]") != null) { "ИРНИТУ изменил страницу поиска. Попробуйте позже" }
        val normalized = query.trim().replace('–', '-').replace('—', '-')
        return page.select("main a[href]").mapNotNull { link ->
            val id = groupPath.find(link.attr("href"))?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
            val name = link.text().trim()
            if (id <= 0 || name.isBlank() || !name.contains(normalized, ignoreCase = true)) null
            else StudyGroup(University.IRNITU.localGroupId(id), name, university = University.IRNITU)
        }.distinctBy { it.groupId }
    }

    fun parse(html: String, group: StudyGroup): ScheduleSnapshot {
        require(group.university == University.IRNITU)
        val remoteId = group.university.remoteGroupId(group.groupId)
        val page = Jsoup.parse(html)
        val heading = page.selectFirst("h1")?.text().orEmpty()
        check(heading.startsWith("Группа ")) { "ИРНИТУ не вернул расписание выбранной группы" }
        val days = page.select("main .sch-list-day")
        check(days.isNotEmpty()) { "У группы ИРНИТУ пока нет расписания. Сохранённые пары доступны" }
        val metadata = page.select(".schedule-info .info-block-item").associate { item ->
            item.selectFirst(".info-block-item-label")?.text().orEmpty().removeSuffix(":").trim().lowercase(Locale.ROOT) to
                item.selectFirst(".info-block-item-value")?.text().orEmpty().trim()
        }
        val dates = days.map { day ->
            val raw = datePattern.find(day.selectFirst(".sch-list-day-header")?.id().orEmpty())?.value
                ?: datePattern.find(day.attr("data-params"))?.value
                ?: error("ИРНИТУ изменил даты в расписании")
            LocalDate.parse(raw, dateFormat)
        }
        val shownWeek = metadata["показана неделя"].orEmpty().lowercase(Locale.ROOT)
        val week = when {
            "нечет" in shownWeek || "нечёт" in shownWeek -> 1
            "чет" in shownWeek || "чёт" in shownWeek -> 2
            else -> error("Не удалось определить чётность недели ИРНИТУ")
        }
        val lessons = mutableListOf<Lesson>()
        val periods = linkedMapOf<String, String>()
        days.forEachIndexed { index, day ->
            val dayOfWeek = dates[index].dayOfWeek.value
            day.select(".sch-list-item").forEach { item ->
                val startText = timePattern.find(item.selectFirst(".sch-list-item-time-s")?.text().orEmpty())?.value
                    ?: error("ИРНИТУ изменил время начала занятий")
                val start = LocalTime.parse(startText, timeFormat)
                val endText = timePattern.find(item.selectFirst(".sch-list-item-time-f")?.text().orEmpty())?.value
                // Official university regulations, section 6.7: standard classes last 90 minutes.
                val end = endText?.let { LocalTime.parse(it, timeFormat) } ?: start.plusMinutes(90)
                check(end.isAfter(start)) { "Неподдерживаемое время занятия ИРНИТУ" }
                val startTime = start.format(DateTimeFormatter.ofPattern("HH:mm"))
                val endTime = end.format(DateTimeFormatter.ofPattern("HH:mm"))
                periods[startTime] = endTime
                item.select(".sch-list-item-week").forEach { parity ->
                    val weeks = when {
                        parity.hasClass("week-all") -> listOf(1, 2)
                        parity.hasClass("week-odd") -> listOf(1)
                        parity.hasClass("week-even") -> listOf(2)
                        else -> error("ИРНИТУ изменил обозначения недель")
                    }
                    parity.select(".schcls-card:not(.schcls-empty)").forEach cardLoop@{ card ->
                        val subject = card.selectFirst(".schcls-item-name")?.text().orEmpty().trim()
                        if (subject.isBlank()) return@cardLoop
                        val cardGroups = card.select(".schcls-item-group a").mapNotNull {
                            groupPath.find(it.attr("href"))?.groupValues?.get(1)?.toLongOrNull()
                        }
                        if (remoteId !in cardGroups) return@cardLoop
                        val rawId = card.id().removePrefix("sh").toLongOrNull()
                            ?: error("ИРНИТУ изменил ID занятий")
                        check(rawId > 0) { "Некорректный ID занятия ИРНИТУ" }
                        val roomText = card.selectFirst(".schcls-item-aud")?.text().orEmpty().trim()
                        val roomParts = Regex("^([A-Za-zА-Яа-яЁё]+)-(.+)$").matchEntire(roomText)
                        val subgroup = subgroupForGroup(card, remoteId)
                        val description = subgroup?.let { "Подгруппа $it" }.orEmpty()
                        weeks.forEach { value ->
                            // All-week classes have separate stable IDs in the two cached weeks.
                            val id = Math.addExact(Math.multiplyExact(rawId, 16L), (value - 1) * 8L + dayOfWeek)
                            lessons += Lesson(id, value, dayOfWeek, startTime, endTime, subject,
                                card.selectFirst(".schcls-item-prepod")?.text().orEmpty().trim(),
                                card.selectFirst(".schcls-item-distype")?.text().orEmpty().trim(),
                                roomParts?.groupValues?.get(1)?.let { "Корпус $it" }.orEmpty(),
                                roomParts?.groupValues?.get(2) ?: roomText, description, subgroup = subgroup)
                        }
                    }
                }
            }
        }
        check(lessons.isNotEmpty()) { "У группы ИРНИТУ пока нет занятий. Старое расписание сохранено" }
        check(lessons.groupBy { it.id }.values.none { it.distinct().size > 1 }) { "ИРНИТУ вернул конфликтующие занятия" }
        val actual = group.copy(groupName = heading.removePrefix("Группа ").trim(), course = metadata["курс"]?.toIntOrNull() ?: group.course)
        return ScheduleSnapshot(actual, lessons.distinctBy { it.id }, periods.entries.sortedBy { it.key }.mapIndexed { i, period ->
            TimetablePeriod(i + 1, period.key, period.value)
        }, WeekReference(ScheduleCycle.monday(dates.first()).toString(), week))
    }

    private fun subgroupForGroup(card: Element, remoteId: Long): Int? {
        // A shared class may list several groups. Only read the marker after our group's link.
        val link = card.select(".schcls-item-group a").firstOrNull {
            groupPath.find(it.attr("href"))?.groupValues?.get(1)?.toLongOrNull() == remoteId
        } ?: return null
        val suffix = buildString {
            var node = link.nextSibling()
            while (node != null) {
                if (node is Element && (node.tagName() == "a" || node.select("a[href]").isNotEmpty())) break
                append(' ')
                when (node) {
                    is TextNode -> append(node.text())
                    is Element -> append(node.text())
                }
                node = node.nextSibling()
            }
        }
        return subgroupPattern.find(suffix)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
    }
}
