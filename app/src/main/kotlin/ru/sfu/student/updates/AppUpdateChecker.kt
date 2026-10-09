package ru.sfu.student.updates

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.sfu.student.core.AppUpdate
import ru.sfu.student.core.AppUpdateInterval

data class UpdateCheckRecord(val lastAttempt: Long? = null, val release: AppUpdate? = null, val promptedAttempt: Long? = null)
interface UpdateCheckStore {
    fun read(): UpdateCheckRecord
    fun write(record: UpdateCheckRecord)
}
sealed interface UpdateCheckResult {
    data class Available(val release: AppUpdate) : UpdateCheckResult
    data object Current : UpdateCheckResult
    data object Skipped : UpdateCheckResult
}

class AppUpdateChecker(private val store: UpdateCheckStore, private val fetch: suspend () -> AppUpdate,
    private val installedVersion: Int, private val deviceSdk: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()

    suspend fun check(force: Boolean = false): UpdateCheckResult = mutex.withLock {
        val old = store.read()
        val now = clock()
        if (!force && !AppUpdateInterval.isDue(old.lastAttempt, now)) {
            val pending = old.release?.takeIf { it.isCompatibleUpdate(installedVersion, deviceSdk) && old.promptedAttempt != old.lastAttempt }
            return@withLock pending?.let(UpdateCheckResult::Available) ?: UpdateCheckResult.Skipped
        }
        // Record attempts before networking: offline launches must also respect the daily limit.
        val attempted = UpdateCheckRecord(lastAttempt = now)
        store.write(attempted)
        val release = fetch()
        store.write(attempted.copy(release = release))
        if (release.isCompatibleUpdate(installedVersion, deviceSdk)) UpdateCheckResult.Available(release) else UpdateCheckResult.Current
    }

    suspend fun markPrompted(versionCode: Int) = mutex.withLock {
        val record = store.read()
        if (record.release?.versionCode == versionCode) store.write(record.copy(promptedAttempt = record.lastAttempt))
    }
}
