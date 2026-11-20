package app.sequi.timer

import app.sequi.engine.StepKind
import kotlinx.coroutines.flow.MutableStateFlow

data class RunSummary(val activeSec: Int, val pausedSec: Int, val avgHr: Int?, val maxHr: Int?, val completed: Boolean)

/** What the UI sees. The service owns the truth and publishes here. */
data class RunState(
    val active: Boolean = false,
    val name: String = "",
    val label: String = "",
    val kind: StepKind = StepKind.WORK,
    val remainingMs: Long = 0,
    val durationMs: Long = 0,
    val round: Int = 0,
    val rounds: Int = 0,
    val paused: Boolean = false,
    val bpm: Int? = null,
    val nextLabel: String? = null,
    val nextSeconds: Int = 0,
    val summary: RunSummary? = null, // non-null once finished/stopped
)

object TimerBus {
    val state = MutableStateFlow(RunState())
    fun clear() { state.value = RunState() }
}
