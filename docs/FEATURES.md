# Features

## Timers
A timer is one of three shapes:

| Shape | Parameters | Example | Edited on |
|---|---|---|---|
| **Countdown** | duration, alerts at any "time left" | 10 min with alerts at 5:00, 2:00, 1:00 and the end | watch + phone |
| **Intervals** | work, rest, rounds, optional warm-up & cool-down, alerts inside each work | 4 × 1 min work / 5 s rest, Tabata 8 × 20/10, EMOM (rest = 0) | watch + phone |
| **Custom** | any number of *blocks*; a block = list of named steps (work/rest/easy) repeated N times; per-step alerts | 5 min warm-up → 8 × (20 s sprint / 10 s walk) → 5 min cool-down | phone (shown & runnable on watch) |

**Alert times are fully custom.** Each step can have alerts as *time left* (e.g. 0:10 left) and, in Custom timers, as *time since the step started* (e.g. after 0:20). Any number, any second.

* No rest is added after the **last** round.
* Optional **get-ready** lead-in (default 3 s, 0–30 s in Settings).
* Seeded presets: 10 min + alerts, 4 × 1 min / 5 s, Tabata, HIIT 40/20 (with warm-up/cool-down), EMOM 10, Boxing 3 rounds, Plank 1 min. All editable/deletable.

## Cues (always strong haptics, optional sound)
| Moment | Haptic | Sound (if enabled) |
|---|---|---|
| WORK starts | two strong pulses | double beep |
| REST starts | one long pulse | ack tone |
| Warm-up / cool-down starts | three medium pulses | beep |
| Alert mark (e.g. 1 min left) | three short pulses | prompt tone |
| Last 3-2-1 seconds (optional) | short tick each second | short beep |
| Finished | long triple buzz | alert tone |

Haptics use the *alarm* vibration usage so they fire even with Do-Not-Disturb.

## "Can't cheat" design
* Time is `SystemClock.elapsedRealtime()` (monotonic; counts through deep sleep) minus pause time — it cannot drift or be skipped.
* Runs in a **foreground service** with a CPU wake lock, plus a Wear OS **Ongoing Activity** so it shows on the watch face/recents.
* **Hold ■ to stop** (a sweaty tap can't end it); pauses are allowed but **logged** (`paused` seconds stored with each session).
* If the system wakes the app very late, only the most relevant cue plays (never a burst).
* No always-on display and no per-frame ticking: the service wakes about **once per second**.

## Alarms
* Any number; repeat days or one-shot; snooze 10 min / dismiss.
* One OS alarm (`setAlarmClock`) always points at the next due alarm; re-armed on boot, update, time and time-zone change.
* Rings with strong repeating vibration (and the alarm ringtone if Sound is on) for up to 60 s.

## Heart rate & stats
* Heart rate streams from Health Services during a workout (permission requested on first timer; optional in Settings).
* Each session stores: start time, planned/active/paused seconds, completed?, avg / max / min BPM.
* **Stats** screen: last 7 or 30 days — workouts, active time, avg & max HR, bar charts of active minutes/day and avg HR/day.
* **Workout times** screen (separate): histogram of start hour (last 90 days), most common start hour, and morning/afternoon/evening/night counts.

## Settings
Sound (off by default) · 3-2-1 ticks · Heart rate · Get-ready seconds.

## Phone app (Galaxy S25)
* **Timers**: list with preview strips, full editor (blocks, steps, names, alerts, reorder, templates), sync status chip, manual sync button.
* **Stats**: 7/30/90 days — workouts, active time, avg/max heart rate, charts for active minutes/day, heart rate/day and training hours, recent workouts list. Data comes from the watch.
* **Sync**: Bluetooth via the Wearable Data Layer. Timers merge both ways (newest edit wins, deletions propagate); history flows watch → phone. Works offline from the internet; nothing leaves your devices.
* Footprint: the phone app only runs while open; sync is handled by a tiny listener service the system wakes when data changes.

## Look & feel (v0.2)
Pure-black background (battery on OLED), mint accent, a colour per step type (mint work, coral rest, blue warm/cool-down), timer cards with a coloured shape preview, a progress ring on the run screen with round dots and a "next step" hint.
