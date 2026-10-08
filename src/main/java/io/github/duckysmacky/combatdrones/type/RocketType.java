package io.github.duckysmacky.combatdrones.type;

import java.util.Locale;

/**
 * Rocket sizes a rocket pod can fire, one size at a time. Each rocket takes bay units (a 32-unit bay holds 32 light or
 * 16 heavy) and explodes with its own blast power. Values come from the config ({@code [rockets.<name>]}) and are
 * synced to clients for the HUD and tooltips.
 */
public enum RocketType {
    LIGHT("rocket", 1, 3.0),
    HEAVY("rocket_heavy", 2, 5.5);

    private final String itemName;
    private final int defaultBayUnits;
    private final double defaultBlastPower;
    private int bayUnits;
    private double blastPower;

    RocketType(String itemName, int bayUnits, double blastPower) {
        this.itemName = itemName;
        this.defaultBayUnits = bayUnits;
        this.defaultBlastPower = blastPower;
        this.bayUnits = bayUnits;
        this.blastPower = blastPower;
    }

    /** Lowercase name used for config tables and HUD/lang keys. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Item registry name, also the projectile's geo model. */
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

    public int getDefaultBayUnits() {
        return defaultBayUnits;
    }

    public double getDefaultBlastPower() {
        return defaultBlastPower;
    }

    public void set(int bayUnits, double blastPower) {
        this.bayUnits = bayUnits;
        this.blastPower = blastPower;
    }

    /** How many of these fit in a bay of {@code bayCapacity} units. */
    public int capacityIn(int bayCapacity) {
        return bayCapacity / Math.max(1, bayUnits);
    }

    /** Safe decode for synced/saved ordinals; LIGHT for anything unknown (also what older saves without a kind held). */
    public static RocketType byIndex(int index) {
        RocketType[] types = values();
        return index >= 0 && index < types.length ? types[index] : LIGHT;
    }
}
