package ru.sfu.student.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import ru.sfu.student.R
import ru.sfu.student.core.ScheduleCycle
import ru.sfu.student.core.Lesson
import ru.sfu.student.data.StudentTask
import ru.sfu.student.data.displayName
import ru.sfu.student.ui.screens.*
import ru.sfu.student.ui.components.AppUpdateDialog
import java.time.LocalDate

@Composable fun StudentShell(requestedScreen: Int, navigationRequest: Int, model: StudentViewModel = viewModel()) {
    var screen by rememberSaveable { mutableIntStateOf(requestedScreen.coerceIn(0, 3)) }
    var full by rememberSaveable { mutableStateOf(false) }
    var groups by rememberSaveable { mutableStateOf(false) }
    var editedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftGroupId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftLessonId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftLessonDate by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var customEditor by rememberSaveable { mutableStateOf(false) }
    var customId by rememberSaveable { mutableStateOf<Long?>(null) }
    var customGroupId by rememberSaveable { mutableStateOf<Long?>(null) }
    var customDate by rememberSaveable { mutableStateOf<String?>(null) }
    var customOnlyDate by rememberSaveable { mutableStateOf<String?>(null) }
    var manageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var manageDate by rememberSaveable { mutableStateOf<String?>(null) }
    var manageDelete by rememberSaveable { mutableStateOf(false) }
    val state by model.state.collectAsStateWithLifecycle()
    val importing by model.importing.collectAsStateWithLifecycle()
    val appUpdate by model.appUpdate.collectAsStateWithLifecycle()
    val updateDownload by model.updateDownload.collectAsStateWithLifecycle()
    val savingCustom by model.savingCustomLesson.collectAsStateWithLifecycle()
    val now by model.clock.collectAsStateWithLifecycle()
    var calendarDate by rememberSaveable { mutableStateOf(now.atZone(ScheduleCycle.zone).toLocalDate().toString()) }
    val day by model.selectedDay.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        model.onForeground()
        try { while (isActive) { model.refreshClock(); delay(1_000) } }
        finally { model.onBackground() }
    } }
    LaunchedEffect(state.ready, state.activeGroup?.groupId, importing) { model.autoRefreshSchedule() }
    LaunchedEffect(navigationRequest) { if (navigationRequest > 0) { screen = requestedScreen.coerceIn(0, 3); full = false } }
    LaunchedEffect(model) { model.events.collect { event -> when (event) {
        is UiEvent.Message -> snackbar.showSnackbar(event.text)
        is UiEvent.TaskDeleted -> if (snackbar.showSnackbar("Задание удалено", "Отменить", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) model.restoreTask(event.task)
    } } }
    BackHandler(full) { full = false }
    fun openCustom(id: Long?, groupId: Long, date: LocalDate, only: Boolean) {
        customId = id; customGroupId = groupId; customDate = date.toString()
        customOnlyDate = date.toString().takeIf { only }; customEditor = true
    }
    val onManageLesson: (Lesson, LocalDate, Boolean) -> Unit = { lesson, date, deleting ->
        state.customLessons.firstOrNull { it.id == -lesson.id && it.groupId == state.activeGroup?.groupId }?.let { own ->
            if (own.data.repeat == 0 && !deleting) openCustom(own.id, own.groupId, date, false)
            else { manageId = own.id; manageDate = date.toString(); manageDelete = deleting }
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            // Keep existing screen IDs: reminder notification intents still open tasks at 1.
            val entries = listOf(Triple("Расписание", R.drawable.ic_schedule, 0), Triple("Календарь", R.drawable.ic_calendar, 3),
                Triple("Задачи", R.drawable.ic_check, 1), Triple("Настройки", R.drawable.ic_settings, 2))
            entries.forEach { (label, icon, index) -> NavigationBarItem(screen == index, { screen = index; full = false },
                icon = { Icon(painterResource(icon), null) }, label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer)) }
        }
    }, floatingActionButton = {
        if (screen == 1) ExtendedFloatingActionButton(onClick = { editedId = null; draftGroupId = null; draftLessonId = null; draftLessonDate = null; editor = true }, containerColor = MaterialTheme.colorScheme.primary) { Text("+ Задача", color = MaterialTheme.colorScheme.onPrimary) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (screen != 0) Text(when (screen) { 1 -> "Задачи"; 3 -> "Календарь"; else -> "Настройки" },
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            val onTask: (StudentTask) -> Unit = { editedId = it.id; draftGroupId = null; draftLessonId = null; draftLessonDate = null; editor = true }
            val onLesson: (Lesson) -> Unit = { lesson ->
                state.activeGroup?.let { group ->
                    editedId = null; draftGroupId = group.groupId; draftLessonId = lesson.id; draftLessonDate = null; editor = true
                }
            }
            when {
                screen == 0 && full -> FullScheduleScreen(state, now, { full = false }, onLesson, onManageLesson)
                screen == 0 -> ScheduleScreen(state, now, day, importing, model::selectDay, { groups = true }, { model.importSchedule() }, { full = true }, onTask, onLesson, onManageLesson)
                screen == 1 -> TasksScreen(state, now, onTask, { model.saveTask(it.copy(done = !it.done)) }, model::deleteTask, { groups = true })
                screen == 3 -> CalendarScreen(state, now, LocalDate.parse(calendarDate), importing, { calendarDate = it.toString() },
                    { groups = true }, { model.importSchedule() }, onTask, { model.saveTask(it.copy(done = !it.done)) },
                    { lesson, date -> onLesson(lesson); draftLessonDate = date.toString() },
                    { date -> state.activeGroup?.let { openCustom(null, it.groupId, date, false) } }, onManageLesson)
                else -> SettingsScreen(state, now, importing, model, { groups = true })
            }
        }
    }
    if (groups) GroupsSheet(state, model, { groups = false })
    if (editor && state.ready) key(editedId, draftGroupId, draftLessonId, draftLessonDate) {
        TaskEditor(state.tasks.firstOrNull { it.id == editedId }, state, { editor = false },
            { model.saveTask(it); editor = false }, { model.deleteTask(it); editor = false }, zone = ScheduleCycle.zone,
            initialGroupId = draftGroupId,
            initialLesson = state.lessonsForGroup(draftGroupId).firstOrNull { it.id == draftLessonId },
            initialLessonDate = draftLessonDate?.let(LocalDate::parse),
            now = now)
    }
    if (customEditor && state.ready) state.groups.firstOrNull { it.groupId == customGroupId }?.let { group ->
        key(customId, customGroupId, customDate, customOnlyDate) {
            val own = state.customLessons.firstOrNull { it.id == customId }
            CustomLessonEditor(own, group,
                LocalDate.parse(customDate), customOnlyDate != null || own?.parentId != null, state, savingCustom, { customEditor = false },
                { model.saveCustomLesson(it, customOnlyDate?.let(LocalDate::parse)) { customEditor = false } })
        }
    }
    state.customLessons.firstOrNull { it.id == manageId }?.let { own ->
        val date = LocalDate.parse(manageDate)
        fun choose(only: Boolean) {
            if (manageDelete) model.deleteCustomLesson(own.id, date.takeIf { only })
            else openCustom(own.id, own.groupId, date, only)
            manageId = null
        }
        AlertDialog(onDismissRequest = { manageId = null },
            title = { Text(if (manageDelete) "Удалить свою пару?" else "Что изменить?") },
            text = { Text(own.data.subject + "\n" + if (manageDelete) "Задания и установленные сроки сохранятся." else "Выберите одно занятие или весь повторяющийся ряд.") },
            confirmButton = { Column {
                if (own.data.repeat != 0) {
                    TextButton(onClick = { choose(true) }) { Text("Только это занятие") }
                    TextButton(onClick = { choose(false) }) { Text("Весь ряд") }
                } else TextButton(onClick = { choose(false) }) { Text("Удалить") }
            } },
            dismissButton = { TextButton(onClick = { manageId = null }) { Text("Отмена") } })
    }
    if (state.ready && !editor && !groups && !customEditor && manageId == null) appUpdate?.let { release ->
        AppUpdateDialog(release, updateDownload, model::updatePromptShown, model::downloadAppUpdate,
            model::dismissAppUpdate, model::updateInstallError)
    }
}
