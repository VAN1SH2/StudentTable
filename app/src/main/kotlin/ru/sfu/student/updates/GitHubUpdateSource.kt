package ru.sfu.student.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.sfu.student.BuildConfig
import ru.sfu.student.core.AppUpdate
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GitHubUpdateSource(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()) {
    suspend fun fetch(): AppUpdate = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(AppUpdate.MANIFEST_URL).header("Accept", "application/json")
            .header("Cache-Control", "no-cache").header("User-Agent", "StudentTable/${BuildConfig.VERSION_NAME}").build()
        client.newCall(request).execute().use { response ->
            currentCoroutineContext().ensureActive()
            if (!response.isSuccessful) throw IOException("GitHub недоступен (HTTP ${response.code})")
            val body = response.body ?: throw IOException("GitHub вернул пустой ответ")
            body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 16_384) { "Сведения об обновлении слишком большие" }
                    output.write(buffer, 0, count)
                }
                AppUpdate.parse(output.toString("UTF-8"))
            }
        }
    }
}
