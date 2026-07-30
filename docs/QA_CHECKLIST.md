# Power Clock — Manual QA checklist

Legend for the *Result* column of this build (v1.0.0, built in a headless
Linux CI environment):

- **PASS (automated)** — covered by unit tests executed in this environment
- **PASS (build-env)** — verified in this environment (build, static checks)
- **NOT RUN (needs device)** — requires a physical device or emulator with
  camera/audio/lock screen; no false claims are made

## Build & static checks (executed)

| Check | Result |
| --- | --- |
| `./gradlew clean test lint assembleDebug` succeeds | PASS (build-env) |
| 71 JVM unit tests green | PASS (automated) |
| Android lint: 0 errors | PASS (build-env) |
| Release compilation (`assembleRelease`, R8) succeeds | PASS (build-env) |
| APK structurally valid (zip + manifest + dex present) | PASS (build-env) |
| No `INTERNET` permission in merged manifest | PASS (build-env) |
| Secret scan (no keys/tokens/passwords in source) | PASS (build-env) |

## Alarm engine (logic automated; device behavior needs hardware)

| Check | Result |
| --- | --- |
| Next occurrence: one-time today/tomorrow, midnight rollover | PASS (automated) |
| Weekday recurrence incl. single-day week wrap | PASS (automated) |
| DST spring-forward gap and fall-back ambiguity | PASS (automated) |
| Next occurrence after dismissal (one-time vs repeating) | PASS (automated) |
| Duplicate ring prevention / overlap queue FIFO | PASS (automated) |
| Rings while screen locked, full-screen UI over keyguard | NOT RUN (needs device) |
| Reboot rescheduling (BOOT_COMPLETED) | NOT RUN (needs device) |
| Doze delivery via setAlarmClock | NOT RUN (needs device) |
| Alarm fires shortly after reboot | NOT RUN (needs device) |
| Audio/vibration/torch/wake-lock cleanup after dismissal | NOT RUN (needs device) |
| Auto-silence timeout records "missed" | NOT RUN (needs device) |
| Incoming call ducking / focus loss and recovery | NOT RUN (needs device) |
| Bluetooth disconnect during ringing | NOT RUN (needs device) |

## Missions

| Check | Result |
| --- | --- |
| Rep counters: valid reps, partials, noise, double-count guard, pause | PASS (automated) |
| Sensitivity levels differ (beginner vs strict) | PASS (automated) |
| Fallback selection never requires the failed capability | PASS (automated) |
| Math generator answers correct across difficulties | PASS (automated) |
| Memory sequence bounds and growth | PASS (automated) |
| Phrase matching normalization | PASS (automated) |
| Live camera workout counting (real human) | NOT RUN (needs device) |
| Camera denied → automatic non-camera fallback | NOT RUN (needs device) |
| QR card scan dismisses only the matching card | NOT RUN (needs device) |
| Shake mission counts firm shakes | NOT RUN (needs device) |
| Alarm without missions still requires the default workout | PASS (automated: MissionEnforcerTest) |
| Widget dial keeps time and next-alarm line updates on change | NOT RUN (needs device) |
| Camera permission requested at ring time before fallback | NOT RUN (needs device) |

## Sounds & custom music

| Check | Result |
| --- | --- |
| Audio URI policy (SAF-only + private copies) | PASS (automated) |
| 8 original tones bundled and referenced by catalog | PASS (build-env) |
| Preview / volume / gradual ramp / Heavy Sleeper restore | NOT RUN (needs device) |
| Custom track picked via SAF, metadata shown, start position | NOT RUN (needs device) |
| Deleted/corrupt custom track falls back to bundled tone | NOT RUN (needs device) |

## UI / UX

| Check | Result |
| --- | --- |
| Instrumented flows compile (onboarding, alarm CRUD, missions) | PASS (build-env) |
| Onboarding completes and lands on dashboard | NOT RUN (needs device) |
| Create → edit → delete alarm round-trip | NOT RUN (needs device) |
| Dark and light themes render correctly | NOT RUN (needs device) |
| Largest system font: no clipped content on main screens | NOT RUN (needs device) |
| TalkBack focus order on dashboard and ringing screen | NOT RUN (needs device) |
| Rotation during ringing and workout missions | NOT RUN (needs device) |

Run the NOT RUN items on a physical Android 8–15 device before any store
release; the instrumentation suite (`connectedDebugAndroidTest`) automates
several of them.
