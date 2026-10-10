package ru.sfu.student.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import ru.sfu.student.core.*
import java.time.LocalDate

fun SavedGroup.remote() = StudyGroup(groupId, groupName, course, educationLevel, isSession, university)

class StudentRepository(private val db: StudentDatabase, private val source: ScheduleSource) {
    val dao = db.dao()
    val lessons = dao.lessons()
    val tasks = dao.tasks()
    val groups = dao.groups()
    val customLessons = dao.customLessons()
    val customExclusions = dao.customExclusions()
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
        refreshCustomTaskBindings()
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
        val available = LessonSubgroups.available(dao.allLessons().filter { it.groupId == groupId }.map { it.data } +
            dao.allCustomLessons().filter { it.groupId == groupId }.map { it.core().template() })
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
        check(snapshot.lessons.all { it.id > 0 }) { "Некорректные идентификаторы пар. Старое расписание сохранено" }
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
                else { dao.moveTasks(groupId, actual); dao.moveCustomLessons(groupId, actual); dao.deleteGroup(groupId) }
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
            dao.allCustomLessons().filter { it.groupId == actual }.forEach { custom ->
                val subjectId = snapshot.lessons.firstOrNull { LessonBinding.normalized(it.subject) == LessonBinding.normalized(custom.data.subject) }?.subjectId
                if (custom.data.subjectId != subjectId) dao.updateCustomLesson(custom.copy(data = custom.data.copy(subjectId = subjectId)))
            }
            refreshCustomTaskBindings()
        }
        return snapshot.lessons.size
    }
    suspend fun updateSettings(change: (Settings) -> Settings) = db.withTransaction {
        dao.saveSettings(change(dao.getSettings() ?: Settings()))
    }
    suspend fun saveTask(task: StudentTask) = db.withTransaction {
        val group = task.groupId?.let { dao.group(it) }
        val resolved = if (task.lessonId != null && task.lessonId < 0) resolveCustomTask(task) else if (group == null) task else TaskLessonBindings.reconcile(task,
            dao.allLessons().filter { it.groupId == group.groupId }.map { it.data }, group.weekReference(dao.getSettings() ?: Settings()))
        dao.saveTask(resolved)
    }
    private suspend fun refreshTaskBindings(group: SavedGroup, lessons: List<Lesson>, settings: Settings) {
        val reference = group.weekReference(settings)
        dao.boundTasks(group.groupId).filter { (it.lessonId ?: 0) >= 0 }.forEach { task ->
            val resolved = TaskLessonBindings.reconcile(task, lessons, reference)
            if (resolved != task) dao.saveTask(resolved)
        }
    }

    suspend fun saveCustomLesson(item: StoredCustomLesson, onlyDate: LocalDate? = null): Long = db.withTransaction {
        val group = checkNotNull(dao.group(item.groupId)) { "Группа уже удалена" }
        val existing = if (item.id == 0L) null else checkNotNull(dao.customLesson(item.id)) { "Пара уже удалена" }
        require(existing == null || existing.groupId == item.groupId) { "Группа пары изменилась" }
        require(existing?.parentId == null || item.data.repeat == 0) { "Отдельное изменение ряда относится к одному занятию" }
        val single = onlyDate != null && existing != null && existing.data.repeat != 0
        val importedSubjectId = dao.allLessons().firstOrNull { it.groupId == item.groupId &&
            LessonBinding.normalized(it.data.subject) == LessonBinding.normalized(item.data.subject) }?.data?.subjectId
        val data = item.data.copy(subject = item.data.subject.trim(), teacher = item.data.teacher.trim(),
            type = item.data.type.trim(), building = item.data.building.trim(), room = item.data.room.trim(),
            subjectId = importedSubjectId,
            repeat = if (single) 0 else item.data.repeat,
            untilDate = item.data.untilDate.takeUnless { single || item.data.repeat == 0 },
            subgroup = item.data.subgroup.takeIf { group.university == University.IRNITU })
        data.validate()
        val id = if (onlyDate != null && existing != null && existing.data.repeat != 0) {
            val reference = group.weekReference(dao.getSettings() ?: Settings())
            require(existing.core(dao.allCustomExclusions()).occurs(onlyDate, LocalDate.parse(reference.monday), reference.week)) { "Это занятие уже изменено" }
            dao.excludeCustomLesson(CustomLessonExclusion(existing.id, onlyDate.toString()))
            val replacement = dao.insertCustomLesson(StoredCustomLesson(groupId = item.groupId,
                data = data.copy(repeat = 0, untilDate = null), parentId = existing.id, originalDate = onlyDate.toString()))
            dao.boundTasks(item.groupId).filter { it.lessonId == -existing.id && it.lessonDate == onlyDate.toString() }.forEach {
                dao.saveTask(it.copy(lessonId = -replacement, lessonDate = data.startDate))
            }
            replacement
        } else if (existing == null) {
            dao.insertCustomLesson(StoredCustomLesson(groupId = item.groupId, data = data))
        } else {
            dao.updateCustomLesson(existing.copy(data = data))
            if (existing.data.repeat == 0 && data.repeat == 0 && existing.data.startDate != data.startDate) {
                dao.boundTasks(item.groupId).filter { it.lessonId == -existing.id }.forEach {
                    dao.saveTask(it.copy(lessonDate = data.startDate))
                }
            }
            existing.id
        }
        refreshCustomTaskBindings()
        id
    }

    suspend fun deleteCustomLesson(id: Long, onlyDate: LocalDate? = null) = db.withTransaction {
        val existing = dao.customLesson(id) ?: return@withTransaction
        if (onlyDate != null && existing.data.repeat != 0) {
            dao.excludeCustomLesson(CustomLessonExclusion(id, onlyDate.toString()))
            dao.boundTasks(existing.groupId).filter { it.lessonId == -id && it.lessonDate == onlyDate.toString() }.forEach {
                dao.saveTask(it.withoutLesson())
            }
        } else {
            val ids = (dao.allCustomLessons().filter { it.id == id || it.parentId == id }).map { -it.id }.toSet()
            dao.boundTasks(existing.groupId).filter { it.lessonId in ids }.forEach { dao.saveTask(it.withoutLesson()) }
            dao.deleteCustomLesson(id)
        }
    }

    suspend fun resetCustomSchedule(groupId: Long) = db.withTransaction {
        checkNotNull(dao.group(groupId)) { "Группа уже удалена" }
        dao.boundTasks(groupId).filter { (it.lessonId ?: 0) < 0 }.forEach { dao.saveTask(it.withoutLesson()) }
        dao.clearCustomLessons(groupId)
    }

    private suspend fun refreshCustomTaskBindings() {
        dao.allGroups().forEach { group ->
            dao.boundTasks(group.groupId).filter { (it.lessonId ?: 0) < 0 }.forEach { task ->
                val resolved = resolveCustomTask(task)
                if (resolved != task) dao.saveTask(resolved)
            }
        }
    }

    private suspend fun resolveCustomTask(task: StudentTask): StudentTask {
        val stored = task.lessonId?.let { dao.customLesson(-it) } ?: return task.withoutLesson()
        if (stored.groupId != task.groupId) return task.withoutLesson()
        val group = dao.group(stored.groupId) ?: return task.withoutLesson()
        val reference = group.weekReference(dao.getSettings() ?: Settings())
        val custom = stored.core(dao.allCustomExclusions())
        val date = task.lessonDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (date != null && !custom.occurs(date, LocalDate.parse(reference.monday), reference.week)) return task.withoutLesson()
        return task.copy(subject = stored.data.subject, subjectId = stored.data.subjectId,
            lessonBindingKey = LessonBinding.key(custom.template()))
    }
}

private fun StudentTask.withoutLesson() = copy(lessonId = null, lessonDate = null, lessonBindingKey = null)
