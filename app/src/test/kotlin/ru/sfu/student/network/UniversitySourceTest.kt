package ru.sfu.student.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.sfu.student.core.*
import java.io.File

class UniversitySourceTest {
    private fun fixture(name: String) = File("../core/src/test/resources/$name").readText()

    @Test fun irnituSearchPostsQueryAndImportUsesSelectedGroupId() = runBlocking {
        val calls = mutableListOf<Pair<String, Map<String, String>?>>()
        val source = IrnituScheduleSource { url, fields ->
            calls += url to fields
            if (fields != null) fixture("irnitu-search-2026-09-30.html") else fixture("irnitu-478237-2026-09-30.html")
        }
        val result = source.search("ИСТб-25")
        assertEquals(2, result.size)
        assertEquals(mapOf("zapros" to "ИСТб-25"), calls.single().second)
        val schedule = source.fetch(result.first())
        assertEquals(-478237L, schedule.group.groupId)
        assertEquals("https://www.istu.edu/raspisanie/grup/478237/", calls.last().first)
        assertNull(calls.last().second)
        assertTrue(schedule.lessons.isNotEmpty())
    }

    @Test fun routerKeepsSameNumericRemoteIdInSeparateUniversities() = runBlocking {
        fun provider(university: University) = object : ScheduleSource {
            override suspend fun search(query: String) = listOf(StudyGroup(university.localGroupId(7), query, university = university))
            override suspend fun fetch(group: StudyGroup) = ScheduleSnapshot(group, emptyList())
        }
        val router = UniversityScheduleSource(University.entries.associateWith { provider(it) })
        val sfu = router.search("Test", University.SFU).single()
        val irnitu = router.search("Test", University.IRNITU).single()
        assertEquals(7L, sfu.groupId)
        assertEquals(-7L, irnitu.groupId)
        assertEquals(University.IRNITU, router.fetch(irnitu).group.university)
        assertEquals(University.SFU, router.fetch(sfu).group.university)
    }
}
