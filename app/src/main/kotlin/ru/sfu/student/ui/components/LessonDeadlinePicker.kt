package ru.sfu.student.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.sfu.student.R
import ru.sfu.student.core.LessonOccurrence
import java.time.format.DateTimeFormatter

@Composable fun LessonDeadlinePicker(choices: List<LessonOccurrence>, selectedKey: String?,
    nearestKey: String?, onSelect: (LessonOccurrence) -> Unit, onManualDate: () -> Unit) {
    val scroll = rememberScrollState()
    val cardWidth = 220.dp
    val stride = with(LocalDensity.current) { (cardWidth + 10.dp).roundToPx() }
    LaunchedEffect(selectedKey, choices.map { it.key }, stride) {
        val index = choices.indexOfFirst { it.key == selectedKey }
        if (index >= 0) scroll.animateScrollTo(index * stride, animationSpec = tween(180))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("К какой паре", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (choices.isEmpty()) {
            Text("В ближайший месяц занятий этого предмета нет. Выберите срок вручную.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.forEach { occurrence -> key(occurrence.key) {
                val selected = occurrence.key == selectedKey
                Surface(onClick = { onSelect(occurrence) }, modifier = Modifier.width(cardWidth).heightIn(min = 126.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(occurrence.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Russian))
                                .replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (selected) Icon(painterResource(R.drawable.ic_check), "Выбрано",
                                modifier = Modifier.padding(start = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("${occurrence.lesson.startTime} — ${occurrence.lesson.endTime}",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Text(typeLabel(occurrence.lesson.type).ifBlank { "Занятие" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (occurrence.key == nearestKey) Text("Ближайшая пара", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            } }
        }
        TextButton(onClick = onManualDate, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Выбрать дату вручную")
        }
    }
}
