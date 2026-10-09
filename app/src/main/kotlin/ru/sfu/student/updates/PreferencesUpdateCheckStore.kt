package ru.sfu.student.updates

import android.content.Context
import ru.sfu.student.core.AppUpdate
import java.io.IOException

class PreferencesUpdateCheckStore(context: Context) : UpdateCheckStore {
    private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    override fun read() = UpdateCheckRecord(
        lastAttempt = if (preferences.contains("last_attempt")) preferences.getLong("last_attempt", 0) else null,
        release = preferences.getString("release", null)?.let { runCatching { AppUpdate.parse(it) }.getOrNull() },
        promptedAttempt = if (preferences.contains("prompted_attempt")) preferences.getLong("prompted_attempt", 0) else null)

    override fun write(record: UpdateCheckRecord) {
        val edit = preferences.edit()
        if (record.lastAttempt == null) edit.remove("last_attempt") else edit.putLong("last_attempt", record.lastAttempt)
        if (record.release == null) edit.remove("release") else edit.putString("release", record.release.toJson())
        if (record.promptedAttempt == null) edit.remove("prompted_attempt") else edit.putLong("prompted_attempt", record.promptedAttempt)
        if (!edit.commit()) throw IOException("Не удалось сохранить время проверки обновлений")
    }
}
