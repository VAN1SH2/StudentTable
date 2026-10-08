package ru.sfu.student.core

import com.google.gson.JsonParser
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class NextTimetableParserTest {
    private val parser = NextTimetableParser(107691)
    private fun snapshot(): String = javaClass.getResource("/sfu-107691-2026-09-30.html")!!.readText()
    private fun row(id: Int = 1, week: Int = 1, subject: String = "Предмет [тест]", group: Int = 107691) = """
        {"id":$id,"week":$week,"dayOfWeek":1,"time":"15.55 - 17.30","date":null,
        "subject":{"name":${Gson().toJson(subject)}},"teacher":{"name":"Преподаватель"},
        "group":{"id":$group},"building":"ЭИОС","room":"https://example.org/course","description":null,"type":"лекция"}
    """.trimIndent()
    private fun flight(vararg chunks: String) = chunks.joinToString("") { "<script>self.__next_f.push([1,${Gson().toJson(it)}])</script>" }

    @Test fun actualSfuResponseHasBothWeeksAndOnlineLessons() {
        val lessons = parser.parse(snapshot())
        assertEquals(29, lessons.size)
        assertEquals(setOf(1, 2), lessons.map { it.week }.toSet())
        val first = lessons.first { it.id == 3252604L }
        assertEquals("Иностранный язык", first.subject)
        assertEquals("15:55", first.startTime)
        assertEquals("17:30", first.endTime)
        assertTrue(lessons.any { it.building == "ЭИОС" && it.room.startsWith("https://") })
    }
    @Test fun streamCanSplitInsidePropertyAndEscapedQuotes() {
        val data = "1:[{\"timetables\":[${row(subject = "Курс \"A\" [1]")},${row(2, 2)}]}]\n"
        val cut = data.indexOf("tables")
        val lessons = parser.parse(flight(data.take(cut), data.drop(cut)))
        assertEquals(2, lessons.size)
        assertEquals("Курс \"A\" [1]", lessons.first().subject)
    }
    @Test fun acceptsPlainJsonAndFiltersOtherGroups() {
        assertEquals(1, parser.parse("{\"timetables\":[${row()},${row(2, group = 9)}]}").size)
    }
    @Test fun deduplicatesIdenticalRows() {
        assertEquals(1, parser.parse("{\"timetables\":[${row()},${row()}]}").size)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsConflictingIds() {
        parser.parse("{\"timetables\":[${row()},${row(week = 2)}]}")
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMissingTimetables() { parser.parse("<html>Service unavailable</html>") }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyArray() { parser.parse("{\"timetables\":[]}") }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownWeek() { parser.parse("{\"timetables\":[${row(week = 3)}]}") }
    @Test(expected = IllegalStateException::class) fun rejectsTruncatedResponse() { parser.parse("{\"timetables\":[${row()}") }
    @Test(expected = java.time.DateTimeException::class) fun rejectsInvalidTime() {
        parser.parse("{\"timetables\":[${row().replace("15.55", "25.55")}]}")
    }
    @Test(expected = ScheduleUnavailableException::class) fun reportsRemovedGroupAsUnavailableNotSchemaChange() {
        parser.parse(javaClass.getResource("/sfu-group-unavailable-2026-09-30.html")!!.readText())
    }
    @Test fun resolvesPeriodWhenTimeIsAPeriodNumber() {
        val response = "{\"timetables\":[${row().replace("15.55 - 17.30", "1")}],\"timetablePeriods\":[{\"id\":1,\"beginTime\":\"15:55:00\",\"endTime\":\"17:30:00\"}]}"
        assertEquals("15:55", parser.parse(response).single().startTime)
    }
}
