package app.sequi.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Small heading that groups the controls below it. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp))
}

/** "−  3  +" control with a label, so it is obvious what it adjusts. */
@Composable
fun LabeledStepper(label: String, hint: String?, value: String, onMinus: (() -> Unit)?, onPlus: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { onMinus?.invoke() }, enabled = onMinus != null) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(value, Modifier.width(48.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(onClick = { onPlus?.invoke() }, enabled = onPlus != null) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

/** One "⋮" button that opens a short, readable list of actions. */
@Composable
fun OverflowMenu(actions: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { (label, act) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; act() }) }
        }
    }
}

fun daysLabel(days: Set<Int>): String = when {
    days.isEmpty() -> "Once"
    days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    else -> days.sorted().joinToString(", ") { java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
}
