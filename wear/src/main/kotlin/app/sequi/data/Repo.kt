package app.sequi.data

import android.content.Context
import app.sequi.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class Settings(
    val sound: Boolean = false,
    val ticks: Boolean = true,
    val heartRate: Boolean = true,
    val prepSec: Int = 3,
    val voice: Boolean = false,
)

/**
 * Watch-side store. Timers, alarms and history live in the shared [Library] (synced with the phone);
 * only settings are watch-only.
 */
object Repo {
    private lateinit var dir: File
    private lateinit var ctx: Context
    private lateinit var library: Library

    private val _settings = MutableStateFlow(Settings())
    val alarms get() = library.alarms
    val settings: StateFlow<Settings> = _settings
    val sequences get() = library.sequences
    val sessions get() = library.sessions

    @Synchronized
    fun init(context: Context) {
        if (::dir.isInitialized) return
        ctx = context.applicationContext
        dir = ctx.filesDir
        library = Library(dir)
        SequiSync.init(ctx, library)
        SequiSync.pull()
        // An alarm edited on the phone must re-arm the OS alarm here.
        library.onRemoteAlarmsChanged = { runCatching { app.sequi.alarm.AlarmScheduler.reschedule(ctx) } }
        val p = prefs()
        _settings.value = Settings(
            sound = p.getBoolean("sound", false),
            ticks = p.getBoolean("ticks", true),
            heartRate = p.getBoolean("hr", true),
            prepSec = p.getInt("prep", 3),
            voice = p.getBoolean("voice", false),
        )
    }

    private fun prefs() = ctx.getSharedPreferences("sequi", Context.MODE_PRIVATE)

    // ---- settings ----
    @Synchronized
    fun updateSettings(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        _settings.value = s
        prefs().edit().putBoolean("sound", s.sound).putBoolean("ticks", s.ticks)
            .putBoolean("hr", s.heartRate).putInt("prep", s.prepSec).putBoolean("voice", s.voice).apply()
    }

    // ---- recently started timers (for the Tile) ----
    fun recentIds(): List<String> = prefs().getString("recent", "")!!.split(",").filter { it.isNotBlank() }

    fun markUsed(id: String) {
        val list = (listOf(id) + recentIds()).distinct().take(6)
        prefs().edit().putString("recent", list.joinToString(",")).apply()
        runCatching { androidx.wear.tiles.TileService.getUpdater(ctx).requestUpdate(app.sequi.tile.QuickTile::class.java) }
    }

    /** Up to [n] timers for the Tile: most recently used first, then the rest of the list. */
    fun quickTimers(n: Int): List<Sequence> {
        val all = sequences.value
        val recent = recentIds().mapNotNull { id -> all.firstOrNull { it.id == id } }
        return (recent + all).distinctBy { it.id }.take(n)
    }

    // ---- timers & history (shared with the phone) ----
    fun sequence(id: String) = library.sequence(id)
    fun saveSequence(s: Sequence) = library.save(s)
    fun deleteSequence(id: String) = library.delete(id)
    fun newId() = library.newId()
    fun addSession(s: Session) = library.addSession(s)

    // ---- alarms (shared with the phone) ----
    fun alarm(id: String) = library.alarm(id)
    fun saveAlarm(a: Alarm) = library.saveAlarm(a)
    fun deleteAlarm(id: String) = library.deleteAlarm(id)
    fun newAlarmId() = library.newId()
}
