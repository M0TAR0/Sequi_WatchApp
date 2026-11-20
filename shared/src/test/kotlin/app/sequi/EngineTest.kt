package app.sequi

import app.sequi.engine.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EngineTest {
    private val intervals = Sequence("a", "x", Spec.Intervals(workSec = 60, restSec = 5, rounds = 4))

    @Test fun intervalsDropTrailingRest() {
        val t = intervals.timeline(prepSec = 0)
        // 4 work + 3 rest
        assertEquals(7, t.steps.size)
        assertEquals(StepKind.WORK, t.steps.last().kind)
        assertEquals((4 * 60 + 3 * 5) * 1000L, t.totalMs)
        assertEquals(4 * 60 + 3 * 5, intervals.spec.totalSeconds())
    }

    @Test fun prepIsAddedFirst() {
        val t = intervals.timeline(prepSec = 3)
        assertEquals(StepKind.PREP, t.steps.first().kind)
        assertEquals(3000L, t.steps[1].startMs)
    }

    @Test fun zeroRestSkipsRestSteps() {
        val emom = Sequence("e", "emom", Spec.Intervals(60, 0, 10))
        assertEquals(10, emom.timeline(0).steps.size)
    }

    @Test fun countdownMilestones() {
        val s = Sequence("c", "10", Spec.Countdown(600, listOf(300, 120, 60)))
        val t = s.timeline(0)
        val m = t.cues.filter { it.type == CueType.MILESTONE }
        assertEquals(listOf(300_000L, 480_000L, 540_000L), m.map { it.atMs })
        assertEquals(CueType.FINISH, t.cues.last().type)
        assertEquals(600_000L, t.cues.last().atMs)
    }

    @Test fun alertLongerThanTimerIsIgnored() {
        val s = Sequence("c", "1", Spec.Countdown(60, listOf(300, 30)))
        assertEquals(1, s.timeline(0).cues.count { it.type == CueType.MILESTONE })
    }

    @Test fun cuesAreDeliveredExactlyOnceAcrossWindows() {
        val t = intervals.timeline(3)
        val all = t.cues
        val collected = ArrayList<Cue>()
        var last = -1L
        var now = 0L
        while (now <= t.totalMs + 500) {
            collected += t.cuesBetween(last, now)
            last = now
            now += 137 // deliberately awkward step size
        }
        assertEquals(all, collected)
    }

    @Test fun positionAt() {
        val t = intervals.timeline(0)
        val p = t.positionAt(61_000)
        assertEquals(StepKind.REST, p.step.kind)
        assertEquals(4000L, p.remainingMs)
        assertTrue(t.positionAt(t.totalMs).finished)
    }

    @Test fun stepStartAtZero() {
        val t = intervals.timeline(0)
        assertEquals(CueType.STEP_START, t.cuesBetween(-1, 0).single().type)
    }

    @Test fun statsDaily() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 19)
        val ms = today.atTime(18, 30).atZone(zone).toInstant().toEpochMilli()
        val s = listOf(
            Session(ms, "a", 600, 600, 0, true, 140, 170, 100),
            Session(ms + 3_600_000, "b", 300, 300, 0, true, 120, 150, 90),
        )
        val d = dailyStats(s, 7, today, zone)
        assertEquals(7, d.size)
        assertEquals(2, d.last().sessions)
        assertEquals(900, d.last().activeSec)
        assertEquals(133, d.last().avgHr) // (140*600+120*300)/900
        assertEquals(2, hourHistogram(s, zone)[18] + hourHistogram(s, zone)[19])
    }
}
