package app.sequi.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.sequi.data.Library
import app.sequi.engine.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(lib: Library, modifier: Modifier = Modifier) {
    val sessions by lib.sessions.collectAsState()
    var days by remember { mutableIntStateOf(7) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val daily = remember(sessions, days) { dailyStats(sessions, days, today, zone) }
    val sum = remember(sessions, days) { summarize(daily, sessions, today, zone) }
    val hist = remember(sessions) { hourHistogram(sessions.filter { it.startEpochMs >= System.currentTimeMillis() - 90L * 86_400_000 }, zone) }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM · HH:mm") }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Stats") }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "7 days", 30 to "30 days", 90 to "90 days").forEach { (d, l) ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text(l) })
                    }
                }
            }
            if (sessions.isEmpty()) {
                item { Text("No workouts yet. Finish a timer on the watch and it will show up here.", style = MaterialTheme.typography.bodyMedium) }
                return@LazyColumn
            }
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    Tile("${sum.sessions}", "workouts")
                    Tile(fmtClock(sum.activeSec * 1000L), "active")
                    Tile(sum.avgHr?.toString() ?: "–", "avg ♥", SequiColors.Heart)
                    Tile(sum.maxHr?.toString() ?: "–", "max ♥", SequiColors.Heart)
                }
            }
            item { ChartCard("Active minutes per day", daily.map { it.activeSec / 60f }, SequiColors.Work, "min") }
            item { ChartCard("Average heart rate per day", daily.map { (it.avgHr ?: 0).toFloat() }, SequiColors.Heart, "bpm") }
            item {
                val peak = hist.indices.maxByOrNull { hist[it] } ?: 0
                ChartCard("When you train (start hour, 90 days)", hist.map { it.toFloat() }, SequiColors.Easy, "", footer = "Most common: %02d:00 · axis 0h → 23h".format(peak))
            }
            item { Text("Recent workouts", style = MaterialTheme.typography.titleSmall) }
            items(sessions.sortedByDescending { it.startEpochMs }.take(15)) { s ->
                ListItem(
                    headlineContent = { Text(s.name) },
                    supportingContent = { Text(Instant.ofEpochMilli(s.startEpochMs).atZone(zone).format(fmt) + if (!s.completed) " · stopped early" else "") },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmtClock(s.activeSec * 1000L))
                            s.avgHr?.let { Text("♥ $it", color = SequiColors.Heart, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Tile(value: String, caption: String, accent: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartCard(title: String, values: List<Float>, color: Color, unit: String, footer: String? = null) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (unit.isNotEmpty()) Text("max ${values.maxOrNull()?.toInt() ?: 0} $unit", style = MaterialTheme.typography.labelSmall)
            }
            val dim = MaterialTheme.colorScheme.surfaceContainerHigh
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val n = values.size.coerceAtLeast(1)
                val gap = if (n > 31) 1.dp.toPx() else 3.dp.toPx()
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
            footer?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
