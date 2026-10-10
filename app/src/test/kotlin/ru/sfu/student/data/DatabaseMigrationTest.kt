package ru.sfu.student.data

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.sfu.student.data.database.DatabaseMigrations
import ru.sfu.student.core.DeadlineReminders
import ru.sfu.student.core.LessonBinding
import ru.sfu.student.reminders.reminderLeads
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DatabaseMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var database: StudentDatabase? = null
    @After fun close() { database?.close(); context.deleteDatabase("migration-test.db") }

    private fun migrate(fromVersion: Int = 1): StudentDatabase {
        val schema = JSONObject(File("schemas/ru.sfu.student.data.StudentDatabase/$fromVersion.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-test.db").callback(object : SupportSQLiteOpenHelper.Callback(fromVersion) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) for (j in 0 until indices.length()) {
                            db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
                        }
                    }
                    if (fromVersion == 1) {
                        db.execSQL("INSERT INTO settings VALUES (1,1,1,30,'2026-09-28',2,1,123456)")
                        db.execSQL("INSERT INTO tasks VALUES (42,'Не потерять задание','Операционные системы','Заметка',1900000000000,60,0)")
                        db.execSQL("INSERT INTO lessons VALUES (12,12,1,3,'14:10','15:45','Операционные системы','Преподаватель','пр. занятие','Корпус №17','3-01','')")
                    } else {
                        db.execSQL("INSERT INTO groups (groupId,groupName,course,educationLevel,isSession,lastUpdated) VALUES (107691,'КИ25-20Б (2 подгруппа)',2,'bachelor',0,123456)")
                        db.execSQL("INSERT INTO settings VALUES (1,1,1,30,'2026-09-28',2,1,123456,107691,107691)")
                        db.execSQL("INSERT INTO tasks (id,title,subject,notes,dueAt,remindMinutes,done,groupId,subjectId) VALUES (42,'Не потерять задание','Операционные системы','Заметка',1900000000000,60,0,107691,214)")
                        db.execSQL("INSERT INTO lessons (groupId,id,data_id,data_week,data_dayOfWeek,data_startTime,data_endTime,data_subject,data_teacher,data_type,data_building,data_room,data_description,data_subjectId) VALUES (107691,12,12,1,3,'14:10','15:45','Операционные системы','Преподаватель','пр. занятие','Корпус №17','3-01','',214)")
                        if (fromVersion >= 3) {
                            db.execSQL("UPDATE groups SET weekAnchorMonday='2026-09-28', weekAnchorWeek=2, weekConfirmed=1")
                            db.execSQL("INSERT INTO groups (groupId,groupName,course,educationLevel,isSession,lastUpdated,universityCode,weekAnchorMonday,weekAnchorWeek,weekConfirmed) VALUES (-478237,'ИСТб-25-1',2,'',0,987654,'irnitu','2026-09-28',1,1)")
                            db.execSQL("INSERT INTO lessons (groupId,id,data_id,data_week,data_dayOfWeek,data_startTime,data_endTime,data_subject,data_teacher,data_type,data_building,data_room,data_description,data_subjectId) VALUES (-478237,51,51,1,1,'11:45','13:15','ИРНИТУ предмет','','лабораторная работа','Корпус В','208','Подгруппа 1',NULL)")
                            db.execSQL("INSERT INTO lessons (groupId,id,data_id,data_week,data_dayOfWeek,data_startTime,data_endTime,data_subject,data_teacher,data_type,data_building,data_room,data_description,data_subjectId) VALUES (-478237,52,52,1,1,'11:45','13:15','ИРНИТУ предмет','','лабораторная работа','Корпус В','209','Подгруппа 2',NULL)")
                            db.execSQL("INSERT INTO lessons (groupId,id,data_id,data_week,data_dayOfWeek,data_startTime,data_endTime,data_subject,data_teacher,data_type,data_building,data_room,data_description,data_subjectId) VALUES (-478237,53,53,2,1,'11:45','13:15','ИРНИТУ предмет','','лекция','Корпус К','311','',NULL)")
                            db.execSQL("INSERT INTO tasks (id,title,subject,notes,dueAt,remindMinutes,done,groupId,subjectId) VALUES (43,'Задание ИРНИТУ','ИРНИТУ предмет','',1900000000000,60,0,-478237,NULL)")
                            if (fromVersion >= 4) {
                                db.execSQL("UPDATE groups SET selectedSubgroup=2 WHERE groupId=-478237")
                                db.execSQL("UPDATE lessons SET data_subgroup=1 WHERE groupId=-478237 AND id=51")
                                db.execSQL("UPDATE lessons SET data_subgroup=2 WHERE groupId=-478237 AND id=52")
                            }
                            if (fromVersion >= 5) {
                                db.execSQL("UPDATE tasks SET lessonId=12, lessonDate='2026-09-30' WHERE id=42")
                                db.execSQL("UPDATE tasks SET remindMinutes=-1 WHERE id=43")
                                db.execSQL("INSERT INTO deliveries VALUES ('task:42:1900000000000',123456)")
                            }
                            if (fromVersion >= 6) {
                                db.execSQL("UPDATE tasks SET reminderMinutes='2880,60' WHERE id=42")
                                db.execSQL("INSERT INTO deliveries VALUES ('task:42:1900000000000:60',123456)")
                            }
                            if (fromVersion >= 7) db.execSQL("UPDATE tasks SET lessonBindingKey='existing-stable-key' WHERE id=42")
                        }
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase; helper.close()
        return Room.databaseBuilder(context, StudentDatabase::class.java, "migration-test.db")
            .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4, DatabaseMigrations.MIGRATION_4_5, DatabaseMigrations.MIGRATION_5_6, DatabaseMigrations.MIGRATION_6_7, DatabaseMigrations.MIGRATION_7_8).allowMainThreadQueries().build().also { database = it }
    }
    @Test fun preservesTasksLessonsSettingsAndValidatesGeneratedSchema() = runBlocking {
        val db = migrate(); val dao = db.dao()
        val task = dao.task(42)!!
        assertEquals("Не потерять задание", task.title)
        assertEquals("Заметка", task.notes)
        assertEquals(107691L, task.groupId)
        assertEquals(1900000000000, task.dueAt)
        assertNull(task.lessonId)
        assertNull(task.lessonDate)
        assertEquals(1, dao.allLessons().size)
        assertEquals(123456L, dao.group(107691)!!.lastUpdated)
        assertEquals(2, dao.getSettings()!!.anchorWeek)
        assertEquals("2026-09-28", dao.getSettings()!!.anchorMonday)
        val group = dao.group(107691)!!
        assertEquals("sfu", group.universityCode)
        assertEquals("2026-09-28", group.weekAnchorMonday)
        assertEquals(2, group.weekAnchorWeek)
        assertTrue(group.weekConfirmed)
        assertNull(group.selectedSubgroup)
    }
    @Test fun groupDeletionPreservesTasksAndIsolatesSchedules() = runBlocking {
        val dao = migrate().dao()
        dao.insertGroup(SavedGroup(108257, "КИ25-20Б (2 подгруппа)"))
        val original = dao.allLessons().single()
        dao.insertLessons(listOf(original.copy(groupId = 108257)))
        assertEquals(2, dao.allLessons().size)
        dao.deleteGroup(107691)
        assertNull(dao.task(42)!!.groupId)
        assertEquals(108257L, dao.allLessons().single().groupId)
    }
    @Test fun idChangeCascadesAndUndoRestoresTask() = runBlocking {
        val dao = migrate().dao()
        dao.rekeyGroup(107691, 108257)
        assertEquals(108257L, dao.task(42)!!.groupId)
        assertEquals(108257L, dao.allLessons().single().groupId)
        val task = dao.task(42)!!
        dao.deleteTask(42); assertNull(dao.task(42))
        dao.saveTask(task); assertEquals(task, dao.task(42))
    }
    @Test fun versionTwoMigrationPreservesSubjectLinksAndUniversityWeek() = runBlocking {
        val dao = migrate(2).dao()
        assertEquals(214L, dao.task(42)!!.subjectId)
        assertEquals(214L, dao.allLessons().single().data.subjectId)
        assertEquals(107691L, dao.getSettings()!!.activeGroupId)
        val group = dao.group(107691)!!
        assertEquals("sfu", group.universityCode)
        assertEquals("2026-09-28", group.weekAnchorMonday)
        assertEquals(2, group.weekAnchorWeek)
        assertTrue(group.weekConfirmed)
        dao.insertGroup(SavedGroup(-107691, "ИРНИТУ", universityCode = "irnitu"))
        assertEquals(2, dao.allGroups().size)
        assertEquals("СФУ", dao.group(107691)!!.university.label)
        assertEquals("ИРНИТУ", dao.group(-107691)!!.university.label)
    }

    @Test fun versionThreeMigrationKeepsTasksAndCachedSubgroups() = runBlocking {
        val dao = migrate(3).dao()
        val group = dao.group(-478237)!!
        assertNull(group.selectedSubgroup)
        assertEquals(987654L, group.lastUpdated)
        assertEquals(1, group.weekAnchorWeek)
        assertTrue(group.weekConfirmed)
        assertEquals(-478237L, dao.task(43)!!.groupId)
        assertEquals("Задание ИРНИТУ", dao.task(43)!!.title)
        assertEquals(214L, dao.task(42)!!.subjectId)
        assertEquals(4, dao.allLessons().size)
        val irnitu = dao.allLessons().filter { it.groupId == -478237L }.map { it.data }
        assertEquals(listOf(1, 2), ru.sfu.student.core.LessonSubgroups.available(irnitu))
        assertEquals(1, dao.lesson(-478237, 51)!!.data.subgroup)
        assertEquals(2, dao.lesson(-478237, 52)!!.data.subgroup)
        assertNull(dao.lesson(-478237, 53)!!.data.subgroup)
        assertEquals(setOf(51L, 53L), irnitu.filter { group.copy(selectedSubgroup = 1).acceptsLesson(it) }.map { it.id }.toSet())
    }

    @Test fun versionFourMigrationKeepsTasksRemindersGroupsAndSubgroupSelections() = runBlocking {
        val dao = migrate(4).dao()
        val sfuTask = dao.task(42)!!
        val irnituTask = dao.task(43)!!
        assertEquals("Не потерять задание", sfuTask.title)
        assertEquals("Заметка", sfuTask.notes)
        assertEquals(214L, sfuTask.subjectId)
        assertEquals(1900000000000, sfuTask.dueAt)
        assertEquals(60, sfuTask.remindMinutes)
        assertFalse(sfuTask.done)
        assertNull(sfuTask.lessonId)
        assertNull(sfuTask.lessonDate)
        assertEquals(-478237L, irnituTask.groupId)
        assertNull(irnituTask.lessonId)
        assertNull(irnituTask.lessonDate)
        assertEquals(2, dao.allGroups().size)
        assertEquals(4, dao.allLessons().size)
        assertEquals(107691L, dao.getSettings()!!.activeGroupId)
        assertEquals(2, dao.group(-478237)!!.selectedSubgroup)
        assertEquals(2, dao.lesson(-478237, 52)!!.data.subgroup)
        val linked = sfuTask.copy(lessonId = 12, lessonDate = "2026-09-30")
        dao.saveTask(linked)
        assertEquals(linked, dao.task(42))
    }

    @Test fun versionFiveMigrationKeepsClassLinksAndAlreadyDeliveredLegacyReminders() = runBlocking {
        val dao = migrate(5).dao()
        val task = dao.task(42)!!
        assertEquals(12L, task.lessonId)
        assertEquals("2026-09-30", task.lessonDate)
        assertEquals("Заметка", task.notes)
        assertNull(task.reminderMinutes)
        assertEquals(listOf(60), task.reminderLeads)
        assertTrue(dao.task(43)!!.reminderLeads.isEmpty())
        assertEquals(1, dao.wasDelivered(DeadlineReminders.key(task.id, task.dueAt, 60)))
        assertEquals(2, dao.group(-478237)!!.selectedSubgroup)
        val multiple = task.copy(reminderMinutes = listOf(2880, 1440, 60, 0))
        dao.saveTask(multiple)
        assertEquals(multiple, dao.task(42))
        dao.deleteTask(42); dao.saveTask(multiple)
        assertEquals(multiple, dao.task(42))
        dao.saveTask(multiple.copy(reminderMinutes = emptyList()))
        assertTrue(dao.task(42)!!.reminderLeads.isEmpty())
    }

    @Test fun versionSixMigrationPreservesDataAndCapturesCachedClassBeforeTheNextRefresh() = runBlocking {
        val db = migrate(6)
        val dao = db.dao()
        val before = dao.task(42)!!
        assertNull(before.lessonBindingKey)
        assertEquals(listOf(2880, 60), before.reminderMinutes)
        assertEquals("2026-09-30", before.lessonDate)
        assertEquals(1, dao.wasDelivered(DeadlineReminders.key(before.id, before.dueAt, 60)))
        val offline = object : ru.sfu.student.core.ScheduleSource {
            override suspend fun search(query: String) = emptyList<ru.sfu.student.core.StudyGroup>()
            override suspend fun fetch(group: ru.sfu.student.core.StudyGroup): ru.sfu.student.core.ScheduleSnapshot = error("No network in migration recovery")
        }
        StudentRepository(db, offline).initialize()
        val captured = dao.task(42)!!
        assertEquals(before.copy(lessonBindingKey = LessonBinding.key(dao.lesson(107691, 12)!!.data)), captured)
        assertEquals(2, dao.group(-478237)!!.selectedSubgroup)
        dao.deleteTask(captured.id)
        dao.saveTask(captured)
        assertEquals(captured, dao.task(42))
    }
    @Test fun versionSevenMigrationPreservesExistingDataAndCreatesCascadingLocalClasses() = runBlocking {
        val dao = migrate(7).dao()
        assertTrue(dao.allCustomLessons().isEmpty())
        assertTrue(dao.allCustomExclusions().isEmpty())
        val task = dao.task(42)!!
        assertEquals("Заметка", task.notes)
        assertEquals(listOf(2880, 60), task.reminderMinutes)
        assertEquals(12L, task.lessonId)
        assertEquals("existing-stable-key", task.lessonBindingKey)
        assertEquals(2, dao.allGroups().size)
        assertEquals(4, dao.allLessons().size)
        val id = dao.insertCustomLesson(StoredCustomLesson(groupId = 107691,
            data = ru.sfu.student.core.CustomLessonDetails("Своя пара", "10:00", "11:30", "2026-10-05", 3)))
        dao.excludeCustomLesson(CustomLessonExclusion(id, "2026-10-12"))
        dao.rekeyGroup(107691, 108257)
        assertEquals(108257L, dao.customLesson(id)!!.groupId)
        assertEquals(1, dao.allCustomExclusions().size)
        dao.deleteGroup(108257)
        assertTrue(dao.allCustomLessons().isEmpty())
        assertTrue(dao.allCustomExclusions().isEmpty())
        assertEquals(task.copy(groupId = null), dao.task(42))
    }
}
