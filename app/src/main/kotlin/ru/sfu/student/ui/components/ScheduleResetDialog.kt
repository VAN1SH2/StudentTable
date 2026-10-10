package ru.sfu.student.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable fun ScheduleResetDialog(groupName: String, count: Int, onReset: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Вернуть исходное расписание?") },
        text = { Text("Все собственные пары и изменения расписания группы «$groupName» будут удалены (сохранённых пар: $count). Останется расписание, загруженное с сайта университета. Задания и их сроки сохранятся без привязки к удалённым парам.") },
        confirmButton = { TextButton(onClick = onReset) { Text("Сбросить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
