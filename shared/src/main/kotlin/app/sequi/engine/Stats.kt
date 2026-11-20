package app.sequi.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A finished (or stopped) workout, as stored in history. */
data class Session(
    val startEpochMs: Long,
    val name: String,
    val plannedSec: Int,
    val activeSec: Int,
    val pausedSec: Int,
    val completed: Boolean,
    val avgHr: Int?,
    val maxHr: Int?,
    val minHr: Int?,
)

data class DayStat(val date: LocalDate, val sessions: Int, val activeSec: Int, val avgHr: Int?)

data class PeriodSummary(
    val sessions: Int,
    val activeSec: Int,
    val avgHr: Int?,
    val maxHr: Int?,
)

/** Last [days] days ending at [today] (inclusive), oldest first. Days with no workout are zeros. */
fun dailyStats(sessions: List<Session>, days: Int, today: LocalDate, zone: ZoneId): List<DayStat> {
    val byDay = sessions.groupBy { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }
    return (days - 1 downTo 0).map { back ->
        val d = today.minusDays(back.toLong())
        val list = byDay[d].orEmpty()
        val withHr = list.filter { it.avgHr != null && it.activeSec > 0 }
        val hr = if (withHr.isEmpty()) null else
            (withHr.sumOf { it.avgHr!!.toLong() * it.activeSec } / withHr.sumOf { it.activeSec.toLong() }).toInt()
        DayStat(d, list.size, list.sumOf { it.activeSec }, hr)
    }
}

fun summarize(stats: List<DayStat>, sessions: List<Session>, today: LocalDate, zone: ZoneId): PeriodSummary {
    val from = stats.first().date
    val inRange = sessions.filter {
        val d = Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate()
        !d.isBefore(from) && !d.isAfter(today)
    }
    val withHr = inRange.filter { it.avgHr != null && it.activeSec > 0 }
    val avg = if (withHr.isEmpty()) null else
        (withHr.sumOf { it.avgHr!!.toLong() * it.activeSec } / withHr.sumOf { it.activeSec.toLong() }).toInt()
    return PeriodSummary(inRange.size, inRange.sumOf { it.activeSec }, avg, inRange.mapNotNull { it.maxHr }.maxOrNull())
}

/** How many workouts started in each hour of the day (0..23), over the sessions given. */
fun hourHistogram(sessions: List<Session>, zone: ZoneId): IntArray {
    val h = IntArray(24)
    sessions.forEach { h[Instant.ofEpochMilli(it.startEpochMs).atZone(zone).hour]++ }
    return h
}
