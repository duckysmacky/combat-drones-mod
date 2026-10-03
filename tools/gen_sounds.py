#!/usr/bin/env python3
"""Synthesizes the placeholder drone sounds (until the art pass) into src/main/resources/assets/combat_drones/sounds/.

Needs numpy and ffmpeg (with libvorbis). Minecraft 1.12 only positions *mono* sounds in 3D, so everything is mono.
Run from the repo root: python3 tools/gen_sounds.py
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


def motor():
    """One-second seamless loop: quad prop whine. Every frequency is a whole number of Hz so the loop has no seam."""
    t = time(1.0)
    fundamental = 190
    whine = sum(np.sin(2 * np.pi * fundamental * k * t) / k ** 1.3 for k in range(1, 9))
    # Four props slightly out of phase give the characteristic beating "buzz"
    beating = 0.6 + 0.4 * np.sin(2 * np.pi * 23 * t)
    rng = np.random.default_rng(7)
    noise = rng.standard_normal(len(t))
    # Cheap band-limit of the noise with a moving average, then wrap-around blend to keep the loop seamless
    noise = np.convolve(np.concatenate([noise[-64:], noise]), np.ones(64) / 64, mode='valid')[:len(t)]
    return normalize(whine * beating + 0.35 * normalize(noise, 1.0), 0.85)


def tone(frequency, seconds, square=0.0):
    t = time(seconds)
    sine = np.sin(2 * np.pi * frequency * t)
    wave_shape = (1 - square) * sine + square * np.sign(sine)
    attack = np.minimum(1, t / 0.005)
    release = np.minimum(1, (seconds - t) / 0.02)
    return wave_shape * attack * release


def link():
    """Rising two-tone confirmation chirp."""
    gap = np.zeros(int(RATE * 0.04))
    return normalize(np.concatenate([tone(880, 0.09), gap, tone(1320, 0.12)]), 0.7)


def beep():
    """Short goggle/OSD beep; callers vary pitch for different warnings."""
    return normalize(tone(2000, 0.08, square=0.3), 0.6)


if __name__ == '__main__':
    write_ogg('motor', motor())
    write_ogg('link', link())
    write_ogg('beep', beep())
    print('Wrote', sorted(os.listdir(OUT)))
