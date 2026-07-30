# Power Clock — Architecture

Single-module (`:app`) Kotlin project with a clean layered structure:
MVVM + unidirectional data flow, Hilt DI, coroutines/Flow everywhere.

```
com.powerclock.alarm
├── PowerClockApp            Hilt application; creates notification channels
├── MainActivity             Single-activity Compose host (splash + theme)
├── di/                      Hilt modules (Room database, DAOs)
├── domain/                  Pure Kotlin — fully unit-testable, no Android deps*
│   ├── model/               Alarm, MissionConfig/MissionType, WakeEvent
│   ├── scheduling/          NextOccurrenceCalculator (java.time, DST-safe)
│   ├── missions/            Math/memory/phrase generators, FallbackSelector
│   ├── pose/                PoseSample model + RepCounter state machines
│   ├── stats/               WakeStats (streaks, Power Score), InsightEngine
│   └── audio/               SoundCatalog, AudioUriPolicy
├── data/
│   ├── db/                  Room: AlarmEntity, WakeEventEntity, DAOs (schema exported)
│   ├── prefs/               SettingsRepository — DataStore-backed UserSettings
│   ├── repo/                AlarmRepository, HistoryRepository (CSV export)
│   └── audio/               CustomAudioStore (SAF validation, private copies)
├── alarmengine/             The reliability core
│   ├── AlarmScheduler       setAlarmClock() per alarm, immutable PendingIntents
│   ├── AlarmReceiver        trigger → foreground service (tiny, fast)
│   ├── SystemEventReceiver  boot/update/time/timezone/exact-permission re-arm
│   ├── AlarmRingingService  foreground: Media3 audio, vibration, torch,
│   │                        wake lock, RingQueue, auto-silence, cleanup
│   ├── RingQueue            pure overlap/dedupe queue (unit-tested)
│   ├── RingingStateHolder   StateFlow bridge service ↔ ringing UI
│   └── RingingActivity      full-screen, lock-screen ringing host
├── camera/                  CameraX plumbing: PoseAnalyzer (MediaPipe),
│                            QrAnalyzer + QrCardGenerator (ZXing)
├── widget/                  home-screen clock widget (AppWidgetProvider)
└── ui/                      Compose Material 3 screens, one ViewModel each
    ├── home/ alarms/ editor/ progress/ settings/ onboarding/
    ├── ringing/ workout/ reliability/ earlyrise/ privacy/ qrcard/ about/
    ├── components/          PowerCard, ProgressRing, HeroClock, TimeFormat…
    └── theme/               Brand palette, typography, shapes
```

\* `domain/` uses only `java.time`, `kotlin.*`, and Kotlin stdlib.

## Home-screen widget

`widget/PowerClockWidgetProvider` publishes the brand mark as a working
clock. The hands are the framework's `AnalogClock` driven inside the launcher
process, so the widget keeps time without this app being scheduled at all —
`updatePeriodMillis` is zero, and the provider only redraws the next-alarm
line, pushed from `AlarmScheduler` whenever an alarm is armed or cancelled.
`layout-v31/` adds the sweeping second hand that `AnalogClock` only gained in
Android 12.

A second provider, `PowerClockIconWidgetProvider`, publishes a one-cell
version holding only the dial, so it can sit among the app icons and read as
a Power Clock icon that keeps real time.

A live *launcher icon* is not attainable and is deliberately not attempted.
Launcher3 and its derivatives read the dynamic-clock metadata
(`LEVEL_PER_TICK_ICON_ROUND` and its layer-index keys) only after looking the
package up by name — the AOSP and Lawnchair implementations both hardcode
`com.google.android.deskclock` — so a third-party app can never qualify. The
only other route, swapping activity-aliases on a timer, churns the launcher
and is unsafe at clock frequencies. The icon-sized widget covers the case
instead, and the launch animation (`ic_splash_animated`) sweeps the icon's
hands into place through the splash screen.

## Design system

`ui/theme/` holds the whole visual language in two files. `Theme.kt` defines
the obsidian / champagne-gold / platinum palette for both dark and light
schemes: gold carries the accents, platinum the secondary information, so the
two never compete. `Type.kt` pairs Sora (display face, used for clock
readings and titles) with Inter (text face, used for body copy and controls),
bundled as static weights under `res/font/`. Clock styles enable tabular
figures so a ticking readout never shifts the characters around it; Sora
carries no dingbats, so the few symbol badges (✓ ★ ○) are pinned to Inter.

