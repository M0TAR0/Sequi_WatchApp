package app.sequi.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sequi.data.Library
import app.sequi.data.SequiSync
import app.sequi.engine.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimersScreen(lib: Library, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val timers by lib.sequences.collectAsState()
    val watch by WatchLink.watchName.collectAsState()
    val checked by WatchLink.checked.collectAsState()

    fun toast(msg: String) = android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
    val backup = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(lib.exportJson().toByteArray()) }
        }.onSuccess { toast("Backup saved") }.onFailure { toast("Could not save the backup") }
    }
    val restore = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            lib.importJson(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
        }.onSuccess { (t, a, w) -> toast("Restored $t timers, $a alarms, $w new workouts") }.onFailure { toast("That file is not a Sequi backup") }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Sequi") },
                actions = {
                    IconButton(onClick = { WatchLink.refresh(ctx); SequiSync.pull(); SequiSync.push() }) { Icon(Icons.Filled.Refresh, "Sync now") }
                    OverflowMenu(listOf(
                        "Back up everything…" to { backup.launch("sequi-backup.json") },
                        "Restore from backup…" to { restore.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        "Paste a shared timer" to {
                            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            val text = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            runCatching { lib.importShared(text) }
                                .onSuccess { toast("Added “${it.name}”") }.onFailure { toast("Copy a shared Sequi timer first") }
                        },
                    ))
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onOpen("new") }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New timer") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                val text = when {
                    !checked -> "Checking watch…"
                    watch != null -> "Synced with $watch"
                    else -> "Watch not connected — changes sync when it is"
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { WatchLink.refresh(ctx) })
            }
            items(timers, key = { it.id }) { s -> TimerCard(s, onClick = { onOpen(s.id) }) }
        }
    }
}

@Composable
private fun TimerCard(s: Sequence, onClick: () -> Unit) {
    val total = remember(s.spec) { fmtClock(s.spec.totalSeconds() * 1000L) }
    val summary = remember(s.spec) { s.spec.summary() }
    val strip = remember(s.spec) { s.spec.preview() }
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.titleLarge)
                Text(total, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PreviewStrip(strip)
        }
    }
}

@Composable
fun PreviewStrip(parts: List<Pair<Float, StepKind>>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        var x = 0f
        val gap = 1.5.dp.toPx()
        parts.forEach { (frac, kind) ->
            val w = (size.width * frac - gap).coerceAtLeast(1.dp.toPx())
            drawRoundRect(kindAccent(kind), Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2))
            x += size.width * frac
        }
    }
}
