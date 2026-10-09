package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test

class AppUpdateTest {
    private val release = AppUpdate(1, "ru.sfu.student", 15, "0.7.1", 26,
        "https://github.com/VAN1SH2/StudentTable/releases/download/build-9-1/StudentTable-0.7.1.apk", "a".repeat(64), 12345)

    @Test fun readsPublishedManifestAndComparesBuildNumbersRatherThanNames() {
        assertEquals(release, AppUpdate.parse(release.toJson()))
        assertTrue(release.isCompatibleUpdate(14, 26))
        assertFalse(release.isCompatibleUpdate(15, 35))
        assertFalse(release.isCompatibleUpdate(16, 35))
        assertFalse(release.copy(minSdk = 35).isCompatibleUpdate(14, 34))
    }

    @Test fun checksDailyBoundaryFirstLaunchAndClockChanges() {
        val time = 1_000_000L
        assertTrue(AppUpdateInterval.isDue(null, time))
        assertFalse(AppUpdateInterval.isDue(time, time))
        assertFalse(AppUpdateInterval.isDue(time, time + AppUpdateInterval.MILLIS - 1))
        assertTrue(AppUpdateInterval.isDue(time, time + AppUpdateInterval.MILLIS))
        assertTrue(AppUpdateInterval.isDue(time, time - 1))
        assertFalse(AppUpdateInterval.isDue(0, 1))
    }

    @Test fun rejectsOtherRepositoriesInsecureLinksAndMismatchedApkNames() {
        listOf(
            release.copy(apkUrl = release.apkUrl.replace("https:", "http:")),
            release.copy(apkUrl = release.apkUrl.replace("VAN1SH2", "another-user")),
            release.copy(apkUrl = release.apkUrl.replace("github.com", "github.com.example.org")),
            release.copy(apkUrl = release.apkUrl.replace("github.com", "user@github.com")),
            release.copy(apkUrl = release.apkUrl + "?redirect=example"),
            release.copy(apkUrl = release.apkUrl.replace("0.7.1.apk", "0.7.2.apk")),
            release.copy(applicationId = "another.app")
        ).forEach { assertTrue(it.toString(), runCatching { AppUpdate.parse(it.toJson()) }.isFailure) }
    }

    @Test fun rejectsBrokenUnsupportedAndOversizedMetadata() {
        listOf(release.copy(schemaVersion = 2), release.copy(versionCode = 0), release.copy(minSdk = 0),
            release.copy(sha256 = "bad"), release.copy(sizeBytes = 0), release.copy(sizeBytes = AppUpdate.MAX_APK_BYTES + 1),
            release.copy(versionName = "../bad")).forEach { assertTrue(runCatching { AppUpdate.parse(it.toJson()) }.isFailure) }
        listOf("{}", "not json", release.toJson().replace("\"versionCode\":15", "\"versionCode\":15.5"),
            " ".repeat(16_385)).forEach { assertTrue(runCatching { AppUpdate.parse(it) }.isFailure) }
    }
}
