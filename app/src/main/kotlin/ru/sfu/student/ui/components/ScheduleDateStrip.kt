package ru.sfu.student.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.sfu.student.core.ScheduleDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

@Composable fun ScheduleDateStrip(days: List<ScheduleDay>, selected: Int, today: LocalDate, onSelect: (Int) -> Unit) {
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0))
    LaunchedEffect(selected, days.firstOrNull()?.date) {
        snapshotFlow { scroll.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        val layout = scroll.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == selected }
        val fullyVisible = item != null && item.offset >= layout.viewportStartOffset + layout.beforeContentPadding &&
            item.offset + item.size <= layout.viewportEndOffset - layout.afterContentPadding
        if (!fullyVisible) scroll.animateScrollToItem((selected - 2).coerceAtLeast(0))
    }
    LazyRow(Modifier.fillMaxWidth(), state = scroll, overscrollEffect = null, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(days, key = { _, day -> day.date.toString() }) { index, day ->
            val active = selected == index
            val isToday = day.date == today
            val interaction = remember { MutableInteractionSource() }
            val tileShape = RoundedCornerShape(14.dp)
            val foreground by animateColorAsState(
                if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(180), label = "dayTextColor")
            val background by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = tween(180), label = "dayBackgroundColor")
            Column(Modifier.width(56.dp).selectable(active, interactionSource = interaction, indication = null,
                onClick = { onSelect(index) }, role = Role.Tab).semantics {
                contentDescription = "${day.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Russian))}, пар: ${day.lessons.size}" + if (isToday) ", сегодня" else ""
            }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Surface(modifier = Modifier.fillMaxWidth().clip(tileShape).indication(interaction, ripple()), shape = tileShape,
                    color = background,
                    border = if (isToday && !active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(day.date.dayOfMonth.toString().padStart(2, '0'), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, color = foreground)
                        Text(listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")[day.date.dayOfWeek.value - 1] + if (isToday) " ·" else "",
                            style = MaterialTheme.typography.labelSmall, color = foreground)
                    }
                }
                Surface(shape = CircleShape, color = if (day.lessons.isEmpty()) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.height(20.dp).widthIn(min = 20.dp)) {
                    Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                        Text(day.lessons.size.toString(), style = MaterialTheme.typography.labelSmall,
                            color = if (day.lessons.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
