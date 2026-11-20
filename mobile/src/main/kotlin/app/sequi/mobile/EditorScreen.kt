package app.sequi.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.sequi.data.Library
import app.sequi.engine.*

// ---- UI-side model with stable ids, so text fields keep their state when things move ----
private var nextUid = 0L
private fun uid() = nextUid++

private data class UStep(val uid: Long = uid(), val step: CustomStep)
private data class UBlock(val uid: Long = uid(), val repeat: Int, val dropLastRest: Boolean, val steps: List<UStep>)

private fun Spec.toUi() = toCustom().blocks.map { b -> UBlock(repeat = b.repeat, dropLastRest = b.dropLastRest, steps = b.steps.map { UStep(step = it) }) }
private fun List<UBlock>.toSpec() = Spec.Custom(map { b -> CustomBlock(b.steps.map { it.step }, b.repeat, b.dropLastRest) })

private fun template(name: String): List<UBlock> = when (name) {
    "Countdown" -> Spec.Countdown(600, listOf(300, 120, 60)).toUi()
    "Intervals" -> Spec.Intervals(40, 20, 6).toUi()
    "Workout" -> Spec.Intervals(40, 20, 6, warmupSec = 300, cooldownSec = 300).toUi()
    else -> listOf(UBlock(repeat = 1, dropLastRest = true, steps = listOf(UStep(step = CustomStep("Work", StepKind.WORK, 60)))))
}

