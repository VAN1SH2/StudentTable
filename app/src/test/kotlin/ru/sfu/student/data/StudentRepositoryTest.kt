package ru.sfu.student.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.sfu.student.core.*
import ru.sfu.student.ui.StudentState
import ru.sfu.student.ui.tasksForLesson
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StudentRepositoryTest {
    private lateinit var db: StudentDatabase
    private val old = Lesson(12, 1, 3, "14:10", "15:45", "Предмет", "", "", "", "", "", 214)
    @Before fun create() { db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), StudentDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }
    private fun source(fail: Boolean) = object : ScheduleSource {
        override suspend fun search(query: String) = emptyList<StudyGroup>()
        override suspend fun fetch(group: StudyGroup): ScheduleSnapshot {
            if (fail) throw java.io.IOException("Offline")
            return ScheduleSnapshot(group, listOf(old.copy(id = 13)))
        }
    }
    private suspend fun seed() {
        db.dao().insertGroup(SavedGroup(1, "Группа A", lastUpdated = 123))
        db.dao().insertGroup(SavedGroup(2, "Группа B", lastUpdated = 456))
        db.dao().insertLessons(listOf(StoredLesson(1, old.id, old), StoredLesson(2, old.id, old)))
    }
    @Test fun failedImportKeepsAllLocalLessonsAndTimestamp() = runBlocking {
        seed()
        try { StudentRepository(db, source(true)).importSchedule(1); fail("Expected network error") } catch (_: java.io.IOException) { }
        assertEquals(2, db.dao().allLessons().size)
        assertEquals(123L, db.dao().group(1)!!.lastUpdated)
    }
    @Test fun updateReplacesOnlySelectedGroupAndLinksLegacySubjects() = runBlocking {
        seed()
        db.dao().saveTask(StudentTask(7, "Задание", "Предмет", dueAt = 1900000000000, groupId = 1))
        StudentRepository(db, source(false)).importSchedule(1)
        assertEquals(setOf(13L), db.dao().allLessons().filter { it.groupId == 1L }.map { it.id }.toSet())
        assertEquals(setOf(12L), db.dao().allLessons().filter { it.groupId == 2L }.map { it.id }.toSet())
        assertEquals(456L, db.dao().group(2)!!.lastUpdated)
        assertEquals(214L, db.dao().task(7)!!.subjectId)
    }
    @Test fun correctingIrnituWeekDoesNotChangeSfuGroups() = runBlocking {
        seed()
        val dao = db.dao()
        dao.insertGroup(SavedGroup(-1, "ИРНИТУ A", universityCode = "irnitu"))
        dao.insertGroup(SavedGroup(-2, "ИРНИТУ B", universityCode = "irnitu"))
        val before = dao.group(1)!!
        StudentRepository(db, source(false)).setCurrentWeek(-1, "2026-09-28", 2)
        assertEquals(before, dao.group(1))
        listOf(-1L, -2L).forEach { id ->
            assertEquals(2, dao.group(id)!!.weekAnchorWeek)
            assertTrue(dao.group(id)!!.weekConfirmed)
        }
    }

    @Test fun subgroupChoiceWorksOfflineKeepsCommonClassesAndIsPerGroup() = runBlocking {
        val dao = db.dao()
        dao.insertGroup(SavedGroup(-1, "ИРНИТУ A", universityCode = "irnitu"))
        dao.insertGroup(SavedGroup(-2, "ИРНИТУ B", universityCode = "irnitu", selectedSubgroup = 2))
        val classes = listOf(old.copy(id = 10, subject = "Общий предмет"),
            old.copy(id = 11, subgroup = 1, subject = "Практика 1"), old.copy(id = 12, subgroup = 2, subject = "Практика 2"))
        dao.insertLessons(classes.map { StoredLesson(-1, it.id, it) })
        dao.saveTask(StudentTask(7, "Не терять задачу", "Практика 2", dueAt = 1900000000000, groupId = -1))
        val repository = StudentRepository(db, source(true))
        repository.selectSubgroup(-1, 1)
        val chosen = dao.group(-1)!!
        assertEquals(1, chosen.selectedSubgroup)
        assertEquals(2, dao.group(-2)!!.selectedSubgroup)
        assertEquals(3, dao.allLessons().size)
        assertNotNull(dao.task(7))
        val state = StudentState(Settings(activeGroupId = -1), dao.allGroups(), dao.allLessons())
        assertEquals(setOf(10L, 11L), state.activeLessons.map { it.id }.toSet())
        assertEquals(listOf(1, 2), state.subgroupsForGroup(-1))
        assertTrue(state.activeScheduleLoaded)
        repository.selectSubgroup(-1, null)
        assertNull(dao.group(-1)!!.selectedSubgroup)
        assertTrue(classes.all { dao.group(-1)!!.acceptsLesson(it) })
    }

    @Test fun refreshingSchedulePreservesSubgroupPreference() = runBlocking {
        val dao = db.dao()
        dao.insertGroup(SavedGroup(-1, "ИРНИТУ", universityCode = "irnitu", selectedSubgroup = 2))
        StudentRepository(db, source(false)).importSchedule(-1)
        assertEquals(2, dao.group(-1)!!.selectedSubgroup)
        assertEquals(1, dao.allLessons().size)
    }

    @Test fun missingSubgroupIsRejectedWithoutChangingSavedChoice() = runBlocking {
        val dao = db.dao()
        dao.insertGroup(SavedGroup(-1, "ИРНИТУ", universityCode = "irnitu"))
        dao.insertLessons(listOf(StoredLesson(-1, 12, old.copy(subgroup = 1))))
        try { StudentRepository(db, source(true)).selectSubgroup(-1, 9); fail("Unknown subgroup must be rejected") }
        catch (_: IllegalArgumentException) { }
        assertNull(dao.group(-1)!!.selectedSubgroup)
    }

    @Test fun selectedLessonSurvivesSavingEditingRefreshingAndDeleteUndo() = runBlocking {
        seed()
        val dao = db.dao()
        val task = StudentTask(7, "Практика", "Предмет", dueAt = 1900000000000, groupId = 1,
            subjectId = 214, lessonId = old.id, lessonDate = "2030-03-13", reminderMinutes = listOf(2880, 60))
        dao.saveTask(task)
        assertEquals(task, dao.task(7))
        val edited = dao.task(7)!!.copy(title = "Практика с заметкой", dueAt = 1899996400000, done = true)
        dao.saveTask(edited)
        StudentRepository(db, source(false)).importSchedule(1)
        val rebound = edited.copy(lessonId = 13, lessonBindingKey = LessonBinding.key(old))
        assertEquals(rebound, dao.task(7))
        dao.deleteTask(7)
        assertNull(dao.task(7))
        StudentRepository(db, source(false)).saveTask(edited.copy(lessonBindingKey = LessonBinding.key(old)))
        assertEquals(rebound, dao.task(7))
    }

    @Test fun nextWeekTaskRemainsOnTheSameDatedPracticeAfterRolloverAndRefresh() = runBlocking {
        val dao = db.dao()
        val zone = ZoneOffset.ofHours(7)
        val target = LocalDate.of(2026, 10, 8)
        val group = SavedGroup(11, "СФУ", weekAnchorMonday = "2026-09-28", weekAnchorWeek = 1, weekConfirmed = true)
        val practice = old.copy(week = 2, dayOfWeek = 4, type = "Практика")
        val lecture = practice.copy(id = 10, type = "Лекция", startTime = "12:00")
        dao.insertGroup(group)
        dao.insertLessons(listOf(practice, lecture).map { StoredLesson(11, it.id, it) })
        val task = StudentTask(7, "На следующую практику", practice.subject, dueAt = target.minusDays(1).atTime(18, 0).atZone(zone).toInstant().toEpochMilli(),
            groupId = 11, subjectId = 214, lessonId = practice.id, lessonDate = target.toString(), reminderMinutes = listOf(1440, 60))
        dao.saveTask(task)
        val before = ScheduleWindow.days(listOf(practice, lecture), target.minusWeeks(1), LocalDate.parse(group.weekAnchorMonday), 1).single { it.date == target }
        assertEquals(listOf(task), tasksForLesson(before.lessons.single { it.type == "Практика" }, 11, listOf(task), target))
        val updated = practice.copy(id = 901, subjectId = 500, room = "410")
        val newLecture = lecture.copy(id = 902, subjectId = 500)
        val source = object : ScheduleSource {
            override suspend fun search(query: String) = emptyList<StudyGroup>()
            override suspend fun fetch(group: StudyGroup) = ScheduleSnapshot(group, listOf(updated, newLecture))
        }
        val repository = StudentRepository(db, source)
        repository.initialize()
        repository.importSchedule(11)
        val saved = dao.task(7)!!
        val after = ScheduleWindow.days(dao.allLessons().map { it.data }, target, LocalDate.parse(group.weekAnchorMonday), 1).single { it.date == target }
        assertEquals(3, after.date.dayOfWeek.value - 1)
        assertEquals(listOf(saved), tasksForLesson(updated, 11, listOf(saved), after.date))
        assertTrue(tasksForLesson(newLecture, 11, listOf(saved), after.date).isEmpty())
        assertTrue(tasksForLesson(updated, 11, listOf(saved), target.plusWeeks(2)).isEmpty())
        assertEquals(task.dueAt, saved.dueAt)
        assertEquals(task.reminderMinutes, saved.reminderMinutes)
    }

    @Test fun initializingRepairsAnAlreadyOrphanedTaskWithoutInternet() = runBlocking {
        val dao = db.dao()
        val group = SavedGroup(11, "СФУ", weekAnchorMonday = "2026-09-28", weekAnchorWeek = 1, weekConfirmed = true)
        val date = LocalDate.of(2026, 10, 8)
        val practice = old.copy(id = 900, week = 2, dayOfWeek = 4, type = "Практика")
        dao.insertGroup(group)
        dao.insertLessons(listOf(StoredLesson(11, 900, practice)))
        dao.saveTask(StudentTask(7, "Старая привязка", practice.subject, dueAt = date.atTime(14, 10).atZone(ScheduleCycle.zone).toInstant().toEpochMilli(),
            groupId = 11, subjectId = 214, lessonId = 12, lessonDate = date.toString()))
        StudentRepository(db, source(true)).initialize()
        val task = dao.task(7)!!
        assertEquals(900L, task.lessonId)
        assertEquals(listOf(task), tasksForLesson(practice, 11, listOf(task), date))
    }

    @Test fun irnituRolloverKeepsTheTaskOnlyOnItsChosenSubgroup() = runBlocking {
        val dao = db.dao()
        val date = LocalDate.of(2026, 10, 8)
        val group = SavedGroup(-11, "ИРНИТУ", universityCode = "irnitu", weekAnchorMonday = "2026-09-28",
            weekAnchorWeek = 1, weekConfirmed = true, selectedSubgroup = 1)
        val first = old.copy(id = 201, week = 2, dayOfWeek = 4, type = "Лабораторная работа", subgroup = 1, subjectId = null)
        val second = first.copy(id = 202, subgroup = 2)
        dao.insertGroup(group)
        dao.insertLessons(listOf(first, second).map { StoredLesson(-11, it.id, it) })
        val deadline = date.atTime(14, 10).atZone(ScheduleCycle.zone).toInstant().toEpochMilli()
        dao.saveTask(StudentTask(7, "Лабораторная", first.subject, dueAt = deadline,
            groupId = -11, lessonId = first.id, lessonDate = date.toString()))
        val refreshed = listOf(first.copy(id = 9001), second.copy(id = 9002))
        val source = object : ScheduleSource {
            override suspend fun search(query: String) = emptyList<StudyGroup>()
            override suspend fun fetch(group: StudyGroup) = ScheduleSnapshot(group, refreshed)
        }
        StudentRepository(db, source).importSchedule(-11)
        val task = dao.task(7)!!
        val state = StudentState(Settings(activeGroupId = -11), dao.allGroups(), dao.allLessons(), listOf(task))
        val day = ScheduleWindow.days(state.activeLessons, date, LocalDate.parse(group.weekAnchorMonday), 1).single { it.date == date }
        assertEquals(listOf(9001L), day.lessons.map { it.id })
        assertEquals(listOf(task), tasksForLesson(day.lessons.single(), -11, state.tasks, date))
        assertTrue(tasksForLesson(refreshed[1], -11, state.tasks, date).isEmpty())
        assertEquals(1, dao.group(-11)!!.selectedSubgroup)
    }
}
