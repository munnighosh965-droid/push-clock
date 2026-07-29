# Power Clock — Known limitations

An honest list of what this version does not (or cannot) do.

## Android platform constraints

- **Force stop kills alarms.** If the user force-stops the app from system
  settings, Android blocks all of its alarms until the app is opened again.
  This applies to every third-party alarm clock and is explained in
  onboarding and the Reliability Check screen.
- **Exact-alarm permission revocation** downgrades scheduling to
  `setAndAllowWhileIdle`, which Android may delay by several minutes. The
  Reliability Check reports this clearly instead of pretending otherwise.
- **Aggressive OEM battery managers** (some Xiaomi/Huawei/Oppo builds) can
  still delay or suppress background delivery. The Reliability screen links
  to battery-optimization settings, but OEM-specific whitelisting screens
  are not deep-linked.

## Testing gaps in this build environment

- The build/CI environment for this release had **no KVM and no attached
  device**, so instrumentation tests and device-only behaviors (lock-screen
  ringing, reboot rescheduling, Doze, camera counting with a real human,
  audio focus during calls) were compiled and code-reviewed but **not
  executed**. See `docs/QA_CHECKLIST.md` for the precise list.
- Pose-counting accuracy was validated with synthetic landmark sequences,
  not with live humans of different body types, clothing, and lighting.
  Real-world tuning of thresholds may be needed.

## Functional limitations

- **No snooze and no emergency dismiss by design** — completing a mission
  (a workout by default) is the only way to stop an alarm. The "I cannot
  safely exercise" switch and automatic fallbacks keep alarms dismissible
  when a capability fails.
- **Step-count mission is not implemented.** Reliable step detection would
  require the `ACTIVITY_RECOGNITION` permission and hardware-dependent
  sensors; it was deliberately left out rather than shipped half-working.
  The permission is consequently not requested.
- **Landscape is not optimized.** The ringing and workout screens are
  designed portrait-first; they remain usable but not tailored in landscape.
- **Pose detection needs a full-body view.** Very small rooms or extreme
  camera angles reduce accuracy; the app pauses counting and coaches
  repositioning, and the fallback mission always remains available.
- **Flashlight pulses stop when a camera mission starts**, because the
  camera and torch share hardware on most devices.
- **Custom music via SAF grants can expire** on some providers (e.g. cloud
  documents). The "Copy into Power Clock" option exists precisely for this;
  uncopied tracks fall back to the bundled tone when unreadable.
- **Single language (English).** Strings are centralized but only English
  copy ships in 1.0.0.
- **Preferences are stored unencrypted** in app-private DataStore. Nothing
  stored is sensitive (no credentials or health details), and Android's
  sandbox protects app-private storage; `androidx.security-crypto` was not
  added because it remains alpha.

## Release engineering

- The release APK ships **unsigned** because no production signing
  credentials were (or should be) present in this environment. Signing
  instructions are in the README.
- The debug APK is large (~74 MB) because MediaPipe bundles native
  libraries for all four ABIs; the R8-minified release APK is significantly
  smaller. ABI splits/App Bundles would shrink installs further and are the
  obvious next step for store distribution.
