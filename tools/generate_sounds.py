#!/usr/bin/env python3
"""Synthesizes the eight original Power Clock alarm tones.

Every tone is generated from scratch with numpy (no samples, no third-party
audio), so the results are fully original and royalty-free. Output WAV files
are converted to OGG Vorbis with ffmpeg into app/src/main/res/raw/.

Each tone is designed as a seamless loop: patterns start and end in silence
or at a zero crossing so Media3's REPEAT_MODE_ONE loops without clicks.
"""

import os
import subprocess
import tempfile

import numpy as np

SR = 44100


def t(dur):
    return np.linspace(0, dur, int(SR * dur), endpoint=False)


def env(x, attack=0.01, release=0.05):
    """Attack/release envelope to avoid clicks at note edges."""
    n = len(x)
    a = int(SR * attack)
    r = int(SR * release)
    e = np.ones(n)
    if a > 0:
        e[:a] = np.linspace(0, 1, a)
    if r > 0:
        e[-r:] = np.linspace(1, 0, r)
    return x * e


def silence(dur):
    return np.zeros(int(SR * dur))


def norm(x, level=0.85):
    peak = np.max(np.abs(x)) or 1.0
    return x / peak * level


def saw(freq, dur):
    ph = np.cumsum(np.full(int(SR * dur), freq)) / SR
    return 2.0 * (ph % 1.0) - 1.0


def square(freq, dur, duty=0.5):
    ph = (np.cumsum(np.full(int(SR * dur), freq)) / SR) % 1.0
    return np.where(ph < duty, 1.0, -1.0)


def sine(freq, dur):
    return np.sin(2 * np.pi * freq * t(dur))


def sweep(f0, f1, dur):
    tt = t(dur)
    freqs = np.linspace(f0, f1, len(tt))
    ph = np.cumsum(freqs) / SR
    return np.sin(2 * np.pi * ph)


def reactor():
    """Deep industrial pulse: low saw throbs with a rising overtone."""
    out = []
    for i in range(8):
        f = 110 + 8 * i
        core = 0.7 * saw(f, 0.30) + 0.4 * sine(f * 2, 0.30) + 0.25 * sine(f * 3.01, 0.30)
        out.append(env(core, 0.005, 0.08))
        out.append(silence(0.12))
    return np.concatenate(out)


def power_pulse():
    """Rhythmic square pulses stepping up in pitch, then resetting."""
    out = []
    steps = [330, 392, 440, 523, 587, 659]
    for f in steps:
        out.append(env(0.8 * square(f, 0.14, 0.35) + 0.3 * sine(f * 2, 0.14), 0.004, 0.03))
        out.append(silence(0.09))
    out.append(silence(0.25))
    return np.concatenate(out)


def digital_siren():
    """Two-tone alternating sweep, classic siren feel but synthetic."""
    out = []
    for _ in range(3):
        out.append(env(sweep(620, 980, 0.42), 0.01, 0.02))
        out.append(env(sweep(980, 620, 0.42), 0.01, 0.02))
    out.append(silence(0.3))
    return np.concatenate(out)


def bell_strike(f0, dur):
    """Inharmonic decaying partials approximating a heavy struck bell."""
    tt = t(dur)
    partials = [(1.0, 1.0, 3.0), (2.76, 0.6, 4.5), (5.4, 0.35, 6.0), (8.9, 0.2, 8.0)]
    x = np.zeros(len(tt))
    for ratio, amp, decay in partials:
        x += amp * np.sin(2 * np.pi * f0 * ratio * tt) * np.exp(-decay * tt)
    return x


def heavy_bell():
    out = []
    for _ in range(3):
        out.append(env(bell_strike(220, 1.15), 0.002, 0.1))
        out.append(silence(0.35))
    return np.concatenate(out)


def morning_horn():
    """Brassy stacked-harmonic blasts, bright and insistent."""
    out = []
    for f in [262, 262, 330, 392]:
        blast = (
            0.8 * saw(f, 0.32)
            + 0.5 * saw(f * 1.005, 0.32)
            + 0.3 * sine(f * 2, 0.32)
        )
        out.append(env(blast, 0.02, 0.06))
        out.append(silence(0.10))
    out.append(silence(0.4))
    return np.concatenate(out)


def electric_rise():
    """Fast rising arpeggio sweeps with a sparkle overtone."""
    out = []
    for base in [220, 277, 330]:
        for mult in [1, 1.26, 1.5, 2.0]:
            f = base * mult
            out.append(env(0.7 * sine(f, 0.09) + 0.35 * sine(f * 3, 0.09), 0.004, 0.02))
        out.append(silence(0.12))
    out.append(silence(0.3))
    return np.concatenate(out)


def rapid_beep():
    out = []
    for _ in range(4):
        for _ in range(5):
            out.append(env(sine(1046, 0.06) + 0.3 * sine(2093, 0.06), 0.003, 0.01))
            out.append(silence(0.05))
        out.append(silence(0.28))
    return np.concatenate(out)


def emergency_buzz():
    """Harsh detuned sawtooth buzz bursts; maximum urgency."""
    out = []
    for _ in range(4):
        buzz = 0.7 * saw(400, 0.5) + 0.7 * saw(407, 0.5) + 0.3 * square(100, 0.5)
        out.append(env(buzz, 0.005, 0.04))
        out.append(silence(0.22))
    return np.concatenate(out)


TONES = {
    "alarm_reactor": reactor,
    "alarm_power_pulse": power_pulse,
    "alarm_digital_siren": digital_siren,
    "alarm_heavy_bell": heavy_bell,
    "alarm_morning_horn": morning_horn,
    "alarm_electric_rise": electric_rise,
    "alarm_rapid_beep": rapid_beep,
    "alarm_emergency_buzz": emergency_buzz,
}


def write_wav(path, data):
    import wave

    pcm = (norm(data) * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    out_dir = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
    os.makedirs(out_dir, exist_ok=True)
    for name, fn in TONES.items():
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            write_wav(tmp.name, fn())
            ogg = os.path.join(out_dir, f"{name}.ogg")
            subprocess.run(
                ["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "4", ogg],
                check=True,
            )
            os.unlink(tmp.name)
            print(f"wrote {ogg} ({os.path.getsize(ogg)} bytes)")


if __name__ == "__main__":
    main()
