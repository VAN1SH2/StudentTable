package ru.sfu.student.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.sfu.student.core.StudyGroup
import ru.sfu.student.core.University
import java.io.File

class SfuSourceTest {
    private fun fixture(name: String) = File("../core/src/test/resources/$name").readText()
    @Test fun repairsRetiredIdOnlyForExactSameNamedSubgroup() = runBlocking {
        val urls = mutableListOf<String>()
        val source = SfuScheduleSource { url -> urls += url; when {
            "/107691?" in url -> fixture("sfu-group-unavailable-2026-09-30.html")
            "?search=" in url -> fixture("sfu-search-2026-09-30.html")
            "/108257?" in url -> fixture("sfu-107691-2026-09-30.html").replace("107691", "108257")
            else -> error("Unexpected request $url")
        } }
        val result = source.fetch(StudyGroup(107691, "КИ25-20Б (2 подгруппа)"))
        assertEquals(108257L, result.group.groupId)
        assertEquals(29, result.lessons.size)
        assertEquals(3, urls.size)
        assertTrue(urls.last().contains("view=two_weeks&scope=regular"))
        assertFalse(urls.any { "_rsc" in it })
    }
    @Test fun persistedIdDoesNotRequireRepeatedSearch() = runBlocking {
        val urls = mutableListOf<String>()
        val source = SfuScheduleSource { url -> urls += url; fixture("sfu-107691-2026-09-30.html").replace("107691", "108257") }
        assertEquals(29, source.fetch(StudyGroup(108257, "КИ25-20Б (2 подгруппа)")).lessons.size)
        assertEquals(listOf(SfuScheduleSource.scheduleUrl(108257)), urls)
    }

    @Test fun searchAndSelectedGroupImportUseCurrentModels() = runBlocking {
        val source = SfuScheduleSource { url -> when {
            "?search=" in url -> fixture("sfu-search-2026-09-30.html")
            "/108257?" in url -> fixture("sfu-107691-2026-09-30.html").replace("107691", "108257")
            else -> error("Unexpected request $url")
        } }
        val groups = source.search("КИ25-20")
        assertTrue(groups.isNotEmpty())
        assertTrue(groups.all { it.javaClass == StudyGroup::class.java && it.university == University.SFU })
        val group = groups.single { it.groupName == "КИ25-20Б (2 подгруппа)" }
        val snapshot = source.fetch(group)
        assertEquals(group, snapshot.group)
        assertEquals(29, snapshot.lessons.size)
        assertTrue(snapshot.lessons.all { it.subgroup == null })
    }
}
