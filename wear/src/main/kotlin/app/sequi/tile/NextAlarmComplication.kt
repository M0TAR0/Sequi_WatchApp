package app.sequi.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.sequi.alarm.AlarmScheduler
import app.sequi.data.Repo
import app.sequi.ui.MainActivity
import java.time.format.TextStyle
import java.util.Locale

/** Watch-face complication: when your next alarm rings. Tap to open the app. */
class NextAlarmComplication : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, "7:30", "AM", "Mon")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Repo.init(this)
        val next = AlarmScheduler.nextAlarm()
        if (next == null) return build(request.complicationType, "--", "Alarm", "No alarm set")
        val (a, at) = next
        val h12 = if (a.hour % 12 == 0) 12 else a.hour % 12
        return build(request.complicationType, "%d:%02d".format(h12, a.minute), if (a.hour < 12) "AM" else "PM",
            at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
    }

    private fun build(type: ComplicationType, time: String, title: String, day: String): ComplicationData? {
        val tap = PendingIntent.getActivity(this, 5, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val desc = PlainComplicationText.Builder("Next alarm $time $title").build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(PlainComplicationText.Builder(time).build(), desc)
                .setTitle(PlainComplicationText.Builder(title).build()).setTapAction(tap).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(PlainComplicationText.Builder("$time $title").build(), desc)
                .setTitle(PlainComplicationText.Builder(day).build()).setTapAction(tap).build()
            else -> null
        }
    }

    companion object {
        fun refresh(ctx: Context) = runCatching {
            ComplicationDataSourceUpdateRequester.create(ctx, ComponentName(ctx, NextAlarmComplication::class.java)).requestUpdateAll()
        }
    }
}
