package io.github.duckysmacky.combatdrones.config;

/**
 * A sensor vehicle's lock-on settings: {@code [uav]} for the Recon UAV, the type's own table for a hand-flown sensor
 * drone. Field names are the config keys. Read-only once loaded: a reload builds a new one.
 */
public final class LockOnSettings {
    public boolean lockOnEnabled = true;
    public double lockOnRange = 192.0;
    public double lockOnConeDegrees = 6.0;
    public double lockOnAimMargin = 1.5;
    public double lockOnHoldSeconds;
    public double lockOnDurationSeconds;
    public int lockOnCharges = 3;
    public double lockOnReloadSeconds = 30.0;
    public int maxActiveLockOns = 1;
    public boolean reloadDuringLockOn = false;
    public boolean lockOnForAllies = true;
    public boolean lockOnWarning = true;
    public boolean lockedWarning = true;
    public boolean trackNonPlayers = false;

    LockOnSettings(double holdSeconds, double durationSeconds) {
        lockOnHoldSeconds = holdSeconds;
        lockOnDurationSeconds = durationSeconds;
    }

    public int holdTicks() {
        return (int) Math.round(lockOnHoldSeconds * 20);
    }

    public long durationTicks() {
        return (long) (lockOnDurationSeconds * 20);
    }

    public int reloadTicks() {
        return Math.max(1, (int) Math.round(lockOnReloadSeconds * 20));
    }
}
