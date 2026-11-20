package app.sequi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sequi.engine.*
import androidx.wear.compose.material3.*
import app.sequi.alarm.AlarmScheduler
import app.sequi.data.*
import app.sequi.data.Repo

@Composable
fun AlarmsScreen(onEdit: (String) -> Unit) {
    val alarms by Repo.alarms.collectAsState()
    ListScreen {
        item { ListHeader { Text("Alarms") } }
        val sorted = alarms.sortedBy { it.hour * 60 + it.minute }
        if (sorted.isEmpty()) item { Text("No alarms yet", style = MaterialTheme.typography.bodySmall, color = SequiColors.Dim) }
        items(sorted.size) { i ->
            val a = sorted[i]
            Button(
                onClick = { onEdit(a.id) }, modifier = Modifier.fillMaxWidth(),
                colors = if (a.enabled) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                label = { Text(fmtAlarmTime(a.hour, a.minute)) },
                secondaryLabel = { Text(AlarmScheduler.daysLabel(a.days) + if (a.enabled) "" else " · off") },
            )
        }
        item { FilledTonalButton(onClick = { onEdit("new") }, modifier = Modifier.fillMaxWidth()) { Text("+ New alarm") } }
    }
}

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun AlarmEditScreen(id: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val existing = remember { (if (id == "new") null else Repo.alarm(id)) }
    var hour by remember { mutableIntStateOf(existing?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var days by remember { mutableStateOf(existing?.days ?: emptySet()) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }

    ListScreen {
        item { ListHeader { Text(if (existing == null) "New alarm" else "Edit alarm") } }

        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(fmtAlarmTime(hour, minute), style = MaterialTheme.typography.displaySmall, color = if (enabled) MaterialTheme.colorScheme.primary else SequiColors.Dim)
                Text(AlarmScheduler.daysLabel(days), style = MaterialTheme.typography.labelMedium, color = SequiColors.Dim)
            }
        }
        section("Time")
        item {
            val (h12, pm) = to12h(hour)
            Stepper("Hour", "$h12", { hour = from12h((h12 + 10) % 12 + 1, pm) }, { hour = from12h(h12 % 12 + 1, pm) })
        }
        item {
            val pm = hour >= 12
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                for ((label, isPm) in listOf("AM" to false, "PM" to true)) {
                    val m = Modifier.weight(1f)
                    val click = { if (pm != isPm) hour = (hour + 12) % 24 }
                    if (pm == isPm) Button(onClick = click, modifier = m) { Text(label) }
                    else FilledTonalButton(onClick = click, modifier = m) { Text(label) }
                }
            }
        }
        item { Stepper("Minute", "%02d".format(minute), { minute = (minute + 55) % 60 }, { minute = (minute + 5) % 60 }) }

        section("Repeat")
        item { Text(if (days.isEmpty()) "Tap days to repeat weekly" else "Tap a day to turn it off", style = MaterialTheme.typography.labelSmall, color = SequiColors.Dim) }
        item { DayRow(1..4, days) { days = it } }
        item { DayRow(5..7, days) { days = it } }

        section("Alarm")
        item { SwitchButton(checked = enabled, onCheckedChange = { enabled = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Enabled") }) }

        item { Spacer(Modifier.height(6.dp)) }
        item {
            Button(onClick = {
                Repo.saveAlarm(Alarm(existing?.id ?: Repo.newAlarmId(), hour, minute, days, enabled))
                AlarmScheduler.reschedule(ctx)
                onDone()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
        if (existing != null) item {
            FilledTonalButton(onClick = { Repo.deleteAlarm(existing.id); AlarmScheduler.reschedule(ctx); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
        }
    }
}

@Composable
private fun DayRow(range: IntRange, days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        for (d in range) {
            val on = d in days
            val click = { onChange(if (on) days - d else days + d) }
            val m = Modifier.size(width = 44.dp, height = 44.dp)
            if (on) Button(onClick = click, modifier = m, contentPadding = PaddingValues(0.dp)) { Text(DAY_LETTERS[d - 1]) }
            else FilledTonalButton(onClick = click, modifier = m, contentPadding = PaddingValues(0.dp)) { Text(DAY_LETTERS[d - 1]) }
        }
    }
}
