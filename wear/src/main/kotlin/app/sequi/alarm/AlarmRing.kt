package app.sequi.alarm

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.sequi.R
import app.sequi.data.Repo
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

/** Vibrates (and optionally rings) until dismissed, snoozed, or 60 s have passed. */
class AlarmRingService : Service() {
    private var ringtone: Ringtone? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> { stopRinging(); return START_NOT_STICKY }
            ACTION_SNOOZE -> { AlarmScheduler.snooze(this); stopRinging(); return START_NOT_STICKY }
        }
        Repo.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false)
            })
        }
        startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        val vib = getSystemService(VibratorManager::class.java).defaultVibrator
        val pattern = longArrayOf(0, 800, 400, 800, 400, 1200, 1000)
        val amps = intArrayOf(0, 255, 0, 255, 0, 255, 0)
        vib.vibrate(VibrationEffect.createWaveform(pattern, amps, 0), VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))

        if (Repo.settings.value.sound) {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                isLooping = true
                play()
            }
        }
        scope.launch { delay(60_000); stopRinging() }
        return START_NOT_STICKY
    }

    private fun stopRinging() {
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        ringtone?.stop()
        sendBroadcast(Intent(AlarmRingActivity.ACTION_CLOSE).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun action(a: String, req: Int) = PendingIntent.getService(
        this, req, Intent(this, AlarmRingService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)

    private fun notification(): Notification {
        val full = PendingIntent.getActivity(this, 0, Intent(this, AlarmRingActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Alarm")
            .setContentText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Snooze", action(ACTION_SNOOZE, 2))
            .addAction(0, "Dismiss", action(ACTION_DISMISS, 3))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "alarm"
        private const val ID = 2
        const val ACTION_DISMISS = "app.sequi.ALARM_DISMISS"
        const val ACTION_SNOOZE = "app.sequi.ALARM_SNOOZE"
    }
}

class AlarmRingActivity : ComponentActivity() {
    private val closer = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        androidx.core.content.ContextCompat.registerReceiver(
            this, closer, android.content.IntentFilter(ACTION_CLOSE), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            app.sequi.ui.SequiTheme {
                AppScaffold {
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { send(AlarmRingService.ACTION_DISMISS) }, modifier = Modifier.fillMaxWidth()) { Text("Dismiss") }
                        FilledTonalButton(onClick = { send(AlarmRingService.ACTION_SNOOZE) }, modifier = Modifier.fillMaxWidth()) { Text("Snooze 10 min") }
                    }
                }
            }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmRingService::class.java).setAction(action))
        finish()
    }

    override fun onDestroy() { unregisterReceiver(closer); super.onDestroy() }

    companion object { const val ACTION_CLOSE = "app.sequi.ALARM_CLOSE" }
}
