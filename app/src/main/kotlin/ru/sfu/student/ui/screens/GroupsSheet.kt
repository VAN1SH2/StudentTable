package ru.sfu.student.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.sfu.student.R
import ru.sfu.student.core.University
import ru.sfu.student.ui.*
import ru.sfu.student.data.*
import ru.sfu.student.ui.components.SubgroupSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GroupsSheet(state: StudentState, model: StudentViewModel, onClose: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<SavedGroup?>(null) }
    val query by model.searchQuery.collectAsState()
    val search by model.search.collectAsState()
    val university by model.searchUniversity.collectAsState()
    LaunchedEffect(Unit) { model.selectSearchUniversity(state.activeGroup?.university ?: University.SFU) }
    DisposableEffect(Unit) { onDispose { model.resetSearch() } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (adding) "Добавить группу" else "Мои группы", style = MaterialTheme.typography.headlineSmall)
            if (adding) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    University.entries.forEach { value ->
                        FilterChip(university == value, { model.selectSearchUniversity(value) }, label = { Text(value.label) })
                    }
                }
                OutlinedTextField(query, model::searchGroups, label = { Text("Название группы") }, placeholder = { Text(university.groupExample) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Поиск на официальном сайте ${university.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                search.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (query.trim().length >= 2 && !search.loading && search.error == null && search.results.isEmpty()) Text("Группы не найдены")
                LazyColumn(Modifier.weight(1f)) {
                    items(search.results, key = { it.groupId }) { group ->
                        val saved = state.groups.any { it.groupId == group.groupId }
                        ListItem(headlineContent = { Text(group.groupName) }, supportingContent = {
                            Text(if (saved) "${group.university.label} · уже сохранена" else group.university.label + if (group.course > 0) " · ${group.course} курс" else "")
                        }, modifier = Modifier.clickable { if (saved) model.activate(group.groupId) else model.addGroup(group); onClose() })
                    }
                }
                TextButton(onClick = { adding = false; model.resetSearch() }) { Text("Назад к моим группам") }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.groups, key = { it.groupId }) { group ->
                        ListItem(headlineContent = { Text(group.displayName()) }, supportingContent = {
                            Text(if (group.groupId == state.settings.primaryGroupId) "Основная · напоминания о парах" else "Нажмите, чтобы открыть")
                        }, leadingContent = {
                            if (group.groupId == state.settings.activeGroupId) Icon(painterResource(R.drawable.ic_check), "Активная группа", tint = MaterialTheme.colorScheme.primary)
                            else Spacer(Modifier.size(24.dp))
                        }, trailingContent = { IconButton(onClick = { delete = group }) { Icon(painterResource(R.drawable.ic_delete), "Удалить группу") } },
                            modifier = Modifier.clickable { model.activate(group.groupId); onClose() })
                        if (group.university == University.IRNITU) SubgroupSelector(group.selectedSubgroup,
                            state.subgroupsForGroup(group.groupId), state.hasScheduleForGroup(group.groupId),
                            { model.selectSubgroup(group.groupId, it) }, Modifier.padding(horizontal = 16.dp))
                        if (group.groupId != state.settings.primaryGroupId) TextButton(onClick = { model.primary(group.groupId) }) { Text("Сделать основной") }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Добавить группу") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    delete?.let { group -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Удалить группу?") },
        text = { Text("${group.displayName()}\n\nРасписание будет удалено. Задания сохранятся без привязки к группе.") },
        confirmButton = { TextButton(onClick = { model.removeGroup(group.groupId); delete = null }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("Отмена") } }) }
}
