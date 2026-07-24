# Power Clock — Privacy Policy

**Effective: version 1.0.0**

Power Clock is designed to be local-first and private by construction.

## The short version

Power Clock does not collect, transmit, sell, or share any data. It cannot:
the app does not request the `INTERNET` permission, so the operating system
prevents it from ever making a network connection.

## What stays on your device

All of the following is stored only in Power Clock's private app storage on
your phone and never leaves it:

- Your alarms and their settings
- Your onboarding answers (nickname, wake times, sleep profile, fitness
  comfort, mission preferences)
- Wake history (ring times, mission completion times, repetitions,
  emergency dismissals, optional morning-energy ratings)
- Streaks, statistics, and the Power Score (computed locally)
- Your random QR-card identifier
- An optional private copy of a music file you selected

## Camera

Camera access is requested only if you choose a camera-counted workout or QR
mission. Frames are processed in memory by an on-device pose model
(MediaPipe) or QR decoder (ZXing) and immediately discarded.

- No photos or videos are saved or recorded.
- Nothing is uploaded (no internet permission exists).
- No face recognition or identification of any kind is performed.

## Custom music

Music is selected through Android's document picker; Power Clock never
requests broad storage access. The selected file is only read to play your
alarm and, if you choose, copied once into app-private storage for
reliability. It is never uploaded. You are responsible for the content you
select.

## What Power Clock does NOT do

- No analytics or crash-reporting SDKs
- No advertising and no advertising identifiers
- No third-party tracking of any kind
- No accounts, logins, or remote profiles
- No sale or transfer of personal data (there is nothing to transfer)
- No logging of personal information in production builds

## Your controls

In *Settings → Privacy & local data* you can:

- **Export** your wake history as CSV to a location you pick
- **Reset** your wake history
- **Delete all data**, returning the app to a fresh-install state

Uninstalling the app also removes all of its data.

## Permissions used and why

| Permission | Why |
| --- | --- |
| Notifications | Show the ringing alarm and its full-screen view |
| Exact alarms (`SCHEDULE_EXACT_ALARM`) | Ring at the precise minute, even in Doze |
| Full-screen intent | Open the mission screen over the lock screen |
| Camera | Count workout reps / scan QR codes, on-device only, optional |
| Vibration | Alarm vibration patterns |
| Boot completed | Re-arm alarms after a restart |
| Wake lock | Keep the CPU awake briefly while ringing |
| Foreground service (media playback) | Reliable alarm audio |

Power Clock never requests contacts, location, microphone, phone state, or
broad file access.

## Health disclaimer

Power Clock encourages light morning movement but is not a medical device
and provides no medical advice. Adjust targets to your abilities and use the
non-physical missions whenever exercise isn't right for you.

## Changes

Any future change to this policy ships inside the app update itself and is
visible in this document before you install the update.
