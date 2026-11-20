package app.sequi.data

import org.json.JSONArray
import org.json.JSONObject

/** An alarm clock entry. Lives in the shared [Library] so the phone and the watch edit the same list. */
data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    /** java.time.DayOfWeek values (1 = Monday .. 7 = Sunday). Empty = one-shot. */
    val days: Set<Int>,
    val enabled: Boolean = true,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

object AlarmCodec {
    fun toJson(a: Alarm): JSONObject = JSONObject().put("id", a.id).put("h", a.hour).put("m", a.minute)
        .put("days", JSONArray(a.days.toList())).put("on", a.enabled).put("upd", a.updatedAt).put("del", a.deleted)

    /** Tolerant: files written before v0.3 have a numeric id and no timestamps. */
    fun fromJson(o: JSONObject): Alarm {
        val d = o.optJSONArray("days")
        return Alarm(
            o.get("id").toString(), o.getInt("h"), o.getInt("m"),
            if (d == null) emptySet() else (0 until d.length()).map { d.getInt(it) }.toSet(),
            o.optBoolean("on", true), o.optLong("upd", 0), o.optBoolean("del", false),
        )
    }

    fun listToJson(list: List<Alarm>): String =
        JSONArray().also { a -> list.sortedBy { it.id }.forEach { a.put(toJson(it)) } }.toString()

    fun listFromJson(text: String): List<Alarm> {
        val a = JSONArray(text)
        return (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
    }
}

/** Last-write-wins merge by id; a deletion is a newer version with deleted = true. */
fun mergeAlarms(local: List<Alarm>, remote: List<Alarm>): List<Alarm> {
    val map = LinkedHashMap<String, Alarm>()
    local.forEach { map[it.id] = it }
    remote.forEach { r ->
        val l = map[r.id]
        if (l == null || r.updatedAt > l.updatedAt) map[r.id] = r
    }
    return map.values.toList()
}

/** 12-hour clock text, e.g. "7:30 AM". */
fun fmtAlarmTime(hour: Int, minute: Int): String =
    "%d:%02d %s".format(if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")

/** 24h -> (1..12, isPm) and back, for 12-hour pickers. */
fun to12h(hour: Int): Pair<Int, Boolean> = (if (hour % 12 == 0) 12 else hour % 12) to (hour >= 12)
fun from12h(h12: Int, pm: Boolean): Int = (h12 % 12) + if (pm) 12 else 0
