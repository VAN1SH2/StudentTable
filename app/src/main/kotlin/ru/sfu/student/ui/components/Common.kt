package ru.sfu.student.ui.components

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.sfu.student.core.ScheduleCycle
import ru.sfu.student.core.University
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val Russian: Locale = Locale.forLanguageTag("ru")
val DayNames = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
fun minuteWord(minutes: Long): String = when {
    minutes % 100 in 11L..14L -> "минут"
    minutes % 10 == 1L -> "минуту"
    minutes % 10 in 2L..4L -> "минуты"
    else -> "минут"
}
fun displayTime(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ScheduleCycle.zone).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Russian))
fun updatedLabel(epoch: Long, today: LocalDate): String {
    if (epoch == 0L) return "Расписание ещё не загружено"
    val date = Instant.ofEpochMilli(epoch).atZone(ScheduleCycle.zone)
    val whenText = when (date.toLocalDate()) { today -> "сегодня"; today.minusDays(1) -> "вчера"; else -> date.format(DateTimeFormatter.ofPattern("d MMM", Russian)) }
    return "Обновлено $whenText в ${date.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}
fun dueLabel(epoch: Long, now: Instant): String {
    val date = Instant.ofEpochMilli(epoch).atZone(ScheduleCycle.zone)
    val label = "до ${date.format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", Russian))}"
    return if (epoch < now.toEpochMilli()) "Просрочено · $label" else label
}
fun weekLabel(week: Int, university: University?): String =
    if (university == University.IRNITU) "Неделя $week · ${if (week == 1) "нечётная" else "чётная"}" else "Неделя $week"
fun typeLabel(type: String) = when (type.lowercase()) {
    "практика" -> "Практическое занятие"
    "пр. занятие" -> "Практическое занятие"; "лаб. работа" -> "Лабораторная работа"; "лекция" -> "Лекция"; else -> type.replaceFirstChar { it.uppercase() }
}
fun openCourse(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme !in listOf("http", "https")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
        Toast.makeText(context, "Не найдено приложение для открытия ссылки", Toast.LENGTH_SHORT).show()
    }
}
@Composable fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}
@Composable fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content()
    } }
}
