# Sequi

A sequenceable timer and alarm clock for the **Samsung Galaxy Watch 8** (Wear OS 6),
built for exercise: timers you can't cheat, strong haptic cues, and heart-rate stats.

* **Watch app** (`:wear`) works standalone. **Phone app** (`:mobile`, Galaxy S25) is the comfortable
  place to build free-form timers and browse stats; the two sync over Bluetooth (no internet, no account).
* Modules: `:shared` (timer engine, storage, sync) · `:wear` · `:mobile`.
* Package `app.sequi` (watch and phone must share it for sync) · Kotlin + Jetpack Compose · min Android/Wear OS 13/4 (API 33), target API 36.

Docs: [Features](docs/FEATURES.md) · [Architecture](docs/ARCHITECTURE.md) · [Roadmap](docs/ROADMAP.md)

---

## Try it on your watch (step by step)

You have Android Studio installed, so this is mostly clicking. You only do steps 1–2 once.

### 1. Turn on developer mode on the watch
1. Watch: **Settings → About watch → Software information**.
2. Tap **Software version** repeatedly (5–7 times) until it says developer mode is on (the exact label can differ slightly by One UI version).
3. Go back to **Settings → Developer options** and enable **ADB debugging** and **Wireless debugging**.

The watch and your computer must be on the **same Wi-Fi network**.

### 2. Connect the computer to the watch
On the watch: **Developer options → Wireless debugging → Pair new device**. It shows an
IP address, a *pairing port* and a *6-digit code*. On the computer terminal:

```bash
adb pair  <IP>:<PAIRING_PORT>      # type the 6-digit code when asked
adb connect <IP>:<PORT>            # PORT is the one shown on the main Wireless debugging screen (different from the pairing port)
adb devices                        # your watch should be listed as "device"
```

(Android Studio can do the same: **Device Manager → Pair devices using Wi-Fi**, or
*Running Devices → Pair Devices Over Wi-Fi*.)

### 3. Install the watch app
**Option A – Android Studio (easiest):** open the `seq-timer-wearable` folder as a project
(*File → Open*), wait for Gradle sync, choose the **wear** run configuration, pick your watch in the device drop-down, press **▶ Run**.

**Option B – terminal:**
```bash
adb devices                                   # note the watch's serial (IP:port)
ANDROID_SERIAL=<watch serial> ./gradlew :wear:installDebug
```
(`ANDROID_SERIAL` matters once the phone is connected too: Gradle otherwise tries *every* connected device.)
Then open **Sequi** from the watch's app list.

### 3b. Install the phone app (Galaxy S25)
1. Phone: **Settings → About phone → Software information**, tap **Build number** 7 times, then **Settings → Developer options → USB debugging** on.
2. Plug the phone in with USB, accept the "Allow USB debugging" prompt, check `adb devices` lists it.
3. Android Studio: choose the **mobile** run configuration and your phone, **▶ Run** — or
   ```bash
   ANDROID_SERIAL=<phone serial> ./gradlew :mobile:installDebug
   ```
4. Both apps must be installed from **this same computer** (same debug signing key) and the watch must be paired to the phone as usual (Galaxy Wearable). After that, timers you save on either device appear on the other within seconds. The phone's **↻** button forces a sync.

### 3c. Smoother: install the optimised build
Debug builds are noticeably slower. For daily use install the **release** variant (same key, so sync keeps working):
```bash
ANDROID_SERIAL=<watch serial> ./gradlew :wear:installRelease
ANDROID_SERIAL=<phone serial> ./gradlew :mobile:installRelease
```

### 4. First run on the watch
* Allow **Notifications** when asked (needed for the "running timer" shown on the watch face and for alarms).
* Start any timer → allow **Heart rate (sensors)** when asked (you can refuse; the timer still works).
* **Keep it reliable:** *Settings → Apps → Sequi → Battery → Unrestricted* (label varies), so the
  system never stops an alarm or timer to save power.

