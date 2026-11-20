package app.sequi.mobile

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Is a watch connected right now? Cheap one-shot check, refreshed on demand. */
object WatchLink {
    private val _watch = MutableStateFlow<String?>(null)
    private val _checked = MutableStateFlow(false)
    val watchName: StateFlow<String?> = _watch
    val checked: StateFlow<Boolean> = _checked

    fun refresh(ctx: Context) {
        runCatching {
            Wearable.getNodeClient(ctx).connectedNodes
                .addOnSuccessListener { nodes -> _watch.value = nodes.firstOrNull()?.displayName; _checked.value = true }
                .addOnFailureListener { _watch.value = null; _checked.value = true }
        }.onFailure { _checked.value = true }
    }
}
