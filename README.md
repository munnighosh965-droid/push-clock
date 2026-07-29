# Power Clock

**WAKE. MOVE. WIN.**

Power Clock is a completely free, privacy-first Android alarm clock for heavy
sleepers and chronic snoozers. When an alarm rings you must complete a
wake-up mission — camera-counted push-ups, squats, or jumping jacks, a math
challenge, a memory game, typing a phrase, shaking the phone, or scanning a
QR card placed away from your bed — before it stops.

- **No ads. No subscriptions. No in-app purchases. No accounts.**
- **No internet permission at all** — the app is physically unable to upload
  anything. All pose detection runs on-device with MediaPipe.
- Original code, UI, branding, icons, and eight synthesized alarm tones, set
  in the open-source Sora and Inter typefaces.

## Requirements

- Android Studio (Ladybug or newer) or plain JDK 17+ with the Android SDK
- Android SDK: platform 35, build-tools 35.0.0
- A device or emulator on Android 8.0 (API 26) or newer

## Building

```bash
# Debug build with unit tests and lint (the canonical check):
./gradlew clean test lint assembleDebug

# Debug APK output:
app/build/outputs/apk/debug/app-debug.apk

# Unsigned release build:
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release-unsigned.apk
```

If the SDK is not auto-detected, create `local.properties` with
`sdk.dir=/path/to/android-sdk`.

### Installing

```bash
adb install -r deliverables/PowerClock-v1.2.0-debug.apk
```

### Signing the release APK

No production credentials ship with this repository (deliberately). To sign:

```bash
keytool -genkeypair -v -keystore powerclock.keystore -alias powerclock \
        -keyalg RSA -keysize 4096 -validity 10000
zipalign -f -p 4 app-release-unsigned.apk app-release-aligned.apk
apksigner sign --ks powerclock.keystore --out PowerClock-release.apk app-release-aligned.apk
```

## Regenerating generated assets

- Alarm tones: `python3 tools/generate_sounds.py` (needs numpy + ffmpeg).
  All eight tones are synthesized from scratch and royalty-free.
- Pose model: `app/src/main/assets/pose_landmarker_lite.task` is Google's
  MediaPipe Pose Landmarker (Lite) model, Apache License 2.0, downloaded from
  `https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task`.

## Testing

```bash
./gradlew test          # 83 JVM unit tests (scheduling, DST, rep counters, stats, queueing...)
./gradlew lint          # Android lint (0 errors)
./gradlew connectedDebugAndroidTest   # UI tests; requires an emulator or device
```

## Project documentation

| Document | Purpose |
| --- | --- |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | End-user manual |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Technical architecture |
| [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md) | Privacy policy (local-first) |
| [docs/QA_CHECKLIST.md](docs/QA_CHECKLIST.md) | Manual QA checklist + executed results |
| [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) | Honest list of limitations |
| [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) | Open-source license notices |

## Deliverables

Pre-built artifacts live in [`deliverables/`](deliverables/) together with
SHA-256 checksums (`checksums.sha256`).
