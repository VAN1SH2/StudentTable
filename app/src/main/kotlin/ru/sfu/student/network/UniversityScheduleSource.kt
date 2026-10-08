package ru.sfu.student.network

import ru.sfu.student.core.*

/** Adding a university only changes the provider registry, not the screens or task storage. */
class UniversityScheduleSource(private val sources: Map<University, ScheduleSource> = mapOf(
    University.SFU to SfuScheduleSource(), University.IRNITU to IrnituScheduleSource()
)) : ScheduleSource {
    override suspend fun search(query: String): List<StudyGroup> = search(query, University.SFU)

    override suspend fun search(query: String, university: University): List<StudyGroup> = provider(university).search(query)

    override suspend fun fetch(group: StudyGroup): ScheduleSnapshot = provider(group.university).fetch(group)

    private fun provider(university: University): ScheduleSource = checkNotNull(sources[university]) {
        "Источник ${university.label} не подключён"
    }
}