fun parseDuration(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
    return parts.map { it.toInt() }.fold(0) { acc, n -> acc * 60 + n }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(lib: Library, idArg: String, onClose: () -> Unit) {
    val existing = remember { if (idArg == "new") null else lib.sequence(idArg) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var blocks by remember { mutableStateOf(existing?.spec?.toUi() ?: template("Blank")) }

    fun setBlock(bi: Int, f: (UBlock) -> UBlock) { blocks = blocks.mapIndexed { i, b -> if (i == bi) f(b) else b } }
    fun setStep(bi: Int, si: Int, f: (CustomStep) -> CustomStep) =
        setBlock(bi) { b -> b.copy(steps = b.steps.mapIndexed { i, s -> if (i == si) s.copy(step = f(s.step)) else s }) }
    fun <T> List<T>.moved(i: Int, d: Int): List<T> {
        val j = i + d
        if (j !in indices) return this
        return toMutableList().also { it.add(j, it.removeAt(i)) }
    }

    val spec = blocks.toSpec()
    val valid = blocks.isNotEmpty() && blocks.all { it.steps.isNotEmpty() && it.steps.all { s -> s.step.seconds > 0 } }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New timer" else "Edit timer") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (existing != null) {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        OverflowMenu(listOf(
                            "Share timer" to {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_SUBJECT, existing.name)
                                    .putExtra(android.content.Intent.EXTRA_TEXT, lib.shareText(existing))
                                ctx.startActivity(android.content.Intent.createChooser(send, "Share timer"))
                            },
                            "Delete timer" to { confirmDelete = true },
                        ))
                    }
                    TextButton(enabled = valid, onClick = {
                        lib.save(Sequence(existing?.id ?: lib.newId(), name.ifBlank { spec.autoName() }, spec))
                        onClose()
                    }) { Text("Save") }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).imePadding(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 64.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item { SectionTitle("Details") }
            item {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text(spec.autoName()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
                            Column {
                                Text("Total time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fmtClock(spec.totalSeconds() * 1000L), style = MaterialTheme.typography.displaySmall)
                            }
                            Text("${blocks.sumOf { it.steps.size }} steps", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PreviewStrip(spec.preview())
                    }
                }
            }
            if (existing == null) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Start from a template", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Blank", "Countdown", "Intervals", "Workout").forEach { t ->
                            AssistChip(onClick = { blocks = template(t) }, label = { Text(t) })
                        }
                    }
                }
            }
            item { SectionTitle("Blocks") }
            blocks.forEachIndexed { bi, b ->
                item(key = b.uid) {
                    BlockSection(
                        index = bi, block = b,
                        onRepeat = { r -> setBlock(bi) { it.copy(repeat = r.coerceIn(1, 99)) } },
                        onDropRest = { d -> setBlock(bi) { it.copy(dropLastRest = d) } },
                        onMoveBlock = { d -> blocks = blocks.moved(bi, d) },
                        onDeleteBlock = { blocks = blocks.filterIndexed { i, _ -> i != bi } },
                        onStep = { si, f -> setStep(bi, si, f) },
                        onMoveStep = { si, d -> setBlock(bi) { it.copy(steps = it.steps.moved(si, d)) } },
                        onDeleteStep = { si -> setBlock(bi) { it.copy(steps = it.steps.filterIndexed { i, _ -> i != si }) } },
                        onAddStep = { kind ->
                            val label = when (kind) { StepKind.REST -> "Rest"; StepKind.EASY -> "Easy"; else -> "Work" }
                            setBlock(bi) { it.copy(steps = it.steps + UStep(step = CustomStep(label, kind, if (kind == StepKind.REST) 15 else 60))) }
                        },
                    )
                }
            }
            item {
                FilledTonalButton(onClick = {
                    blocks = blocks + UBlock(repeat = 1, dropLastRest = true, steps = listOf(UStep(step = CustomStep("Work", StepKind.WORK, 60))))
                }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add block") }
            }
            item {
                Text(
                    "A block repeats its steps (e.g. 8 rounds of work + rest). Alerts fire on the watch with strong haptics at the times you choose, " +
                        "counted either as time left or time since the step started.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this timer?") },
        text = { Text("It will also disappear from the watch.") },
        confirmButton = { TextButton(onClick = { lib.delete(existing!!.id); onClose() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun BlockSection(
    index: Int, block: UBlock,
    onRepeat: (Int) -> Unit, onDropRest: (Boolean) -> Unit, onMoveBlock: (Int) -> Unit, onDeleteBlock: () -> Unit,
    onStep: (Int, (CustomStep) -> CustomStep) -> Unit, onMoveStep: (Int, Int) -> Unit, onDeleteStep: (Int) -> Unit,
    onAddStep: (StepKind) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (index > 0) HorizontalDivider(Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Block ${index + 1}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OverflowMenu(listOf("Move block up" to { onMoveBlock(-1) }, "Move block down" to { onMoveBlock(1) }, "Delete block" to onDeleteBlock))
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledStepper("Repeat", "How many times this block runs", "× ${block.repeat}",
                    onMinus = if (block.repeat > 1) ({ onRepeat(block.repeat - 1) }) else null, onPlus = { onRepeat(block.repeat + 1) })
                if (block.repeat > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Skip the rest after the last repeat", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(block.dropLastRest, onDropRest)
                }
            }
        }
        block.steps.forEachIndexed { si, us ->
            key(us.uid) {
                StepEditor(
                    number = si + 1, step = us.step,
                    onChange = { f -> onStep(si, f) },
                    onUp = { onMoveStep(si, -1) }, onDown = { onMoveStep(si, 1) }, onDelete = { onDeleteStep(si) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onAddStep(StepKind.WORK) }, Modifier.weight(1f)) { Text("+ Work") }
            OutlinedButton(onClick = { onAddStep(StepKind.REST) }, Modifier.weight(1f)) { Text("+ Rest") }
            OutlinedButton(onClick = { onAddStep(StepKind.EASY) }, Modifier.weight(1f)) { Text("+ Easy") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepEditor(number: Int, step: CustomStep, onChange: ((CustomStep) -> CustomStep) -> Unit, onUp: () -> Unit, onDown: () -> Unit, onDelete: () -> Unit) {
    var durText by remember { mutableStateOf(fmtClock(step.seconds * 1000L)) }
    var addingAlert by remember { mutableStateOf(false) }
    val accent = kindAccent(step.kind)

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = accent, shape = MaterialTheme.shapes.small, modifier = Modifier.size(width = 6.dp, height = 24.dp)) {}
                Text("Step $number", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OverflowMenu(listOf("Move up" to onUp, "Move down" to onDown, "Delete step" to onDelete))
            }
            OutlinedTextField(step.label, { v -> onChange { it.copy(label = v) } }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val kinds = listOf(StepKind.WORK to "Work", StepKind.REST to "Rest", StepKind.EASY to "Easy")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    kinds.forEachIndexed { i, (k, label) ->
                        SegmentedButton(selected = step.kind == k, onClick = { onChange { it.copy(kind = k) } },
                            shape = SegmentedButtonDefaults.itemShape(i, kinds.size)) { Text(label) }
                    }
                }
            }
            OutlinedTextField(
                durText,
                { v ->
                    durText = v
                    parseDuration(v)?.takeIf { it in 1..36000 }?.let { s -> onChange { it.copy(seconds = s, alerts = it.alerts.filter { a -> a < s }, alertsAfter = it.alertsAfter.filter { a -> a < s }, every = if (it.every < s) it.every else 0) } }
                },
                label = { Text("Length (minutes:seconds)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = parseDuration(durText)?.let { it < 1 } ?: true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Alerts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("A strong pulse on the watch at these moments.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    step.alerts.sortedDescending().forEach { a ->
                        InputChip(selected = false, onClick = { onChange { it.copy(alerts = it.alerts - a) } },
                            label = { Text("${fmtClock(a * 1000L)} left") }, trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) })
                    }
                    if (step.every > 0) InputChip(selected = false, onClick = { onChange { it.copy(every = 0) } },
                        label = { Text("every ${fmtClock(step.every * 1000L)}") }, trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) })
                    step.alertsAfter.sorted().forEach { a ->
                        InputChip(selected = false, onClick = { onChange { it.copy(alertsAfter = it.alertsAfter - a) } },
                            label = { Text("after ${fmtClock(a * 1000L)}") }, trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) })
                    }
                }
                OutlinedButton(onClick = { addingAlert = true }) { Text("+ Add alert") }
            }
        }
    }

    if (addingAlert) AlertDialogAdd(
        limit = step.seconds,
        onDismiss = { addingAlert = false },
        onAdd = { mode, sec ->
            onChange {
                when (mode) {
                    AlertMode.LEFT -> it.copy(alerts = (it.alerts + sec).distinct())
                    AlertMode.SINCE -> it.copy(alertsAfter = (it.alertsAfter + sec).distinct())
                    AlertMode.EVERY -> it.copy(every = sec)
                }
            }
            addingAlert = false
        },
    )
}

