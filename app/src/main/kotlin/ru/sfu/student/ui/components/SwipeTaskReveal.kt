package ru.sfu.student.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.sfu.student.R
import kotlin.math.roundToInt

@Composable fun SwipeTaskReveal(open: Boolean, onOpenChange: (Boolean) -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val actionWidth = 72.dp
    val width = with(LocalDensity.current) { actionWidth.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentOpen by rememberUpdatedState(open)
    val changeOpen by rememberUpdatedState(onOpenChange)
    fun settle(revealed: Boolean) {
        settling?.cancel()
        settling = scope.launch {
            animate(offset, if (revealed) -width else 0f, animationSpec = tween(180, easing = FastOutSlowInEasing)) { value, _ -> offset = value }
        }
    }
    LaunchedEffect(open, width) { if (!dragging) settle(open) }
    Box(modifier.clip(RoundedCornerShape(16.dp)).semantics {
        customActions = listOf(CustomAccessibilityAction(if (open) "Закрыть кнопку удаления" else "Показать кнопку удаления") {
            onOpenChange(!open); true
        })
    }.pointerInput(width) {
        detectHorizontalDragGestures(
            onDragStart = { settling?.cancel(); dragging = true },
            onHorizontalDrag = { change, amount -> change.consume(); offset = (offset + amount).coerceIn(-width, 0f) },
            onDragEnd = {
                dragging = false
                val reveal = offset < -width * 0.45f
                if (reveal == currentOpen) settle(reveal) else changeOpen(reveal)
            },
            onDragCancel = { dragging = false; settle(currentOpen) }
        )
    }) {
        if (offset < 0f) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.width(actionWidth).fillMaxHeight()) {
                IconButton(onClick = { onOpenChange(false); onDelete() }, enabled = open && !dragging && offset <= -width * 0.95f,
                    modifier = Modifier.fillMaxSize()) {
                    Icon(painterResource(R.drawable.ic_delete), "Удалить задачу", tint = MaterialTheme.colorScheme.onError)
                }
            }
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }.fillMaxWidth()) { content() }
    }
}
