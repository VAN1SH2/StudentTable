package ru.sfu.student

import android.app.Application
import androidx.room.Room
import ru.sfu.student.data.database.DatabaseMigrations
import kotlinx.coroutines.*
import ru.sfu.student.data.*
import ru.sfu.student.reminders.Reminders
import ru.sfu.student.network.UniversityScheduleSource

class StudentApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, StudentDatabase::class.java, "student.db").addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4, DatabaseMigrations.MIGRATION_4_5, DatabaseMigrations.MIGRATION_5_6, DatabaseMigrations.MIGRATION_6_7).build() }
    // Replace this one binding to use a different schedule provider.
    val repository by lazy { StudentRepository(database, UniversityScheduleSource()) }
    val reminders by lazy { Reminders(this, database.dao()) }
    override fun onCreate() {
        super.onCreate()
        reminders.createChannel()
        reminders.installMaintenance()
    }
}
