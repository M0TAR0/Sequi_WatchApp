package app.sequi.engine

/** What a step *is*; drives colour, label and haptic pattern. */
enum class StepKind { PREP, WORK, REST, EASY }

/**
 * A timer definition. Countdown and Intervals are quick shapes (editable on the watch);
 * Custom is the free-form shape (built on the phone) that can express anything.
 */
sealed interface Spec {
    /** One countdown with optional alerts at "N seconds remaining". */
    data class Countdown(val seconds: Int, val alerts: List<Int>) : Spec

    /** Work/rest intervals repeated [rounds] times, optional warm-up / cool-down. [workAlerts] apply to every work step. */
    data class Intervals(
        val workSec: Int,
        val restSec: Int,
        val rounds: Int,
        val warmupSec: Int = 0,
        val cooldownSec: Int = 0,
        val workAlerts: List<Int> = emptyList(),
    ) : Spec

    /** Any number of blocks, each a list of steps repeated [CustomBlock.repeat] times. */
    data class Custom(val blocks: List<CustomBlock>) : Spec
}

/**
 * One step of a custom timer. Alerts can be placed anywhere inside it:
 * [alerts] = seconds *remaining*, [alertsAfter] = seconds *since the step started*,
 * [every] = a repeating alert every N seconds since the start (0 = off).
 */
data class CustomStep(
    val label: String,
    val kind: StepKind,
    val seconds: Int,
    val alerts: List<Int> = emptyList(),
    val alertsAfter: List<Int> = emptyList(),
    val every: Int = 0,
)

/** [steps] repeated [repeat] times. With [dropLastRest], a trailing REST step is skipped on the final repetition. */
data class CustomBlock(val steps: List<CustomStep>, val repeat: Int = 1, val dropLastRest: Boolean = true)

