package io.github.duckysmacky.combatdrones.physics;

/** Battery and radio-link models shared by server logic and the pilot's HUD. No Minecraft dependency. */
public final class DroneSystems {
    /**
     * Fraction of signal range with a perfect link; quality then falls linearly to 0 at full range.
     * ponytail: distance-only falloff; add terrain/line-of-sight attenuation if range needs to feel physical.
     */
    public static final double SIGNAL_FALLOFF_START = 0.6;
    /** Motors draw at least this share of full-throttle drain whenever they're spinning. */
    public static final double IDLE_DRAIN_FRACTION = 0.2;

    private DroneSystems() {
    }

    /** 1 = perfect link, 0 = at (or past) the edge of range. */
    public static double signalQuality(double distance, double range) {
        if (range <= 0) {
            return 0;
        }
        double falloffStart = range * SIGNAL_FALLOFF_START;
        if (distance <= falloffStart) {
            return 1;
        }
        return Math.max(0, 1 - (distance - falloffStart) / (range - falloffStart));
    }

    /** Charge used this tick with motors running at {@code thrustFraction} of full thrust. */
    public static double drainPerTick(double fullThrottleDrain, double thrustFraction) {
        return fullThrottleDrain * Math.max(IDLE_DRAIN_FRACTION, thrustFraction);
    }
}
