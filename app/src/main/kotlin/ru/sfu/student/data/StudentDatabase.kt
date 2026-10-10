package ru.sfu.student.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.sfu.student.core.Lesson
import ru.sfu.student.core.CustomLessonDetails
import ru.sfu.student.core.CustomLesson
import java.time.LocalDate
import ru.sfu.student.data.database.TaskReminderConverters

@Entity(tableName = "groups")
data class SavedGroup(@PrimaryKey val groupId: Long, val groupName: String, val course: Int = 0,
    val educationLevel: String = "", val isSession: Boolean = false, val lastUpdated: Long = 0,
    @ColumnInfo(defaultValue = "'sfu'") val universityCode: String = "sfu",
    @ColumnInfo(defaultValue = "''") val weekAnchorMonday: String = "",
    @ColumnInfo(defaultValue = "1") val weekAnchorWeek: Int = 1,
    @ColumnInfo(defaultValue = "0") val weekConfirmed: Boolean = false,
    val selectedSubgroup: Int? = null)

@Entity(tableName = "lessons", primaryKeys = ["groupId", "id"],
    foreignKeys = [ForeignKey(entity = SavedGroup::class, parentColumns = ["groupId"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)])
data class StoredLesson(val groupId: Long, val id: Long, @Embedded(prefix = "data_") val data: Lesson)

@Entity(tableName = "custom_lessons", indices = [Index("groupId"), Index("parentId")],
    foreignKeys = [ForeignKey(entity = SavedGroup::class, parentColumns = ["groupId"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = StoredCustomLesson::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.CASCADE)])
data class StoredCustomLesson(@PrimaryKey(autoGenerate = true) val id: Long = 0, val groupId: Long,
    @Embedded(prefix = "data_") val data: CustomLessonDetails, val parentId: Long? = null, val originalDate: String? = null) {
    fun core(exclusions: List<CustomLessonExclusion> = emptyList()) = CustomLesson(id, data,
        exclusions.filter { it.customId == id }.map { LocalDate.parse(it.date) }.toSet())
}

@Entity(tableName = "custom_lesson_exclusions", primaryKeys = ["customId", "date"],
    foreignKeys = [ForeignKey(entity = StoredCustomLesson::class, parentColumns = ["id"], childColumns = ["customId"], onDelete = ForeignKey.CASCADE)])
data class CustomLessonExclusion(val customId: Long, val date: String)

@Entity(tableName = "tasks", indices = [Index("groupId")],
    foreignKeys = [ForeignKey(entity = SavedGroup::class, parentColumns = ["groupId"], childColumns = ["groupId"], onDelete = ForeignKey.SET_NULL, onUpdate = ForeignKey.CASCADE)])
data class StudentTask(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String,
    val subject: String = "", val notes: String = "", val dueAt: Long, val remindMinutes: Int = 1440,
    val done: Boolean = false, val groupId: Long? = null, val subjectId: Long? = null,
    val lessonId: Long? = null, val lessonDate: String? = null,
    val reminderMinutes: List<Int>? = null, val lessonBindingKey: String? = null)

@Entity(tableName = "settings")
data class Settings(@PrimaryKey val id: Int = 1, val deadlineNotifications: Boolean = true,
    val lessonNotifications: Boolean = false, val lessonLeadMinutes: Int = 15,
    val anchorMonday: String = "2026-08-31", val anchorWeek: Int = 1, val weekConfirmed: Boolean = false,
    val lastImportAt: Long = 0, val activeGroupId: Long? = null, val primaryGroupId: Long? = null)

@Entity(tableName = "deliveries") data class Delivery(@PrimaryKey val key: String, val sentAt: Long)

@Dao interface StudentDao {
    @Query("SELECT * FROM groups ORDER BY groupName") fun groups(): Flow<List<SavedGroup>>
    @Query("SELECT * FROM groups") suspend fun allGroups(): List<SavedGroup>
    @Query("SELECT * FROM groups WHERE groupId = :id") suspend fun group(id: Long): SavedGroup?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGroup(group: SavedGroup): Long
    @Update suspend fun updateGroup(group: SavedGroup)
    @Query("UPDATE groups SET groupId = :newId WHERE groupId = :oldId") suspend fun rekeyGroup(oldId: Long, newId: Long)
    @Query("DELETE FROM groups WHERE groupId = :id") suspend fun deleteGroup(id: Long)
    @Query("SELECT * FROM lessons ORDER BY data_week, data_dayOfWeek, data_startTime") fun lessons(): Flow<List<StoredLesson>>
    @Query("SELECT * FROM lessons") suspend fun allLessons(): List<StoredLesson>
    @Query("SELECT * FROM lessons WHERE groupId = :groupId AND id = :id") suspend fun lesson(groupId: Long, id: Long): StoredLesson?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLessons(items: List<StoredLesson>)
    @Query("DELETE FROM lessons WHERE groupId = :groupId") suspend fun clearLessons(groupId: Long)
    @Query("SELECT * FROM custom_lessons ORDER BY data_startDate, data_startTime") fun customLessons(): Flow<List<StoredCustomLesson>>
    @Query("SELECT * FROM custom_lessons") suspend fun allCustomLessons(): List<StoredCustomLesson>
    @Query("SELECT * FROM custom_lessons WHERE id = :id") suspend fun customLesson(id: Long): StoredCustomLesson?
    @Insert suspend fun insertCustomLesson(item: StoredCustomLesson): Long
    @Update suspend fun updateCustomLesson(item: StoredCustomLesson)
    @Query("DELETE FROM custom_lessons WHERE id = :id") suspend fun deleteCustomLesson(id: Long)
    @Query("DELETE FROM custom_lessons WHERE groupId = :groupId") suspend fun clearCustomLessons(groupId: Long)
    @Query("UPDATE custom_lessons SET groupId = :newId WHERE groupId = :oldId") suspend fun moveCustomLessons(oldId: Long, newId: Long)
    @Query("SELECT * FROM custom_lesson_exclusions") fun customExclusions(): Flow<List<CustomLessonExclusion>>
    @Query("SELECT * FROM custom_lesson_exclusions") suspend fun allCustomExclusions(): List<CustomLessonExclusion>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun excludeCustomLesson(item: CustomLessonExclusion)
    @Query("SELECT * FROM tasks ORDER BY done, dueAt") fun tasks(): Flow<List<StudentTask>>
    @Query("SELECT * FROM tasks WHERE done = 0") suspend fun activeTasks(): List<StudentTask>
    @Query("SELECT * FROM tasks WHERE groupId = :groupId AND lessonId IS NOT NULL") suspend fun boundTasks(groupId: Long): List<StudentTask>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: Long): StudentTask?
    @Upsert suspend fun saveTask(task: StudentTask)
    @Query("UPDATE tasks SET subjectId = :subjectId WHERE groupId = :groupId AND subject = :subject AND subjectId IS NULL")
    suspend fun linkSubject(groupId: Long, subject: String, subjectId: Long)
    @Query("UPDATE tasks SET groupId = :newId WHERE groupId = :oldId") suspend fun moveTasks(oldId: Long, newId: Long)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: Long)
    @Query("SELECT * FROM settings WHERE id = 1") fun settings(): Flow<Settings?>
    @Query("SELECT * FROM settings WHERE id = 1") suspend fun getSettings(): Settings?
    @Upsert suspend fun saveSettings(settings: Settings)
    @Query("SELECT COUNT(*) FROM deliveries WHERE `key` = :key") suspend fun wasDelivered(key: String): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun recordDelivery(item: Delivery): Long
    @Query("DELETE FROM deliveries WHERE sentAt < :before") suspend fun pruneDeliveries(before: Long)
}

@TypeConverters(TaskReminderConverters::class)
@Database(entities = [StoredLesson::class, StoredCustomLesson::class, CustomLessonExclusion::class, StudentTask::class, Settings::class, SavedGroup::class, Delivery::class], version = 8, exportSchema = true)
abstract class StudentDatabase : RoomDatabase() { abstract fun dao(): StudentDao }
