package app.sequi.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sequi.data.Alarm
import app.sequi.data.fmtAlarmTime
import app.sequi.data.Library

@Composable
fun AlarmsScreen(lib: Library, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val alarms by lib.alarms.collectAsState()
    val sorted = alarms.sortedBy { it.hour * 60 + it.minute }
    Scaffold(
        modifier = modifier,
        topBar = { Text("Alarms", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onOpen("new") }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New alarm") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Alarms ring on your watch with strong vibration. Changes sync over Bluetooth.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sorted.isEmpty()) item {
                Text("No alarms yet. Tap “New alarm” to add one.", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodyLarge)
            }
            items(sorted, key = { it.id }) { a ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen(a.id) }) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(fmtAlarmTime(a.hour, a.minute), style = MaterialTheme.typography.displaySmall,
                                color = if (a.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(daysLabel(a.days), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(a.enabled, { lib.saveAlarm(a.copy(enabled = it)) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(lib: Library, idArg: String, onClose: () -> Unit) {
    val existing = remember { if (idArg == "new") null else lib.alarm(idArg) }
    val picker = rememberTimePickerState(existing?.hour ?: 7, existing?.minute ?: 0, is24Hour = false)
    var days by remember { mutableStateOf(existing?.days ?: emptySet()) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New alarm" else "Edit alarm") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
                    TimePicker(picker)
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Repeat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Once" to emptySet<Int>(), "Weekdays" to setOf(1, 2, 3, 4, 5), "Every day" to (1..7).toSet()).forEach { (label, set) ->
                            FilterChip(selected = days == set, onClick = { days = set }, label = { Text(label) })
                        }
                    }
                    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        for (d in 1..7) FilledIconToggleButton(
                            checked = d in days, onCheckedChange = { days = if (d in days) days - d else days + d },
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        ) { Text(letters[d - 1], style = MaterialTheme.typography.titleSmall) }
                    }
                    Text(if (days.isEmpty()) "Rings once, then turns itself off." else daysLabel(days),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Enabled", style = MaterialTheme.typography.titleMedium)
                    Text("Rings on your watch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(enabled, { enabled = it })
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    lib.saveAlarm(Alarm(existing?.id ?: lib.newId(), picker.hour, picker.minute, days, enabled))
                    onClose()
                }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Save alarm") }
                if (existing != null) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete alarm") }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this alarm?") },
        text = { Text("It will also disappear from the watch.") },
        confirmButton = { TextButton(onClick = { lib.deleteAlarm(existing!!.id); onClose() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
