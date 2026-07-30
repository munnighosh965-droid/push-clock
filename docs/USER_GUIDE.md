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

## Choosing an alarm sound

Open an alarm and use the **Sound** card:

- **Sounds** — the eight original Power Clock tones, plus **Sounds on this
  device**, which opens Android's own picker so you can use any alarm,
  ringtone or notification sound already on your phone.
- **My music** — pick any audio file through Android's document picker. Power
  Clock never asks for broad storage access and never uploads anything, and
  you can set the playback start position.

Music you pick is **copied into Power Clock automatically**, so the alarm
keeps playing your track even if the original file is later moved, deleted,
or has its permission withdrawn. If there is not enough free space to copy
it, the app says so and plays the file where it lies.

If a chosen sound is ever unreadable at ring time, the bundled fallback tone
plays instead — an alarm is never silent. If Android refuses to let Power
Clock read a device sound (some ROMs restrict user-added ringtones), the app
says so when you pick it rather than failing at 6 a.m.

## The home-screen widget

Long-press your home screen, choose **Widgets**, and drag the **Power Clock**
widget out. It is the app's logo running as a real clock: the champagne-gold
ring with its power notch is the dial, the hands show the actual time, and
the digital readout and next-alarm line sit underneath. Tapping it opens the
app, and it can be resized freely.

The hands are driven by Android itself inside your launcher, so the widget
costs nothing to keep ticking. On Android 12 and newer it also has a sweeping
second hand; on older versions it updates once a minute, which is all the
platform's clock view supports there.

There are two widgets:

- **Power Clock** — the full card above: dial, digital time, next alarm.
- **Power Clock icon** — a one-cell dial with no card, sized to sit *among
  your app icons*. It looks like the app icon, except the hands really move.
  The quickest way to add it is **Settings → Home screen → Add the live clock
  icon**, which asks your launcher to place it for you.

Android does not let any third-party app animate its own launcher icon — the
launcher looks up the preinstalled clock app by package name and ignores
everyone else — so the icon-sized widget is as close as Power Clock can get.
The app icon itself is a real analog clock face, and it animates its hands
into place every time you launch the app.

## The dashboard

The home screen leads with an animated analog clock — a sweeping second
hand, an elapsed-seconds arc on the rim, and a soft breathing glow — above
the current time and date. All times in Power Clock are shown in 12-hour
format with AM/PM. If you enable **Reduce motion** in Settings (or turn
animations off system-wide), the dial and the rest of the dashboard become
completely static.

## When the alarm rings

The full-screen ringing view opens by itself, over the lock screen, rather
than arriving as a notification you have to tap.

On **Android 14 and newer this needs a permission** — "Full-screen alarm
view" in the Reliability Check, also offered during onboarding. Without it
Android silently downgrades alarms to a heads-up notification in the status
bar. Power Clock also opens the screen directly when the alarm fires, so the
notification is only a backup.

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
