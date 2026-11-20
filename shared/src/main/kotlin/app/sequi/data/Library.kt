package app.sequi.data

import app.sequi.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

/**
 * Timers + workout history, stored as two small JSON files. Used identically by the
 * watch and the phone; [onLocalChange] lets the app push edits to the other device.
 */
class Library(private val dir: File) {
    private var all: List<Sequence> = emptyList() // includes tombstones
    private val _sequences = MutableStateFlow<List<Sequence>>(emptyList())
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    private var allAlarms: List<Alarm> = emptyList() // includes tombstones
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms
    val sequences: StateFlow<List<Sequence>> = _sequences
    val sessions: StateFlow<List<Session>> = _sessions

    var onLocalChange: (() -> Unit)? = null
    /** Called after alarms changed because of a sync from the other device (the watch re-arms the OS alarm). */
    var onRemoteAlarmsChanged: (() -> Unit)? = null

    init {
        val seqFile = File(dir, "sequences.json")
        all = if (seqFile.exists()) runCatching { Codec.sequencesFromJson(seqFile.readText()) }.getOrDefault(Presets.all())
        else Presets.all()
        publishSequences()
        val af = File(dir, "alarms.json")
        allAlarms = if (af.exists()) runCatching { AlarmCodec.listFromJson(af.readText()) }.getOrDefault(emptyList()) else emptyList()
        publishAlarms()
        val sf = File(dir, "sessions.json")
        _sessions.value = if (sf.exists()) runCatching { Codec.sessionsFromJson(sf.readText()) }.getOrDefault(emptyList()) else emptyList()
    }

    fun sequence(id: String) = _sequences.value.firstOrNull { it.id == id }
    fun newId() = UUID.randomUUID().toString().take(8)

    @Synchronized fun save(s: Sequence) {
        val v = s.copy(updatedAt = System.currentTimeMillis(), deleted = false)
        all = if (all.any { it.id == v.id }) all.map { if (it.id == v.id) v else it } else all + v
        persistSequences(); onLocalChange?.invoke()
    }

    @Synchronized fun delete(id: String) {
        all = all.map { if (it.id == id) it.copy(deleted = true, updatedAt = System.currentTimeMillis()) else it }
        persistSequences(); onLocalChange?.invoke()
    }

    fun alarm(id: String) = _alarms.value.firstOrNull { it.id == id }

    @Synchronized fun saveAlarm(a: Alarm) {
        val v = a.copy(updatedAt = System.currentTimeMillis(), deleted = false)
        allAlarms = if (allAlarms.any { it.id == v.id }) allAlarms.map { if (it.id == v.id) v else it } else allAlarms + v
        persistAlarms(); onLocalChange?.invoke()
    }

    @Synchronized fun deleteAlarm(id: String) {
        allAlarms = allAlarms.map { if (it.id == id) it.copy(deleted = true, updatedAt = System.currentTimeMillis()) else it }
        persistAlarms(); onLocalChange?.invoke()
    }

    @Synchronized fun addSession(s: Session) {
        _sessions.value = mergeSessions(_sessions.value, listOf(s))
        persistSessions(); onLocalChange?.invoke()
    }

    // ---- backup / share ----
    /** Everything (timers, alarms, history) as one JSON document, for a backup file. */
    fun exportJson(): String = org.json.JSONObject().put("app", "sequi").put("v", 1)
        .put("timers", org.json.JSONArray(Codec.sequencesToJson(all.filterNot { it.deleted })))
        .put("alarms", org.json.JSONArray(AlarmCodec.listToJson(allAlarms.filterNot { it.deleted })))
        .put("sessions", org.json.JSONArray(Codec.sessionsToJson(_sessions.value)))
        .toString()

