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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.sfu.student.core.*
import ru.sfu.student.data.*
import ru.sfu.student.ui.*
import ru.sfu.student.ui.components.LessonDeadlinePicker
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private fun reminderLabel(value: Int) = when (value) { -1 -> "Нет"; 0 -> "В срок"; 15 -> "15 минут"; 60 -> "Час"; 1440 -> "День"; 2880 -> "2 дня"; else -> "$value минут" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TaskEditor(task: StudentTask?, state: StudentState, onDismiss: () -> Unit,
    onSave: (StudentTask) -> Unit, onDelete: (StudentTask) -> Unit, zone: ZoneId = ScheduleCycle.zone,
    initialGroupId: Long? = null, initialLesson: Lesson? = null, now: Instant = Instant.now(), initialLessonDate: LocalDate? = null) {
    val context = LocalContext.current
    val fieldShape = RoundedCornerShape(16.dp)
    val seededGroupId = if (task != null) task.groupId else initialGroupId ?: state.activeGroup?.groupId
    val seededSubjectId = if (task != null) task.subjectId else initialLesson?.subjectId
    val seededSubject = if (task != null) task.subject else initialLesson?.subject.orEmpty()
    val initialOccurrence = remember(task?.id, seededGroupId, initialLesson?.id, initialLessonDate, zone) {
        if (task == null && initialLesson != null) state.initialTaskOccurrence(seededGroupId, initialLesson, now, zone, initialLessonDate)
        else null
    }
    var title by rememberSaveable { mutableStateOf(task?.title.orEmpty()) }
    var groupId by rememberSaveable { mutableStateOf(seededGroupId) }
    var subjectId by rememberSaveable { mutableStateOf(seededSubjectId) }
    var subject by rememberSaveable { mutableStateOf(seededSubject) }
    var epoch by rememberSaveable { mutableLongStateOf(task?.dueAt ?: initialOccurrence?.startsAt?.toEpochMilli()
        ?: now.atZone(zone).plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()) }
    var selectedLessonId by rememberSaveable { mutableStateOf(task?.lessonId ?: initialOccurrence?.lesson?.id) }
    var selectedLessonDate by rememberSaveable { mutableStateOf(task?.lessonDate ?: initialOccurrence?.date?.toString()) }
    var selectedBindingKey by rememberSaveable { mutableStateOf(task?.lessonBindingKey ?: initialOccurrence?.lesson?.let(LessonBinding::key)) }
    var reminders by rememberSaveable { mutableStateOf(DeadlineReminders.resolve(task?.reminderMinutes, task?.remindMinutes ?: 1440)) }
    var groupMenu by remember { mutableStateOf(false) }
    var subjectMenu by remember { mutableStateOf(false) }
    val due = Instant.ofEpochMilli(epoch).atZone(zone)
    val selectedGroup = state.groups.firstOrNull { it.groupId == groupId }
    val nowMinute = now.truncatedTo(ChronoUnit.MINUTES)
    val upcoming = remember(state.lessons, selectedGroup, subjectId, subject, nowMinute, zone) {
        state.upcomingLessons(groupId, subjectId, subject, now, zone)
    }
    val picked = remember(state.lessons, groupId, selectedLessonId, selectedLessonDate, selectedBindingKey, zone) {
        state.lessonOccurrence(groupId, selectedLessonId, selectedLessonDate, zone, selectedBindingKey)
    }
    val choices = remember(upcoming, picked) { lessonDeadlineChoices(upcoming, picked) }
    val selectedOccurrenceKey = picked?.key ?: selectedLessonDate?.let { date -> selectedLessonId?.let { id -> "$date:$id" } }
    val subjects = state.lessonsForGroup(groupId).distinctBy { it.subjectId?.toString() ?: it.subject }
        .map { SubjectChoice(it.subjectId, it.subject) }.sortedBy { it.name }.toMutableList()
    if (subject.isNotBlank() && subjects.none { it.name == subject }) subjects += SubjectChoice(subjectId, subject)
    fun chooseSubject(item: SubjectChoice?) {
        val changed = subjectId != item?.id || subject != item?.name.orEmpty()
        subjectId = item?.id; subject = item?.name.orEmpty(); subjectMenu = false
        if (changed) {
            selectedLessonId = null; selectedLessonDate = null; selectedBindingKey = null
            if (task == null && item != null) {
                val next = state.upcomingLessons(groupId, subjectId, subject, now, zone).firstOrNull()
                if (next != null) {
                    epoch = next.startsAt.toEpochMilli(); selectedLessonId = next.lesson.id; selectedLessonDate = next.date.toString()
                    selectedBindingKey = LessonBinding.key(next.lesson)
                }
            }
        }
    }
    fun pickDate() {
        DatePickerDialog(context, { _, year, month, day ->
            epoch = LocalDate.of(year, month + 1, day).atTime(due.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        }, due.year, due.monthValue - 1, due.dayOfMonth).show()
    }
    fun pickTime() {
        TimePickerDialog(context, { _, hour, minute ->
            epoch = due.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli()
        }, due.hour, due.minute, true).show()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (task == null) "Новая задача" else "Редактировать задачу") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it.take(200) }, label = { Text("Что сделать *") }, singleLine = true,
                shape = fieldShape, modifier = Modifier.fillMaxWidth())
            ExposedDropdownMenuBox(groupMenu, { groupMenu = !groupMenu }) {
                OutlinedTextField(state.groups.firstOrNull { it.groupId == groupId }?.displayName() ?: "Без группы", {}, readOnly = true,
                    label = { Text("Группа") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupMenu) },
                    shape = fieldShape,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                ExposedDropdownMenu(groupMenu, { groupMenu = false }) {
                    DropdownMenuItem(text = { Text("Без группы") }, onClick = {
                        groupId = null; subject = ""; subjectId = null; selectedLessonId = null; selectedLessonDate = null; selectedBindingKey = null; groupMenu = false
                    })
                    state.groups.forEach { group -> DropdownMenuItem(text = { Text(group.displayName()) }, onClick = {
                        if (groupId != group.groupId) { subject = ""; subjectId = null; selectedLessonId = null; selectedLessonDate = null; selectedBindingKey = null }
                        groupId = group.groupId; groupMenu = false
                    }) }
                }
            }
            ExposedDropdownMenuBox(subjectMenu, { subjectMenu = !subjectMenu }) {
                OutlinedTextField(subject.ifBlank { "Без предмета" }, {}, readOnly = true, label = { Text("Предмет") },
                    shape = fieldShape,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjectMenu) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                ExposedDropdownMenu(subjectMenu, { subjectMenu = false }) {
                    DropdownMenuItem(text = { Text("Без предмета") }, onClick = { chooseSubject(null) })
                    subjects.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { chooseSubject(item) }) }
                }
            }
            if (subjects.isEmpty()) Text("Предметы появятся после загрузки расписания группы.", style = MaterialTheme.typography.bodySmall)
            if (subject.isNotBlank() && selectedGroup != null) {
                if (!selectedGroup.hasConfirmedWeek(state.settings)) Text("Проверьте номер недели этой группы в настройках.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LessonDeadlinePicker(choices, selectedOccurrenceKey, upcoming.firstOrNull()?.key, { occurrence ->
                    selectedLessonId = occurrence.lesson.id; selectedLessonDate = occurrence.date.toString()
                    selectedBindingKey = LessonBinding.key(occurrence.lesson)
                    epoch = occurrence.startsAt.toEpochMilli()
                }, { pickDate() })
                if (selectedOccurrenceKey != null) {
                    if (picked == null) Text("Выбранная пара на $selectedLessonDate отсутствует в текущем расписании. Можно выбрать другую.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Задание появится только у выбранной пары. Срок можно изменить ниже.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { selectedLessonId = null; selectedLessonDate = null; selectedBindingKey = null }, contentPadding = PaddingValues(0.dp)) {
                        Text("Без привязки к паре")
                    }
                } else Text("Без выбора пары задание относится ко всему предмету.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (picked?.startsAt?.toEpochMilli() == epoch) "Срок — начало выбранной пары" else "Срок · время устройства", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate() }, modifier = Modifier.weight(1f)) { Text(due.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))) }
                OutlinedButton(onClick = { pickTime() }, modifier = Modifier.weight(1f)) { Text(due.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            }
            Text("За сколько напомнить", style = MaterialTheme.typography.labelLarge)
            listOf(listOf(-1, 0, 15), listOf(60, 1440, 2880)).forEach { values -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                values.forEach { value -> FilterChip(selected = if (value < 0) reminders.isEmpty() else value in reminders,
                    onClick = { reminders = DeadlineReminders.toggle(reminders, value) }, modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                    label = { Text(reminderLabel(value), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(), maxLines = 2) }) }
            } }
            if (task != null) TextButton(onClick = { onDelete(task) }) { Text("Удалить задачу", color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
            val hasLesson = selectedGroup != null && subject.isNotBlank() && selectedLessonId != null && selectedLessonDate != null
            onSave(StudentTask(id = task?.id ?: 0, title = title.trim(), subject = subject, notes = task?.notes.orEmpty(),
                dueAt = epoch, remindMinutes = reminders.maxOrNull() ?: -1, done = task?.done ?: false, groupId = groupId, subjectId = subjectId,
                lessonId = if (hasLesson) picked?.lesson?.id ?: selectedLessonId else null, lessonDate = if (hasLesson) selectedLessonDate else null,
                lessonBindingKey = if (hasLesson) picked?.lesson?.let(LessonBinding::key) ?: selectedBindingKey else null,
                reminderMinutes = DeadlineReminders.normalize(reminders)))
        }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
