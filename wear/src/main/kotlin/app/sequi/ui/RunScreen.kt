package app.sequi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import app.sequi.engine.fmtClock
import app.sequi.engine.fmtSeconds
import app.sequi.timer.RunState
import app.sequi.timer.TimerBus
import app.sequi.timer.TimerService

@Composable
fun RunScreen(onExit: () -> Unit) {
    val ctx = LocalContext.current
    val st by TimerBus.state.collectAsState()
    val sum = st.summary
    if (sum != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text(if (sum.completed) "Done" else "Stopped", style = MaterialTheme.typography.displaySmall,
                color = if (sum.completed) SequiColors.Work else SequiColors.Rest)
            Text(st.name, style = MaterialTheme.typography.labelMedium, color = SequiColors.Dim, textAlign = TextAlign.Center, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Stat(fmtClock(sum.activeSec * 1000L), "active")
                sum.avgHr?.let { Stat("$it", "avg ♥", SequiColors.Heart) }
                sum.maxHr?.let { Stat("$it", "max ♥", SequiColors.Heart) }
            }
            if (sum.pausedSec > 0) Text("paused ${sum.pausedSec}s", style = MaterialTheme.typography.labelSmall, color = SequiColors.Dim)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { TimerBus.clear(); onExit() }) { Text("Close") }
        }
        return
    }
    RunningFace(st, onToggle = { TimerService.toggle(ctx) }, onStop = { TimerService.stop(ctx) })
}

@Composable
private fun RunningFace(st: RunState, onToggle: () -> Unit, onStop: () -> Unit) {
    val accent = if (st.paused) SequiColors.Dim else kindAccent(st.kind)
    val progress = if (st.durationMs > 0) (st.remainingMs.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize()) {
        // Progress ring hugging the edge of the round screen.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2 + 3.dp.toPx()
            val d = size.minDimension - inset * 2
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arcSize = Size(d, d)
            drawArc(accent.copy(alpha = 0.16f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (progress > 0f) drawArc(accent, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 20.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text(if (st.paused) "PAUSED" else st.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1)
            Text(fmtClock(st.remainingMs), fontSize = 54.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            if (st.rounds in 2..10) RoundDots(st.round, st.rounds, accent)
            else if (st.rounds > 10) Text("${st.round} / ${st.rounds}", style = MaterialTheme.typography.labelMedium, color = SequiColors.Dim)
            Spacer(Modifier.height(2.dp))
            val sub = buildString {
                st.nextLabel?.let { append("Next: $it ${fmtSeconds(st.nextSeconds)}") }
            }
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = SequiColors.Dim, maxLines = 1)
            st.bpm?.let { Text("♥ $it", style = MaterialTheme.typography.labelMedium, color = SequiColors.Heart) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) { Text(if (st.paused) "▶" else "❚❚", fontSize = 14.sp) }
                // Hold to stop, so a sweaty tap can't end the workout.
                FilledTonalButton(onClick = {}, onLongClick = onStop, onLongClickLabel = "Stop",
                    modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp)) {
                    Text("■", fontSize = 14.sp, color = SequiColors.Rest)
                }
            }
        }
    }
}

@Composable
private fun RoundDots(round: Int, rounds: Int, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        for (i in 1..rounds) {
            val size = if (i == round) 8.dp else 6.dp
            Box(Modifier.size(size).background(if (i <= round) accent else accent.copy(alpha = 0.25f), CircleShape))
        }
    }
}
