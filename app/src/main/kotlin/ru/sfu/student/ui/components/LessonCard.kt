package ru.sfu.student.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.sfu.student.core.Lesson
import ru.sfu.student.data.StudentTask
import ru.sfu.student.ui.theme.TaskColors
import java.time.Instant

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun LessonCard(lesson: Lesson, tasks: List<StudentTask>, now: Instant, status: String? = null,
    onTask: (StudentTask) -> Unit = {}, onCreateTask: () -> Unit = {}) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    Card(
        onClick = onCreateTask, modifier = Modifier.fillMaxWidth(), shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${lesson.startTime} — ${lesson.endTime}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
            Text(lesson.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val details = listOf(typeLabel(lesson.type), lesson.description.trim()).filter { it.isNotBlank() }
            if (details.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                details.forEach { detail ->
                    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            val online = lesson.building.equals("ЭИОС", true) && (lesson.room.startsWith("http://") || lesson.room.startsWith("https://"))
            val location = if (online) "ЭИОС" else listOf(lesson.building, lesson.room.takeUnless { it.startsWith("http") }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (lesson.teacher.isNotBlank() || location.isNotBlank()) FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (lesson.teacher.isNotBlank()) Text(lesson.teacher, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = if (location.isNotBlank()) 12.dp else 0.dp))
                if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            if (online) TextButton(onClick = { openCourse(context, lesson.room) }, contentPadding = PaddingValues(0.dp)) { Text("Открыть курс ↗") }
            if (tasks.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(if (tasks.size == 1) "Задание" else "Задания", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                tasks.forEach { task ->
                    val overdue = task.dueAt < now.toEpochMilli()
                    Surface(onClick = { onTask(task) }, color = if (overdue) MaterialTheme.colorScheme.errorContainer else TaskColors.lessonPendingContainer,
                        border = if (overdue) null else BorderStroke(1.dp, TaskColors.lessonPendingOutline),
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 8.dp, horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text((if (tasks.size > 1) "• " else "") + task.title, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (overdue) MaterialTheme.colorScheme.error else TaskColors.onPendingContainer)
                            Text(dueLabel(task.dueAt, now), style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) MaterialTheme.colorScheme.error else TaskColors.onPendingContainer)
                        }
                    }
                }
            }
        }
    }
}
