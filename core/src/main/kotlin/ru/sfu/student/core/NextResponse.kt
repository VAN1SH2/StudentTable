package ru.sfu.student.core

import com.google.gson.*

/** Decode data, never execute scripts. Also accepts a raw React Server Components response. */
object NextResponse {
    private val header = Regex("self\\.__next_f\\.push\\(\\[\\s*1\\s*,\\s*\"")
    fun content(html: String): String {
        require(html.length <= 12_000_000) { "Ответ СФУ слишком большой" }
        val frames = header.findAll(html).map { match ->
            val start = match.range.last
            var escaped = false
            var end = -1
            for (i in start + 1 until html.length) {
                if (escaped) escaped = false
                else if (html[i] == '\\') escaped = true
                else if (html[i] == '"') { end = i; break }
            }
            check(end >= 0) { "Неполный блок Next.js" }
            JsonParser.parseString(html.substring(start, end + 1)).asString
        }.toList()
        return if (frames.isEmpty()) html else frames.joinToString("")
    }
    fun arrays(content: String, property: String): List<JsonArray> =
        Regex("\"${Regex.escape(property)}\"\\s*:\\s*\\[").findAll(content).map { match ->
            JsonParser.parseString(balanced(content, match.range.last, '[', ']')).asJsonArray
        }.toList()

    fun balanced(text: String, start: Int, open: Char, close: Char): String {
        var depth = 0; var quoted = false; var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                open -> depth++
                close -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        error("Неполный ответ СФУ")
    }

    /** Find matching records in one linear pass, regardless of property order. */
    fun objects(content: String, key: String): List<JsonObject> {
        val stack = ArrayDeque<Int>(); val spans = mutableListOf<Pair<Int, Int>>()
        var quoted = false; var escaped = false
        for (i in content.indices) {
            val c = content[i]
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> stack.addLast(i)
                '}' -> if (stack.isNotEmpty()) spans += stack.removeLast() to i
            }
        }
        return spans.asSequence().map { content.substring(it.first, it.second + 1) }
            .filter { it.contains("\"$key\"") }
            .mapNotNull { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
            .filter { it.has(key) }.toList()
    }
}

class ScheduleUnavailableException : IllegalStateException("СФУ не нашёл расписание этой группы. Проверьте её через поиск групп")

class SfuGroupParser {
    fun parse(response: String): List<StudyGroup> = NextResponse.objects(NextResponse.content(response), "groupId")
        .filter { it.has("groupName") }.map { item ->
            StudyGroup(item.get("groupId").asLong, item.get("groupName").asString,
                item.get("course")?.takeUnless { it.isJsonNull }?.asInt ?: 0,
                item.get("educationLevel")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                item.get("isSession")?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
                university = University.SFU)
        }.also { items -> require(items.all { it.groupId > 0 && it.groupName.isNotBlank() }) { "Некорректные группы СФУ" } }
        .distinctBy { it.groupId }
}
