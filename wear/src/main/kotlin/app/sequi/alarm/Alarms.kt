package app.sequi.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sequi.data.Alarm
import app.sequi.data.Repo
import app.sequi.ui.MainActivity
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {
    private const val SNOOZE_KEY = "snoozeAt"

    /** Next trigger for [a] strictly after [from], or null if it's disabled. */
    fun nextTrigger(a: Alarm, from: LocalDateTime): LocalDateTime? {
        if (!a.enabled) return null
        for (add in 0..7) {
            val day = from.toLocalDate().plusDays(add.toLong())
            val cand = day.atTime(a.hour, a.minute)
            val ok = a.days.isEmpty() || day.dayOfWeek.value in a.days
            if (ok && cand.isAfter(from)) return cand
        }
        return null
    }

    /** The alarm that will ring next, with its time, or null. */
    fun nextAlarm(): Pair<Alarm, LocalDateTime>? {
        val now = LocalDateTime.now()
        return Repo.alarms.value.mapNotNull { a -> nextTrigger(a, now)?.let { a to it } }.minByOrNull { it.second }
    }

    /** (Re)schedule the single OS alarm for whichever of our alarms is next, and refresh the watch-face complication. */
    fun reschedule(ctx: Context) {
        arm(ctx)
        app.sequi.tile.NextAlarmComplication.refresh(ctx)
    }

    private fun arm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val times = Repo.alarms.value.mapNotNull { nextTrigger(it, now) }
            .map { it.atZone(zone).toInstant().toEpochMilli() }.toMutableList()
        val snooze = ctx.getSharedPreferences("sequi", Context.MODE_PRIVATE).getLong(SNOOZE_KEY, 0)
        if (snooze > System.currentTimeMillis()) times += snooze
        val pi = fireIntent(ctx)
        val next = times.minOrNull()
        if (next == null) { am.cancel(pi); return }
        val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        // Never let scheduling crash the app: a throw here used to make it fail on every launch.
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(next, show), pi)
        } catch (e: SecurityException) {
            android.util.Log.e("Sequi", "Exact alarm not permitted, using inexact fallback", e)
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi) }
        }
    }

    fun snooze(ctx: Context, minutes: Int = 10) {
        ctx.getSharedPreferences("sequi", Context.MODE_PRIVATE).edit()
            .putLong(SNOOZE_KEY, System.currentTimeMillis() + minutes * 60_000L).apply()
        reschedule(ctx)
    }

    private fun fireIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, AlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** One-shot alarms switch themselves off once they have rung. */
    fun markFired(ctx: Context) {
        val now = LocalDateTime.now()
        Repo.alarms.value.filter { it.enabled && it.days.isEmpty() }.forEach {
            // A one-shot is "due" if its time today has just passed (within 2 minutes).
            val t = now.toLocalDate().atTime(it.hour, it.minute)
            if (!t.isAfter(now) && t.plusMinutes(2).isAfter(now)) Repo.saveAlarm(it.copy(enabled = false))
        }
    }

    fun daysLabel(days: Set<Int>): String = when {
        days.isEmpty() -> "Once"
        days.size == 7 -> "Every day"
        days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
        days == setOf(6, 7) -> "Weekends"
        else -> days.sorted().joinToString(" ") { DayOfWeek.of(it).name.take(2).lowercase().replaceFirstChar(Char::uppercase) }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        ctx.getSharedPreferences("sequi", Context.MODE_PRIVATE).edit().remove("snoozeAt").apply()
        AlarmScheduler.markFired(ctx)
        AlarmScheduler.reschedule(ctx)
        ctx.startForegroundService(Intent(ctx, AlarmRingService::class.java))
    }
}

/** Alarms are lost on reboot / time change, so put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        AlarmScheduler.reschedule(ctx)
    }
}
