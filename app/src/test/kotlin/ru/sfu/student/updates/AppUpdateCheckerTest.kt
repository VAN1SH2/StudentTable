package ru.sfu.student.updates

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.sfu.student.core.AppUpdate
import ru.sfu.student.core.AppUpdateInterval
import java.io.IOException

class AppUpdateCheckerTest {
    private val release = AppUpdate(1, "ru.sfu.student", 15, "0.7.1", 26,
        "https://github.com/VAN1SH2/StudentTable/releases/download/build-9-1/StudentTable-0.7.1.apk", "a".repeat(64), 12345)
    private class MemoryStore : UpdateCheckStore {
        var record = UpdateCheckRecord()
        override fun read() = record
        override fun write(record: UpdateCheckRecord) { this.record = record }
    }

    @Test fun repeatedEntriesAndRestartWaitFor24HoursAndDismissedOffersStayHidden() = runBlocking {
        val store = MemoryStore()
        var now = 100L
        var calls = 0
        fun checker() = AppUpdateChecker(store, { calls++; release }, 14, 35) { now }
        val first = checker()
        assertEquals(UpdateCheckResult.Available(release), first.check())
        first.markPrompted(15)
        now += AppUpdateInterval.MILLIS - 1
        assertEquals(UpdateCheckResult.Skipped, checker().check())
        assertEquals(1, calls)
        now++
        assertEquals(UpdateCheckResult.Available(release), checker().check())
        assertEquals(2, calls)
    }

    @Test fun offerNotYetShownSurvivesRestartWithoutAnotherRequest() = runBlocking {
        val store = MemoryStore()
        var calls = 0
        fun checker() = AppUpdateChecker(store, { calls++; release }, 14, 35) { 100L }
        assertEquals(UpdateCheckResult.Available(release), checker().check())
        val restarted = checker()
        assertEquals(UpdateCheckResult.Available(release), restarted.check())
        restarted.markPrompted(15)
        assertEquals(UpdateCheckResult.Skipped, checker().check())
        assertEquals(1, calls)
    }

    @Test fun offlineAttemptsAreThrottledAndRetryAfter24Hours() = runBlocking {
        val store = MemoryStore()
        var now = 100L
        var calls = 0
        val checker = AppUpdateChecker(store, { calls++; throw IOException("Offline") }, 14, 35) { now }
        try { checker.check(); fail("Expected network failure") } catch (_: IOException) { }
        assertEquals(UpdateCheckResult.Skipped, checker.check())
        assertEquals(1, calls)
        now += AppUpdateInterval.MILLIS
        try { checker.check(); fail("Expected network failure") } catch (_: IOException) { }
        assertEquals(2, calls)
    }

    @Test fun equalOlderAndIncompatibleReleasesAreNotOffered() = runBlocking {
        listOf(release.copy(versionCode = 14), release.copy(versionCode = 13), release.copy(minSdk = 36)).forEach { latest ->
            val checker = AppUpdateChecker(MemoryStore(), { latest }, 14, 35) { 100L }
            assertEquals(UpdateCheckResult.Current, checker.check())
            assertEquals(UpdateCheckResult.Skipped, checker.check())
        }
    }

    @Test fun manualChecksBypassTheDailyLimit() = runBlocking {
        var calls = 0
        val checker = AppUpdateChecker(MemoryStore(), { calls++; release }, 14, 35) { 100L }
        checker.check(); checker.markPrompted(15)
        assertEquals(UpdateCheckResult.Available(release), checker.check(force = true))
        assertEquals(2, calls)
    }

    @Test fun simultaneousEntriesShareOneNetworkRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val checker = AppUpdateChecker(MemoryStore(), { calls++; entered.complete(Unit); finish.await(); release }, 14, 35) { 100L }
        val first = async { checker.check() }
        entered.await()
        val second = async { checker.check() }
        yield(); finish.complete(Unit)
        assertEquals(UpdateCheckResult.Available(release), first.await())
        assertEquals(UpdateCheckResult.Available(release), second.await())
        assertEquals(1, calls)
    }

    @Test fun cancellingACheckPreservesTheAttemptAndPropagatesCancellation() = runBlocking {
        val store = MemoryStore()
        val entered = CompletableDeferred<Unit>()
        val checker = AppUpdateChecker(store, { entered.complete(Unit); awaitCancellation() }, 14, 35) { 100L }
        val job = launch { checker.check() }
        entered.await(); job.cancelAndJoin()
        assertEquals(100L, store.record.lastAttempt)
        assertEquals(UpdateCheckResult.Skipped, checker.check())
    }
}