private enum class AlertMode { LEFT, SINCE, EVERY }

@Composable
private fun AlertDialogAdd(limit: Int, onDismiss: () -> Unit, onAdd: (AlertMode, Int) -> Unit) {
    var mode by remember { mutableStateOf(AlertMode.LEFT) }
    var text by remember { mutableStateOf("0:30") }
    val sec = parseDuration(text)
    val ok = sec != null && sec in 1 until limit
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == AlertMode.LEFT, onClick = { mode = AlertMode.LEFT }, label = { Text("Time left") })
                    FilterChip(selected = mode == AlertMode.SINCE, onClick = { mode = AlertMode.SINCE }, label = { Text("Since the step started") })
                    FilterChip(selected = mode == AlertMode.EVERY, onClick = { mode = AlertMode.EVERY }, label = { Text("Repeating, every…") })
                }
                OutlinedTextField(text, { text = it }, label = { Text(if (mode == AlertMode.EVERY) "Every (m:ss)" else "m:ss") }, singleLine = true, isError = !ok,
                    supportingText = { Text(if (mode == AlertMode.EVERY) "Pulses at each multiple, e.g. 0:30, 1:00, 1:30…" else "Between 0:01 and ${fmtClock((limit - 1) * 1000L)}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onAdd(mode, sec!!) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