## Alarm reliability design

1. **Scheduling** — every enabled alarm is armed individually with
   `AlarmManager.setAlarmClock()` (the exact, Doze-exempt, user-visible alarm
   API). The PendingIntent is unique per alarm row id and `FLAG_IMMUTABLE |
   FLAG_UPDATE_CURRENT`, so re-scheduling always *replaces* — duplicates are
   impossible at the AlarmManager level. If the exact-alarm permission is
   revoked, we degrade to `setAndAllowWhileIdle` and the Reliability screen
   says so honestly.
2. **Re-arming** — `SystemEventReceiver` handles BOOT_COMPLETED,
   LOCKED_BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED and
   SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED; `AppViewModel` also re-arms
   on every app start (idempotent).
3. **Ringing** — `AlarmReceiver` immediately starts the foreground
   `AlarmRingingService` (`mediaPlayback` type), which:
   - posts the ongoing CATEGORY_ALARM notification with a full-screen intent,
   - inserts the `WakeEvent` history row *before* anything can fail,
   - schedules the *next* occurrence up front (repeating) or disables the
     alarm (one-time), so a crash can never lose future alarms,
   - plays audio via Media3/ExoPlayer with `USAGE_ALARM` attributes, audio
     focus handling, `REPEAT_MODE_ONE` looping, optional gradual volume ramp,
     and an error listener that swaps in the bundled fallback tone,
   - runs vibration and optional torch pulses,
   - holds a partial wake lock with a hard timeout,
   - auto-silences after the configurable safety timeout (recorded as
     *missed*),
   - releases **everything** in one idempotent `stopRingingInternal()` used
     by every exit path including `onDestroy`.
4. **Overlaps** — `RingQueue` allows exactly one active alarm; simultaneous
   alarms wait in FIFO order and duplicate ids are ignored.
5. **Next occurrence** — `NextOccurrenceCalculator` is pure `ZonedDateTime`
   math: DST gaps resolve to the shifted instant, ambiguous times take the
   earlier offset, weekday masks scan at most 8 days. 15 unit tests cover
   midnight rollover, DST both ways, timezone moves, and dismissal.

## Missions

An alarm stores its mission stack as a compact encoded string
(`PUSH_UPS:5:1:NORMAL|MATH:2:1:NORMAL`). During ringing, `RingingViewModel`
walks the stack. `MissionEnforcer` guarantees the stack always contains a
workout mission (a default 5-squat workout is appended when none is
configured; users who marked exercise as unsafe get a brain mission
instead), so an alarm can never be dismissed with a single tap. If a
mission cannot run (camera denied/busy, sensor missing, model unavailable)
`FallbackSelector` swaps *only that mission* for the configured fallback,
guaranteeing the fallback never needs the failed capability.

### Pose pipeline

CameraX `ImageAnalysis` (RGBA, latest-frame) → rotation-corrected bitmap →
MediaPipe Pose Landmarker (LIVE_STREAM, on-device, lite model from assets) →
13 relevant joints mapped into `PoseSample` → exercise-specific `RepCounter`.

Each counter is a three-phase state machine (waiting → start position → work
phase) with per-sensitivity angle thresholds (hysteresis), minimum phase
durations, a minimum rep interval (700 ms), confidence gating that *pauses*
instead of guessing, and exercise-specific validation (push-up body
alignment, squat hip-drop with live standing calibration, jumping-jack
shoulder-width-relative leg spread). 13 unit tests drive the machines with
synthetic landmark sequences.

## Data

- **Room** (`powerclock.db`, schema exported to `app/schemas/`): `alarms`
  and `wake_events` tables.
- **DataStore** (`powerclock_settings`): the whole `UserSettings` profile.
- No network layer exists; the manifest strips `INTERNET` even if a library
  were to request it (`tools:node="remove"`).

## Testing strategy

- **JVM unit tests (71)** — scheduling/DST, mission encoding, wake stats &
  Power Score, insights, rep counters (valid/partial/noise/double-count/
  pause), fallback selection, ring-queue dedupe, audio URI policy.
- **Instrumentation tests** — Hilt-driven onboarding→dashboard flow, alarm
  create/edit/delete round-trip, sound selection, math/typing mission
  completion (require a device/emulator).
