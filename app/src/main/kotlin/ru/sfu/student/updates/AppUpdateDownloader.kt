package ru.sfu.student.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.sfu.student.BuildConfig
import ru.sfu.student.core.AppUpdate
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val bytes: Long, val total: Long) : UpdateDownloadState
    data class Ready(val file: File) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

class AppUpdateDownloader(private val context: Context, private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES).build(),
    private val verifyArchive: (File, AppUpdate) -> Unit = { file, release -> verifyUpdateArchive(context, file, release) }) {
    @Volatile private var activeCall: Call? = null
    fun cancel() { activeCall?.cancel() }

    suspend fun download(release: AppUpdate, onProgress: (Long) -> Unit): File = withContext(Dispatchers.IO) {
        AppUpdate.parse(release.toJson())
        val directory = File(context.cacheDir, "updates")
        check(directory.isDirectory || directory.mkdirs()) { "Не удалось подготовить загрузку обновления" }
        val target = File(directory, "StudentTable-${release.versionName}.apk")
        if (target.isFile && target.length() == release.sizeBytes && sha256(target) == release.sha256) {
            verifyArchive(target, release)
            return@withContext target
        }
        directory.listFiles()?.forEach { it.delete() }
        val partial = File(directory, "download.part.apk")
        val call = client.newCall(Request.Builder().url(release.apkUrl)
            .header("User-Agent", "StudentTable/${BuildConfig.VERSION_NAME}").build())
        activeCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Не удалось скачать APK (HTTP ${response.code})")
                val body = response.body ?: throw IOException("GitHub вернул пустой APK")
                val length = body.contentLength()
                require(length == -1L || length == release.sizeBytes) { "Размер APK не совпадает со сведениями об обновлении" }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                var lastProgress = 0L
                body.byteStream().use { input -> FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(65_536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= release.sizeBytes) { "Размер APK превышает ожидаемый" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val tick = System.nanoTime()
                        if (tick - lastProgress >= 200_000_000) { onProgress(total); lastProgress = tick }
                    }
                    output.fd.sync()
                } }
                require(total == release.sizeBytes && digest.digest().hex() == release.sha256) {
                    "Скачанный APK повреждён. Попробуйте загрузить обновление ещё раз."
                }
            }
            currentCoroutineContext().ensureActive()
            verifyArchive(partial, release)
            check(partial.renameTo(target)) { "Не удалось сохранить APK" }
            onProgress(release.sizeBytes)
            target
        } finally {
            call.cancel()
            activeCall = null
            partial.delete()
        }
    }
}

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(65_536)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().hex()
}

@Suppress("DEPRECATION")
private fun signatureHashes(info: PackageInfo): Set<String> {
    val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
    return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
}

@Suppress("DEPRECATION")
fun verifyUpdateArchive(context: Context, file: File, release: AppUpdate) {
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val manager = context.packageManager
    val installed = manager.getPackageInfo(context.packageName, flags)
    val archive = manager.getPackageArchiveInfo(file.absolutePath, flags)
        ?: throw IOException("Android не распознал скачанный APK")
    require(archive.packageName == context.packageName && archive.packageName == release.applicationId &&
        PackageInfoCompat.getLongVersionCode(archive) == release.versionCode.toLong() && archive.versionName == release.versionName &&
        PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed)) {
        "APK не соответствует предложенному обновлению"
    }
    val existing = signatureHashes(installed)
    require(existing.isNotEmpty() && existing == signatureHashes(archive)) { "Подпись обновления отличается от установленного приложения" }
}
