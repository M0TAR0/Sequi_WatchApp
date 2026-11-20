package app.sequi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import app.sequi.data.Repo
import app.sequi.engine.*
import app.sequi.timer.TimerBus

@Composable
fun HomeScreen(
    onStart: (String) -> Unit, onEdit: (String) -> Unit, onNew: () -> Unit, onRun: () -> Unit,
    onAlarms: () -> Unit, onStats: () -> Unit, onTimes: () -> Unit, onSettings: () -> Unit,
) {
    val seqs by Repo.sequences.collectAsState()
    val run by TimerBus.state.collectAsState()
    val alarms by Repo.alarms.collectAsState()
    ListScreen {
        item { ListHeader { Text("Timers") } }
        if (run.active) item {
            Button(onClick = onRun, modifier = Modifier.fillMaxWidth(),
                label = { Text("Running") }, secondaryLabel = { Text(run.name) })
        }
        items(seqs.size, key = { seqs[it].id }) { i ->
            val s = seqs[i]
            TimerCard(s, onClick = { onStart(s.id) }, onLongClick = { onEdit(s.id) })
        }
        item { FilledTonalButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("+ New timer") } }
        item { Text("Tap to start · hold to edit", style = MaterialTheme.typography.labelSmall, color = SequiColors.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

        section("More")
        item {
            val on = alarms.count { it.enabled }
            NavChip("Alarms", if (on == 0) "none set" else "$on on", onAlarms)
        }
        item { NavChip("Stats", "history and heart rate", onStats) }
        item { NavChip("Workout times", "when you train", onTimes) }
        item { NavChip("Settings", "sound, haptics, heart rate", onSettings) }
    }
}

@Composable
private fun NavChip(text: String, hint: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        label = { Text(text) }, secondaryLabel = { Text(hint, maxLines = 1) })
}

@Composable
private fun TimerCard(s: Sequence, onClick: () -> Unit, onLongClick: () -> Unit) {
    // Building a timeline is not free; do it once per timer, not on every recomposition.
    val info = remember(s.spec) { s.spec.summary() + " · " + fmtClock(s.spec.totalSeconds() * 1000L) }
    val strip = remember(s.spec) { s.spec.preview() }
    Card(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Edit", modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                info,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
            PreviewStrip(strip, Modifier.padding(top = 2.dp))
        }
    }
}
