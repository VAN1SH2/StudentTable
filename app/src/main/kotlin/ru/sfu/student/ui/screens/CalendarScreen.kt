package ru.sfu.student.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.sfu.student.R
import ru.sfu.student.core.*
import ru.sfu.student.data.*
import ru.sfu.student.ui.*
import ru.sfu.student.ui.components.*
import ru.sfu.student.ui.theme.TaskColors
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun CalendarScreen(state: StudentState, now: Instant, selectedDate: LocalDate, importing: Boolean,
    onSelectDate: (LocalDate) -> Unit, onGroups: () -> Unit, onRefresh: () -> Unit,
    onTask: (StudentTask) -> Unit, onDone: (StudentTask) -> Unit, onLesson: (Lesson, LocalDate) -> Unit) {
    val context = LocalContext.current
    val zone = ScheduleCycle.zone
    val today = now.atZone(zone).toLocalDate()
    val group = state.activeGroup
    val month = YearMonth.from(selectedDate)
    val dates = remember(month) { ScheduleCalendar.monthDates(month) }
    val deadlines = remember(state.tasks, group?.groupId, zone) { state.calendarDeadlines(zone) }
    val day = remember(state.lessons, state.groups, state.settings, state.tasks, selectedDate, zone) {
        state.calendarDay(selectedDate, zone)
    }
    fun pickDate() {
        DatePickerDialog(context, { _, year, monthIndex, dayOfMonth ->
            onSelectDate(LocalDate.of(year, monthIndex + 1, dayOfMonth))
        }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onGroups, contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f)) {
                    Text((group?.displayName() ?: "Выбрать группу") + " ▾", modifier = Modifier.fillMaxWidth())
                }
                IconButton(onClick = onRefresh, enabled = !importing && group != null) {
                    if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(painterResource(R.drawable.ic_refresh), "Обновить расписание", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelectDate(selectedDate.minusMonths(1)) }) {
                    Icon(painterResource(R.drawable.ic_back), "Предыдущий месяц")
                }
                TextButton(onClick = { pickDate() }, modifier = Modifier.weight(1f)) {
                    Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Russian)).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { onSelectDate(selectedDate.plusMonths(1)) }) {
                    Icon(painterResource(R.drawable.ic_back), "Следующий месяц", modifier = Modifier.graphicsLayer { rotationZ = 180f })
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { label ->
                    Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dates.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { date ->
                            val tasks = deadlines[date].orEmpty()
                            CalendarDate(date, month, date == selectedDate, date == today, tasks.size,
                                tasks.any { it.dueAt < now.toEpochMilli() }, { onSelectDate(date) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSelectDate(today) }, contentPadding = PaddingValues(0.dp)) { Text("Сегодня") }
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = { pickDate() }, contentPadding = PaddingValues(0.dp)) { Text("Выбрать дату") }
                Spacer(Modifier.weight(1f))
                Surface(Modifier.size(6.dp), shape = CircleShape, color = TaskColors.onPendingContainer) {}
                Text(" Дедлайны", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Russian)).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (group != null) {
                Text(weekLabel(day.schedule.week, group.university), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                if (!state.weekConfirmed) Text("Укажите текущую неделю в настройках", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Пары · ${day.schedule.lessons.size}", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
        }
        when {
            !state.ready -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            group == null -> item { EmptyState("Добавьте свою группу", "Выберите вуз и группу, чтобы видеть расписание в календаре.") {
                TextButton(onClick = onGroups) { Text("Мои группы") }
            } }
            !state.activeScheduleLoaded -> item { EmptyState("Расписание пока не загружено", "После загрузки оно будет доступно на любую дату без интернета.") {
                TextButton(onClick = onRefresh, enabled = !importing) { Text("Загрузить расписание") }
            } }
            day.schedule.lessons.isEmpty() -> item { Text("В этот день пар нет", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (state.ready && group != null) items(day.schedule.lessons, key = { "lesson-${it.id}" }) { lesson ->
            val remaining = if (selectedDate == today) ScheduleCycle.remainingMinutes(lesson, selectedDate, now, zone) else null
            LessonCard(lesson, tasksForLesson(lesson, group.groupId, state.tasks, selectedDate), now,
                status = remaining?.let { "До конца пары — $it мин" }, onTask = onTask, onCreateTask = { onLesson(lesson, selectedDate) })
        }
        item { Text("Дедлайны · ${day.deadlines.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (state.ready && day.deadlines.isEmpty()) item { Text("На этот день дедлайнов нет", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(day.deadlines, key = { "task-${it.id}" }) { task ->
            CalendarDeadline(task, now, zone, { onTask(task) }, { onDone(task) })
        }
    }
}

@Composable private fun CalendarDate(date: LocalDate, month: YearMonth, isSelected: Boolean, isToday: Boolean,
    deadlines: Int, overdue: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        YearMonth.from(date) != month -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(onClick = onClick, modifier = modifier.height(48.dp).semantics(mergeDescendants = true) {
        contentDescription = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Russian)) +
            (if (isToday) ", сегодня" else "") + (if (deadlines > 0) ", дедлайнов: $deadlines" else "")
        selected = isSelected
    }, shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (isToday && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, color = textColor,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal)
            Spacer(Modifier.height(4.dp))
            if (deadlines > 0) Surface(Modifier.size(5.dp), shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (overdue) MaterialTheme.colorScheme.error else TaskColors.onPendingContainer) {}
            else Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable private fun CalendarDeadline(task: StudentTask, now: Instant, zone: ZoneId, onEdit: () -> Unit, onDone: () -> Unit) {
    val overdue = task.dueAt < now.toEpochMilli()
    OutlinedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = if (overdue) MaterialTheme.colorScheme.surface else TaskColors.pendingContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(false, { onDone() })
            Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(task.subject.ifBlank { "Без предмета" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val time = Instant.ofEpochMilli(task.dueAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
                Text((if (overdue) "Просрочено · " else "") + "до $time", style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else TaskColors.onPendingContainer)
            }
        }
    }
}
