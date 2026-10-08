package ru.sfu.student.core

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class SfuGroupParserTest {
    private val parser = SfuGroupParser()
    @Test fun actualSearchFindsCurrentIdsAndSubgroups() {
        val response = javaClass.getResource("/sfu-search-2026-09-30.html")!!.readText()
        val groups = parser.parse(response)
        assertEquals(108257L, groups.single { it.groupName == "КИ25-20Б (2 подгруппа)" }.groupId)
        assertEquals(108256L, groups.single { it.groupName == "КИ25-20Б (1 подгруппа)" }.groupId)
        assertEquals(2, groups.first { it.groupId == 108257L }.course)
        assertEquals(groups.size, groups.map { it.groupId }.distinct().size)
        assertTrue(groups.all { it.javaClass == StudyGroup::class.java && it.university == University.SFU })
    }
    @Test fun handlesSplitFramesAndAnyPropertyOrder() {
        val payload = "1:{\"groups\":[{\"groupName\":\"Группа [A]\",\"course\":2,\"groupId\":17,\"educationLevel\":\"bachelor\",\"isSession\":false}]}\n"
        val cut = payload.indexOf("groupId") + 3
        val response = listOf(payload.take(cut), payload.drop(cut)).joinToString("") { "<script>self.__next_f.push([1,${Gson().toJson(it)}])</script>" }
        assertEquals("Группа [A]", parser.parse(response).single().groupName)
    }
    @Test fun emptySearchIsNotAnError() { assertTrue(parser.parse("1:{\"groups\":[]}\n").isEmpty()) }
    @Test fun ignoresNavigationObjectsAndDeduplicates() {
        val record = "{\"groupId\":5,\"groupName\":\"КИ25-20Б (2 подгруппа)\"}"
        assertEquals(1, parser.parse("{\"nav\":{\"groupId\":9},\"results\":[$record,$record]}").size)
    }
}