### 5. What to test (checklist)
- [ ] Start **Tabata**: you should feel two strong pulses at each WORK start, one long pulse at REST, ticks in the last 3 s, a long triple buzz at the end.
- [ ] Start **10 min + alerts** and check the alerts at 5:00, 2:00, 1:00 remaining (change the timer to 1 minute for a quick check: long-press it on the home list → Edit).
- [ ] Turn the screen off / lower your wrist mid-timer: cues must still fire on time.
- [ ] Pause and resume; hold **■** to stop. After finishing, open **Stats** and **Workout times**.
- [ ] Press the watch's home button during a timer: the timer should appear on the watch face / recents as a running activity.
- [ ] Create an alarm 2 minutes ahead (Alarms → + New alarm) and let it ring; try Snooze and Dismiss.
- [ ] Settings: switch **Sound** on and check the beeps.

**New in v0.2**
- [ ] Look: home cards with the coloured strip, run screen with the progress ring and round dots.
- [ ] Watch editor: edit a timer, add an alert at an odd time (e.g. 0:45 left) and run it — you must feel the pulse exactly then.
- [ ] Phone: create a timer with two blocks (e.g. warm-up + 4 × 30 s/10 s) and an alert "after 0:15"; it should appear on the watch within seconds and run correctly.
- [ ] Phone → Stats shows the workouts you did on the watch (heart rate, hours chart).
- [ ] Delete a timer on the phone → it disappears on the watch (and the other way round).

> **Honest status:** v0.1 (timers, haptics, alarms, heart rate) was confirmed working on your watch.
> v0.2 (new look, custom alert times, free-form timers, phone app + sync) compiles, passes 15 unit tests
> (timeline/cues, custom steps, JSON, sync merge, stats) and lint, but the **phone app and phone↔watch sync
> have not been run on real devices yet**. Tell me what you see and I'll fix it.

---

## Using the app

* **Home**: timer cards with a coloured preview strip. **Tap** = start, **long-press** = edit/delete. Below: New timer, Alarms, Stats, Times, Settings.
* **Run screen**: progress ring + big countdown, colour per step (mint work, coral rest, blue warm/cool-down), round dots, next step, live heart rate. ❚❚ pauses, **hold ■** to stop.
* **Watch editor**: *Countdown* (duration + alerts at **any** time left) or *Intervals* (work, rest, rounds, warm-up/cool-down, alerts inside each work). Tap an alert to remove it.
* **Phone editor**: free-form timers — any number of blocks (each repeatable), any steps (work/rest/easy, named), alerts per step either as *time left* or *time since the step started*. Templates to start from.
* **Alarms** (watch): any number, repeat days (none = once), snooze 10 min.

## Build / test from the terminal

```bash
./gradlew :shared:testDebugUnitTest   # engine, custom timers, JSON, sync merge, stats
./gradlew :wear:assembleDebug         # wear/build/outputs/apk/debug/wear-debug.apk
./gradlew :mobile:assembleDebug       # mobile/build/outputs/apk/debug/mobile-debug.apk
./gradlew :wear:lintDebug :mobile:lintDebug
```
Needs JDK 17+ and the Android SDK (path in `local.properties`, already set for this machine).

**New in v1.0**
- [ ] Watch face → swipe to add the **Sequi** Tile; tap a timer on it to start it. Add the **Next alarm** complication to a watch face.
- [ ] Alarm editor (watch and phone) uses 1–12 with AM/PM.
- [ ] Watch editor: tap a stepper's value (mint outline) and turn the crown/bezel to change it; tap again to release.
- [ ] Settings → Voice cues on, start Tabata with earbuds: hear "Work. Round 1 of 8", "Rest", "Done".
- [ ] Phone → Timers → ⋮: back up to a file, restore it; open a timer → ⋮ → Share timer, then on another device copy the text and use "Paste a shared timer".
- [ ] Phone custom timer: add alert → "Repeating, every…" 0:30 on a 2-minute step; the watch pulses at 0:30, 1:00, 1:30.
