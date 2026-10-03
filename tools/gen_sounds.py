#!/usr/bin/env python3
"""Synthesizes the placeholder drone sounds (until the art pass) into src/main/resources/assets/combat_drones/sounds/.

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


def motor():
    """One-second seamless loop: small FPV props, a high whine with a fast buzz. Whole-Hz frequencies loop cleanly."""
    t = time(1.0)
    fundamental = 420
    whine = sum(np.sin(2 * np.pi * fundamental * k * t) / k ** 1.1 for k in range(1, 12))
    # Four props slightly out of phase give the characteristic beating buzz
    beating = 0.65 + 0.35 * np.sin(2 * np.pi * 37 * t)
    rng = np.random.default_rng(7)
    noise = rng.standard_normal(len(t))
    # Light smoothing keeps the hiss bright (prop wash), wrap-around padding keeps the loop seamless
    noise = np.convolve(np.concatenate([noise[-8:], noise]), np.ones(8) / 8, mode='valid')[:len(t)]
    return normalize(whine * beating + 0.25 * normalize(noise, 1.0), 0.85)


def motor_far():
    """One-second seamless loop heard by other players: a softer, lower drone hum with a whoosh, no harsh highs."""
    t = time(1.0)
    fundamental = 200
    hum = sum(np.sin(2 * np.pi * fundamental * k * t) / k ** 2 for k in range(1, 7))
    beating = 0.75 + 0.25 * np.sin(2 * np.pi * 17 * t)
    rng = np.random.default_rng(13)
    noise = rng.standard_normal(len(t))
    # Heavy smoothing turns the noise into a low prop-wash whoosh
    noise = np.convolve(np.concatenate([noise[-200:], noise]), np.ones(200) / 200, mode='valid')[:len(t)]
    return normalize(hum * beating + 0.5 * normalize(noise, 1.0), 0.75)


def engine():
    """One-second seamless loop for ramp-launched planes: a low pulsing propeller-engine drone, kept soft."""
    t = time(1.0)
    hum = sum(np.sin(2 * np.pi * 120 * k * t) / k ** 1.8 for k in range(1, 8))
    # Engine firing pulses (whole-Hz so the loop stays seamless)
    pulse = 0.7 + 0.3 * np.sin(2 * np.pi * 40 * t)
    rng = np.random.default_rng(17)
    noise = rng.standard_normal(len(t))
    noise = np.convolve(np.concatenate([noise[-120:], noise]), np.ones(120) / 120, mode='valid')[:len(t)]
    return normalize(hum * pulse + 0.35 * normalize(noise, 1.0), 0.75)


def launch():
    """Catapult launch: a thump, then a rising-falling whoosh of air."""
    rng = np.random.default_rng(23)
    t = time(0.9)
    noise = rng.standard_normal(len(t))
    smoothing = 24
    whoosh = np.convolve(np.concatenate([np.zeros(smoothing), noise]), np.ones(smoothing) / smoothing, mode='valid')[:len(t)]
    envelope = np.sin(np.pi * np.minimum(1, t / 0.9)) ** 2
    thump = np.sin(2 * np.pi * 70 * t) * np.exp(-t * 25)
    return normalize(whoosh * envelope * 1.5 + thump, 0.8)


def rocket():
    """Rocket leaving the pod: an ignition pop, then a hissing rush that fades as it flies off."""
    rng = np.random.default_rng(31)
    t = time(1.1)
    noise = rng.standard_normal(len(t))
    smoothing = 6
    hiss = np.convolve(np.concatenate([np.zeros(smoothing), noise]), np.ones(smoothing) / smoothing, mode='valid')[:len(t)]
    envelope = np.minimum(1, t / 0.03) * np.exp(-t * 3)
    pop = np.sin(2 * np.pi * 90 * t) * np.exp(-t * 40)
    return normalize(hiss * envelope + 0.8 * pop, 0.8)


def gun():
    """One round from the gun mount: a short, hard crack with a little low-end thump."""
    rng = np.random.default_rng(37)
    t = time(0.12)
    crack = rng.standard_normal(len(t)) * np.exp(-t * 70)
    thump = np.sin(2 * np.pi * 110 * t) * np.exp(-t * 45)
    return normalize(crack + 0.7 * thump, 0.8)


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


def click():
    """Battery pack snapping into place: two short broadband ticks."""
    rng = np.random.default_rng(3)

    def tick(seconds, frequency):
        t = time(seconds)
        return (rng.standard_normal(len(t)) * 0.6 + np.sin(2 * np.pi * frequency * t)) * np.exp(-t * 90)
    return normalize(np.concatenate([tick(0.03, 3200), np.zeros(int(RATE * 0.05)), tick(0.06, 1800)]), 0.7)


def clink():
    """Grenade clipped onto the frame: a short metallic ring (inharmonic partials)."""
    t = time(0.35)
    partials = [(1250, 1.0), (2710, 0.6), (4190, 0.35), (6020, 0.2)]
    ring = sum(amplitude * np.sin(2 * np.pi * frequency * t) for frequency, amplitude in partials)
    return normalize(ring * np.exp(-t * 14) * np.minimum(1, t / 0.002), 0.6)


def hit():
    """Projectile hitting the airframe: a sharp plastic/metal crack with a short rattle."""
    rng = np.random.default_rng(5)
    t = time(0.18)
    crack = rng.standard_normal(len(t)) * np.exp(-t * 60)
    ring = (np.sin(2 * np.pi * 1700 * t) + 0.5 * np.sin(2 * np.pi * 3100 * t)) * np.exp(-t * 30)
    return normalize(crack + 0.6 * ring, 0.75)


def connect():
    """Goggles finding the video feed: a burst of static settling into a rising three-tone chime."""
    rng = np.random.default_rng(11)
    t = time(0.25)
    static = rng.standard_normal(len(t)) * np.exp(-t * 10) * 0.5
    chime = np.concatenate([tone(988, 0.07), tone(1319, 0.07), tone(1976, 0.12)])
    return normalize(np.concatenate([static, chime]), 0.7)


SOUNDS = {
    'motor': motor,
    'motor_far': motor_far,
    'link': link,
    'beep': beep,
    'click': click,
    'clink': clink,
    'connect': connect,
    'hit': hit,
    'engine': engine,
    'launch': launch,
    'rocket': rocket,
    'gun': gun,
}

if __name__ == '__main__':
    import sys
    # Vorbis output isn't byte-identical between runs, so only regenerate what you name (default: all)
    for name in sys.argv[1:] or SOUNDS:
        write_ogg(name, SOUNDS[name]())
    print('Wrote', sys.argv[1:] or sorted(SOUNDS))
