package ru.sfu.student.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import ru.sfu.student.core.*

fun SavedGroup.remote() = StudyGroup(groupId, groupName, course, educationLevel, isSession, university)

class StudentRepository(private val db: StudentDatabase, private val source: ScheduleSource) {
    val dao = db.dao()
    val lessons = dao.lessons()
    val tasks = dao.tasks()
    val groups = dao.groups()
    val settings = dao.settings().map { it ?: Settings() }
    suspend fun initialize() = db.withTransaction {
        if (dao.getSettings() == null) {
            dao.saveSettings(Settings())
        }
        val settings = dao.getSettings() ?: Settings()
        val lessons = dao.allLessons().groupBy { it.groupId }
        dao.allGroups().forEach { group ->
            refreshTaskBindings(group, lessons[group.groupId].orEmpty().map { it.data }, settings)
        }
    }
    suspend fun searchGroups(query: String, university: University = University.SFU): List<StudyGroup> = source.search(query, university)
    suspend fun addGroup(group: StudyGroup) = db.withTransaction {
        val s = dao.getSettings() ?: Settings()
        val peer = dao.allGroups().firstOrNull { it.university == group.university && it.hasConfirmedWeek(s) }
        val reference = peer?.weekReference(s)
        dao.insertGroup(SavedGroup(group.groupId, group.groupName, group.course, group.educationLevel, group.isSession,
            universityCode = group.university.code, weekAnchorMonday = reference?.monday.orEmpty(),
            weekAnchorWeek = reference?.week ?: 1, weekConfirmed = reference != null))
        dao.saveSettings(s.copy(activeGroupId = group.groupId, primaryGroupId = s.primaryGroupId ?: group.groupId))
    }
    suspend fun setCurrentWeek(groupId: Long, monday: String, week: Int) = db.withTransaction {
        require(week in 1..2)
        val selected = checkNotNull(dao.group(groupId)) { "Сначала выберите группу" }
        dao.allGroups().filter { it.university == selected.university }.forEach { group ->
            dao.updateGroup(group.copy(weekAnchorMonday = monday, weekAnchorWeek = week, weekConfirmed = true))
        }
    }
    suspend fun activate(id: Long) = updateSettings { it.copy(activeGroupId = id) }
    suspend fun selectSubgroup(groupId: Long, subgroup: Int?) = db.withTransaction {
        val group = checkNotNull(dao.group(groupId)) { "Сначала выберите группу" }
        require(group.university == University.IRNITU) { "Подгруппа СФУ выбирается вместе с группой" }
        val available = LessonSubgroups.available(dao.allLessons().filter { it.groupId == groupId }.map { it.data })
        require(subgroup == null || subgroup > 0 && (subgroup in available || subgroup == group.selectedSubgroup)) {
            "Этой подгруппы нет в загруженном расписании"
        }
        dao.updateGroup(group.copy(selectedSubgroup = subgroup))
    }
    suspend fun primary(id: Long) = updateSettings { it.copy(primaryGroupId = id) }
    suspend fun removeGroup(id: Long) = db.withTransaction {
        // FK SET NULL preserves tasks when their group is removed.
        dao.deleteGroup(id)
        val remaining = dao.allGroups().firstOrNull()?.groupId
        val s = dao.getSettings() ?: Settings()
        dao.saveSettings(s.copy(activeGroupId = if (s.activeGroupId == id) remaining else s.activeGroupId,
            primaryGroupId = if (s.primaryGroupId == id) remaining else s.primaryGroupId))
    }
    suspend fun importSchedule(groupId: Long): Int {
        val group = checkNotNull(dao.group(groupId)) { "Сначала выберите группу" }
        val snapshot = source.fetch(group.remote())
        check(snapshot.group.university == group.university) { "Ответ получен от другого вуза" }
        check(snapshot.lessons.isNotEmpty()) { "${group.university.label} вернул пустое расписание. Старые пары сохранены" }
        db.withTransaction {
            check(dao.group(groupId) != null) { "Группа была удалена во время обновления" }
            val oldLessons = dao.allLessons().filter { it.groupId == groupId }.map { it.data }
            dao.boundTasks(groupId).forEach { task ->
                val captured = TaskLessonBindings.capture(task, oldLessons)
                if (captured != task) dao.saveTask(captured)
            }
            val actual = snapshot.group.groupId
            if (actual != groupId) {
                if (dao.group(actual) == null) dao.rekeyGroup(groupId, actual)
                else { dao.moveTasks(groupId, actual); dao.deleteGroup(groupId) }
                val s = dao.getSettings() ?: Settings()
                dao.saveSettings(s.copy(activeGroupId = if (s.activeGroupId == groupId) actual else s.activeGroupId,
                    primaryGroupId = if (s.primaryGroupId == groupId) actual else s.primaryGroupId))
            }
            val previous = dao.group(actual) ?: group
            val settings = dao.getSettings() ?: Settings()
            val cached = dao.allLessons().filter { it.groupId == actual }.map { it.data }
            // Save the old lesson identity before clearing rows whose remote IDs may have changed.
            dao.boundTasks(actual).forEach { task ->
                val captured = TaskLessonBindings.capture(task, cached)
                if (captured != task) dao.saveTask(captured)
            }
            val importedReference = snapshot.weekReference.takeIf { !previous.hasConfirmedWeek(settings) }
            dao.updateGroup(previous.copy(groupId = actual, groupName = snapshot.group.groupName,
                course = snapshot.group.course, educationLevel = snapshot.group.educationLevel,
                isSession = snapshot.group.isSession, universityCode = snapshot.group.university.code,
                lastUpdated = System.currentTimeMillis(), weekAnchorMonday = importedReference?.monday ?: previous.weekAnchorMonday,
                weekAnchorWeek = importedReference?.week ?: previous.weekAnchorWeek,
                weekConfirmed = previous.weekConfirmed || importedReference != null))
            dao.clearLessons(actual)
            dao.insertLessons(snapshot.lessons.map { StoredLesson(actual, it.id, it) })
            refreshTaskBindings(dao.group(actual)!!, snapshot.lessons, settings)
            snapshot.lessons.distinctBy { it.subjectId }.forEach { lesson ->
                lesson.subjectId?.let { dao.linkSubject(actual, lesson.subject, it) }
            }
        }
        return snapshot.lessons.size
    }
    suspend fun updateSettings(change: (Settings) -> Settings) = db.withTransaction {
        dao.saveSettings(change(dao.getSettings() ?: Settings()))
    }
    suspend fun saveTask(task: StudentTask) = db.withTransaction {
        val group = task.groupId?.let { dao.group(it) }
        val resolved = if (group == null) task else TaskLessonBindings.reconcile(task,
            dao.allLessons().filter { it.groupId == group.groupId }.map { it.data }, group.weekReference(dao.getSettings() ?: Settings()))
        dao.saveTask(resolved)
    }
    private suspend fun refreshTaskBindings(group: SavedGroup, lessons: List<Lesson>, settings: Settings) {
        val reference = group.weekReference(settings)
        dao.boundTasks(group.groupId).forEach { task ->
            val resolved = TaskLessonBindings.reconcile(task, lessons, reference)
            if (resolved != task) dao.saveTask(resolved)
        }
    }
}
