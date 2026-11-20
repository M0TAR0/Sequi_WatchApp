package app.sequi.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import app.sequi.R
import app.sequi.data.Repo
import app.sequi.engine.*
import app.sequi.ui.MainActivity
import kotlinx.coroutines.*

/**
 * Runs one workout. Time comes from SystemClock.elapsedRealtime() (monotonic, keeps
 * counting in deep sleep), minus the time spent paused. Wakes about once a second.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var feedback: Feedback
    private var hr: HeartRateTracker? = null

    private lateinit var seq: Sequence
    private lateinit var timeline: Timeline
    private var startAt = 0L
    private var pausedTotal = 0L
    private var pauseAt = -1L // >=0 while paused
    private var startEpoch = 0L

    // heart-rate accumulation
    private var hrSum = 0L; private var hrN = 0; private var hrMax = 0; private var hrMin = Int.MAX_VALUE
    @Volatile private var bpm: Int? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        feedback = Feedback(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getStringExtra(EXTRA_ID).orEmpty())
            ACTION_TOGGLE -> togglePause()
            ACTION_STOP -> finish(completed = false)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(id: String) {
        if (job?.isActive == true) return
        val s = Repo.sequence(id) ?: run { stopSelf(); return }
        val settings = Repo.settings.value
        seq = s
        timeline = s.timeline(settings.prepSec)
        pausedTotal = 0; pauseAt = -1
        hrSum = 0; hrN = 0; hrMax = 0; hrMin = Int.MAX_VALUE; bpm = null
        startAt = SystemClock.elapsedRealtime()
        startEpoch = System.currentTimeMillis()

        val useHr = settings.heartRate && HrPermission.granted(this)
        val type = if (useHr) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification(timeline.steps.first(), startAt), type)

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sequi:timer")
            .apply { acquire(timeline.totalMs + 3 * 3600_000L) }

        if (useHr) {
            hr = HeartRateTracker(this) { v ->
                if (v > 0) { hrSum += v; hrN++; if (v > hrMax) hrMax = v; if (v < hrMin) hrMin = v; bpm = v }
            }.also { runCatching { it.start() } }
        }

        if (settings.voice) feedback.prepareVoice()
        Repo.markUsed(id)
        job = scope.launch { loop(settings.sound, settings.ticks, settings.voice) }
    }

    private fun elapsed(now: Long) = (if (pauseAt >= 0) pauseAt else now) - startAt - pausedTotal

    private suspend fun loop(sound: Boolean, ticks: Boolean, voice: Boolean) {
        var last = -1L
        var lastKey = -1L
        var lastStep = -1
        while (currentCoroutineContext().isActive) {
            val now = SystemClock.elapsedRealtime()
            val e = elapsed(now)
            if (pauseAt < 0) {
                var cues = timeline.cuesBetween(last, e)
                if (!ticks) cues = cues.filter { it.type != CueType.TICK }
                // If we woke up very late, only play the most relevant cue (never a burst).
                if (cues.isNotEmpty()) {
                    val toPlay = if (e - last > 2000 && last >= 0)
                        listOf(cues.firstOrNull { it.type == CueType.FINISH } ?: cues.last()) else cues
                    toPlay.forEach { feedback.play(it, sound) }
                    if (voice) toPlay.forEach { c -> voiceLine(c)?.let(feedback::speak) }
                }
                last = e
            }
            val pos = timeline.positionAt(e)
            if (pos.finished) { finish(completed = true); return }
            // Publish when the displayed second / step / pause state changes.
            val displaySec = (pos.remainingMs + 999) / 1000
            val key = displaySec * 1000 + pos.stepIndex * 10 + (if (pauseAt >= 0) 1 else 0)
            if (key != lastKey) {
                lastKey = key
                publish(pos)
                if (pos.stepIndex != lastStep) {
                    lastStep = pos.stepIndex
                    notifyOngoing(pos.step, now + pos.remainingMs)
                }
            } else if (bpm != TimerBus.state.value.bpm) publish(pos)
            // Sleep until the next whole-second boundary of the current step.
            val rem = pos.remainingMs % 1000
            delay(if (pauseAt >= 0) 500 else (if (rem == 0L) 1000 else rem).coerceAtLeast(20))
        }
    }

    /** What to say for a cue, or null for silence. Ticks and alerts stay haptic-only. */
    private fun voiceLine(c: Cue): String? = when (c.type) {
        CueType.STEP_START -> timeline.steps.getOrNull(c.stepIndex)?.let { st ->
            when {
                st.kind == StepKind.PREP -> null
                st.rounds > 1 && st.kind == StepKind.WORK -> "${st.label}. Round ${st.round} of ${st.rounds}"
                else -> st.label
            }
        }
        CueType.FINISH -> "Done"
        else -> null
    }

    private fun publish(pos: Position) {
        TimerBus.state.value = RunState(
            active = true, name = seq.name, label = pos.step.label, kind = pos.step.kind,
            remainingMs = (pos.remainingMs + 999) / 1000 * 1000, durationMs = pos.step.durationMs,
            round = pos.step.round, rounds = pos.step.rounds, paused = pauseAt >= 0, bpm = bpm,
            nextLabel = timeline.steps.getOrNull(pos.stepIndex + 1)?.label,
            nextSeconds = ((timeline.steps.getOrNull(pos.stepIndex + 1)?.durationMs ?: 0L) / 1000).toInt(),
        )
    }

    private fun togglePause() {
        if (job?.isActive != true) return
        val now = SystemClock.elapsedRealtime()
        if (pauseAt < 0) pauseAt = now else { pausedTotal += now - pauseAt; pauseAt = -1 }
        val pos = timeline.positionAt(elapsed(now))
        publish(pos)
        notifyOngoing(pos.step, now + pos.remainingMs)
    }

    private fun finish(completed: Boolean) {
        if (job?.isActive != true) { stopSelf(); return }
        val now = SystemClock.elapsedRealtime()
        val activeSec = (elapsed(now).coerceAtMost(timeline.totalMs) / 1000).toInt()
        val paused = (pausedTotal + if (pauseAt >= 0) now - pauseAt else 0) / 1000
        job?.cancel()
        runCatching { hr?.stop() }
        feedback.let { if (!completed) it.cancel() }
        wakeLock?.let { if (it.isHeld) it.release() }
        val avg = if (hrN > 0) (hrSum / hrN).toInt() else null
        val summary = RunSummary(activeSec, paused.toInt(), avg, if (hrN > 0) hrMax else null, completed)
        if (activeSec >= 10) {
            Repo.addSession(
                Session(startEpoch, seq.name, seq.spec.totalSeconds(), activeSec, paused.toInt(), completed,
                    avg, if (hrN > 0) hrMax else null, if (hrN > 0) hrMin else null)
            )
        }
        TimerBus.state.value = TimerBus.state.value.copy(active = false, paused = false, summary = summary)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        feedback.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    // ---------- notification / ongoing activity ----------
    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null); enableVibration(false)
                }
            )
        }
    }

    private fun touchIntent() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun builder(step: FlatStep) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(seq.name)
        .setContentText(step.label)
        .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(touchIntent())

    private fun buildNotification(step: FlatStep, endsAtElapsed: Long): Notification {
        val b = builder(step)
        applyOngoing(b, step, endsAtElapsed)
        return b.build()
    }

    private fun applyOngoing(b: NotificationCompat.Builder, step: FlatStep, endsAtElapsed: Long) {
        val paused = pauseAt >= 0
        val status = Status.Builder().apply {
            if (paused) {
                addTemplate("Paused · #l#")
            } else {
                addTemplate("#t# #l#")
                addPart("t", Status.TimerPart(endsAtElapsed))
            }
            addPart("l", Status.TextPart(step.label))
        }.build()
        OngoingActivity.Builder(this, NOTIF_ID, b)
            .setStaticIcon(R.drawable.ic_stat)
            .setTouchIntent(touchIntent())
            .setStatus(status)
            .build().apply(this)
    }

    private fun notifyOngoing(step: FlatStep, endsAtElapsed: Long) {
        val b = builder(step)
        applyOngoing(b, step, endsAtElapsed)
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, b.build())
    }

    companion object {
        private const val CHANNEL = "timer"
        private const val NOTIF_ID = 1
        const val ACTION_START = "app.sequi.START"
        const val ACTION_TOGGLE = "app.sequi.TOGGLE"
        const val ACTION_STOP = "app.sequi.STOP"
        const val EXTRA_ID = "id"

        fun start(ctx: Context, id: String) = ContextCompat.startForegroundService(
            ctx, Intent(ctx, TimerService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, id))
        fun toggle(ctx: Context) = ctx.startService(Intent(ctx, TimerService::class.java).setAction(ACTION_TOGGLE))
        fun stop(ctx: Context) = ctx.startService(Intent(ctx, TimerService::class.java).setAction(ACTION_STOP))
    }
}
