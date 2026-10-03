package io.github.duckysmacky.combatdrones.type;

import java.util.Locale;

/**
 * Bomb sizes a DJI-style drone can drop. Each bomb takes bay units (a 4-unit bay holds 4 light, 2 medium or 1 heavy),
 * explodes with its own blast power and slows the drone by its weight while carried. Values come from the config
 * ({@code [bombs.<name>]}) and are synced to clients, since weight changes flight prediction.
 */
public enum BombType {
    LIGHT("bomb_light", 1, 2.5, 0.03),
    // Keeps the original "bomb" item ID: it was the only bomb before sizes existed
    MEDIUM("bomb", 2, 5.0, 0.07),
    HEAVY("bomb_heavy", 4, 8.0, 0.15);

    private final String itemName;
    private final int defaultBayUnits;
    private final double defaultBlastPower;
    private final double defaultWeight;
    private int bayUnits;
    private double blastPower;
    private double weight;

    BombType(String itemName, int bayUnits, double blastPower, double weight) {
        this.itemName = itemName;
        this.defaultBayUnits = bayUnits;
        this.defaultBlastPower = blastPower;
        this.defaultWeight = weight;
        this.bayUnits = bayUnits;
        this.blastPower = blastPower;
        this.weight = weight;
    }

    /** Lowercase name used for config tables and HUD/lang keys. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

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

    /** Fraction of top speed and acceleration lost per bomb carried. */
    public double getWeight() {
        return weight;
    }

    public int getDefaultBayUnits() {
        return defaultBayUnits;
    }

    public double getDefaultBlastPower() {
        return defaultBlastPower;
    }

    public double getDefaultWeight() {
        return defaultWeight;
    }

    public void set(int bayUnits, double blastPower, double weight) {
        this.bayUnits = bayUnits;
        this.blastPower = blastPower;
        this.weight = weight;
    }

    /** How many of these fit in a bay of {@code bayCapacity} units. */
    public int capacityIn(int bayCapacity) {
        return bayCapacity / Math.max(1, bayUnits);
    }

    /** Safe decode for synced/saved ordinals; null for "none" or garbage. */
    public static BombType byIndex(int index) {
        BombType[] types = values();
        return index >= 0 && index < types.length ? types[index] : null;
    }
}
