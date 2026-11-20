package app.sequi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material3.*
import app.sequi.data.Repo
import app.sequi.engine.*

/** Step size grows with the value, so you can reach 10 min quickly but still set 25 s precisely. */
private fun stepSize(v: Int) = when {
    v < 60 -> 5
    v < 300 -> 15
    v < 1800 -> 60
    else -> 300
}
private fun up(v: Int, max: Int) = (v + stepSize(v)).coerceAtMost(max)
private fun down(v: Int, min: Int) = (v - stepSize(v - 1)).coerceAtLeast(min)

/**
 * Creates or edits a timer on the watch: Countdown or Intervals, both with alerts at any time you choose.
 * Free-form timers (any number of steps/blocks) are built in the phone app and just appear here.
 */
@Composable
fun EditorScreen(idArg: String, onDone: () -> Unit) {
    val existing = remember { if (idArg == "new") null else Repo.sequence(idArg) }
    val isCustom = existing?.spec is Spec.Custom
    var intervals by remember { mutableStateOf(existing?.spec is Spec.Intervals) }

    val cd = existing?.spec as? Spec.Countdown
    var duration by remember { mutableIntStateOf(cd?.seconds ?: 600) }
    var alerts by remember { mutableStateOf(cd?.alerts ?: listOf(300, 120, 60)) }

    val iv = existing?.spec as? Spec.Intervals
    var work by remember { mutableIntStateOf(iv?.workSec ?: 40) }
    var rest by remember { mutableIntStateOf(iv?.restSec ?: 20) }
    var rounds by remember { mutableIntStateOf(iv?.rounds ?: 6) }
    var warm by remember { mutableIntStateOf(iv?.warmupSec ?: 0) }
    var cool by remember { mutableIntStateOf(iv?.cooldownSec ?: 0) }
    var workAlerts by remember { mutableStateOf(iv?.workAlerts ?: emptyList()) }

    fun buildSpec(): Spec = if (intervals) Spec.Intervals(work, rest, rounds, warm, cool, workAlerts.filter { it < work })
    else Spec.Countdown(duration, alerts.filter { it < duration }.sortedDescending())

    val currentSpec = buildSpec()
    val total = remember(currentSpec) { fmtClock(currentSpec.totalSeconds() * 1000L) }
    val summary = remember(currentSpec) { currentSpec.summary() }
    val strip = remember(currentSpec) { currentSpec.preview() }

    ListScreen {
        item { ListHeader { Text(if (existing == null) "New timer" else "Edit timer") } }

        if (isCustom) {
            val sp = existing!!.spec
            item { Text(existing.name, style = MaterialTheme.typography.titleSmall) }
            item { Text("${sp.summary()} · ${fmtClock(sp.totalSeconds() * 1000L)}", style = MaterialTheme.typography.labelSmall, color = SequiColors.Dim) }
            item { PreviewStrip(sp.preview()) }
            item { Text("Custom timers are edited in the Sequi phone app.", style = MaterialTheme.typography.bodySmall, color = SequiColors.Dim) }
            item { FilledTonalButton(onClick = { Repo.deleteSequence(existing.id); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Delete") } }
            return@ListScreen
        }

        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(total, style = MaterialTheme.typography.displaySmall)
                Text(summary, style = MaterialTheme.typography.labelSmall, color = SequiColors.Dim, maxLines = 1)
                PreviewStrip(strip, Modifier.padding(horizontal = 12.dp))
            }
        }
        section("Type")
        item {
            Button(onClick = { intervals = !intervals }, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (intervals) "Intervals" else "Countdown") }, secondaryLabel = { Text("tap to switch type") })
        }
        section(if (intervals) "Each round" else "Duration")
        if (!intervals) {
            item { Stepper("Duration", fmtClock(duration * 1000L), { duration = down(duration, 5) }, { duration = up(duration, 10800) }) }
            alertItems("Alerts (time left)", alerts, duration) { alerts = it }
        } else {
            item { Stepper("Work", fmtSeconds(work), { work = down(work, 5) }, { work = up(work, 3600) }) }
            item { Stepper("Rest", if (rest == 0) "none" else fmtSeconds(rest), { rest = down(rest, 0) }, { rest = up(rest, 600) }) }
            item { Stepper("Rounds", "$rounds", { rounds = (rounds - 1).coerceAtLeast(1) }, { rounds = (rounds + 1).coerceAtMost(99) }) }
            section("Warm-up and cool-down")
            item { Stepper("Warm-up", if (warm == 0) "off" else fmtSeconds(warm), { warm = down(warm, 0) }, { warm = if (warm == 0) 30 else up(warm, 3600) }) }
            item { Stepper("Cool-down", if (cool == 0) "off" else fmtSeconds(cool), { cool = down(cool, 0) }, { cool = if (cool == 0) 30 else up(cool, 3600) }) }
            alertItems("Alerts in each work (time left)", workAlerts, work) { workAlerts = it }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Button(onClick = {
                val sp = currentSpec
                // Keep a preset's own name unless the shape changed.
                val keep = existing != null && existing.spec::class == sp::class && !existing.name.contains("×") && !existing.name.endsWith("timer")
                Repo.saveSequence(Sequence(existing?.id ?: Repo.newId(), if (keep) existing!!.name else sp.autoName(), sp))
                onDone()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
        if (existing != null) item {
            FilledTonalButton(onClick = { Repo.deleteSequence(existing.id); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
        }
    }
}

/** Chips for the alerts you have (tap to remove) plus a mini picker to add any other time. */
private fun ScalingLazyListScope.alertItems(title: String, alerts: List<Int>, limit: Int, onChange: (List<Int>) -> Unit) {
    section(title)
    val shown = alerts.filter { it < limit }.sortedDescending()
    if (shown.isEmpty()) item { Text("none", style = MaterialTheme.typography.labelSmall, color = SequiColors.Dim) }
    items(shown.size) { i ->
        val a = shown[i]
        FilledTonalButton(onClick = { onChange(alerts - a) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(fmtClock(a * 1000L) + " left")
                Text("✕", color = SequiColors.Rest)
            }
        }
    }
    item { AlertAdder(limit) { onChange((alerts + it).distinct()) } }
}

@Composable
private fun AlertAdder(limit: Int, onAdd: (Int) -> Unit) {
    val max = (limit - 5).coerceAtLeast(5)
    var v by remember { mutableIntStateOf(60) }
    if (v > max) v = max
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Stepper("New alert at", fmtClock(v * 1000L) + " left", { v = down(v, 5) }, { v = up(v, max) })
        Button(onClick = { onAdd(v) }, modifier = Modifier.fillMaxWidth()) { Text("+ Add alert") }
    }
}
