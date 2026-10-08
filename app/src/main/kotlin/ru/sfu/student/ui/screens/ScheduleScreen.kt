package ru.sfu.student.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.sfu.student.R
import ru.sfu.student.core.*
import ru.sfu.student.data.*
import ru.sfu.student.ui.*
import ru.sfu.student.ui.components.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun ScheduleScreen(state: StudentState, now: Instant, selectedDay: Int, importing: Boolean,
    onSelectDay: (Int) -> Unit, onGroups: () -> Unit, onRefresh: () -> Unit, onFull: () -> Unit,
    onTask: (StudentTask) -> Unit, onLesson: (Lesson) -> Unit) {
    val group = state.activeGroup
    val zone = ScheduleCycle.zone
    val today = now.atZone(zone).toLocalDate()
    val reference = state.weekReference
    val days = remember(state.lessons, group, today, reference) {
        ScheduleWindow.days(state.activeLessons, today, LocalDate.parse(reference.monday), reference.week)
    }
    val selected = selectedDay.coerceIn(0, 13)
    val selectedWeek = days[selected].week
    val motion = rememberSchedulePagerMotion(selected, days.first().date, onSelectDay)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = onGroups, contentPadding = PaddingValues(vertical = 4.dp)) {
                Text((group?.displayName()?.replace(" (", " · ")?.removeSuffix(")") ?: "Выбрать группу") + "  ▾",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(weekLabel(selectedWeek, group?.university), style = MaterialTheme.typography.bodyMedium)
                    Text(updatedLabel(group?.lastUpdated ?: 0, today), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onRefresh, enabled = !importing && group != null) {
                    if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(painterResource(R.drawable.ic_refresh), "Обновить расписание", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (!state.weekConfirmed) Text("Укажите текущую неделю в настройках", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ScheduleDateStrip(days, selected, today, onSelectDay)
        HorizontalPager(state = motion.pager, key = { days[it].date.toString() },
            modifier = Modifier.weight(1f).graphicsLayer { alpha = motion.opacity }, verticalAlignment = Alignment.Top) { page ->
            val date = days[page].date
            val daily = days[page].lessons
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Russian)).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                }
                when {
                    !state.ready -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    group == null -> item { EmptyState("Добавьте свою группу", "Выберите вуз, найдите группу и сохраните её.") { TextButton(onClick = onGroups) { Text("Мои группы") } } }
                    !state.activeScheduleLoaded -> item { EmptyState("Расписание пока не загружено", "Обновите расписание кнопкой ↻. После загрузки пары доступны без интернета.") { TextButton(onClick = onRefresh, enabled = !importing) { Text("Загрузить расписание") } } }
                    daily.isEmpty() -> item {
                        val from = if (date == today) now else date.plusDays(1).atStartOfDay(zone).toInstant()
                        val next = state.activeLessons.map { it to ScheduleCycle.nextStart(it, from, LocalDate.parse(reference.monday), reference.week, 0, zone) }.minByOrNull { it.second }
                        EmptyState(if (date == today) "Сегодня пар нет" else "В этот день пар нет", next?.let {
                            "Следующая пара:\n${it.second.atZone(zone).format(DateTimeFormatter.ofPattern("EEEE, HH:mm", Russian)).replaceFirstChar { ch -> ch.uppercase() }}\n${it.first.subject}"
                        } ?: "Отдыхайте или займитесь заданиями.")
                    }
                }
                items(daily, key = { it.id }) { lesson ->
                    val remaining = ScheduleCycle.remainingMinutes(lesson, date, now, zone)
                    val startsIn = ScheduleCycle.startingSoonMinutes(lesson, date, now, zone)
                    val nextId = daily.firstOrNull { date.atTime(LocalTime.parse(it.startTime)).atZone(zone).toInstant().isAfter(now) }?.id
                    val status = when {
                        date != today -> null
                        remaining != null -> "До конца пары — $remaining мин"
                        lesson.id == nextId && startsIn != null -> "Пара начнётся через $startsIn ${minuteWord(startsIn)}"
                        else -> null
                    }
                    LessonCard(lesson, tasksForLesson(lesson, group!!.groupId, state.tasks, date), now, status, onTask,
                        onCreateTask = { onLesson(lesson) })
                }
                item { TextButton(onClick = onFull, enabled = group != null, modifier = Modifier.fillMaxWidth()) { Text("Посмотреть расписание на 2 недели") } }
            }
        }
    }
}

@Composable fun FullScheduleScreen(state: StudentState, now: Instant, onBack: () -> Unit, onTask: (StudentTask) -> Unit,
    onLesson: (Lesson) -> Unit) {
    val zone = ScheduleCycle.zone
    val today = now.atZone(zone).toLocalDate()
    var week by remember(state.activeGroup?.groupId) { mutableIntStateOf(ScheduleCycle.week(today, LocalDate.parse(state.weekReference.monday), state.weekReference.week)) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Назад") }
            Text("Полное расписание", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        state.activeGroup?.let { group -> Text(group.displayName(), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..2).forEach { value -> FilterChip(week == value, { week = value }, label = { Text(if (state.activeGroup?.university == University.IRNITU) "$value · ${if (value == 1) "нечётная" else "чётная"}" else "Неделя $value") }) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!state.activeScheduleLoaded) item { EmptyState("Расписание пока не загружено", "Вернитесь на главный экран и обновите его.") }
            else (1..7).forEach { day ->
                val daily = state.activeLessons.filter { it.week == week && it.dayOfWeek == day }.sortedBy { it.startTime }
                item(key = "day-$day") {
                    Text(DayNames[day - 1], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    if (daily.isEmpty()) Text("Нет пар", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                items(daily, key = { "lesson-${it.id}" }) { lesson ->
                    LessonCard(lesson, tasksForLesson(lesson, state.activeGroup!!.groupId, state.tasks), now, onTask = onTask,
                        onCreateTask = { onLesson(lesson) })
                }
            }
        }
    }
}
