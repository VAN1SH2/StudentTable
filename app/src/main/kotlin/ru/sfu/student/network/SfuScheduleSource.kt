package ru.sfu.student.network

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.sfu.student.core.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class SfuScheduleSource(private val pageLoader: (suspend (String) -> String)? = null) : ScheduleSource {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS).build()
    override suspend fun search(query: String): List<StudyGroup> = withContext(Dispatchers.Default) {
        SfuGroupParser().parse(get(searchUrl(query))).filter { it.groupName.contains(query.trim(), ignoreCase = true) }
    }

    override suspend fun fetch(group: StudyGroup): ScheduleSnapshot = withContext(Dispatchers.Default) {
        var actual = group
        val data = try { NextTimetableParser(group.groupId).parseSchedule(get(scheduleUrl(group.groupId))) }
        catch (missing: ScheduleUnavailableException) {
            // University IDs can change. Resolve only the exact same named group, then persist the new ID.
            val matches = search(group.groupName.substringBefore(" (")).filter { it.groupName.trim() == group.groupName.trim() && !it.isSession }
            check(matches.size == 1 && matches.single().groupId != group.groupId) { missing.message.orEmpty() }
            actual = matches.single()
            NextTimetableParser(actual.groupId).parseSchedule(get(scheduleUrl(actual.groupId)))
        }
        ScheduleSnapshot(actual, data.lessons, data.periods)
    }
    private suspend fun get(url: String): String = pageLoader?.invoke(url) ?: download(url)
    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val call = client.newCall(Request.Builder().url(url).header("Accept", "text/html")
            .header("User-Agent", "StudentTable/0.3.2 (Android; personal timetable)").build())
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { if (it is CancellationException) call.cancel() }
        try {
            call.execute().use { response ->
                check(response.isSuccessful) { "Сайт СФУ недоступен (HTTP ${response.code})" }
                val body = checkNotNull(response.body) { "СФУ вернул пустой ответ" }
                body.byteStream().use { input ->
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val count = input.read(buffer); if (count < 0) break
                        check(output.size() + count <= 12_000_000) { "Ответ СФУ слишком большой" }; output.write(buffer, 0, count) }
                    output.toString("UTF-8")
                }
            }
        } finally { cancellation.dispose() }
    }
    companion object {
        const val BASE = "https://sfu.ru/ru/education/schedule"
        fun scheduleUrl(id: Long) = "$BASE/$id?date=&view=two_weeks&scope=regular"
        fun searchUrl(query: String) = "$BASE?search=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}" 
    }
}
