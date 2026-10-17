package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.physics.SoundBands;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;

/**
 * Engine of one vehicle as others hear it: a loop per distance band (close, mid, far) from the type's {@code sound},
 * crossfaded by the listener's distance over {@code motorSoundRange} ({@link SoundBands}), with its own falloff, a
 * slight pitch drop far out and Doppler. Only bands with gain play, so a vehicle holds at most two channels (three
 * with the dive layer). Ticked by {@link DroneSounds} in two steps: {@link #prepare} (where is it, how far) for every
 * voice, then {@link #play} with whether it's among the nearest few that get to sound. Subclasses say where the
 * vehicle is and how its engine runs.
 */
abstract class EngineVoice {
    /** Below this a band is stopped rather than played silently. */
    private static final double AUDIBLE = 0.002;
    /** A wanted band the sound engine isn't playing (dropped at the channel limit) is restarted after this long. */
    private static final int RESTART_TICKS = 20;
    /** Under a roof the bands are heard as if this much further out (the far bands are the muffled ones)... */
    private static final double INDOOR_SHIFT = 0.35;
    /** ...and quieter. */
    private static final double INDOOR_GAIN = 0.6;
    /** The terminal sound starts when a diving munition is this many ticks from impact. */
    private static final int TERMINAL_TICKS = 50;

    private final DroneType.Sound sound;
    private final String[] events;
    private final double[] untils;
    private final double range;
    private final BandSound[] bands;
    private final int[] startedTick;
    private BandSound diveLayer;
    private BandSound terminal;
    private double lastDistance = Double.NaN;
    private double radialSpeed;
    /** This tick's position and distance from the listener (0 when heard from inside), set by {@link #prepare}. */
    private Vec3d at;
    private double distance;
    private int ticks;
    private boolean done;

    EngineVoice(DroneType type) {
        this(type.getSound(), type.getMotorSoundRange());
    }

    EngineVoice(@Nullable DroneType.Sound sound, double range) {
        this.sound = sound;
        events = sound == null ? new String[0] : sound.getEvents();
        untils = sound == null ? new double[0] : sound.getUntils();
        this.range = range;
        bands = new BandSound[events.length];
        startedTick = new int[events.length];
    }

    /** False once the engine stops or the vehicle is gone: the voice then falls silent for good. */
    abstract boolean isRunning();

    abstract Vec3d position();

    /** Pitch from the engine's state (throttle, speed, boost). */
    abstract double enginePitch();

    /** The listener rides it (remote pilot): heard as the close band, full volume, no Doppler. */
    boolean heardFromInside() {
        return false;
    }

    /** Its own pilot hears the dive and terminal sounds too (the air roar), unless they're an outside-only warning. */
    boolean diveHeardInside() {
        return true;
    }

    /** 0..1: how much of the dive layer plays (a steep, fast dive is 1). */
    double diveAmount() {
        return 0;
    }

    double divePitch() {
        return 1;
    }

    /** Ticks until it hits something on its current course, or {@link Double#POSITIVE_INFINITY} if not soon. */
    double ticksToImpact() {
        return Double.POSITIVE_INFINITY;
    }

    /** Blocks from the listener as of the last {@link #prepare}. */
    final double getDistance() {
        return distance;
    }

    final boolean isDone() {
        return done;
    }

    final void stop() {
        done = true;
        for (int band = 0; band < bands.length; band++) {
            stopBand(band);
        }
        if (diveLayer != null) {
            diveLayer.stop();
            diveLayer = null;
        }
        // Cut at impact too: the blast takes over, and its start was only an estimate of when that would be
        if (terminal != null) {
            terminal.stop();
            terminal = null;
        }
    }

