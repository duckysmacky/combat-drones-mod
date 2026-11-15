package io.github.duckysmacky.combatdrones.type;

import java.util.Locale;

/**
 * Bomb sizes a bomber's bay can carry, one size at a time (a 96-unit bay holds 96 small, 48 medium or 24 large). Each
 * has its own blast, its own reload between drops made by hand, and its own spacing in a carpet run flown by the
 * autopilot. Values come from the config ({@code [aerial_bombs.<name>]}) and are synced to clients for the HUD and the
 * planner's drop preview.
 */
public enum AerialBombType {
    SMALL("aerial_bomb_small", 1, 4.0, 5, 4),
    MEDIUM("aerial_bomb_medium", 2, 8.25, 20, 10),
    LARGE("aerial_bomb_large", 4, 12.5, 40, 20);

    private final String itemName;
    private final int defaultBayUnits;
    private final double defaultBlastPower;
    private final double defaultCooldownTicks;
    private final double defaultCarpetIntervalTicks;
    private int bayUnits;
    private double blastPower;
    private double cooldownTicks;
    private double carpetIntervalTicks;

    AerialBombType(String itemName, int bayUnits, double blastPower, double cooldownTicks, double carpetIntervalTicks) {
        this.itemName = itemName;
        this.defaultBayUnits = bayUnits;
        this.defaultBlastPower = blastPower;
        this.defaultCooldownTicks = cooldownTicks;
        this.defaultCarpetIntervalTicks = carpetIntervalTicks;
        set(bayUnits, blastPower, cooldownTicks, carpetIntervalTicks);
    }

    /** Lowercase name used for config tables and HUD/lang keys. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Item registry name, also the falling bomb's geo model. */
    public String itemName() {
        return itemName;
    }

    public int getBayUnits() {
        return bayUnits;
    }

    /** Vanilla scale: TNT = 4. */
    public double getBlastPower() {
        return blastPower;
    }

    /** Ticks between drops made by hand. */
    public double getCooldownTicks() {
        return cooldownTicks;
    }

    /** Ticks between bombs in a carpet run. */
    public double getCarpetIntervalTicks() {
        return carpetIntervalTicks;
    }

    public int getDefaultBayUnits() {
        return defaultBayUnits;
    }

    public double getDefaultBlastPower() {
        return defaultBlastPower;
    }

    public double getDefaultCooldownTicks() {
        return defaultCooldownTicks;
    }

    public double getDefaultCarpetIntervalTicks() {
        return defaultCarpetIntervalTicks;
    }

    public void set(int bayUnits, double blastPower, double cooldownTicks, double carpetIntervalTicks) {
        this.bayUnits = bayUnits;
        this.blastPower = blastPower;
        this.cooldownTicks = cooldownTicks;
        this.carpetIntervalTicks = carpetIntervalTicks;
    }

    /** How many of these fit in a bay of {@code bayCapacity} units. */
    public int capacityIn(int bayCapacity) {
        return bayCapacity / Math.max(1, bayUnits);
    }

    /** Safe decode for synced/saved ordinals; SMALL for anything unknown. */
    public static AerialBombType byIndex(int index) {
        AerialBombType[] types = values();
        return index >= 0 && index < types.length ? types[index] : SMALL;
    }
}
