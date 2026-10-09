package ru.sfu.student.updates

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import androidx.core.content.FileProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.sfu.student.core.AppUpdate
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AppUpdateStorageAndDownloadTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val bytes = "An APK fixture".toByteArray()
    private val release = AppUpdate(1, "ru.sfu.student", 15, "0.7.1", 26,
        "https://github.com/VAN1SH2/StudentTable/releases/download/build-9-1/StudentTable-0.7.1.apk",
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size.toLong())
    @Before fun clear() {
        context.getSharedPreferences("app_updates", 0).edit().clear().commit()
        File(context.cacheDir, "updates").deleteRecursively()
    }
    private fun client(body: ByteArray = bytes) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody()).build()
    }.build()

    @Test fun checkAndPromptTimesAndPendingReleasePersistAcrossStoreRecreation() {
        val record = UpdateCheckRecord(100, release, 100)
        PreferencesUpdateCheckStore(context).write(record)
        assertEquals(record, PreferencesUpdateCheckStore(context).read())
        PreferencesUpdateCheckStore(context).write(UpdateCheckRecord(200))
        assertEquals(UpdateCheckRecord(200), PreferencesUpdateCheckStore(context).read())
    }

    @Test fun downloadedBytesAreVerifiedBeforeHandingTheFileToTheInstallerAndCanBeReused() = runBlocking {
        var archiveChecks = 0
        val downloader = AppUpdateDownloader(context, client()) { file, metadata ->
            assertArrayEquals(bytes, file.readBytes()); assertEquals(release, metadata); archiveChecks++
        }
        val file = downloader.download(release) { }
        assertTrue(file.isFile)
        assertEquals(file, downloader.download(release) { })
        assertEquals(2, archiveChecks)
        assertFalse(File(file.parentFile, "download.part.apk").exists())
    }

    @Test fun corruptOrTruncatedDownloadsAreNeverHandedToTheInstaller() = runBlocking {
        listOf(bytes.reversedArray(), bytes.copyOf(bytes.size - 1)).forEach { body ->
            val downloader = AppUpdateDownloader(context, client(body)) { _, _ -> fail("Invalid APK was accepted") }
            assertTrue(runCatching { downloader.download(release) { } }.isFailure)
            assertTrue(File(context.cacheDir, "updates").listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun rejectedArchiveIsDeletedInsteadOfBeingInstalled() = runBlocking {
        val downloader = AppUpdateDownloader(context, client()) { _, _ -> error("Wrong signing certificate") }
        assertTrue(runCatching { downloader.download(release) { } }.isFailure)
        assertTrue(File(context.cacheDir, "updates").listFiles().orEmpty().isEmpty())
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(code: Int, certificate: String = "aabb") = PackageInfo().apply {
        packageName = context.packageName; versionCode = code; versionName = release.versionName
        signingInfo = SigningInfo().also { shadowOf(it).setSignatures(arrayOf(Signature(certificate))) }
    }

    @Test fun installerValidationRequiresTheSameSigningCertificateAsTheInstalledApp() {
        val manager = shadowOf(context.packageManager)
        manager.installPackage(packageInfo(14))
        val file = File(context.cacheDir, "fixture.apk")
        manager.setPackageArchiveInfo(file.absolutePath, packageInfo(15))
        verifyUpdateArchive(context, file, release)
        manager.setPackageArchiveInfo(file.absolutePath, packageInfo(15, "ccdd"))
        val failure = runCatching { verifyUpdateArchive(context, file, release) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("Подпись"))
    }

    @Test fun installerValidationRejectsAnotherAppAndWrongVersionMetadata() {
        val manager = shadowOf(context.packageManager)
        manager.installPackage(packageInfo(14))
        val file = File(context.cacheDir, "fixture.apk")
        listOf(packageInfo(15).apply { packageName = "another.app" }, packageInfo(14),
            packageInfo(15).apply { versionName = "0.7.2" }).forEach { archive ->
            manager.setPackageArchiveInfo(file.absolutePath, archive)
            assertTrue(runCatching { verifyUpdateArchive(context, file, release) }.isFailure)
        }
    }

    @Test fun fileProviderSharesOnlyTheUpdateCacheDirectory() {
        val authority = "${context.packageName}.updates"
        val allowed = File(context.cacheDir, "updates/StudentTable-0.7.1.apk")
        assertEquals("content", FileProvider.getUriForFile(context, authority, allowed).scheme)
        assertTrue(runCatching { FileProvider.getUriForFile(context, authority, File(context.cacheDir, "private-file")) }.isFailure)
    }
}
