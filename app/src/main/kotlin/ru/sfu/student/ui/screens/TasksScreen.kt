package ru.sfu.student.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.sfu.student.data.*
import ru.sfu.student.ui.StudentState
import ru.sfu.student.ui.theme.TaskColors
import ru.sfu.student.ui.components.*
import java.time.Instant

@Composable fun TasksScreen(state: StudentState, now: Instant, onEdit: (StudentTask) -> Unit,
    onDone: (StudentTask) -> Unit, onDelete: (StudentTask) -> Unit, onGroups: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    var allGroups by remember { mutableStateOf(false) }
    var openedTaskId by remember { mutableStateOf<Long?>(null) }
    val visible = if (allGroups) state.tasks else state.activeTasks
    val tasks = visible.filter { it.done == done }
    LaunchedEffect(done, allGroups, state.activeGroup?.groupId) { openedTaskId = null }
    LaunchedEffect(tasks.map { it.id }) { if (tasks.none { it.id == openedTaskId }) openedTaskId = null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onGroups, contentPadding = PaddingValues(0.dp)) { Text((state.activeGroup?.displayName() ?: "Без группы") + " ▾") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!done, { done = false }, label = { Text("В работе · ${visible.count { !it.done }}") })
                FilterChip(done, { done = true }, label = { Text("Готово · ${visible.count { it.done }}") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(allGroups, { allGroups = it }); Text("Задачи всех групп", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (tasks.isEmpty()) item { EmptyState(if (done) "Пока нет выполненных задач" else "Всё под контролем", "Добавьте задачу и выберите предмет и срок.") }
        items(tasks, key = { it.id }) { task ->
            val overdue = !task.done && task.dueAt < now.toEpochMilli()
            val open = openedTaskId == task.id
            SwipeTaskReveal(open, { revealed -> openedTaskId = if (revealed) task.id else openedTaskId.takeUnless { it == task.id } },
                { onDelete(task) }, modifier = Modifier.fillMaxWidth().animateItem(
                    fadeInSpec = tween(160), fadeOutSpec = tween(160), placementSpec = tween(220, easing = FastOutSlowInEasing))) {
                OutlinedCard(onClick = { if (open) openedTaskId = null else onEdit(task) }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp), colors = CardDefaults.outlinedCardColors(
                        containerColor = when { task.done -> MaterialTheme.colorScheme.surfaceContainerHigh
                            overdue -> MaterialTheme.colorScheme.surface; else -> TaskColors.pendingContainer })) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Checkbox(task.done, { openedTaskId = null; onDone(task) })
                        Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Text(task.subject.ifBlank { "Без предмета" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (allGroups || task.groupId == null) Text(state.groups.firstOrNull { it.groupId == task.groupId }?.displayName() ?: "Без группы", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (task.done) displayTime(task.dueAt) else dueLabel(task.dueAt, now), style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) MaterialTheme.colorScheme.error else if (!task.done) TaskColors.onPendingContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (task.notes.isNotBlank()) Text(task.notes, maxLines = 2, style = MaterialTheme.typography.bodySmall,
                                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
