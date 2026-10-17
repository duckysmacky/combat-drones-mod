package io.github.duckysmacky.combatdrones.physics;

/**
 * Distance-banded sound mixing, after the reference FPV mod: a vehicle or blast has one sample per distance band (close,
 * mid, far, each recorded or filtered for that distance) and the listener hears the band their distance falls in, with
 * an equal-power crossfade across each boundary. Distances here are fractions of the sound's full range (0 = on top
 * of it, 1 = out of earshot).
 */
public final class SoundBands {
    /** Speed of sound in blocks per tick: 343 m/s at 20 ticks a second. */
    public static final double SOUND_SPEED = 17;

    private SoundBands() {
    }

    /**
     * Gain of each band at {@code fraction} of the range. Band {@code i} covers up to {@code untils[i]} (increasing,
     * the last one 1); each boundary is crossfaded over {@code crossfade} of the range, centred on it, so at most two
     * bands sound at once as long as the crossfade is no wider than the narrowest band. Zero everywhere at range.
     */
    public static double[] bandGains(double fraction, double[] untils, double crossfade) {
        double[] gains = new double[untils.length];
        if (fraction >= 1 || untils.length == 0) {
            return gains;
        }
        for (int band = 0; band < untils.length; band++) {
            double in = band == 0 ? 1 : Math.sin(progress(fraction, untils[band - 1], crossfade) * Math.PI / 2);
            double out = band == untils.length - 1 ? 1 : Math.cos(progress(fraction, untils[band], crossfade) * Math.PI / 2);
            gains[band] = in * out;
        }
        return gains;
    }

    /** 0 before the crossfade round {@code boundary}, 1 after it. */
    private static double progress(double fraction, double boundary, double crossfade) {
        if (crossfade <= 0) {
            return fraction >= boundary ? 1 : 0;
        }
        return clamp((fraction - (boundary - crossfade / 2)) / crossfade, 0, 1);
    }

    /** Overall loudness at {@code fraction} of the range: inverse-distance-like near the source, silent at range. */
    public static double distanceGain(double fraction) {
        if (fraction >= 1) {
            return 0;
        }
        double clamped = Math.max(0, fraction);
        return (1 - clamped) / (1 + clamped);
    }

    /** Far sounds come in a little lower (air takes the edge off): -6% at range. */
    public static double distancePitch(double fraction) {
        return 1 - 0.06 * clamp(fraction, 0, 1);
    }

    /** Pitch factor for a source moving away from the listener at {@code radialSpeed} blocks/tick (negative = closing). */
    public static double doppler(double radialSpeed) {
        // Closing at or above the speed of sound would flip the sign: cap it, the clamp does the rest
        return clamp(SOUND_SPEED / (SOUND_SPEED + Math.max(radialSpeed, -0.9 * SOUND_SPEED)), 0.75, 1.35);
    }

    /**
     * How much of a munition's dive layer plays: swells as the nose goes down from 10 to 35 deg (positive = nose down;
     * route strikes dive at 25-60) and with speed (fraction of top speed, boosting can pass 1).
     */
    public static double diveAmount(double pitchDownDeg, double speedFraction) {
        return smoothstep(pitchDownDeg, 10, 35) * clamp(speedFraction, 0, 1);
    }

    /** 0 below {@code from}, 1 above {@code to}, smooth in between. */
    public static double smoothstep(double value, double from, double to) {
        double t = clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
