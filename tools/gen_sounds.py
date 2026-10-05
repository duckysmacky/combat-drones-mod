#!/usr/bin/env python3
"""Synthesizes the UI beep, the hit crack and the pilot radio static into src/main/resources/assets/combat_drones/sounds/.
Engines, weapons and explosions are real recordings built by tools/art/build_sounds.py.

Needs numpy and ffmpeg (with libvorbis). Minecraft 1.12 only positions *mono* sounds in 3D, so everything is mono.
Run from the repo root: python3 tools/gen_sounds.py [sound names...]
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = 'src/main/resources/assets/combat_drones/sounds/drone'


def write_ogg(name, samples):
    samples = np.clip(samples, -1, 1)
    pcm = (samples * 32767).astype('<i2')
    os.makedirs(OUT, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav_path, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5',
                    os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(wav_path)


def time(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def normalize(samples, peak=0.8):
    return samples / np.max(np.abs(samples)) * peak


def tone(frequency, seconds, square=0.0):
    t = time(seconds)
    sine = np.sin(2 * np.pi * frequency * t)
    wave_shape = (1 - square) * sine + square * np.sign(sine)
    attack = np.minimum(1, t / 0.005)
    release = np.minimum(1, (seconds - t) / 0.02)
    return wave_shape * attack * release


def beep():
    """Short goggle/OSD beep; callers vary pitch for different warnings."""
    return normalize(tone(2000, 0.08, square=0.3), 0.6)


def hit():
    """Projectile hitting the airframe: a sharp plastic/metal crack with a short rattle."""
    rng = np.random.default_rng(5)
    t = time(0.18)
    crack = rng.standard_normal(len(t)) * np.exp(-t * 60)
    ring = (np.sin(2 * np.pi * 1700 * t) + 0.5 * np.sin(2 * np.pi * 3100 * t)) * np.exp(-t * 30)
    return normalize(crack + 0.6 * ring, 0.75)


def pilot_static():
    """Analog video receiver hiss with crackles and a telemetry chirp, leaking from a pilot's stand-in body."""
    rng = np.random.default_rng(11)
    seconds = 1.6
    t = time(seconds)
    noise = rng.standard_normal(len(t))
    # Crude band-pass (difference of moving averages) to take the harsh top and the rumble off the hiss
    hiss = np.convolve(noise, np.ones(4) / 4, 'same') - np.convolve(noise, np.ones(40) / 40, 'same')
    flutter = 0.6 + 0.4 * np.sin(2 * np.pi * 3.3 * t + rng.uniform(0, 2 * np.pi)) * np.sin(2 * np.pi * 0.7 * t)
    signal = 0.35 * hiss * flutter
    for start in rng.uniform(0.2, seconds - 0.1, 6):
        at = int(start * RATE)
        length = int(0.015 * RATE)
        signal[at:at + length] += rng.standard_normal(length) * np.exp(-np.arange(length) / (0.003 * RATE)) * 0.9
    # Two-tone FSK burst, like telemetry on the control link
    bit_samples = int(0.012 * RATE)
    frequencies = np.repeat(np.where(rng.integers(0, 2, 12) == 1, 1200, 2200), bit_samples)
    edges = np.minimum(1, np.minimum(np.arange(len(frequencies)), len(frequencies) - np.arange(len(frequencies))) / (0.005 * RATE))
    chirp = np.sin(2 * np.pi * np.cumsum(frequencies) / RATE) * edges
    at = int(0.15 * RATE)
    signal[at:at + len(chirp)] += 0.5 * chirp
    fade = np.minimum(1, np.minimum(t / 0.08, (seconds - t) / 0.25))
    return normalize(signal * fade, 0.6)


SOUNDS = {
    'beep': beep,
    'hit': hit,
    'pilot_static': pilot_static,
}

if __name__ == '__main__':
    import sys
    # Vorbis output isn't byte-identical between runs, so only regenerate what you name (default: all)
    for name in sys.argv[1:] or SOUNDS:
        write_ogg(name, SOUNDS[name]())
    print('Wrote', sys.argv[1:] or sorted(SOUNDS))
