package app.sequi.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * Phone <-> watch sync over the Wearable Data Layer (Bluetooth, no internet, tiny payloads).
 *  - /sequi/sequences : timers, merged both ways (last write wins, deletions are tombstones)
 *  - /sequi/alarms    : alarm clock entries, merged like timers (last write wins, tombstones)
 *  - /sequi/sessions  : workout history, merged both ways (watch is the producer)
 * Identical content never re-fires an event, so two devices converge and stop.
 */
object SequiSync {
    const val SEQUENCES = "/sequi/sequences"
    const val SESSIONS = "/sequi/sessions"
    const val ALARMS = "/sequi/alarms"

    lateinit var library: Library
    private lateinit var appContext: Context

    fun init(context: Context, lib: Library) {
        appContext = context.applicationContext
        library = lib
        lib.onLocalChange = { push() }
    }

    fun push() = runCatching {
        val client = Wearable.getDataClient(appContext)
        client.putDataItem(PutDataMapRequest.create(SEQUENCES).apply { dataMap.putString("json", library.sequencesPayload()) }
            .asPutDataRequest().setUrgent())
        client.putDataItem(PutDataMapRequest.create(ALARMS).apply { dataMap.putString("json", library.alarmsPayload()) }
            .asPutDataRequest().setUrgent())
        client.putDataItem(PutDataMapRequest.create(SESSIONS).apply { dataMap.putString("json", library.sessionsPayload()) }
            .asPutDataRequest().setUrgent())
    }

    /** Read whatever the other device already published (call on app start). */
    fun pull() = runCatching {
        Wearable.getDataClient(appContext).getDataItems(Uri.parse("wear://*/sequi"), com.google.android.gms.wearable.DataClient.FILTER_PREFIX)
            .addOnSuccessListener { buf ->
                var needPush = false
                buf.forEach { item -> needPush = handle(item.uri.path, DataMapItem.fromDataItem(item).dataMap.getString("json")) || needPush }
                buf.release()
                if (needPush) push()
            }
    }

    internal fun handle(path: String?, json: String?): Boolean {
        if (json == null) return false
        return runCatching {
            when (path) {
                SEQUENCES -> library.mergeRemoteSequences(Codec.sequencesFromJson(json))
                ALARMS -> library.mergeRemoteAlarms(AlarmCodec.listFromJson(json))
                SESSIONS -> library.mergeRemoteSessions(Codec.sessionsFromJson(json))
                else -> false
            }
        }.getOrDefault(false)
    }

    internal fun onEvents(events: DataEventBuffer) {
        var needPush = false
        events.filter { it.type == DataEvent.TYPE_CHANGED }.forEach {
            val json = DataMapItem.fromDataItem(it.dataItem).dataMap.getString("json")
            needPush = handle(it.dataItem.uri.path, json) || needPush
        }
        if (needPush) push()
    }
}

class SequiSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) = SequiSync.onEvents(events)
}
