package app.sequi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import app.sequi.data.Repo
import app.sequi.engine.*
import java.time.LocalDate
import java.time.ZoneId

@Composable
private fun Bars(values: List<Float>, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, height: Int = 48) {
    val dim = MaterialTheme.colorScheme.surfaceContainer
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val n = values.size.coerceAtLeast(1)
        val gap = if (n > 14) 1.dp.toPx() else 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1e-3f)
        values.forEachIndexed { i, v ->
            val x = i * (w + gap)
            drawRoundRect(dim, Offset(x, size.height - 3.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(1.dp.toPx()))
            if (v > 0f) {
                val h = (v / max * size.height).coerceAtLeast(3.dp.toPx())
                drawRoundRect(color, Offset(x, size.height - h), Size(w, h), CornerRadius(w.coerceAtMost(4.dp.toPx())))
            }
        }
    }
}

/** Weekly / monthly analysis: workouts, active time and heart rate. */
@Composable
fun StatsScreen() {
    val sessions by Repo.sessions.collectAsState()
    var month by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val days = if (month) 30 else 7
    val daily = remember(sessions, month) { dailyStats(sessions, days, today, zone) }
    val sum = remember(sessions, month) { summarize(daily, sessions, today, zone) }

    ListScreen {
        item { ListHeader { Text("Stats") } }
        item {
            FilledTonalButton(onClick = { month = !month }, modifier = Modifier.fillMaxWidth()) {
                Text(if (month) "Last 30 days" else "Last 7 days")
            }
        }
        if (sum.sessions == 0) {
            item { Text("No workouts yet.\nFinish a timer to see stats.", style = MaterialTheme.typography.bodySmall) }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Stat("${sum.sessions}", "workouts")
                    Stat(fmtClock(sum.activeSec * 1000L), "active")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Stat(sum.avgHr?.toString() ?: "–", "avg ♥", SequiColors.Heart)
                    Stat(sum.maxHr?.toString() ?: "–", "max ♥", SequiColors.Heart)
                }
            }
            item { Text("Active minutes / day", style = MaterialTheme.typography.labelSmall, color = SequiColors.Dim) }
            item { Bars(daily.map { it.activeSec / 60f }, SequiColors.Work, Modifier.padding(horizontal = 8.dp)) }
            item { Text("Avg heart rate / day", style = MaterialTheme.typography.labelSmall, color = SequiColors.Dim) }
            item { Bars(daily.map { (it.avgHr ?: 0).toFloat() }, SequiColors.Heart, Modifier.padding(horizontal = 8.dp)) }
        }
    }
}

/** When do you usually train? Workout start hours over the last 90 days. */
@Composable
fun TimesScreen() {
    val sessions by Repo.sessions.collectAsState()
    val zone = ZoneId.systemDefault()
    val cutoff = System.currentTimeMillis() - 90L * 86_400_000
    val hist = remember(sessions) { hourHistogram(sessions.filter { it.startEpochMs >= cutoff }, zone) }
    val total = hist.sum()
    val peak = hist.indices.maxByOrNull { hist[it] } ?: 0

    fun part(range: IntRange) = hist.slice(range).sum()
    ListScreen {
        item { ListHeader { Text("Workout times") } }
        if (total == 0) {
            item { Text("Nothing logged yet.", style = MaterialTheme.typography.bodySmall) }
        } else {
            item { Stat("%02d:00".format(peak), "most common start") }
            item { Bars(hist.map { it.toFloat() }, SequiColors.Easy, Modifier.padding(horizontal = 8.dp)) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), Arrangement.SpaceBetween) {
                    Text("0h", style = MaterialTheme.typography.labelSmall); Text("12h", style = MaterialTheme.typography.labelSmall); Text("23h", style = MaterialTheme.typography.labelSmall)
                }
            }
            item { Stat("${part(5..11)}", "morning 5–12") }
            item { Stat("${part(12..17)}", "afternoon 12–18") }
            item { Stat("${part(18..22)}", "evening 18–23") }
            item { Stat("${part(23..23) + part(0..4)}", "night 23–5") }
        }
    }
}

@Composable
fun SettingsScreen() {
    val s by Repo.settings.collectAsState()
    ListScreen {
        item { ListHeader { Text("Settings") } }

        section("Cues")
        item {
            SwitchButton(checked = s.sound, onCheckedChange = { v -> Repo.updateSettings { it.copy(sound = v) } },
                modifier = Modifier.fillMaxWidth(), label = { Text("Sound") }, secondaryLabel = { Text("haptics are always on") })
        }
        item {
            SwitchButton(checked = s.ticks, onCheckedChange = { v -> Repo.updateSettings { it.copy(ticks = v) } },
                modifier = Modifier.fillMaxWidth(), label = { Text("3-2-1 ticks") }, secondaryLabel = { Text("before each change") })
        }

        item {
            SwitchButton(checked = s.voice, onCheckedChange = { v -> Repo.updateSettings { it.copy(voice = v) } },
                modifier = Modifier.fillMaxWidth(), label = { Text("Voice cues") }, secondaryLabel = { Text("says “Rest”, “Round 3 of 4”") })
        }

        section("Workout")
        item {
            SwitchButton(checked = s.heartRate, onCheckedChange = { v -> Repo.updateSettings { it.copy(heartRate = v) } },
                modifier = Modifier.fillMaxWidth(), label = { Text("Heart rate") }, secondaryLabel = { Text("recorded in stats") })
        }
        item {
            Stepper("Get ready before start", if (s.prepSec == 0) "off" else "${s.prepSec} s",
                { Repo.updateSettings { it.copy(prepSec = (it.prepSec - 1).coerceAtLeast(0)) } },
                { Repo.updateSettings { it.copy(prepSec = (it.prepSec + 1).coerceAtMost(30)) } })
        }
    }
}
