package io.github.duckysmacky.combatdrones.config;

/**
 * A hand-flown drone type's own config table, {@code [<id>]}: its impact shrapnel, plus grenade extras for grenade
 * carriers and lock-on for sensor types (only those keys are written for it). Field names are the config keys. Per-type
 * flight stats stay in {@code [drones.<id>]}. Read-only once loaded: a reload builds a new one.
 */
public final class DroneSettings {
    public double shrapnelDamage = 6.0;
    public double shrapnelRadius = 7.0;
    public double grenadeBlastBonus = 3.0;
    public double grenadeShrapnelDamage = 5.0;
    public LockOnSettings lockOn = new LockOnSettings(3.0, 30.0);
}
