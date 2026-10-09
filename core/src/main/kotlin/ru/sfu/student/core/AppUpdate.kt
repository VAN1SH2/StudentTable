package ru.sfu.student.core

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.URI

data class AppUpdate(val schemaVersion: Int, val applicationId: String, val versionCode: Int,
    val versionName: String, val minSdk: Int, val apkUrl: String, val sha256: String, val sizeBytes: Long) {
    fun isCompatibleUpdate(installedVersion: Int, deviceSdk: Int) = versionCode > installedVersion && minSdk <= deviceSdk
    fun toJson(): String = Gson().toJson(this)

    companion object {
        const val MANIFEST_URL = "https://github.com/VAN1SH2/StudentTable/releases/latest/download/StudentTable-update.json"
        const val MAX_APK_BYTES = 150_000_000L
        fun parse(json: String): AppUpdate {
            require(json.toByteArray(Charsets.UTF_8).size <= 16_384) { "Сведения об обновлении слишком большие" }
            val data = JsonParser.parseString(json).asJsonObject
            fun number(name: String): Long {
                val value = data.getAsJsonPrimitive(name)
                require(value.isNumber && value.asString.matches(Regex("[0-9]+"))) { "Некорректное поле $name" }
                return value.asString.toLong()
            }
            fun text(name: String): String {
                val value = data.getAsJsonPrimitive(name)
                require(value.isString) { "Некорректное поле $name" }
                return value.asString
            }
            val schema = number("schemaVersion")
            val app = text("applicationId")
            val code = number("versionCode")
            val name = text("versionName")
            val sdk = number("minSdk")
            val url = text("apkUrl")
            val digest = text("sha256")
            val size = number("sizeBytes")
            require(schema == 1L && app == "ru.sfu.student") { "Обновление другого приложения" }
            require(code in 1..Int.MAX_VALUE.toLong() && sdk in 26..100) { "Некорректная версия обновления" }
            require(name.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?"))) { "Некорректное имя версии" }
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.rawUserInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.matches(Regex(
                    "/VAN1SH2/StudentTable/releases/download/build-[0-9]+-[0-9]+/StudentTable-${Regex.escape(name)}\\.apk"))) {
                "Некорректная ссылка на обновление"
            }
            require(digest.matches(Regex("[0-9a-f]{64}")) && size in 1..MAX_APK_BYTES) { "Некорректные сведения об APK" }
            return AppUpdate(1, app, code.toInt(), name, sdk.toInt(), url, digest, size)
        }
    }
}

object AppUpdateInterval {
    const val MILLIS = 24 * 60 * 60 * 1000L
    fun isDue(lastAttempt: Long?, now: Long) = lastAttempt == null || now < lastAttempt || now - lastAttempt >= MILLIS
}
