package app.sequi.timer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType

object HrPermission {
    /** Wear OS 6 / API 36 introduced granular health permissions. */
    val name: String
        get() = if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS

    fun granted(ctx: Context) = ctx.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
}

/** Streams heart-rate samples (BPM) from Health Services while a workout runs. */
class HeartRateTracker(context: Context, private val onBpm: (Int) -> Unit) {
    private val client = HealthServices.getClient(context).measureClient
    private var registered = false

    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {}
        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { onBpm(it.value.toInt()) }
        }
    }

    fun start() {
        if (registered) return
        registered = true
        client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
    }

    fun stop() {
        if (!registered) return
        registered = false
        client.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
    }
}
