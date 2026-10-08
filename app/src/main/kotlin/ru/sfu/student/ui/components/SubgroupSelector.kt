package ru.sfu.student.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun SubgroupSelector(selected: Int?, available: List<Int>, loaded: Boolean,
    onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val choices = (available + listOfNotNull(selected)).distinct().sorted()
    if (choices.isEmpty()) {
        Text(if (loaded) "В расписании нет разделения на подгруппы" else "Подгруппы появятся после загрузки расписания",
            modifier = modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
            Text("Подгруппа: ${selected?.toString() ?: "Все"}  ▾", style = MaterialTheme.typography.bodyMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Все подгруппы" + if (selected == null) "  ✓" else "") },
                onClick = { onSelect(null); expanded = false })
            choices.forEach { value ->
                DropdownMenuItem(text = { Text("$value подгруппа" + if (selected == value) "  ✓" else "") },
                    onClick = { onSelect(value); expanded = false })
            }
        }
    }
}
