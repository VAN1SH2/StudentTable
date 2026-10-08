package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test

class IrnituTimetableParserTest {
    private val parser = IrnituTimetableParser()
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readText()

    @Test fun findsGroupsFromSearchAndUsesIndependentIds() {
        val results = parser.search(fixture("irnitu-search-2026-09-30.html"), "ИСТб-25")
        assertEquals(listOf("ИСТб-25-1", "ИСТб-25-2"), results.map { it.groupName })
        assertEquals(listOf(-478237L, -478238L), results.map { it.groupId })
        assertTrue(results.all { it.university == University.IRNITU })
        assertNotEquals(University.SFU.localGroupId(478237), University.IRNITU.localGroupId(478237))
    }

    @Test fun importsBothParitiesSubgroupsAndOfficialLessonEndTimes() {
        val group = StudyGroup(-478237, "ИСТб-25-1", university = University.IRNITU)
        val result = parser.parse(fixture("irnitu-478237-2026-09-30.html"), group)
        assertEquals(WeekReference("2026-09-28", 1), result.weekReference)
        assertEquals(2, result.group.course)
        assertEquals(setOf(1, 2), result.lessons.map { it.week }.toSet())
        assertEquals(result.lessons.size, result.lessons.distinctBy { it.id }.size)
        val monday = result.lessons.filter { it.dayOfWeek == 1 && it.startTime == "11:45" }
        assertTrue(monday.any { it.week == 1 && it.subject == "Математическая логика и дискретная математика" })
        assertTrue(monday.any { it.week == 2 && it.subject == "Инфокоммуникационные системы и сети" })
        assertTrue(monday.all { it.endTime == "13:15" })
        assertTrue(result.lessons.any { it.building == "Корпус В" && it.room == "208" && it.description == "Подгруппа 2" })
        assertEquals(listOf(1, 2), LessonSubgroups.available(result.lessons))
        assertTrue(result.lessons.filter { it.description == "Подгруппа 2" }.all { it.subgroup == 2 })
        assertTrue(result.lessons.filter { it.description.isBlank() }.all { it.subgroup == null })
        assertEquals(setOf(1, 2), result.lessons.filter { it.dayOfWeek == 1 && it.subject == "Иностранный язык" }.map { it.week }.toSet())
    }

    private fun sharedClass(groups: String): Lesson {
        val html = """
            <main><h1>Группа ИСТб-25-1</h1>
            <div class="schedule-info"><div class="info-block-item">
                <span class="info-block-item-label">Показана неделя:</span>
                <span class="info-block-item-value">нечетная</span>
            </div></div>
            <div class="sch-list-day"><h2 class="sch-list-day-header" id="date-28.09.2026"></h2>
                <div class="sch-list-item"><span class="sch-list-item-time-s">11:45</span>
                    <div class="sch-list-item-week week-odd"><div class="schcls-card" id="sh10">
                        <div class="schcls-item-name">Общее занятие</div>
                        <div class="schcls-item-group">$groups</div>
                    </div></div>
                </div>
            </div></main>
        """.trimIndent()
        return parser.parse(html, StudyGroup(-478237, "ИСТб-25-1", university = University.IRNITU)).lessons.single()
    }

    @Test fun readsSubgroupOfSelectedGroupInsteadOfFirstMarkerOnSharedClass() {
        val lesson = sharedClass("""<a href="/raspisanie/grup/478238/">Другая группа</a> подгруппа 1,
            <a href="/raspisanie/grup/478237/">ИСТб-25-1</a> <span>подгруппа 2</span>""")
        assertEquals(2, lesson.subgroup)
        assertEquals("Подгруппа 2", lesson.description)
    }

    @Test fun anotherGroupsSubgroupDoesNotHideOurCommonClass() {
        val lesson = sharedClass("""<a href="/raspisanie/grup/478237/">ИСТб-25-1</a>,
            <a href="/raspisanie/grup/478238/">Другая группа</a> подгруппа 2""")
        assertNull(lesson.subgroup)
        assertTrue(LessonSubgroups.includes(lesson, 1))
        assertTrue(LessonSubgroups.includes(lesson, 2))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsChangedOrUnavailablePageInsteadOfClearingCache() {
        parser.parse("<h1>Расписание недоступно</h1>", StudyGroup(-1, "Test", university = University.IRNITU))
    }
}
