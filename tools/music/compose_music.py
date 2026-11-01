"""Composes the mod's original level music (procedural, deterministic) and writes it as OGG Vorbis.

    python tools/music/compose_music.py

Writes into src/main/resources/assets/spb-revamped/sounds/music/:
  level94_zone1..4.ogg   "Nostalgia": one calm piece rendered four times on the same timeline, each version more
                         broken (tape wobble and repeated bars, then wrong notes and dropouts, then a slowed,
                         muffled ruin). The game plays the four in sync and crossfades them by zone.
  infinite_field.ogg     sparse piano over a low drone with long silences, for the endless grass field.
All loops are seamless: effects and reverb are circular over the loop length.
Needs numpy and ffmpeg (libvorbis).
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "spb-revamped", "sounds", "music")


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


# ---------------------------------------------------------------------------------------------------------
# Instruments: each returns a mono note of `dur` seconds (plus its own tail), frequency already in Hz
# ---------------------------------------------------------------------------------------------------------

def env(n, attack, tau):
    t = np.arange(n) / SR
    a = np.minimum(1.0, t / max(attack, 1e-4))
    return a * np.exp(-t / tau)


def epiano(f, dur):
    n = int((dur + 1.5) * SR)
    t = np.arange(n) / SR
    index = 1.1 * np.exp(-t / 0.18) + 0.15  # bright tine attack that mellows
    y = np.sin(2 * np.pi * f * t + index * np.sin(2 * np.pi * f * t))
    y += 0.12 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t / 0.4)
    release = np.clip((dur + 0.25 - t) / 0.25, 0, 1)
    return y * env(n, 0.004, 1.9) * release


def musicbox(f, dur):
    n = int((dur + 1.2) * SR)
    t = np.arange(n) / SR
    y = np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * 3.98 * f * t) * np.exp(-t / 0.15)
    return y * env(n, 0.002, 0.8)


def bass(f, dur):
    n = int((dur + 0.6) * SR)
    t = np.arange(n) / SR
    y = np.sin(2 * np.pi * f * t) + 0.18 * np.sin(2 * np.pi * 2 * f * t)
    release = np.clip((dur + 0.3 - t) / 0.3, 0, 1)
    return y * env(n, 0.01, 1.6) * release


def pad(freqs, dur, brightness=4.0, detune_cents=7.0):
    n = int((dur + 1.5) * SR)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for f in freqs:
        for cents in (-detune_cents, 0.0, detune_cents):
            fd = f * 2 ** (cents / 1200)
            for h in range(1, 9):
                if fd * h > 9000:
                    break
                y += np.sin(2 * np.pi * fd * h * t + h) * (1.0 / h) * np.exp(-h / brightness)
    a = np.minimum(1.0, t / 0.9)
    release = np.clip((dur + 1.2 - t) / 1.2, 0, 1)
    return y * a * release / (len(freqs) * 3)


def piano(f, dur, velocity=1.0):
    n = int((dur + 4.0) * SR)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for h in range(1, 9):
        fh = h * f * np.sqrt(1 + 0.0004 * h * h)
        if fh > 12000:
            break
        y += np.sin(2 * np.pi * fh * t) / h ** 1.3 * np.exp(-t / (3.2 / (1 + 0.45 * h)))
    rng = np.random.default_rng(int(f * 10))
    hammer = rng.standard_normal(n) * np.exp(-t / 0.004) * 0.05
    release = np.clip((dur + 0.6 - t) / 0.6, 0, 1)
    return (y * np.minimum(1.0, t / 0.003) + hammer) * release * velocity


# ---------------------------------------------------------------------------------------------------------
# Circular (loop safe) processing
# ---------------------------------------------------------------------------------------------------------

def add(buf, note, start):
    """Adds a note at `start` seconds, wrapping its tail around to the beginning of the loop."""
    i = int(start * SR) % len(buf)
    end = i + len(note)
    if end <= len(buf):
        buf[i:end] += note
    else:
        first = len(buf) - i
        buf[i:] += note[:first]
        rest = note[first:]
        while len(rest):
            k = min(len(rest), len(buf))
            buf[:k] += rest[:k]
            rest = rest[k:]


def lowpass(x, cutoff, order=2):
    spec = np.fft.rfft(x, axis=0)
    freqs = np.fft.rfftfreq(x.shape[0], 1 / SR)
    gain = 1 / np.sqrt(1 + (freqs / cutoff) ** (2 * order))
    return np.fft.irfft(spec * (gain[:, None] if x.ndim == 2 else gain), n=x.shape[0], axis=0)


def reverb(mono, seconds, wet, seed, brightness=3500.0):
    """Stereo circular convolution with decaying noise: the tail wraps into the loop start."""
    n = len(mono)
    rng = np.random.default_rng(seed)
    ir_len = int(seconds * SR)
    t = np.arange(ir_len) / SR
    decay = np.exp(-6.9 * t / seconds)
    out = np.zeros((n, 2))
    spec = np.fft.rfft(mono)
    for ch in range(2):
        ir = rng.standard_normal(ir_len) * decay
        ir = lowpass(ir, brightness)
        ir[: int(0.02 * SR)] = 0  # predelay
        ir /= np.sqrt(np.sum(ir ** 2))
        padded = np.zeros(n)
        padded[: min(ir_len, n)] = ir[:n]
        out[:, ch] = np.fft.irfft(spec * np.fft.rfft(padded), n=n)
    dry = np.stack([mono, mono], axis=1)
    return dry * (1 - wet) + out * wet * 1.6


def wow(x, depth_cents, rate_cycles, flutter_cents=0.0):
    """Tape speed wobble as a small circular time warp: stays in sync with the clean version."""
    n = len(x)
    t = np.arange(n)
    l = n / SR
    f = rate_cycles / l  # whole cycles per loop
    depth = 2 ** (depth_cents / 1200) - 1
    offset = depth / (2 * np.pi * f) * np.sin(2 * np.pi * f * t / SR) * SR
    if flutter_cents:
        ff = round(7.0 * l) / l
        offset += (2 ** (flutter_cents / 1200) - 1) / (2 * np.pi * ff) * np.sin(2 * np.pi * ff * t / SR) * SR
    src = (t + offset) % n
    return np.interp(src, t, x, period=n)


def gate(x, spans, fade=0.01):
    """Silences (start, length) spans in seconds with short fades."""
    g = np.ones(len(x))
    k = int(fade * SR)
    for start, length in spans:
        a, b = int(start * SR), int((start + length) * SR)
        g[a:b] = 0
        g[max(0, a - k):a] *= np.linspace(1, 0, min(k, a))
        g[b:b + k] *= np.linspace(0, 1, len(g[b:b + k]))
    return x * (g[:, None] if x.ndim == 2 else g)


def copy_span(x, src, dst, length, fade=0.03):
    """Replaces [dst, dst+length) with a copy of [src, src+length) (seconds), crossfaded at the edges."""
    a, b, n = int(src * SR), int(dst * SR), int(length * SR)
    k = int(fade * SR)
    seg = x[a:a + n].copy()
    w = np.ones(n)
    w[:k] = np.linspace(0, 1, k)
    w[-k:] = np.linspace(1, 0, k)
    if x.ndim == 2:
        w = w[:, None]
    x[b:b + n] = x[b:b + n] * (1 - w) + seg * w
    return x


def crush(x, levels, mix):
    return x * (1 - mix) + np.round(x * levels) / levels * mix


def write_ogg(stereo, name, quality=4):
    stereo = stereo / max(1e-9, np.max(np.abs(stereo))) * 0.89
    pcm = (np.clip(stereo, -1, 1) * 32767).astype("<i2")
    tmp = os.path.join(tempfile.gettempdir(), name + ".wav")
    with wave.open(tmp, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    os.makedirs(OUT, exist_ok=True)
    target = os.path.join(OUT, name + ".ogg")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp, "-c:a", "libvorbis", "-q:a", str(quality), target], check=True)
    os.remove(tmp)
    print("wrote", os.path.relpath(target), f"{len(stereo) / SR:.1f} s")


# ---------------------------------------------------------------------------------------------------------
# Level 94: "Nostalgia" (F major, 70 BPM, 16 bars)
# ---------------------------------------------------------------------------------------------------------

BPM = 70
BEAT = 60 / BPM
BAR = 4 * BEAT
BARS = 16

# (pad voicing, bass root) per bar of the 8 bar progression, played twice.
CHORDS = [
    ([53, 57, 60, 64], 41),  # Fmaj7
    ([50, 53, 57, 60], 38),  # Dm7
    ([53, 57, 58, 62], 46),  # Bbmaj7
    ([52, 55, 57, 60], 36),  # C6
    ([53, 57, 60, 64], 41),  # Fmaj7
    ([52, 55, 57, 60], 45),  # Am7
    ([53, 57, 58, 62], 46),  # Bbmaj7
    ([53, 55, 60, 64], 36),  # Csus4 -> back to F
]

# (bar, beat, beats, midi) for the electric piano melody, bars 0-7; bars 8-15 repeat it with a new ending.
MELODY = [
    (0, 0, 1.5, 69), (0, 1.5, 0.5, 67), (0, 2, 1, 65), (0, 3, 1, 72),
    (1, 0, 1.5, 74), (1, 1.5, 0.5, 72), (1, 2, 2, 69),
    (2, 0, 1, 70), (2, 1, 1, 69), (2, 2, 1.5, 65), (2, 3.5, 0.5, 67),
    (3, 0, 1, 69), (3, 1, 1, 67), (3, 2, 2, 64),
    (4, 0, 1.5, 69), (4, 1.5, 0.5, 67), (4, 2, 1, 65), (4, 3, 1, 72),
    (5, 0, 1.5, 76), (5, 1.5, 0.5, 74), (5, 2, 2, 72),
    (6, 0, 1, 74), (6, 1, 1, 72), (6, 2, 1, 70), (6, 3, 1, 69),
    (7, 0, 3, 67),
]
ENDING = [(15, 0, 2, 65), (15, 2, 1, 64), (15, 3, 1, 67)]


def nostalgia(zone):
    """Renders the same 16 bars for zone 1..4, each version a little more broken."""
    length = BARS * BAR
    n = int(length * SR)
    rng = np.random.default_rng(940 + zone)
    transpose = 2 ** (-3 / 12) if zone == 4 else 1.0  # the Stage plays it slowed, three semitones down

    lead, chords, low, box = np.zeros(n), np.zeros(n), np.zeros(n), np.zeros(n)
    melody = MELODY + [(b + 8, beat, d, m) for (b, beat, d, m) in MELODY if b < 7] + ENDING
    for bar, beat, beats, midi in melody:
        if zone == 3 and rng.random() < 0.3:
            midi += rng.choice([-1, 1])  # wrong notes
        if zone == 4 and rng.random() < 0.5:
            continue  # notes that are just gone
        f = hz(midi) * transpose
        add(lead, epiano(f, beats * BEAT) * (0.9 + 0.2 * rng.random()), bar * BAR + beat * BEAT)

    for bar in range(BARS):
        voicing, root = CHORDS[bar % 8]
        freqs = [hz(m) * transpose for m in voicing]
        if zone >= 3 and rng.random() < 0.4:
            freqs[rng.integers(len(freqs))] *= 2 ** (rng.choice([-45, 40]) / 1200)  # one voice slides out of tune
        add(chords, pad(freqs, BAR), bar * BAR)
        add(low, bass(hz(root) * transpose, 2 * BEAT), bar * BAR)
        add(low, bass(hz(root) * transpose, 1.5 * BEAT) * 0.7, bar * BAR + 2 * BEAT)
        if bar >= 8 and zone < 4:
            for k, beat in enumerate((0, 1.5, 3)):
                add(box, musicbox(hz(voicing[(k + bar) % 4] + 24) * transpose, 1), bar * BAR + beat * BEAT)

    mono = 0.55 * lead + 0.30 * chords + 0.34 * low + 0.16 * box

    if zone == 4:
        t = np.arange(n) / SR
        drone = np.sin(2 * np.pi * hz(29) * t) + 0.5 * np.sin(2 * np.pi * hz(36) * t + 1)
        drone *= 0.5 + 0.5 * np.sin(2 * np.pi * 2 / length * t)  # two slow swells per loop
        mono = mono * 0.8 + 0.18 * drone

    # Tape: the further you go, the more it wobbles.
    mono = {1: lambda x: x,
            2: lambda x: wow(x, 9, 7, 2),
            3: lambda x: wow(x, 28, 5, 6),
            4: lambda x: wow(x, 65, 3, 10)}[zone](mono)

    stereo = reverb(mono, {1: 2.4, 2: 2.8, 3: 3.2, 4: 5.0}[zone], {1: 0.25, 2: 0.3, 3: 0.35, 4: 0.55}[zone], 94)

    if zone >= 2:
        # Deja vu: a bar plays twice.
        stereo = copy_span(stereo, 4 * BAR, 5 * BAR, BAR)
        stereo = copy_span(stereo, 12 * BAR, 13 * BAR, BAR)
        crackle = np.zeros(n)
        clicks = rng.integers(0, n, size=int(length * (6 if zone == 2 else 14)))
        crackle[clicks] = rng.standard_normal(len(clicks)) * 0.4
        hiss = lowpass(rng.standard_normal(n), 3000) * 0.004 * zone
        stereo += (crackle + hiss)[:, None]

    if zone >= 3:
        # Stutters: a sliver of sound caught and repeated.
        for _ in range(6 if zone == 3 else 4):
            start = rng.uniform(1, length - 2)
            sliver = rng.uniform(0.08, 0.14)
            for k in range(1, 4):
                stereo = copy_span(stereo, start, start + k * sliver, sliver, fade=0.004)
        spans = [(rng.uniform(0.5, length - 2), rng.uniform(0.05, 0.18)) for _ in range(14)]
        if zone == 4:
            spans += [(rng.uniform(0.5, length - 3), rng.uniform(0.5, 1.4)) for _ in range(6)]
        stereo = gate(stereo, spans, fade=0.006)
        stereo = crush(stereo / np.max(np.abs(stereo)), 24 if zone == 3 else 10, 0.45 if zone == 3 else 0.6)

    cutoff = {1: 9000, 2: 6000, 3: 3800, 4: 1500}[zone]
    stereo = lowpass(stereo, cutoff)
    write_ogg(stereo, f"level94_zone{zone}")


# ---------------------------------------------------------------------------------------------------------
# Infinite field: desolation (D aeolian, no pulse, 96 s)
# ---------------------------------------------------------------------------------------------------------

def desolation():
    length = 96.0
    n = int(length * SR)
    t = np.arange(n) / SR
    rng = np.random.default_rng(31)

    # A low fifth that swells and fades, like wind over nothing.
    swell = 0.55 + 0.45 * np.sin(2 * np.pi * 4 / length * t - np.pi / 2)
    drone = (np.sin(2 * np.pi * hz(38) * t) + 0.6 * np.sin(2 * np.pi * hz(45) * t + 0.7)
             + 0.25 * np.sin(2 * np.pi * hz(50) * t * 1.001)) * swell
    air = lowpass(rng.standard_normal(n), 700) * 0.06 * (0.6 + 0.4 * np.sin(2 * np.pi * 3 / length * t))

    pads = np.zeros(n)
    for start in (0.0, 48.0):
        add(pads, pad([hz(50), hz(53), hz(57), hz(64)], 30.0, brightness=2.5, detune_cents=5), start + 6)

    # Few notes, far apart, always falling back to D.
    phrases = [
        (4.0, [(69, 2.5), (65, 2.5), (64, 4.0), (62, 7.0)]),
        (30.0, [(72, 2.5), (69, 2.5), (67, 3.0), (69, 8.0)]),
        (56.0, [(74, 2.0), (72, 2.5), (69, 2.5), (65, 4.0), (64, 8.0)]),
        (80.0, [(65, 2.5), (64, 3.5), (62, 9.0)]),
    ]
    keys = np.zeros(n)
    for start, notes in phrases:
        at = start
        for midi, beats in notes:
            add(keys, piano(hz(midi), min(beats, 4.0), 0.75 + 0.2 * rng.random()), at + rng.uniform(-0.08, 0.08))
            at += beats
    for at, midi in ((47.0, 88), (76.0, 81)):
        add(keys, piano(hz(midi), 2.0, 0.25), at)  # a single high note, very far away

    mono = 0.6 * keys + 0.14 * pads + 0.13 * drone + air
    stereo = reverb(mono, 6.0, 0.5, 31, brightness=2500)
    stereo = lowpass(stereo, 7000)
    write_ogg(stereo, "infinite_field", quality=3)


if __name__ == "__main__":
    for z in (1, 2, 3, 4):
        nostalgia(z)
    desolation()
