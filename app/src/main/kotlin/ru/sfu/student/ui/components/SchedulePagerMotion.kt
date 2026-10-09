package ru.sfu.student.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDate
import kotlin.math.abs

class SchedulePagerMotion(val pager: PagerState, private val fade: Animatable<Float, AnimationVector1D>) {
    val opacity: Float get() = fade.value
}

@Composable fun rememberSchedulePagerMotion(selected: Int, windowStart: LocalDate, onSelect: (Int) -> Unit): SchedulePagerMotion {
    val pager = key(windowStart) { rememberPagerState(initialPage = selected, pageCount = { 14 }) }
    val fade = remember(pager) { Animatable(1f) }
    var navigating by remember(pager) { mutableStateOf(false) }
    var appliedSelection by remember(pager) { mutableIntStateOf(selected) }
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(pager, selected) {
        navigating = true
        appliedSelection = selected
        try {
            if (abs(pager.currentPage - selected) > 1) {
                // Jump directly to a distant date instead of composing and scrolling every intermediate day.
                fade.snapTo(0f)
                pager.scrollToPage(selected)
                fade.animateTo(1f, tween(160))
            } else if (pager.currentPage != selected) {
                fade.snapTo(1f)
                pager.animateScrollToPage(selected, animationSpec = tween(200))
            }
        } finally {
            // A cancelled older request must not reveal an intermediate page or unblock the
            // settled-page observer while the newer date is still being requested.
            withContext(NonCancellable) {
                if (latestSelected == selected && appliedSelection == selected) {
                    fade.snapTo(1f)
                    navigating = false
                }
            }
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow {
            if (!navigating && latestSelected == appliedSelection && !pager.isScrollInProgress) pager.settledPage else null
        }
            .filterNotNull().distinctUntilChanged().collect { page ->
                if (page != latestSelected) latestOnSelect(page)
            }
    }
    return remember(pager) { SchedulePagerMotion(pager, fade) }
}
