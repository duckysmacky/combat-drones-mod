#!/usr/bin/env python3
"""Builds the mod's recorded sounds from the reference mod jars in reference_mods/ (kept local, not in git).

Run from anywhere: python3 tools/art/build_sounds.py
Needs numpy and ffmpeg (libvorbis). Everything is written mono (1.12 only positions mono sounds in 3D), loops get a
crossfaded seam so they repeat without a click. The UI beep and hit crack stay with tools/gen_sounds.py.
Sources: "FPV To Minecraft" 1.20.1 (fpv:) and VIC's Modern Warfare 1.12.2 (mw:), used for this private server only.
The cruise missile sounds come from CC0 / public-domain recordings in reference_mods/sounds/ (SOURCES.txt there);
rebuild just those with --missile.
"""
import subprocess
import sys
import tempfile
import wave
import zipfile
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/combat_drones/sounds"
JARS = {
    "fpv": (ROOT / "reference_mods/FPV To Minecraft Beta-1.20.1-V1.1.8.jar", "assets/fpvdrone/sounds/"),
    "mw": (ROOT / "reference_mods/mw_2.0-0.4.6.2_mc1.12.2.jar", "assets/mw/sounds/"),
}
RATE = 44100


def load(source):
    """'fpv:drone_engine' -> mono float samples at RATE."""
    jar, name = source.split(":")
    path, prefix = JARS[jar]
    with zipfile.ZipFile(path) as archive:
        data = archive.read(prefix + name + ".ogg")
    with tempfile.NamedTemporaryFile(suffix=".ogg") as tmp:
        tmp.write(data)
        tmp.flush()
        raw = subprocess.run(["ffmpeg", "-v", "error", "-i", tmp.name, "-ac", "1", "-ar", str(RATE), "-f", "s16le", "-"],
                             capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768


def cut(samples, start, end=None):
    return samples[int(start * RATE):None if end is None else int(end * RATE)]


def speed(samples, factor):
    """Tape-style speed change: pitch and tempo together (factor > 1 = higher, shorter)."""
    positions = np.arange(0, len(samples) - 1, factor)
    return np.interp(positions, np.arange(len(samples)), samples).astype(np.float32)


def lowpass(samples, cutoff):
    spectrum = np.fft.rfft(samples)
    freqs = np.fft.rfftfreq(len(samples), 1 / RATE)
    spectrum *= 1 / (1 + (freqs / cutoff) ** 4)
    return np.fft.irfft(spectrum, len(samples)).astype(np.float32)


def highpass(samples, cutoff):
    spectrum = np.fft.rfft(samples)
    freqs = np.fft.rfftfreq(len(samples), 1 / RATE)
    spectrum *= 1 - 1 / (1 + (freqs / cutoff) ** 4)
    return np.fft.irfft(spectrum, len(samples)).astype(np.float32)


def trim(samples, threshold=0.02):
    """Drops leading silence so a one-shot plays the moment it's triggered."""
    loud = np.nonzero(np.abs(samples) > threshold * np.max(np.abs(samples)))[0]
    return samples[max(0, loud[0] - int(0.005 * RATE)):] if len(loud) else samples


def fade(samples, fade_in=0.005, fade_out=0.05):
    samples = samples.copy()
    n_in, n_out = int(fade_in * RATE), int(fade_out * RATE)
    if n_in:
        samples[:n_in] *= np.linspace(0, 1, n_in)
    if n_out:
        samples[-n_out:] *= np.linspace(1, 0, n_out)
    return samples


def loop(samples, crossfade=0.4):
    """Seamless loop: the tail is blended into the head (equal power), then dropped."""
    n = int(crossfade * RATE)
    head, tail = samples[:n], samples[-n:]
    t = np.linspace(0, np.pi / 2, n)
    blended = head * np.sin(t) + tail * np.cos(t)
    return np.concatenate([blended, samples[n:-n]])


def mix(*parts):
    length = max(len(part) for part, _ in parts)
    out = np.zeros(length, dtype=np.float32)
    for part, gain in parts:
        out[:len(part)] += part * gain
    return out


def peak(samples, level=0.9):
    return samples / (np.max(np.abs(samples)) + 1e-9) * level


def rms(samples, level=0.25):
    return np.clip(samples / (np.sqrt(np.mean(samples ** 2)) + 1e-9) * level, -1, 1)


def noise_whoosh(duration):
    """Compressed-air hiss that darkens as it dies away: the catapult releasing."""
    rng = np.random.default_rng(7)
    n = int(duration * RATE)
    noise = rng.normal(0, 1, n).astype(np.float32)
    attack = np.minimum(1, np.linspace(0, 40, n))
    bright = lowpass(noise, 5000) * np.exp(-np.linspace(0, 9, n))
    dark = lowpass(noise, 900) * np.exp(-np.linspace(0, 4, n)) * 1.6
    return (bright + dark) * attack


def write(name, samples):
    path = OUT / (name + ".ogg")
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        with wave.open(tmp.name, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(RATE)
            wav.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", tmp.name, "-ac", "1", "-c:a", "libvorbis", "-q:a", "4", str(path)],
                       check=True)
    print(f"{name}.ogg {len(samples) / RATE:.2f}s")


def main():
    quad = load("fpv:drone_engine")
    piston_low = load("fpv:fp_1lowrange")
    piston_mid = load("fpv:fp_1middlerange")

    # Multirotors: the pilot's sharp whine, the duller buzz others hear, the DJI's softer, lower hum
    write("drone/motor", rms(loop(cut(quad, 0.5, 8.5)), 0.22))
    write("drone/motor_far", rms(loop(lowpass(speed(cut(quad, 0.5, 9.5), 0.92), 2500)), 0.25))
    write("drone/motor_dji", rms(loop(lowpass(speed(cut(quad, 1.0, 11.0), 0.7), 1800)), 0.22))
    # Ramp units: Lancet electric pusher, Shahed-style two-stroke, gunship / recon piston engines
    write("unit/engine_electric", rms(loop(highpass(speed(cut(quad, 2.0, 11.0), 0.82), 300)), 0.22))
    write("unit/engine_twostroke", rms(loop(cut(piston_low, 0.1, 4.1), 0.5), 0.27))
    write("unit/engine_piston", rms(loop(lowpass(speed(cut(piston_mid, 0.2, 8.6), 0.85), 3000), 0.5), 0.25))
    write("unit/engine", rms(loop(cut(piston_mid, 0.2, 8.6), 0.5), 0.25))
    # Launch, boost, weapons
    clank = load("fpv:rocketpod_place0")
    write("unit/launch", fade(peak(mix((speed(clank, 0.7), 1.0), (noise_whoosh(1.4), 0.8)))))
    write("unit/boost", fade(peak(speed(load("fpv:basic_ocp_rocket_distant"), 0.85)), fade_out=0.3))
    write("gunship/rocket", fade(peak(load("fpv:basic_ocp_rocket")), fade_out=0.2))
    for index, source in enumerate(("mw:m249", "mw:m60")):
        write(f"gunship/gun{index}", fade(peak(cut(load(source), 0, 0.55)), fade_out=0.2))
    # Gunship cannon: a 20 mm anti-materiel rifle pitched down into a heavier thump, over a low distant boom
    write("gunship/cannon", fade(peak(mix((speed(cut(load("mw:ntw20"), 0, 0.9), 0.75), 1.0),
                                          (lowpass(cut(load("fpv:bomblet_distant0"), 0, 1.2), 900), 0.45))), fade_out=0.3))
    for index, source in enumerate(("mw:bullet_1_rock", "mw:bullet_6_iron", "mw:bullet_5_grass")):
        write(f"bullet/impact{index}", fade(peak(cut(load(source), 0, 0.6), 0.8), fade_out=0.2))
    for index, source in enumerate(("mw:bullet_10_snap", "mw:bullet_13_snap")):
        write(f"bullet/flyby{index}", fade(peak(cut(load(source), 0, 0.6), 0.8), fade_out=0.2))
    write("bomb/release", fade(peak(trim(load("mw:firerate_toggle")), 0.7)))
    # Handling: FPV link power-up, goggles connecting, battery latch, payload / ammo clacks
    write("drone/link", fade(peak(trim(load("mw:nightvision_on")), 0.7), fade_out=0.2))
    write("drone/connect", fade(peak(trim(load("fpv:fpv_connect")), 0.6), fade_out=0.3))
    write("drone/battery", fade(peak(trim(load("mw:krag_chamberclosed")), 0.8)))
    for index in range(3):
        write(f"drone/attach{index}", fade(peak(trim(load(f"fpv:gunrocketpod_reload{index}")), 0.8)))
    # Explosions: sharp close blasts, delayed distant rumbles
    for index, source in enumerate(("fpv:bomblet_close0", "fpv:bomblet_close1", "fpv:rocketboom0", "fpv:rocketboom1")):
        write(f"explosion/near{index}", fade(peak(cut(load(source), 0, 3.0)), fade_out=0.5))
    for index, source in enumerate(("fpv:bomblet_distant0", "fpv:bomblet_distant1", "fpv:rocketboom_distant0",
                                    "fpv:airboom_distant0", "fpv:airboom_distant2")):
        write(f"explosion/far{index}", fade(peak(cut(load(source), 0, 3.5)), fade_out=0.6))


def load_file(name):
    """A downloaded original from reference_mods/sounds/ (see SOURCES.txt there) -> mono float samples at RATE."""
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(ROOT / "reference_mods/sounds" / name), "-ac", "1", "-ar", str(RATE),
                          "-f", "s16le", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768


def missile_sounds():
    """Cruise missile, from real recordings (CC0 / public domain, listed in reference_mods/sounds/SOURCES.txt):
    Artemis II launch audio for ignition, liftoff and the engine roar, an F-15 flyover's approach for the incoming
    dive, a civil-defense siren for the INBOUND alarm."""
    artemis = load_file("artemis_launch.mp3")
    rumble = load_file("canaveral_rumble.mp3")
    # Ignition: the build-up and the engines lighting (the recording's onset is at ~8 s)
    write("missile/ignition", fade(peak(lowpass(cut(artemis, 6.5, 12.5), 7000)), 0.4, 0.5))
    # Liftoff: the full-thrust blast, with the distant launch rumble under it for weight
    liftoff = mix((lowpass(cut(artemis, 8.0, 16.0), 7000), 1.0), (cut(rumble, 12.0, 20.0) * 3.0, 0.6))
    write("missile/liftoff", fade(peak(liftoff), 0.01, 1.5))
    # Engine: the steady roar once it's climbing, looped
    write("missile/engine", rms(loop(lowpass(cut(artemis, 20.0, 34.0), 6000), 1.5), 0.27))
    # Dive, heard by everyone else: a jet's approach, rising as it closes in
    write("missile/scream", fade(rms(cut(load_file("f15_flyover.mp3"), 1.5, 10.5), 0.25), 0.3, 0.3))
    # INBOUND alarm: an air-raid siren's waver
    write("missile/alarm", fade(peak(cut(load_file("siren_waver.ogg"), 17.0, 21.5), 0.8), 0.15, 0.6))


if __name__ == "__main__":
    if "--missile" in sys.argv:
        missile_sounds()
    else:
        main()
        missile_sounds()