    /** Merge a backup in (newer edits win, nothing is deleted). Returns "timers/alarms/workouts" counts added or updated. */
    @Synchronized fun importJson(text: String): Triple<Int, Int, Int> {
        val o = org.json.JSONObject(text)
        require(o.optString("app") == "sequi") { "Not a Sequi backup" }
        val t = Codec.sequencesFromJson(o.optJSONArray("timers")?.toString() ?: "[]")
        val a = AlarmCodec.listFromJson(o.optJSONArray("alarms")?.toString() ?: "[]")
        val h = Codec.sessionsFromJson(o.optJSONArray("sessions")?.toString() ?: "[]")
        // Restored items are stamped "now" so they also win over a deleted copy on the other device.
        val now = System.currentTimeMillis()
        all = mergeSequences(all, t.map { it.copy(updatedAt = maxOf(it.updatedAt, now), deleted = false) })
        allAlarms = mergeAlarms(allAlarms, a.map { it.copy(updatedAt = maxOf(it.updatedAt, now), deleted = false) })
        val before = _sessions.value.size
        _sessions.value = mergeSessions(_sessions.value, h)
        persistSequences(); persistAlarms(); persistSessions()
        onRemoteAlarmsChanged?.invoke(); onLocalChange?.invoke()
        return Triple(t.size, a.size, _sessions.value.size - before)
    }

    /** One timer as shareable text; [importShared] reads it back (always as a new timer). */
    fun shareText(s: Sequence): String = Codec.sequenceToJson(s.copy(deleted = false)).put("app", "sequi").toString()

    fun importShared(text: String): Sequence {
        val o = org.json.JSONObject(text.trim())
        require(o.optString("app") == "sequi") { "Not a Sequi timer" }
        val s = Codec.sequenceFromJson(o).copy(id = newId())
        save(s)
        return s
    }

    // ---- sync ----
    /** Everything including tombstones, for sending to the other device. */
    fun sequencesPayload(): String = Codec.sequencesToJson(all)
    fun alarmsPayload(): String = AlarmCodec.listToJson(allAlarms)
    /** Most recent sessions only: a Data Layer item is limited to ~100 KB. */
    fun sessionsPayload(): String = Codec.sessionsToJson(_sessions.value.takeLast(400))

    /** Returns true if our state differs from [remote] afterwards (so the caller should push back). */
    @Synchronized fun mergeRemoteSequences(remote: List<Sequence>): Boolean {
        val merged = mergeSequences(all, remote)
        if (merged != all) { all = merged; persistSequences() }
        return Codec.sequencesToJson(all) != Codec.sequencesToJson(remote)
    }

    @Synchronized fun mergeRemoteAlarms(remote: List<Alarm>): Boolean {
        val merged = mergeAlarms(allAlarms, remote)
        if (merged != allAlarms) { allAlarms = merged; persistAlarms(); onRemoteAlarmsChanged?.invoke() }
        return AlarmCodec.listToJson(allAlarms) != AlarmCodec.listToJson(remote)
    }

    @Synchronized fun mergeRemoteSessions(remote: List<Session>): Boolean {
        val merged = mergeSessions(_sessions.value, remote)
        if (merged != _sessions.value) { _sessions.value = merged; persistSessions() }
        return Codec.sessionsToJson(_sessions.value.takeLast(400)) != Codec.sessionsToJson(remote.takeLast(400))
    }

    private fun publishSequences() { _sequences.value = all.filterNot { it.deleted } }

    private fun persistSequences() {
        publishSequences()
        write("sequences.json", Codec.sequencesToJson(all))
    }

    private fun publishAlarms() { _alarms.value = allAlarms.filterNot { it.deleted } }

    private fun persistAlarms() {
        publishAlarms()
        write("alarms.json", AlarmCodec.listToJson(allAlarms))
    }

    private fun persistSessions() = write("sessions.json", Codec.sessionsToJson(_sessions.value))

    private fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }
}

object Presets {
    fun all(): List<Sequence> = listOf(
        Sequence("p-10min", "10 min + alerts", Spec.Countdown(600, listOf(300, 120, 60))),
        Sequence("p-1x5", "4 × 1 min / 5 s", Spec.Intervals(60, 5, 4)),
        Sequence("p-tabata", "Tabata", Spec.Intervals(20, 10, 8)),
        Sequence("p-hiit", "HIIT 40/20", Spec.Intervals(40, 20, 6, warmupSec = 60, cooldownSec = 60)),
        Sequence("p-emom", "EMOM 10", Spec.Intervals(60, 0, 10)),
        Sequence("p-boxing", "Boxing 3 rounds", Spec.Intervals(180, 60, 3)),
        Sequence("p-plank", "Plank 1 min", Spec.Countdown(60, listOf(30, 10))),
    )
}
