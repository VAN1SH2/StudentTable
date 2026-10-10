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
import ru.sfu.student.ui.*
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CustomLessonRepositoryTest {
    private lateinit var db: StudentDatabase
    private lateinit var repo: StudentRepository
    private val date = LocalDate.of(2026, 10, 5)
    private val group = SavedGroup(1, "Группа", weekAnchorMonday = "2026-10-05", weekAnchorWeek = 1, weekConfirmed = true)
    private val regular = Lesson(10, 1, 1, "09:00", "09:45", "Предмет", "", "", "", "", "", subjectId = 50)
    private fun details(repeat: Int = 0) = CustomLessonDetails("Предмет", "10:00", "11:30", date.toString(), repeat, subjectId = 50)
    private fun source(newGroupId: Long = 1) = object : ScheduleSource {
        override suspend fun search(query: String) = emptyList<StudyGroup>()
        override suspend fun fetch(group: StudyGroup) = ScheduleSnapshot(group.copy(groupId = newGroupId), listOf(regular.copy(id = 99, subjectId = 90)))
    }
    @Before fun create() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), StudentDatabase::class.java).allowMainThreadQueries().build()
        repo = StudentRepository(db, source())
        db.dao().insertGroup(group)
        db.dao().insertLessons(listOf(StoredLesson(1, regular.id, regular)))
        db.dao().saveSettings(Settings(activeGroupId = 1, primaryGroupId = 1))
    }
    @After fun close() { db.close() }
    private suspend fun add(repeat: Int = 0, groupId: Long = 1) = repo.saveCustomLesson(StoredCustomLesson(groupId = groupId, data = details(repeat)))
    private suspend fun task(id: Long, own: Long, date: LocalDate = this.date) = StudentTask(id, "Сдать задание", "Предмет", "Заметка",
        dueAt = date.minusDays(1).atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), groupId = 1, subjectId = 50,
        lessonId = -own, lessonDate = date.toString(), reminderMinutes = listOf(1440, 60)).also { repo.saveTask(it) }
    private suspend fun state() = StudentState(db.dao().getSettings()!!, db.dao().allGroups(), db.dao().allLessons(),
        db.dao().activeTasks(), true, db.dao().allCustomLessons(), db.dao().allCustomExclusions())

    @Test fun successfulAndFailedImportPreserveLocalClassesAndTheirTaskIdentity() = runBlocking {
        val id = add(3)
        task(1, id)
        val before = db.dao().task(1)!!
        repo.importSchedule(1)
        val updated = db.dao().task(1)!!
        assertEquals(before.copy(subjectId = 90), updated)
        assertEquals(90L, db.dao().customLesson(id)!!.data.subjectId)
        assertEquals(listOf(99L, -id), state().lessonsOn(date).lessons.map { it.id })
        val offline = object : ScheduleSource {
            override suspend fun search(query: String) = emptyList<StudyGroup>()
            override suspend fun fetch(group: StudyGroup): ScheduleSnapshot = throw java.io.IOException("Offline")
        }
        try { StudentRepository(db, offline).importSchedule(1); fail("Must fail offline") } catch (_: java.io.IOException) { }
        assertEquals(updated, db.dao().task(1))
        assertEquals(1, db.dao().allCustomLessons().size)
    }
    @Test fun editingOneOccurrenceMovesOnlyThatClassesTasksAndPreservesManualDeadlines() = runBlocking {
        val id = add(3)
        task(1, id)
        task(2, id, date.plusWeeks(1))
        val first = db.dao().task(1)!!
        val second = db.dao().task(2)!!
        val original = db.dao().customLesson(id)!!
        val replacement = repo.saveCustomLesson(original.copy(data = original.data.copy(startDate = date.plusDays(1).toString(), startTime = "12:00", endTime = "13:30")), date)
        assertEquals(first.copy(lessonId = -replacement, lessonDate = date.plusDays(1).toString(), lessonBindingKey = "custom:v1:${-replacement}"), db.dao().task(1))
        assertEquals(second, db.dao().task(2))
        assertEquals(listOf(regular.id), state().lessonsOn(date).lessons.map { it.id })
        assertEquals(listOf(-replacement), state().lessonsOn(date.plusDays(1)).lessons.map { it.id })
        assertEquals(listOf(-id), state().lessonsOn(date.plusWeeks(1)).lessons.map { it.id })
        assertEquals(id, db.dao().customLesson(replacement)!!.parentId)
    }
    @Test fun editingTheSeriesKeepsBindingAndDoesNotReplaceExistingIndividualExceptions() = runBlocking {
        val id = add(3)
        task(1, id, date.plusWeeks(1))
        val before = db.dao().task(1)!!
        val row = db.dao().customLesson(id)!!
        val replacement = repo.saveCustomLesson(row.copy(data = row.data.copy(startTime = "12:00", endTime = "13:30")), date)
        repo.saveCustomLesson(row.copy(data = row.data.copy(startTime = "14:00", endTime = "15:30", subject = "Новое имя")))
        assertEquals(before.copy(subject = "Новое имя", subjectId = null), db.dao().task(1))
        assertEquals("12:00", state().lessonsOn(date).lessons.single { it.id == -replacement }.startTime)
        assertEquals("14:00", state().lessonsOn(date.plusWeeks(1)).lessons.single { it.id == -id }.startTime)
    }
    @Test fun deletingOneOccurrenceLeavesFutureClassesAndTasksIntact() = runBlocking {
        val id = add(3)
        task(1, id)
        task(2, id, date.plusWeeks(1))
        val first = db.dao().task(1)!!
        val second = db.dao().task(2)!!
        repo.deleteCustomLesson(id, date)
        assertEquals(first.copy(lessonId = null, lessonDate = null, lessonBindingKey = null), db.dao().task(1))
        assertEquals(second, db.dao().task(2))
        assertTrue(state().lessonsOn(date).lessons.none { it.id == -id })
        assertTrue(state().lessonsOn(date.plusWeeks(1)).lessons.any { it.id == -id })
    }
    @Test fun deletingEntireSeriesAlsoDeletesReplacementsAndPreservesCompletedTasks() = runBlocking {
        val id = add(3)
        val replacement = repo.saveCustomLesson(db.dao().customLesson(id)!!, date)
        task(1, replacement)
        val saved = db.dao().task(1)!!.copy(done = true)
        db.dao().saveTask(saved)
        repo.deleteCustomLesson(id)
        assertTrue(db.dao().allCustomLessons().isEmpty())
        assertTrue(db.dao().allCustomExclusions().isEmpty())
        assertEquals(saved.copy(lessonId = null, lessonDate = null, lessonBindingKey = null), db.dao().task(1))
    }
    @Test fun resetIsOfflineGroupScopedAndPreservesImportedScheduleSettingsAndTaskData() = runBlocking {
        db.dao().insertGroup(group.copy(groupId = 2, groupName = "Другая"))
        val id = add(3)
        val other = add(3, 2)
        val replacement = repo.saveCustomLesson(db.dao().customLesson(id)!!, date)
        task(1, replacement)
        val before = db.dao().task(1)!!
        val ordinary = StudentTask(5, "Обычное задание", regular.subject, "Его заметка", dueAt = 1900000000000,
            groupId = 1, subjectId = 50, lessonId = regular.id, lessonDate = date.toString(), reminderMinutes = emptyList())
        db.dao().saveTask(ordinary)
        val settings = db.dao().getSettings()
        val imported = db.dao().allLessons()
        repo.resetCustomSchedule(1)
        assertEquals(listOf(other), db.dao().allCustomLessons().map { it.id })
        assertTrue(db.dao().allCustomExclusions().isEmpty())
        assertEquals(imported, db.dao().allLessons())
        assertEquals(settings, db.dao().getSettings())
        assertEquals(before.copy(lessonId = null, lessonDate = null, lessonBindingKey = null), db.dao().task(1))
        assertEquals(ordinary, db.dao().task(5))
        repo.saveTask(before)
        assertNull(db.dao().task(1)!!.lessonId)
    }
    @Test fun recoveredGroupIdMovesLocalRowsExceptionsAndTasksEvenIntoAnExistingGroup() = runBlocking {
        db.dao().insertGroup(group.copy(groupId = 2))
        val id = add(3)
        val replacement = repo.saveCustomLesson(db.dao().customLesson(id)!!, date)
        task(1, replacement)
        val before = db.dao().task(1)!!
        StudentRepository(db, source(2)).importSchedule(1)
        assertNull(db.dao().group(1))
        assertTrue(db.dao().allCustomLessons().all { it.groupId == 2L })
        assertEquals(1, db.dao().allCustomExclusions().size)
        assertEquals(before.copy(groupId = 2, subjectId = 90), db.dao().task(1))
        assertEquals(2L, db.dao().getSettings()!!.activeGroupId)
    }
    @Test fun invalidSaveDoesNotPartiallyExcludeAnOccurrenceOrChangeTasks() = runBlocking {
        val id = add(3)
        task(1, id)
        val before = db.dao().task(1)
        val row = db.dao().customLesson(id)!!
        try { repo.saveCustomLesson(row.copy(data = row.data.copy(endTime = "08:00")), date); fail("Invalid class accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(row, db.dao().customLesson(id))
        assertEquals(before, db.dao().task(1))
        assertTrue(db.dao().allCustomExclusions().isEmpty())
    }
    @Test fun subgroupFilterAndExactCalendarTaskBindingWorkForFarFutureLocalClass() = runBlocking {
        val irnitu = group.copy(groupId = -1, universityCode = "irnitu", selectedSubgroup = 2)
        db.dao().insertGroup(irnitu)
        val far = date.plusYears(2)
        val id = repo.saveCustomLesson(StoredCustomLesson(groupId = -1, data = details().copy(startDate = far.toString(), subgroup = 2)))
        repo.saveCustomLesson(StoredCustomLesson(groupId = -1, data = details().copy(startDate = far.toString(), subgroup = 1)))
        db.dao().saveSettings(Settings(activeGroupId = -1))
        val state = state()
        assertEquals(listOf(-id), state.lessonsOn(far).lessons.map { it.id })
        assertEquals(listOf(1, 2), state.subgroupsForGroup(-1))
        val occurrence = state.initialTaskOccurrence(-1, state.activeLessons.single(), Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC, far)!!
        assertEquals(far, occurrence.date)
        assertEquals(far.atTime(10, 0).toInstant(ZoneOffset.UTC), occurrence.startsAt)
        assertEquals(listOf(occurrence), lessonDeadlineChoices(emptyList(), occurrence))
    }
    @Test fun movingAOnceClassUpdatesItsTaskDateButKeepsItsDeadlineAndReminderChoices() = runBlocking {
        val id = add()
        task(1, id)
        val before = db.dao().task(1)!!
        val original = db.dao().customLesson(id)!!
        repo.saveCustomLesson(original.copy(data = original.data.copy(startDate = date.plusDays(3).toString(), startTime = "12:00", endTime = "13:30")))
        val expected = before.copy(lessonDate = date.plusDays(3).toString())
        assertEquals(expected, db.dao().task(1))
        val state = state()
        val selected = state.lessonOccurrence(1, -id, expected.lessonDate)!!
        assertEquals(listOf(expected), tasksForLesson(selected.lesson, 1, state.tasks, selected.date))
        assertNull(state.lessonOccurrence(1, -id, date.toString()))
    }
    @Test fun nearestSubjectClassIncludesLocalClassesAndExpiresOnceClassesNormally() = runBlocking {
        val id = add()
        val state = state()
        val now = Instant.parse("2026-10-05T09:01:00Z")
        val occurrence = state.initialTaskOccurrence(1, regular, now, ZoneOffset.UTC)!!
        assertEquals(-id, occurrence.lesson.id)
        assertEquals(date, occurrence.date)
        assertTrue(state.upcomingLessons(1, 50, regular.subject, Instant.parse("2026-10-05T10:01:00Z"), ZoneOffset.UTC).none { it.lesson.id == -id })
    }
}
