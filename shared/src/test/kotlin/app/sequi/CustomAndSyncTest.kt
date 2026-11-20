package app.sequi

import app.sequi.data.*
import app.sequi.engine.*
import org.junit.Assert.*
import org.junit.Test

class CustomAndSyncTest {
    private val custom = Spec.Custom(listOf(
        CustomBlock(listOf(CustomStep("Warm-up", StepKind.EASY, 120))),
        CustomBlock(listOf(
            CustomStep("Sprint", StepKind.WORK, 30, alerts = listOf(10), alertsAfter = listOf(5)),
            CustomStep("Walk", StepKind.REST, 15),
        ), repeat = 3),
        CustomBlock(listOf(CustomStep("Cool-down", StepKind.EASY, 60))),
    ))

    @Test fun customFlattensWithRepeatsAndDropsLastRest() {
        val t = Sequence("c", "c", custom).timeline(0)
        // 1 warm-up + 3 sprints + 2 walks + 1 cool-down
        assertEquals(7, t.steps.size)
        assertEquals((120 + 3 * 30 + 2 * 15 + 60) * 1000L, t.totalMs)
        assertEquals(3, t.steps[1].rounds)
        assertEquals(300, custom.totalSeconds())
    }

    @Test fun customAlertsRemainingAndElapsed() {
        val t = Sequence("c", "c", custom).timeline(0)
        val first = t.steps[1]
        val ms = t.cues.filter { it.type == CueType.MILESTONE && it.stepIndex == 1 }.map { it.atMs - first.startMs }
        // 5 s after start = 25 s remaining; 10 s remaining = 20 s after start
        assertEquals(listOf(5_000L, 20_000L), ms)
    }

    @Test fun toCustomKeepsTimeline() {
        val iv = Spec.Intervals(40, 20, 6, 60, 60, workAlerts = listOf(10))
        val a = Sequence("a", "a", iv).timeline(3)
        val b = Sequence("a", "a", iv.toCustom()).timeline(3)
        assertEquals(a.totalMs, b.totalMs)
        assertEquals(a.cues.map { it.atMs to it.type }, b.cues.map { it.atMs to it.type })
        val cd = Spec.Countdown(600, listOf(300, 60))
        assertEquals(Sequence("a", "a", cd).timeline(0).cues.size, Sequence("a", "a", cd.toCustom()).timeline(0).cues.size)
    }

    @Test fun codecRoundTrip() {
        val list = listOf(
            Sequence("1", "x", custom, 5),
            Sequence("2", "y", Spec.Intervals(20, 10, 8, workAlerts = listOf(5)), 6),
            Sequence("3", "z", Spec.Countdown(60, listOf(30)), 7, deleted = true),
        )
        assertEquals(list, Codec.sequencesFromJson(Codec.sequencesToJson(list)))
        val s = Session(1, "a", 10, 10, 0, true, 100, 120, 90)
        assertEquals(listOf(s), Codec.sessionsFromJson(Codec.sessionsToJson(listOf(s))))
    }

    @Test fun mergeLastWriteWinsAndTombstones() {
        val a = Sequence("1", "old", Spec.Countdown(60, emptyList()), 10)
        val b = a.copy(name = "new", updatedAt = 20)
        assertEquals("new", mergeSequences(listOf(a), listOf(b)).single().name)
        assertEquals("new", mergeSequences(listOf(b), listOf(a)).single().name)
        val gone = a.copy(deleted = true, updatedAt = 30)
        assertTrue(mergeSequences(listOf(b), listOf(gone)).single().deleted)
        assertEquals(2, mergeSequences(listOf(a), listOf(Sequence("2", "n", a.spec, 1))).size)
    }

    @Test fun mergeSessionsDedupes() {
        val s = Session(1, "a", 10, 10, 0, true, null, null, null)
        assertEquals(1, mergeSessions(listOf(s), listOf(s)).size)
    }

    @Test fun alarmsMergeLastWriteWinsAndTombstones() {
        val a = Alarm("a", 7, 0, setOf(1, 2), true, updatedAt = 10)
        val b = Alarm("b", 8, 30, emptySet(), true, updatedAt = 10)
        val phoneEdit = a.copy(hour = 6, updatedAt = 20)
        val watchDeletedB = b.copy(deleted = true, updatedAt = 30)
        val merged = mergeAlarms(listOf(a, b), listOf(phoneEdit, watchDeletedB)).associateBy { it.id }
        assertEquals(6, merged["a"]!!.hour)
        assertTrue(merged["b"]!!.deleted)
        // older remote never overwrites newer local
        assertEquals(6, mergeAlarms(listOf(phoneEdit), listOf(a)).single().hour)
    }

    @Test fun alarmCodecReadsPreV03Files() {
        val old = """[{"id":3,"h":6,"m":45,"days":[1,3],"on":true}]"""
        val a = AlarmCodec.listFromJson(old).single()
        assertEquals("3", a.id); assertEquals(setOf(1, 3), a.days); assertEquals(0L, a.updatedAt)
        assertEquals(a, AlarmCodec.listFromJson(AlarmCodec.listToJson(listOf(a))).single())
    }

    @Test fun everyNSecondsAlertsRepeatInsideTheStep() {
        val seq = Sequence("e", "e", Spec.Custom(listOf(CustomBlock(listOf(CustomStep("Run", StepKind.WORK, 100, every = 30))))))
        val t = seq.timeline(0)
        val ms = t.cues.filter { it.type == CueType.MILESTONE }.map { it.atMs }
        assertEquals(listOf(30_000L, 60_000L, 90_000L), ms)
        val back = Codec.sequenceFromJson(Codec.sequenceToJson(seq))
        assertEquals(seq.spec, back.spec)
    }

    @Test fun twelveHourHelpers() {
        assertEquals("12:05 AM", fmtAlarmTime(0, 5)); assertEquals("12:00 PM", fmtAlarmTime(12, 0)); assertEquals("7:30 PM", fmtAlarmTime(19, 30))
        for (h in 0..23) { val (h12, pm) = to12h(h); assertEquals(h, from12h(h12, pm)) }
    }
}
