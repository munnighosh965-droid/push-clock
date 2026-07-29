# Power Clock — User Guide

Welcome to Power Clock. **WAKE. MOVE. WIN.**

## First launch

Onboarding takes about a minute and asks for your nickname, usual and target
wake times, work days, sleep profile, sound intensity, and — importantly —
whether exercise is safe for you right now. If it isn't, workout missions are
hidden and every alarm uses brain missions instead. Nothing you enter leaves
your phone.

The last onboarding step requests the two permissions that make alarms
dependable: **Notifications** (shows the ringing screen) and **Exact alarms**
(rings at the precise minute, even in Doze). Camera permission is only
requested later, and only if you pick a camera mission.

## Creating an alarm

1. Tap **Set alarm** on the dashboard (or the **+** button in Alarms).
2. Pick the time and the repeat days (Once / Weekdays / Every day / custom).
3. Optionally add a label.
4. Add one or more **missions** (see below) and set the **fallback mission**.
5. Choose a **sound** — one of the eight built-in tones or your own music —
   plus volume, gradual volume, Heavy Sleeper mode, vibration pattern, and
   flashlight pulses.
6. Tap **Save alarm**. The exact next ring time is displayed before you save.

### Missions

| Mission | What you do |
| --- | --- |
| Push-ups / Knee push-ups | The camera counts full elbow-bend cycles |
| Squats | Full stand → squat → stand cycles, camera-counted |
| Jumping jacks | Arms up + feet apart, then back, camera-counted |
| Math | Solve randomized problems (3 difficulty levels) |
| Memory | Repeat a growing sequence of lit pads |
| Typing | Type a wake-up phrase exactly |
| QR scan | Scan your personal Power Clock QR card |
| Shake | Shake the phone until the ring fills |

**Stacking:** add several missions to one alarm — e.g. *scan the bathroom QR
card → 5 squats → 2 math questions*. They run in the order shown in the
editor; reorder with the arrows.

**Workout tips:** each workout has a positioning tutorial and a **Test
mission** mode so you can try it before relying on it. Choose Beginner,
Normal, or Strict detection. Rep counting is fully on-device; frames are
never stored or uploaded, and there is no face recognition.

**QR card:** generate your card in *Settings → Power Clock QR card*, save it
as a PNG, print it or display it somewhere away from bed. Only that exact
card dismisses the mission.

## When the alarm rings

The full-screen ringing view appears (over the lock screen too). Tap
**Start wake-up mission** and complete your stack. Completing a workout is
compulsory: if your alarm has no workout mission configured, a default
5-squat workout is added automatically (users who marked exercise as unsafe
in Settings get a brain mission instead). If a camera or sensor fails, only
the affected mission is swapped for your fallback — an alarm can never
become impossible to dismiss.

- If camera permission was never granted, Power Clock asks for it right on
  the ringing screen before falling back to a non-camera mission.
- **I cannot safely exercise** switches a workout mission to a brain mission.
- If nothing is answered, the alarm silences itself after the auto-silence
  timeout (5–30 min, configurable) and is logged as *missed*.

## Custom music

In the alarm editor open **My music** and pick any local audio file through
Android's file picker — Power Clock never asks for broad storage access. You
can preview it, choose the playback start position, and (recommended) tap
**Copy into Power Clock for reliability** so the alarm still works if the
original file moves. If the track is ever unreadable at ring time, the
bundled fallback tone plays instead. You are responsible for the music you
select; it is never uploaded anywhere.

## Progress, streaks and the Power Score

The Progress tab shows your current and best streaks, 7- and 30-day
summaries, total safe reps, average mission time, badges, and the **Power
Score**:

```
score = 40 × on-time rate (30d) + 30 × completion rate (30d)
      + min(20, streak × 2) + min(10, total reps / 50)
```

Days without alarms are rest days — they never break a streak. Suggestions
(stronger sound, different mission, lower/higher target, earlier bedtime,
mission stacking) are rule-based, explained, and **never applied without
your confirmation**.

## Early Rise Plan

Enable it in Progress or Settings: choose a step (5/10/15 min) and a cadence
(every 2–7 days). When a nudge is due, Power Clock *proposes* moving your
earliest alarm a step closer to your target time — you confirm or skip.

## Reliability Check

*Settings → Alarm Reliability Check* verifies notifications, exact alarms,
full-screen alarm permission, alarm volume, battery optimization, and camera
readiness, with one-tap fixes. Note: if you **force stop** the app from
system settings, Android blocks all of its alarms until it's opened again —
this is an OS rule for every third-party alarm clock.

## Privacy & data

*Settings → Privacy & local data* lets you export your wake history as CSV
(via the system file picker), reset history, or delete **all** data. There
is no cloud copy — the app doesn't even have internet permission.
