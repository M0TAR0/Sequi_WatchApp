# Architecture

Three Gradle modules. Plain Kotlin + Compose; no database, no DI framework, to keep footprint and battery use small.

```
:shared  (Android library, no UI)   engine · JSON codec · Library (storage) · Data Layer sync
:wear    (watch app)                runner service, alarms, Compose for Wear OS UI   -> depends on :shared
:mobile  (phone app)                editor + stats, Compose Material 3                -> depends on :shared
```
Watch and phone use the **same applicationId (`app.sequi`) and signing key**, which the Data Layer requires.

Layout of the watch module (`:shared` pieces are listed after it):

```
app.sequi
├── data/Repo.kt       watch store: settings; delegates timers, alarms and history to the shared Library
├── timer/
│   ├── TimerService   foreground service: the clock loop, wake lock, notification + Ongoing Activity, saves Session
│   ├── TimerBus       StateFlow<RunState> the UI observes (service is the single source of truth)
│   ├── Feedback       haptic patterns (+ optional ToneGenerator beeps)
│   └── HeartRate      Health Services MeasureClient wrapper + permission name (API 36 vs older)
├── alarm/
│   ├── Alarms.kt      AlarmScheduler (next-trigger maths, setAlarmClock), AlarmReceiver, BootReceiver
│   └── AlarmRing.kt   AlarmRingService (vibrate/ring) + AlarmRingActivity (full-screen dismiss/snooze)
├── tile/              QuickTile (3 latest timers) + NextAlarmComplication
└── ui/                Compose for Wear OS (Material 3): Home, Run, Editor, Alarms, Stats/Times/Settings, navigation
```

## `:shared`
```
app.sequi.engine
├── Engine.kt   Spec (Countdown | Intervals | Custom[blocks of steps]) -> Timeline of FlatSteps + Cues; formatting; summary/preview
└── Stats.kt    Session, daily stats, period summary, hour histogram           (pure Kotlin, unit tested)
app.sequi.data
├── Codec.kt    JSON <-> models; mergeSequences (last write wins, tombstones) / mergeSessions (by start time)
├── Library.kt  timers + alarms + history in small JSON files, StateFlows, onLocalChange hook; Presets
├── Alarm.kt    Alarm model, JSON codec, mergeAlarms
└── Sync.kt     SequiSync (push/pull) + SequiSyncService (WearableListenerService, declared in the library manifest)
```

### Sync protocol
Three Data Layer items (`/sequi/alarms` works like timers), each holding one JSON string:
* `/sequi/sequences` — all timers incl. tombstones. Either device pushes after a local edit, merges what it receives, and pushes back only if its merged state differs from what it received. Identical content never fires an event, so the exchange converges and stops.
* `/sequi/sessions` — the latest 400 workouts (an item is limited to ~100 KB); sessions are immutable and keyed by start time.
On app start each side also *pulls* whatever the other has already published.

## Timer engine
`Sequence.timeline(prepSec)` flattens a spec into `FlatStep`s with absolute `startMs`, and
precomputes every `Cue` (`STEP_START`, `MILESTONE`, `TICK`, `FINISH`) sorted by time.
At runtime everything is a pure function of *active elapsed ms*:
`timeline.cuesBetween(lastMs, nowMs)` returns each cue exactly once (tested with awkward step sizes),
`timeline.positionAt(ms)` gives the current step and remaining time.

## Service loop (`TimerService.loop`)
1. `elapsed = (pauseAt or now) − startAt − pausedTotal` using `elapsedRealtime()`.
2. Fire cues in `(last, elapsed]`; publish `RunState` when the displayed second / step / pause state changes.
3. Sleep to the next whole-second boundary of the step (≈1 wake/s; 500 ms poll while paused).
4. On finish/stop: release wake lock, stop HR, save `Session` (if ≥ 10 s active), publish a summary, stop the service.

The foreground-service type is `health` when the heart-rate permission is granted, otherwise `specialUse`
(the `health` type throws without the permission).

## Persistence (`filesDir`)
`sequences.json`, `sessions.json` (both in `Library`), `alarms.json` (atomic write via tmp + rename) and `SharedPreferences` for settings.
Sessions are tiny (~100 B each), so a year of daily workouts is well under 100 KB.

## Permissions
`VIBRATE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`,
`FOREGROUND_SERVICE(_HEALTH/_SPECIAL_USE)`, `health.READ_HEART_RATE` (API 36) / `BODY_SENSORS` (≤ API 35).
Heart-rate: <https://developer.android.com/health-and-fitness/health-services/permissions>.

## Key versions
AGP 9.5.1 · Gradle 9.9.1 · Kotlin 2.4.21 · Wear Compose Material 3 1.7.2 · Material 3 (phone) 1.4.0 · Health Services 1.1.0 · play-services-wearable 20.0.1 · wear-ongoing 1.1.0 · compileSdk 37 / targetSdk 36.
