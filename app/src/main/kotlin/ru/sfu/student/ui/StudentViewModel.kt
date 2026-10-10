package ru.sfu.student.ui

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.sfu.student.StudentApp
import ru.sfu.student.core.*
import ru.sfu.student.data.*
import ru.sfu.student.updates.*
import java.time.Instant
import java.time.LocalDate

data class SubjectChoice(val id: Long?, val name: String)
data class StudentState(val settings: Settings = Settings(), val groups: List<SavedGroup> = emptyList(),
    val lessons: List<StoredLesson> = emptyList(), val tasks: List<StudentTask> = emptyList(), val ready: Boolean = false,
    val customLessons: List<StoredCustomLesson> = emptyList(), val customExclusions: List<CustomLessonExclusion> = emptyList()) {
    val activeGroup get() = groups.firstOrNull { it.groupId == settings.activeGroupId }
    val primaryGroup get() = groups.firstOrNull { it.groupId == settings.primaryGroupId }
    val weekReference get() = activeGroup?.weekReference(settings) ?: WeekReference(settings.anchorMonday, settings.anchorWeek)
    val weekConfirmed get() = activeGroup?.hasConfirmedWeek(settings) ?: false
    val primaryWeekConfirmed get() = primaryGroup?.hasConfirmedWeek(settings) ?: false
    fun lessonsForGroup(groupId: Long?): List<Lesson> {
        return regularLessonsForGroup(groupId) + customForGroup(groupId).map { it.template() }
    }
    fun regularLessonsForGroup(groupId: Long?): List<Lesson> {
        val group = groups.firstOrNull { it.groupId == groupId } ?: return emptyList()
        return lessons.filter { it.groupId == groupId && group.acceptsLesson(it.data) }.map { it.data }
    }
    fun customForGroup(groupId: Long?): List<CustomLesson> {
        val group = groups.firstOrNull { it.groupId == groupId } ?: return emptyList()
        return customLessons.filter { it.groupId == groupId }.map { it.core(customExclusions) }
            .filter { group.acceptsLesson(it.template()) }
    }
    fun lessonsOn(date: LocalDate, groupId: Long? = activeGroup?.groupId): ScheduleDay {
        val group = groups.firstOrNull { it.groupId == groupId }
        val reference = group?.weekReference(settings) ?: weekReference
        return CustomSchedule.day(regularLessonsForGroup(groupId), customForGroup(groupId), date,
            LocalDate.parse(reference.monday), reference.week)
    }
    fun subgroupsForGroup(groupId: Long): List<Int> =
        LessonSubgroups.available(lessons.filter { it.groupId == groupId }.map { it.data } +
            customLessons.filter { it.groupId == groupId }.map { it.core().template() })
    fun hasScheduleForGroup(groupId: Long?): Boolean = lessons.any { it.groupId == groupId } || customLessons.any { it.groupId == groupId }
    val activeLessons get() = lessonsForGroup(activeGroup?.groupId)
    val activeScheduleLoaded get() = hasScheduleForGroup(activeGroup?.groupId)
    val activeTasks get() = tasks.filter { it.groupId == activeGroup?.groupId || it.groupId == null }
    val subjects get() = activeLessons.distinctBy { it.subjectId?.toString() ?: it.subject }
        .map { SubjectChoice(it.subjectId, it.subject) }.sortedBy { it.name }
}
data class SearchState(val loading: Boolean = false, val results: List<StudyGroup> = emptyList(), val error: String? = null)
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class TaskDeleted(val task: StudentTask) : UiEvent
}

class StudentViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as StudentApp
    private val repository = app.repository
    private val initialized = MutableStateFlow(false)
    private val baseState = combine(repository.settings, repository.groups, repository.lessons, repository.tasks, initialized) { settings, groups, lessons, tasks, ready ->
        StudentState(settings, groups, lessons, tasks, ready)
    }
    val state = combine(baseState, repository.customLessons, repository.customExclusions) { base, custom, exclusions ->
        base.copy(customLessons = custom, customExclusions = exclusions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudentState())
    val savingCustomLesson = MutableStateFlow(false)
    val importing = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val searchUniversity = MutableStateFlow(University.SFU)
    val search = MutableStateFlow(SearchState())
    val clock = MutableStateFlow(Instant.now())
    val appUpdate = MutableStateFlow<AppUpdate?>(null)
    val checkingAppUpdate = MutableStateFlow(false)
    val updateDownload = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    private val updateDownloader = AppUpdateDownloader(application)
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    val selectedDay = saved.getStateFlow("selectedDay", clock.value.atZone(ScheduleCycle.zone).dayOfWeek.value - 1)
    private val eventsChannel = kotlinx.coroutines.channels.Channel<UiEvent>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    val events = eventsChannel.receiveAsFlow()
    private var searchJob: Job? = null
    private var foreground = false
    private val autoRefreshAttempts = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            try {
                repository.initialize(); initialized.value = true
                // Recreate persisted reminder jobs once when opening an upgraded app.
                app.reminders.rebuild()
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { eventsChannel.send(UiEvent.Message(friendly(e))) }
        }
        refreshClock()
    }
    fun refreshClock() {
        clock.value = Instant.now()
        val today = clock.value.atZone(ScheduleCycle.zone).toLocalDate()
        if (saved.get<String>("selectionDate") != today.toString()) {
            saved["selectionDate"] = today.toString(); saved["selectedDay"] = today.dayOfWeek.value - 1
        }
    }
    fun selectDay(day: Int) { saved["selectedDay"] = day.coerceIn(0, 13) }
    fun onForeground() {
        foreground = true; autoRefreshAttempts.clear(); refreshClock(); autoRefreshSchedule()
        checkAppUpdate()
    }
    fun onBackground() { foreground = false }
    fun checkAppUpdate(force: Boolean = false) {
        if (updateCheckJob?.isActive == true || updateDownloadJob?.isActive == true) return
        updateCheckJob = viewModelScope.launch {
            checkingAppUpdate.value = true
            try {
                val result = withContext(Dispatchers.IO) { app.updateChecker.check(force) }
                when (result) {
                    is UpdateCheckResult.Available -> appUpdate.value = result.release
                    UpdateCheckResult.Current -> {
                        appUpdate.value = null; updateDownload.value = UpdateDownloadState.Idle
                        if (force) eventsChannel.send(UiEvent.Message("Новых обновлений для этого устройства нет"))
                    }
                    UpdateCheckResult.Skipped -> Unit
                }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { if (force) eventsChannel.send(UiEvent.Message("Не удалось проверить обновление. Проверьте интернет и попробуйте позже.")) }
            finally { checkingAppUpdate.value = false }
        }
    }
    fun updatePromptShown(versionCode: Int) { viewModelScope.launch(Dispatchers.IO) {
        try { app.updateChecker.markPrompted(versionCode) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
    } }
    fun downloadAppUpdate() {
        val release = appUpdate.value ?: return
        if (updateDownloadJob?.isActive == true) return
        updateDownloadJob = viewModelScope.launch {
            updateDownload.value = UpdateDownloadState.Downloading(0, release.sizeBytes)
            try {
                val file = updateDownloader.download(release) { updateDownload.value = UpdateDownloadState.Downloading(it, release.sizeBytes) }
                updateDownload.value = UpdateDownloadState.Ready(file)
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) {
                updateDownload.value = UpdateDownloadState.Failed(if (e is IllegalArgumentException || e is IllegalStateException)
                    e.message ?: "Не удалось проверить APK" else "Не удалось скачать обновление. Проверьте интернет и попробуйте снова.")
            }
        }
    }
    fun dismissAppUpdate() {
        updateDownloader.cancel(); updateDownloadJob?.cancel()
        appUpdate.value = null; updateDownload.value = UpdateDownloadState.Idle
    }
    fun updateInstallError(message: String) { viewModelScope.launch { eventsChannel.send(UiEvent.Message(message)) } }
    override fun onCleared() { updateDownloader.cancel(); super.onCleared() }
    fun autoRefreshSchedule() {
        if (!foreground || !state.value.ready || importing.value) return
        val groupId = state.value.activeGroup?.groupId ?: return
        if (!autoRefreshAttempts.add(groupId)) return
        viewModelScope.launch {
            try {
                val group = repository.dao.group(groupId) ?: return@launch
                if (foreground && state.value.activeGroup?.groupId == groupId &&
                    ScheduleRefresh.isStale(group.lastUpdated, Instant.now().toEpochMilli())) importSchedule(groupId)
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { eventsChannel.send(UiEvent.Message(friendly(e))) }
        }
    }
    fun selectSearchUniversity(university: University) {
        if (searchUniversity.value == university) return
        searchUniversity.value = university
        searchGroups(searchQuery.value)
    }
    fun searchGroups(query: String) {
        val university = searchUniversity.value
        searchQuery.value = query
        searchJob?.cancel()
        search.value = SearchState(loading = query.trim().length >= 2)
        if (query.trim().length < 2) return
        searchJob = viewModelScope.launch {
            delay(350)
            try { search.value = SearchState(results = repository.searchGroups(query, university)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { search.value = SearchState(error = friendly(e)) }
        }
    }
    fun resetSearch() { searchJob?.cancel(); searchQuery.value = ""; search.value = SearchState() }
    fun importSchedule(groupId: Long? = state.value.activeGroup?.groupId) {
        if (importing.value || groupId == null) return
        importing.value = true
        viewModelScope.launch {
            try {
                repository.importSchedule(groupId)
                eventsChannel.send(UiEvent.Message("Расписание обновлено"))
                app.reminders.rebuild()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { eventsChannel.send(UiEvent.Message(friendly(e) + if (state.value.lessons.any { it.groupId == groupId }) ". Сохранённые пары доступны" else "")) }
            finally { importing.value = false }
        }
    }
    private fun change(action: suspend () -> Unit) { viewModelScope.launch {
        try { action(); app.reminders.rebuild() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { eventsChannel.send(UiEvent.Message(friendly(e))) }
    } }
    fun activate(id: Long) = change { repository.activate(id) }
    fun selectSubgroup(groupId: Long, subgroup: Int?) = change { repository.selectSubgroup(groupId, subgroup) }
    fun primary(id: Long) = change { repository.primary(id) }
    fun setWeek(week: Int) {
        val groupId = state.value.activeGroup?.groupId ?: return
        change {
            val today = LocalDate.now(ScheduleCycle.zone)
            repository.setCurrentWeek(groupId, ScheduleCycle.monday(today).toString(), week)
        }
    }
    fun removeGroup(id: Long) = change { repository.removeGroup(id) }
    fun addGroup(group: StudyGroup) { viewModelScope.launch {
        try { repository.addGroup(group); app.reminders.rebuild(); importSchedule(group.groupId) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { eventsChannel.send(UiEvent.Message(friendly(e))) }
    } }
    fun saveTask(task: StudentTask) = change { repository.saveTask(task) }
    fun saveCustomLesson(item: StoredCustomLesson, onlyDate: LocalDate?, onSaved: () -> Unit) {
        if (savingCustomLesson.value) return
        savingCustomLesson.value = true
        viewModelScope.launch {
            try {
                repository.saveCustomLesson(item, onlyDate)
                onSaved()
                app.reminders.rebuild()
                eventsChannel.send(UiEvent.Message("Пара сохранена"))
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { eventsChannel.send(UiEvent.Message(friendly(e))) }
            finally { savingCustomLesson.value = false }
        }
    }
    fun deleteCustomLesson(id: Long, onlyDate: LocalDate?) = change {
        repository.deleteCustomLesson(id, onlyDate)
        eventsChannel.send(UiEvent.Message("Своя пара удалена. Задания сохранены"))
    }
    fun resetCustomSchedule(groupId: Long) = change {
        repository.resetCustomSchedule(groupId)
        eventsChannel.send(UiEvent.Message("Исходное расписание восстановлено"))
    }
    fun deleteTask(task: StudentTask) = change {
        repository.dao.deleteTask(task.id)
        app.reminders.cancelTask(task.id)
        eventsChannel.send(UiEvent.TaskDeleted(task))
    }
    fun restoreTask(task: StudentTask) = change {
        // A removed group cannot be resurrected by Undo; preserve the task without its group.
        val restored = if (task.groupId != null && repository.dao.group(task.groupId) == null)
            task.copy(groupId = null, subjectId = null, lessonId = null, lessonDate = null, lessonBindingKey = null) else task
        repository.saveTask(restored)
    }
    fun settings(transform: (Settings) -> Settings) = change { repository.updateSettings(transform) }
    fun permissionChanged() = change { }
    private fun friendly(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Нет подключения к интернету"
        is java.net.SocketTimeoutException -> "Сайт вуза не ответил вовремя. Попробуйте обновить позже"
        is javax.net.ssl.SSLException -> "Не удалось установить защищённое соединение с сайтом вуза"
        is java.io.IOException -> "Не удалось загрузить расписание. Проверьте подключение"
        else -> e.localizedMessage ?: "Не удалось сохранить изменения"
    }
}
