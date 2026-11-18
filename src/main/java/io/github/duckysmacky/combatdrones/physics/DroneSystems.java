package io.github.duckysmacky.combatdrones.physics;

/** Battery and radio-link models shared by server logic and the pilot's HUD. No Minecraft dependency. */
public final class DroneSystems {
    /**
     * Fraction of signal range with a perfect link; past it the loss grows exponentially, reaching a total loss at
     * full range.
     * shortcut: distance-only falloff; add terrain/line-of-sight attenuation if range needs to feel physical.
     */
    public static final double SIGNAL_FALLOFF_START = 0.5;
    /** Steepness of the loss past the falloff start: 3 gives ~4% loss at 60% range, ~18% at 75%, ~52% at 90%. */
    public static final double SIGNAL_LOSS_CURVE = 3;
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
        double t = Math.min(1, (distance - falloffStart) / (range - falloffStart));
        double loss = (Math.exp(SIGNAL_LOSS_CURVE * t) - 1) / (Math.exp(SIGNAL_LOSS_CURVE) - 1);
        return 1 - loss;
    }

    /** Charge used this tick with motors running at {@code thrustFraction} of full thrust. */
    public static double drainPerTick(double fullThrottleDrain, double thrustFraction) {
        return fullThrottleDrain * Math.max(IDLE_DRAIN_FRACTION, thrustFraction);
    }
}
