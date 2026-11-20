package app.sequi.data

import app.sequi.engine.*
import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialisation shared by storage and phone<->watch sync. */
object Codec {
    private fun JSONArray.ints() = (0 until length()).map { getInt(it) }
    private fun List<Int>.json() = JSONArray(this)

    fun sequenceToJson(s: Sequence): JSONObject = JSONObject()
        .put("id", s.id).put("name", s.name).put("upd", s.updatedAt).put("del", s.deleted)
        .also { o ->
            when (val sp = s.spec) {
                is Spec.Countdown -> o.put("type", "countdown").put("seconds", sp.seconds).put("alerts", sp.alerts.json())
                is Spec.Intervals -> o.put("type", "intervals").put("work", sp.workSec).put("rest", sp.restSec)
                    .put("rounds", sp.rounds).put("warmup", sp.warmupSec).put("cooldown", sp.cooldownSec)
                    .put("walerts", sp.workAlerts.json())
                is Spec.Custom -> o.put("type", "custom").put("blocks", JSONArray().also { arr ->
                    sp.blocks.forEach { b ->
                        arr.put(JSONObject().put("repeat", b.repeat).put("dropLastRest", b.dropLastRest)
                            .put("steps", JSONArray().also { sa ->
                                b.steps.forEach { st ->
                                    sa.put(JSONObject().put("label", st.label).put("kind", st.kind.name).put("sec", st.seconds)
                                        .put("alerts", st.alerts.json()).put("after", st.alertsAfter.json()).put("every", st.every))
                                }
                            }))
                    }
                })
            }
        }

    fun sequenceFromJson(o: JSONObject): Sequence {
        val spec: Spec = when (o.getString("type")) {
            "countdown" -> Spec.Countdown(o.getInt("seconds"), o.getJSONArray("alerts").ints())
            "intervals" -> Spec.Intervals(
                o.getInt("work"), o.getInt("rest"), o.getInt("rounds"), o.getInt("warmup"), o.getInt("cooldown"),
                o.optJSONArray("walerts")?.ints() ?: emptyList(),
            )
            else -> {
                val blocks = o.getJSONArray("blocks")
                Spec.Custom((0 until blocks.length()).map { bi ->
                    val b = blocks.getJSONObject(bi)
                    val steps = b.getJSONArray("steps")
                    CustomBlock(
                        (0 until steps.length()).map { si ->
                            val st = steps.getJSONObject(si)
                            CustomStep(
                                st.getString("label"), StepKind.valueOf(st.getString("kind")), st.getInt("sec"),
                                st.optJSONArray("alerts")?.ints() ?: emptyList(), st.optJSONArray("after")?.ints() ?: emptyList(),
                                st.optInt("every", 0),
                            )
                        },
                        b.optInt("repeat", 1), b.optBoolean("dropLastRest", true),
                    )
                })
            }
        }
        return Sequence(o.getString("id"), o.getString("name"), spec, o.optLong("upd", 0), o.optBoolean("del", false))
    }

    fun sequencesToJson(list: List<Sequence>): String =
        JSONArray().also { a -> list.sortedBy { it.id }.forEach { a.put(sequenceToJson(it)) } }.toString()

    fun sequencesFromJson(text: String): List<Sequence> {
        val a = JSONArray(text)
        return (0 until a.length()).map { sequenceFromJson(a.getJSONObject(it)) }
    }

    fun sessionToJson(s: Session): JSONObject = JSONObject().put("t", s.startEpochMs).put("n", s.name)
        .put("plan", s.plannedSec).put("act", s.activeSec).put("pause", s.pausedSec).put("ok", s.completed)
        .apply { s.avgHr?.let { put("avg", it) }; s.maxHr?.let { put("max", it) }; s.minHr?.let { put("min", it) } }

    fun sessionFromJson(o: JSONObject) = Session(
        o.getLong("t"), o.getString("n"), o.getInt("plan"), o.getInt("act"), o.getInt("pause"), o.getBoolean("ok"),
        if (o.has("avg")) o.getInt("avg") else null,
        if (o.has("max")) o.getInt("max") else null,
        if (o.has("min")) o.getInt("min") else null,
    )

    fun sessionsToJson(list: List<Session>): String =
        JSONArray().also { a -> list.sortedBy { it.startEpochMs }.forEach { a.put(sessionToJson(it)) } }.toString()

    fun sessionsFromJson(text: String): List<Session> {
        val a = JSONArray(text)
        return (0 until a.length()).map { sessionFromJson(a.getJSONObject(it)) }
    }
}

/** Last-write-wins merge by id; a tombstone is just a newer version with deleted = true. */
fun mergeSequences(local: List<Sequence>, remote: List<Sequence>): List<Sequence> {
    val map = LinkedHashMap<String, Sequence>()
    local.forEach { map[it.id] = it }
    remote.forEach { r ->
        val l = map[r.id]
        if (l == null || r.updatedAt > l.updatedAt) map[r.id] = r
    }
    return map.values.toList()
}

/** Sessions are immutable; the start time identifies one. */
fun mergeSessions(local: List<Session>, remote: List<Session>): List<Session> {
    val map = LinkedHashMap<Long, Session>()
    local.forEach { map[it.startEpochMs] = it }
    remote.forEach { if (it.startEpochMs !in map) map[it.startEpochMs] = it }
    return map.values.sortedBy { it.startEpochMs }
}
