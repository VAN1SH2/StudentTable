package ru.sfu.student.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.sfu.student.R
import ru.sfu.student.BuildConfig
import ru.sfu.student.core.*
import ru.sfu.student.data.*

import ru.sfu.student.ui.*
import ru.sfu.student.ui.components.*
import java.time.*

@Composable fun SettingsScreen(state: StudentState, now: Instant, importing: Boolean, model: StudentViewModel, onGroups: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val settings = state.settings
    val checkingUpdate by model.checkingAppUpdate.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var delete by remember { mutableStateOf<SavedGroup?>(null) }
    var resetGroupId by remember { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled(); model.permissionChanged()
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            allowed = NotificationManagerCompat.from(context).areNotificationsEnabled(); model.permissionChanged()
        } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionCard("Мои группы") {
            state.groups.forEach { group ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(group.groupId == settings.primaryGroupId, { model.primary(group.groupId) })
                    Column(Modifier.weight(1f)) {
                        Text(group.displayName(), style = MaterialTheme.typography.bodyMedium)
                        Text(if (group.groupId == settings.primaryGroupId) "Основная группа" else if (group.groupId == settings.activeGroupId) "Активная группа" else "Сохранённая группа", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { delete = group }) { Icon(painterResource(R.drawable.ic_delete), "Удалить группу") }
                }
                if (group.university == University.IRNITU) key(group.groupId) {
                    SubgroupSelector(group.selectedSubgroup, state.subgroupsForGroup(group.groupId),
                        state.hasScheduleForGroup(group.groupId), { model.selectSubgroup(group.groupId, it) })
                }
            }
            TextButton(onClick = onGroups) { Text("Мои группы / добавить группу") }
            Text("Напоминания о парах относятся к основной группе. Переключение активной группы меняет отображаемое расписание.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { SectionCard("Расписание") {
            val today = now.atZone(ScheduleCycle.zone).toLocalDate()
            val week = ScheduleCycle.week(today, LocalDate.parse(state.weekReference.monday), state.weekReference.week)
            Text(state.activeGroup?.displayName() ?: "Выберите группу", style = MaterialTheme.typography.bodySmall)
            Text(if (state.weekConfirmed) weekLabel(week, state.activeGroup?.university) else "Укажите текущую неделю")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..2).forEach { value -> FilterChip(state.weekConfirmed && week == value, {
                    model.setWeek(value)
                }, label = { Text("Неделя $value") }) }
            }
            Text("Неделя автоматически меняется каждый понедельник. Выбор выше исправляет нумерацию групп выбранного вуза.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(updatedLabel(state.activeGroup?.lastUpdated ?: 0, today), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { model.importSchedule() }, enabled = !importing && state.activeGroup != null) { Text(if (importing) "Обновляем…" else "Обновить расписание") }
            val customCount = state.customLessons.count { it.groupId == state.activeGroup?.groupId }
            Text("Своих пар: $customCount. Добавление — на вкладке календаря.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { resetGroupId = state.activeGroup?.groupId }, enabled = state.ready && customCount > 0) {
                Text("Вернуть исходное расписание")
            }
        } }
        item { SectionCard("Уведомления") {
            Text(if (allowed) "Разрешены в Android" else "Выключены в настройках Android", style = MaterialTheme.typography.bodyMedium)
            if (!allowed) {
                TextButton(onClick = { requestPermission() }) { Text("Разрешить уведомления") }
                TextButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Настройки Android") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Дедлайны задач", Modifier.weight(1f)); Switch(settings.deadlineNotifications, { enabled ->
                    model.settings { it.copy(deadlineNotifications = enabled) }; if (enabled) requestPermission()
                })
            }
            Text("Напоминать о паре", style = MaterialTheme.typography.labelLarge)
            listOf(listOf(0, 15), listOf(30, 60)).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { value -> FilterChip(selected = if (value == 0) !settings.lessonNotifications else settings.lessonNotifications && settings.lessonLeadMinutes == value,
                    onClick = { model.settings { it.copy(lessonNotifications = value != 0, lessonLeadMinutes = if (value == 0) it.lessonLeadMinutes else value) }; if (value != 0) requestPermission() },
                    enabled = value == 0 || state.primaryWeekConfirmed, label = { Text(if (value == 0) "Выключено" else "За $value минут") }) }
            } }
            if (!state.primaryWeekConfirmed) Text("Для напоминаний о парах укажите текущую неделю основной группы.", style = MaterialTheme.typography.bodySmall)
            Text("Android может задерживать напоминания при энергосбережении. После принудительной остановки приложения откройте его снова.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { SectionCard("О приложении") {
            Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}")
            TextButton(onClick = { model.checkAppUpdate(force = true) }, enabled = !checkingUpdate) {
                Text(if (checkingUpdate) "Проверяем обновления…" else "Проверить обновления")
            }
            Text("Обновления проверяются при входе в приложение не чаще одного раза в 24 часа.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Источники: официальные сайты СФУ и ИРНИТУ")
            University.entries.forEach { university ->
                TextButton(onClick = { openCourse(context, university.scheduleUrl) }) { Text("Расписание ${university.label} ↗") }
            }
            Text("Расписание, задачи и настройки хранятся локально. Интернет нужен для поиска групп, обновления пар и проверки новых версий приложения.")
            Text("Время и календарь: как на устройстве. Импортируется регулярное расписание; разовые переносы и экзамены проверяйте на сайте.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
    state.groups.firstOrNull { it.groupId == resetGroupId }?.let { group ->
        ScheduleResetDialog(group.displayName(), state.customLessons.count { it.groupId == group.groupId },
            { model.resetCustomSchedule(group.groupId); resetGroupId = null }, { resetGroupId = null })
    }
    delete?.let { group -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Удалить группу?") },
        text = { Text("${group.groupName}\n\nЗадания сохранятся без привязки к группе.") },
        confirmButton = { TextButton(onClick = { model.removeGroup(group.groupId); delete = null }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("Отмена") } }) }
}
