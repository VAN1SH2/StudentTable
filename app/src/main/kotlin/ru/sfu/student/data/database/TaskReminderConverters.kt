package ru.sfu.student.data.database

import androidx.room.TypeConverter
import ru.sfu.student.core.DeadlineReminders

class TaskReminderConverters {
    @TypeConverter fun toStorage(value: List<Int>?): String? = value?.let { DeadlineReminders.encode(it) }
    @TypeConverter fun fromStorage(value: String?): List<Int>? = value?.let { DeadlineReminders.decode(it) }
}
