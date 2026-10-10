package ru.sfu.student.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.sfu.student.*
import ru.sfu.student.R
import ru.sfu.student.core.ScheduleCycle
import ru.sfu.student.core.DeadlineReminders
import ru.sfu.student.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Persistent, approximate reminders: WorkManager restores jobs after reboot. */
class Reminders(private val context: Context, private val dao: StudentDao) {
    private val manager = WorkManager.getInstance(context)
    private val mutex = Mutex()

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Пары и дедлайны", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun installMaintenance() {
        manager.enqueueUniquePeriodicWork("reminder-maintenance", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(12, TimeUnit.HOURS).build())
    }

    fun onDeviceClockChanged() {
        // A receiver only queues durable work; it does not keep a background process alive.
        manager.enqueueUniqueWork("clock-change-maintenance", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MaintenanceWorker>().build())
    }

    suspend fun rebuild() = mutex.withLock {
        manager.cancelAllWorkByTag(TAG).result.await()
        val settings = dao.getSettings() ?: Settings()
        val now = Instant.now()
        val zone = ScheduleCycle.zone
        if (settings.deadlineNotifications) dao.activeTasks().forEach { task ->
            DeadlineReminders.pending(task.id, task.dueAt, task.reminderLeads, now).forEach { reminder ->
                if (dao.wasDelivered(reminder.key) == 0) enqueue("task", task.id, task.dueAt, reminder.key,
                    reminder.at, now, leadMinutes = reminder.minutes)
            }
        }
        val groups = dao.allGroups()
        LessonReminderPlan.upcoming(settings, groups, dao.allLessons(), dao.allCustomLessons(), dao.allCustomExclusions(), now, zone).forEach { planned ->
            val start = planned.startsAt
            val key = "lesson:${planned.groupId}:${planned.lesson.id}:${start.toEpochMilli()}"
            if (dao.wasDelivered(key) == 0) enqueue("lesson", planned.lesson.id, start.toEpochMilli(), key,
                start.minusSeconds(settings.lessonLeadMinutes * 60L), now, planned.groupId)
        }
        dao.pruneDeliveries(now.minusSeconds(60L * 86400).toEpochMilli())
    }

    suspend fun cancelTask(id: Long) {
        manager.cancelAllWorkByTag("task:$id").result.await()
    }

    private fun enqueue(kind: String, id: Long, event: Long, key: String, at: Instant, now: Instant,
        groupId: Long = -1, leadMinutes: Int = -1) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf("kind" to kind, "id" to id, "event" to event, "key" to key,
                "groupId" to groupId, "leadMinutes" to leadMinutes))
            .setInitialDelay(Duration.between(now, at).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(TAG).addTag("$kind:$id").build()
        manager.enqueueUniqueWork("reminder:$key", ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val CHANNEL = "student_reminders"
        const val TAG = "student-reminder"
    }
}

class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as StudentApp).reminders.rebuild()
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (_: Exception) { Result.retry() }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StudentApp
        val dao = app.database.dao()
        val settings = dao.getSettings() ?: Settings()
        val kind = inputData.getString("kind") ?: return Result.failure()
        val id = inputData.getLong("id", -1)
        val event = inputData.getLong("event", 0)
        val key = inputData.getString("key") ?: return Result.failure()
        var deliveryKey = key
        val now = System.currentTimeMillis()
        val title: String
        val body: String
        if (kind == "task") {
            val task = dao.task(id) ?: return Result.success()
            val lead = inputData.getInt("leadMinutes", task.remindMinutes)
            if (!settings.deadlineNotifications || task.done || lead !in task.reminderLeads || task.dueAt != event ||
                now < event - lead * 60000L || now > event + 86400000L) return Result.success()
            deliveryKey = DeadlineReminders.key(id, event, lead)
            if (key != deliveryKey && key != DeadlineReminders.legacyKey(id, event)) return Result.success()
            title = if (now >= event) "Срок задания наступил" else "Скоро дедлайн"
            val time = Instant.ofEpochMilli(event).atZone(ScheduleCycle.zone).format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
            body = "${task.title} · $time"
        } else {
            val groupId = inputData.getLong("groupId", -1)
            val group = dao.group(groupId) ?: return Result.success()
            val reference = group.weekReference(settings)
            val start = Instant.ofEpochMilli(event).atZone(ScheduleCycle.zone)
            val lesson = if (id < 0) {
                val stored = dao.customLesson(-id) ?: return Result.success()
                if (stored.groupId != groupId) return Result.success()
                val custom = stored.core(dao.allCustomExclusions())
                val anchor = LocalDate.parse(reference.monday)
                if (!custom.occurs(start.toLocalDate(), anchor, reference.week)) return Result.success()
                custom.lesson(start.toLocalDate(), anchor, reference.week)
            } else dao.lesson(groupId, id)?.data ?: return Result.success()
            if (!settings.lessonNotifications || !group.hasConfirmedWeek(settings) || settings.primaryGroupId != groupId || !group.acceptsLesson(lesson)) return Result.success()
            if (start.dayOfWeek.value != lesson.dayOfWeek || start.toLocalTime().toString() != lesson.startTime ||
                ScheduleCycle.week(start.toLocalDate(), LocalDate.parse(reference.monday), reference.week) != lesson.week ||
                now < event - settings.lessonLeadMinutes * 60000L || now >= event) return Result.success()
            title = "Скоро пара · ${lesson.startTime}"
            body = "${lesson.subject} · ${lesson.building} ${if (lesson.room.startsWith("https://")) "" else lesson.room}"
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
        // Unique database key prevents duplicate delivery after a restart/rebuild.
        if (dao.recordDelivery(Delivery(deliveryKey, now)) == -1L) return Result.success()
        val intent = Intent(applicationContext, MainActivity::class.java).putExtra("screen", if (kind == "task") 1 else 0)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(applicationContext, deliveryKey.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_school).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pending).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(applicationContext).notify(deliveryKey, 0, notification) }
        catch (_: SecurityException) { return Result.success() }
        // Separate maintenance job avoids cancelling this running work from inside itself.
        WorkManager.getInstance(applicationContext).enqueueUniqueWork("after-delivery", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<MaintenanceWorker>().build())
        return Result.success()
    }
}