/** Name, spec and sync bookkeeping. [updatedAt] drives last-write-wins merging; [deleted] is a tombstone. */
data class Sequence(
    val id: String,
    val name: String,
    val spec: Spec,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

/** One step of the flattened run, with absolute position on the timeline. */
data class FlatStep(
    val label: String,
    val kind: StepKind,
    val startMs: Long,
    val durationMs: Long,
    val round: Int,
    val rounds: Int,
) {
    val endMs get() = startMs + durationMs
}

enum class CueType { STEP_START, MILESTONE, TICK, FINISH }

data class Cue(
    val atMs: Long,
    val type: CueType,
    val stepIndex: Int,
    val kind: StepKind,
    val remainingSec: Int = 0,
)

data class Position(
    val stepIndex: Int,
    val step: FlatStep,
    val stepElapsedMs: Long,
    val remainingMs: Long,
    val finished: Boolean,
)

/**
 * The whole run as a flat, immutable timeline. All logic is a pure function of
 * "active elapsed milliseconds", so it can't drift and is trivially testable.
 */
class Timeline(val steps: List<FlatStep>, private val alertsByStep: List<List<Int>> = emptyList()) {
    val totalMs: Long = steps.lastOrNull()?.endMs ?: 0L

    /** All cues, sorted by time. */
    val cues: List<Cue> = buildCues()

    private fun buildCues(): List<Cue> {
        val out = ArrayList<Cue>()
        steps.forEachIndexed { i, s ->
            out += Cue(s.startMs, CueType.STEP_START, i, s.kind)
            val alerts = alertsByStep.getOrElse(i) { emptyList() }.filter { it > 0 && it * 1000L < s.durationMs }
            for (r in alerts) out += Cue(s.endMs - r * 1000L, CueType.MILESTONE, i, s.kind, r)
            for (r in 3 downTo 1) {
                if (r * 1000L < s.durationMs && r !in alerts) {
                    out += Cue(s.endMs - r * 1000L, CueType.TICK, i, s.kind, r)
                }
            }
        }
        if (steps.isNotEmpty()) out += Cue(totalMs, CueType.FINISH, steps.lastIndex, steps.last().kind)
        return out.sortedWith(compareBy<Cue> { it.atMs }.thenBy { it.type.ordinal })
    }

    /** Cues with fromExclusive < atMs <= toInclusive. Use from = -1 for the very first call. */
    fun cuesBetween(fromExclusive: Long, toInclusive: Long): List<Cue> =
        cues.filter { it.atMs > fromExclusive && it.atMs <= toInclusive }

    fun positionAt(elapsedMs: Long): Position {
        val e = elapsedMs.coerceAtLeast(0)
        if (e >= totalMs) {
            val last = steps.last()
            return Position(steps.lastIndex, last, last.durationMs, 0, true)
        }
        val idx = steps.indexOfFirst { e < it.endMs }
        val s = steps[idx]
        return Position(idx, s, e - s.startMs, s.endMs - e, false)
    }
}

private class Builder(var t: Long = 0) {
    val steps = ArrayList<FlatStep>()
    val alerts = ArrayList<List<Int>>()
    fun add(label: String, kind: StepKind, sec: Int, round: Int = 0, rounds: Int = 0, a: List<Int> = emptyList()) {
        if (sec <= 0) return
        steps += FlatStep(label, kind, t, sec * 1000L, round, rounds)
        alerts += a
        t += sec * 1000L
    }
    fun build() = Timeline(steps, alerts)
}

/** Expand a [Sequence] into a [Timeline]. [prepSec] is the "get ready" lead-in. */
fun Sequence.timeline(prepSec: Int): Timeline {
    val b = Builder()
    b.add("Get ready", StepKind.PREP, prepSec)
    when (val s = spec) {
        is Spec.Countdown -> b.add("Timer", StepKind.WORK, s.seconds, a = s.alerts)
        is Spec.Intervals -> {
            b.add("Warm-up", StepKind.EASY, s.warmupSec)
            for (r in 1..s.rounds) {
                b.add("Work", StepKind.WORK, s.workSec, r, s.rounds, s.workAlerts)
                // No rest after the very last round.
                if (r < s.rounds) b.add("Rest", StepKind.REST, s.restSec, r, s.rounds)
            }
            b.add("Cool-down", StepKind.EASY, s.cooldownSec)
        }
        is Spec.Custom -> for (block in s.blocks) {
            val reps = block.repeat.coerceAtLeast(1)
            for (rep in 1..reps) {
                val steps = if (rep == reps && reps > 1 && block.dropLastRest)
                    block.steps.dropLastWhile { it.kind == StepKind.REST } else block.steps
                for (st in steps) {
                    val repeating = if (st.every > 0) (st.every until st.seconds step st.every).toList() else emptyList()
                    val rem = (st.alerts + (st.alertsAfter + repeating).map { st.seconds - it }).filter { it in 1 until st.seconds }.distinct()
                    b.add(st.label, st.kind, st.seconds, if (reps > 1) rep else 0, if (reps > 1) reps else 0, rem)
                }
            }
        }
    }
    return b.build()
}

/** The same timer as free-form blocks, so the phone editor can edit any shape. */
fun Spec.toCustom(): Spec.Custom = when (this) {
    is Spec.Custom -> this
    is Spec.Countdown -> Spec.Custom(listOf(CustomBlock(listOf(CustomStep("Timer", StepKind.WORK, seconds, alerts)))))
    is Spec.Intervals -> Spec.Custom(buildList {
        if (warmupSec > 0) add(CustomBlock(listOf(CustomStep("Warm-up", StepKind.EASY, warmupSec))))
        val steps = buildList {
            add(CustomStep("Work", StepKind.WORK, workSec, workAlerts))
            if (restSec > 0) add(CustomStep("Rest", StepKind.REST, restSec))
        }
        add(CustomBlock(steps, rounds))
        if (cooldownSec > 0) add(CustomBlock(listOf(CustomStep("Cool-down", StepKind.EASY, cooldownSec))))
    })
}

/** Sum of the timer itself, without the get-ready lead-in. */
fun Spec.totalSeconds(): Int = (timelineOrNull()?.totalMs ?: 0L).let { (it / 1000).toInt() }

private fun Spec.timelineOrNull(): Timeline? =
    runCatching { Sequence("", "", this).timeline(0) }.getOrNull()

fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

fun fmtSeconds(sec: Int): String = when {
    sec >= 60 && sec % 60 == 0 -> "${sec / 60} min"
    sec >= 60 -> "${sec / 60}:%02d".format(sec % 60)
    else -> "$sec s"
}

fun Spec.autoName(): String = when (this) {
    is Spec.Countdown -> fmtSeconds(seconds) + " timer"
    is Spec.Intervals -> "$rounds × ${fmtSeconds(workSec)}" + if (restSec > 0) " / ${fmtSeconds(restSec)}" else ""
    is Spec.Custom -> "Custom " + fmtSeconds(totalSeconds())
}

/** One short line describing the shape, for list cards. */
fun Spec.summary(): String = when (this) {
    is Spec.Countdown -> if (alerts.isEmpty()) "no alerts" else "${alerts.size} alert${if (alerts.size > 1) "s" else ""}"
    is Spec.Intervals -> "$rounds rounds · ${fmtSeconds(workSec)}" + if (restSec > 0) " / ${fmtSeconds(restSec)}" else ""
    is Spec.Custom -> {
        val steps = blocks.sumOf { it.steps.size }
        "${blocks.size} block${if (blocks.size > 1) "s" else ""} · $steps steps"
    }
}

/** Coloured strip preview: (fraction of the total, kind) for each step. Warm-ups etc. included, get-ready excluded. */
fun Spec.preview(): List<Pair<Float, StepKind>> {
    val t = runCatching { Sequence("", "", this).timeline(0) }.getOrNull() ?: return emptyList()
    if (t.totalMs == 0L) return emptyList()
    return t.steps.map { it.durationMs.toFloat() / t.totalMs to it.kind }
}
