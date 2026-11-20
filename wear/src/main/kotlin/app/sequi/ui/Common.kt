package app.sequi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import app.sequi.engine.StepKind

/** Standard scrolling list screen (round-screen friendly, with scroll indicator). */
@Composable
fun ListScreen(content: ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(
            state = state, contentPadding = padding, modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
        )
    }
}

/** A titled group of controls: a little air above, then a quiet heading. */
fun ScalingLazyListScope.section(title: String) {
    item { ListSubHeader(modifier = Modifier.padding(top = 12.dp)) { Text(title) } }
}

/**
 * Label on top, "−  value  +" below, in a soft rounded panel.
 * Tap the value to hand the crown / bezel to this control (it gets a mint outline); turn to adjust, tap again to release.
 */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val focus = remember { FocusRequester() }
    var crown by remember { mutableStateOf(false) }
    var acc by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(24.dp)
    val minus by rememberUpdatedState(onMinus)
    val plus by rememberUpdatedState(onPlus)
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .then(if (crown) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .onFocusChanged { if (!it.isFocused) crown = false }
            .onRotaryScrollEvent {
                if (!crown) false else {
                    acc += it.verticalScrollPixels
                    while (acc >= 60f) { plus(); acc -= 60f }
                    while (acc <= -60f) { minus(); acc += 60f }
                    true
                }
            }
            .focusRequester(focus).focusable()
            .padding(vertical = 10.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (crown) "$label · crown" else label, style = MaterialTheme.typography.labelSmall,
            color = if (crown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(42.dp)) { Text("−") }
            Text(value, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                modifier = Modifier.weight(1f).clickable { crown = !crown; if (crown) focus.requestFocus() })
            FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(42.dp)) { Text("+") }
        }
    }
}

/** Compact stat tile: big value, small caption. */
@Composable
fun Stat(value: String, caption: String, accent: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A thin strip showing the shape of a timer: one coloured segment per step, sized by duration. */
@Composable
fun PreviewStrip(parts: List<Pair<Float, StepKind>>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(5.dp)) {
        var x = 0f
        val gap = 1.dp.toPx()
        parts.forEach { (frac, kind) ->
            val w = (size.width * frac - gap).coerceAtLeast(1.dp.toPx())
            drawRoundRect(kindAccent(kind), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height),
                androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            x += size.width * frac
        }
    }
}
