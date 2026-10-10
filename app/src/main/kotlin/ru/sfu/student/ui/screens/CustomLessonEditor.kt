package ru.sfu.student.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.sfu.student.core.*
import ru.sfu.student.data.*
import ru.sfu.student.ui.StudentState
import ru.sfu.student.ui.components.Russian
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun CustomLessonEditor(item: StoredCustomLesson?, group: SavedGroup, date: LocalDate,
    onlyOccurrence: Boolean, state: StudentState, saving: Boolean, onDismiss: () -> Unit, onSave: (StoredCustomLesson) -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    val existing = item?.data
    var subject by rememberSaveable { mutableStateOf(existing?.subject.orEmpty()) }
    var subjectId by rememberSaveable { mutableStateOf(existing?.subjectId) }
    var startDate by rememberSaveable { mutableStateOf(if (onlyOccurrence || existing == null) date.toString() else existing.startDate) }
    var start by rememberSaveable { mutableStateOf(existing?.startTime ?: "10:00") }
    var end by rememberSaveable { mutableStateOf(existing?.endTime ?: "11:30") }
    var repeat by rememberSaveable { mutableIntStateOf(if (onlyOccurrence) 0 else existing?.repeat ?: 0) }
    var until by rememberSaveable { mutableStateOf(if (onlyOccurrence) null else existing?.untilDate) }
    var type by rememberSaveable { mutableStateOf(existing?.type.orEmpty()) }
    var teacher by rememberSaveable { mutableStateOf(existing?.teacher.orEmpty()) }
    var building by rememberSaveable { mutableStateOf(existing?.building.orEmpty()) }
    var room by rememberSaveable { mutableStateOf(existing?.room.orEmpty()) }
    var subgroup by rememberSaveable { mutableStateOf(existing?.subgroup?.toString().orEmpty()) }
    var subjectMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val subjects = state.lessonsForGroup(group.groupId).distinctBy { LessonBinding.normalized(it.subject) }.sortedBy { it.subject }
    val repeats = listOf("Только в эту дату", "Первая неделя", "Вторая неделя", "Каждую неделю")
    fun pickDate(value: String, update: (String) -> Unit) {
        val selected = LocalDate.parse(value)
        DatePickerDialog(context, { _, y, m, d -> update(LocalDate.of(y, m + 1, d).toString()) },
            selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
    }
    fun pickTime(value: String, update: (String) -> Unit) {
        val selected = LocalTime.parse(value)
        TimePickerDialog(context, { _, h, m -> update(LocalTime.of(h, m).toString()) }, selected.hour, selected.minute, true).show()
    }
    fun save() {
        val details = CustomLessonDetails(subject.trim(), start, end, startDate, repeat,
            until.takeIf { repeat != 0 }, type.trim(), teacher.trim(), building.trim(), room.trim(), subjectId,
            subgroup.toIntOrNull().takeIf { group.university == University.IRNITU })
        try { details.validate() }
        catch (e: Exception) { error = e.message ?: "Проверьте данные пары"; return }
        onSave(StoredCustomLesson(item?.id ?: 0, group.groupId, details))
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (item == null) "Своя пара" else "Изменить свою пару") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(group.displayName(), style = MaterialTheme.typography.bodySmall)
            ExposedDropdownMenuBox(subjectMenu, { subjectMenu = !subjectMenu }) {
                OutlinedTextField(subject, { name ->
                    subject = name.take(200)
                    subjectId = subjects.firstOrNull { LessonBinding.normalized(it.subject) == LessonBinding.normalized(subject) }?.subjectId
                }, singleLine = true, label = { Text("Предмет *") }, shape = shape,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjectMenu) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth())
                ExposedDropdownMenu(subjectMenu, { subjectMenu = false }) {
                    subjects.filter { subject.isBlank() || it.subject.contains(subject, ignoreCase = true) }.forEach { lesson ->
                        DropdownMenuItem(text = { Text(lesson.subject) }, onClick = { subject = lesson.subject; subjectId = lesson.subjectId; subjectMenu = false })
                    }
                }
            }
            OutlinedButton(onClick = { pickDate(startDate) { startDate = it } }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(LocalDate.parse(startDate).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Russian)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickTime(start) { start = it } }, modifier = Modifier.weight(1f), enabled = !saving) { Text("С $start") }
                OutlinedButton(onClick = { pickTime(end) { end = it } }, modifier = Modifier.weight(1f), enabled = !saving) { Text("До $end") }
            }
            if (!onlyOccurrence) ExposedDropdownMenuBox(repeatMenu, { repeatMenu = !repeatMenu }) {
                OutlinedTextField(repeats[repeat], {}, readOnly = true, label = { Text("Повторение") }, shape = shape,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(repeatMenu) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                ExposedDropdownMenu(repeatMenu, { repeatMenu = false }) {
                    repeats.forEachIndexed { index, label -> DropdownMenuItem(text = { Text(label) }, onClick = { repeat = index; repeatMenu = false }) }
                }
            }
            if (repeat != 0) {
                Text("По ${LocalDate.parse(startDate).format(DateTimeFormatter.ofPattern("EEEE", Russian))}, начиная с выбранной даты.", style = MaterialTheme.typography.bodySmall)
                val reference = group.weekReference(state.settings)
                val first = LocalDate.parse(startDate).let {
                    if (repeat in 1..2 && ScheduleCycle.week(it, LocalDate.parse(reference.monday), reference.week) != repeat) it.plusWeeks(1) else it
                }
                Text("Первое занятие: ${first.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate(until ?: LocalDate.parse(startDate).plusMonths(4).toString()) { until = it } }, enabled = !saving) {
                        Text(until?.let { "До " + LocalDate.parse(it).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) } ?: "Указать окончание")
                    }
                    if (until != null) TextButton(onClick = { until = null }) { Text("Убрать") }
                }
                if (repeat in 1..2 && !group.hasConfirmedWeek(state.settings)) Text("Подтвердите номер недели в настройках группы.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(type, { type = it.take(100) }, label = { Text("Тип занятия") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(teacher, { teacher = it.take(200) }, label = { Text("Преподаватель") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(building, { building = it.take(200) }, label = { Text("Корпус / место") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(room, { room = it.take(500) }, label = { Text("Аудитория / ссылка") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            if (group.university == University.IRNITU) OutlinedTextField(subgroup, { subgroup = it.filter(Char::isDigit).take(2) },
                label = { Text("Подгруппа (пусто — для всех)") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            if (item != null) Text("Установленные сроки заданий сохранятся. Отдельные изменения занятий ряда останутся в силе.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { save() }, enabled = !saving && subject.isNotBlank()) { Text(if (saving) "Сохраняем…" else "Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Отмена") } })
}