    /**
     * Step one each tick: stops for good once the engine does, else finds where it is and how far from the listener.
     * Returns that distance as a fraction of its range (0 from inside, 1 or more = out of earshot or stopped).
     */
    final double prepare(Vec3d listener) {
        if (done) {
            return Double.POSITIVE_INFINITY;
        }
        if (!isRunning() || range <= 0 || events.length == 0) {
            stop();
            return Double.POSITIVE_INFINITY;
        }
        ticks++;
        at = position();
        distance = heardFromInside() ? 0 : at.distanceTo(listener);
        // Radial speed from the change in distance, smoothed: interpolated entity positions jitter
        double radial = Double.isNaN(lastDistance) ? 0 : distance - lastDistance;
        radialSpeed += (radial - radialSpeed) * 0.3;
        lastDistance = distance;
        return distance / range;
    }

    /** Step two: sounds (or, past the nearest-voices cap, silences) it for this tick. */
    final void play(boolean audible, boolean indoors) {
        if (done || at == null) {
            return;
        }
        if (!audible) {
            for (int band = 0; band < bands.length; band++) {
                stopBand(band);
            }
            tickDive(null, 0);
            return;
        }
        boolean inside = heardFromInside();
        double fraction = distance / range;
        double muffled = indoors && !inside ? Math.min(0.999, fraction + INDOOR_SHIFT) : fraction;
        double[] gains = SoundBands.bandGains(fraction >= 1 ? 1 : muffled, untils, sound.getCrossfade());
        double dive = sound.getDive() == null || (inside && !diveHeardInside()) ? 0 : diveAmount();
        double overall = (inside ? 1 : SoundBands.distanceGain(fraction)) * (indoors && !inside ? INDOOR_GAIN : 1) * sound.getVolume();
        double pitch = enginePitch() * (inside ? 1 : SoundBands.distancePitch(fraction) * SoundBands.doppler(radialSpeed));
        SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
        for (int band = 0; band < bands.length; band++) {
            // The dive layer takes over from the engine as it swells
            double gain = gains[band] * overall * (1 - 0.6 * dive);
            if (gain < AUDIBLE) {
                stopBand(band);
                continue;
            }
            if (bands[band] != null && ticks - startedTick[band] > RESTART_TICKS && !handler.isSoundPlaying(bands[band])) {
                stopBand(band);
            }
            if (bands[band] == null) {
                bands[band] = new BandSound(events[band]);
                bands[band].set(at.x, at.y, at.z, gain, pitch);
                startedTick[band] = ticks;
                handler.playSound(bands[band]);
            }
            bands[band].set(at.x, at.y, at.z, gain, pitch);
        }
        tickDive(handler, overall * dive);
        tickTerminal(handler, fraction, dive);
    }

    /**
     * The scream of a munition about to hit, once, for everyone in earshot (its pilot too, unless it's an
     * outside-only warning). Booms carry, so does this: the gentler falloff. It follows the munition and ends with it.
     */
    private void tickTerminal(SoundHandler handler, double fraction, double dive) {
        String event = sound.getTerminal();
        double gain = Math.sqrt(SoundBands.distanceGain(Math.min(1, fraction))) * sound.getVolume();
        if (terminal == null && event != null && dive > 0.1 && fraction < 1 && ticksToImpact() < TERMINAL_TICKS) {
            terminal = new BandSound(event, false);
            terminal.set(at.x, at.y, at.z, gain, SoundBands.doppler(radialSpeed));
            handler.playSound(terminal);
        } else if (terminal != null) {
            terminal.set(at.x, at.y, at.z, gain, SoundBands.doppler(radialSpeed));
        }
    }

    /** Swells and fades the dive layer; a null handler (or no gain) silences it. */
    private void tickDive(SoundHandler handler, double gain) {
        String event = sound.getDive();
        if (handler == null || event == null || gain < AUDIBLE) {
            if (diveLayer != null) {
                diveLayer.stop();
                diveLayer = null;
            }
            return;
        }
        double pitch = divePitch() * (heardFromInside() ? 1 : SoundBands.doppler(radialSpeed));
        if (diveLayer == null) {
            diveLayer = new BandSound(event);
            diveLayer.set(at.x, at.y, at.z, gain, pitch);
            handler.playSound(diveLayer);
        }
        diveLayer.set(at.x, at.y, at.z, gain, pitch);
    }

    private void stopBand(int band) {
        if (bands[band] != null) {
            bands[band].stop();
            bands[band] = null;
        }
    }
}
