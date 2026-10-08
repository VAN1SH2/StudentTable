package ru.sfu.student.network

import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.sfu.student.core.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class IrnituScheduleSource(private val pageLoader: (suspend (String, Map<String, String>?) -> String)? = null) : ScheduleSource {
    private val parser = IrnituTimetableParser()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS).build()

    override suspend fun search(query: String): List<StudyGroup> {
        val normalized = query.trim().replace('–', '-').replace('—', '-')
        return parser.search(load("$BASE/poisk", mapOf("zapros" to normalized)), normalized)
    }

    override suspend fun fetch(group: StudyGroup): ScheduleSnapshot = parser.parse(load(scheduleUrl(group), null), group)

    private suspend fun load(url: String, fields: Map<String, String>?): String = pageLoader?.invoke(url, fields) ?: withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "text/html")
            .header("User-Agent", "StudentTable/0.3 (Android; personal timetable)")
        if (fields != null) request.post(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build())
        val call = client.newCall(request.build())
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { if (it is CancellationException) call.cancel() }
        try {
            call.execute().use { response ->
                check(response.isSuccessful) { "Сайт ИРНИТУ недоступен (HTTP ${response.code})" }
                val body = checkNotNull(response.body) { "ИРНИТУ вернул пустой ответ" }
                body.byteStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(output.size() + count <= 12_000_000) { "Ответ ИРНИТУ слишком большой" }
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
            }
        } finally { cancellation.dispose() }
    }

    companion object {
        const val BASE = "https://www.istu.edu/raspisanie"
        fun scheduleUrl(group: StudyGroup): String {
            require(group.university == University.IRNITU)
            return "$BASE/grup/${group.university.remoteGroupId(group.groupId)}/"
        }
    }
}
