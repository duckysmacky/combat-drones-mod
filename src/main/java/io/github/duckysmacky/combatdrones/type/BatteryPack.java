package io.github.duckysmacky.combatdrones.type;

import java.util.Locale;

/**
 * Battery sizes for hand-flown drones. Any drone takes any size, and each is a real trade-off: Light is fast but
 * short-lived, Standard is the reference, Heavy lasts long but slows the drone. Multipliers come from the config ({@code [batteries.<name>]}) and are synced
 * to clients, since flight prediction depends on them.
 */
public enum BatteryPack {
    /** A risky pick: about a minute of racing, but light enough to make the drone 20 % faster. */
    LIGHT(0.5, 1.2),
    /** The reference: normal speed, an average flight. */
    STANDARD(1.0, 1.0),
    /** Long flights, but the weight costs a quarter of top speed and acceleration. */
    HEAVY(2.0, 0.75);

    private final double defaultCapacityMultiplier;
    private final double defaultPerformanceMultiplier;
    private double capacityMultiplier;
    private double performanceMultiplier;

    BatteryPack(double capacityMultiplier, double performanceMultiplier) {
        this.defaultCapacityMultiplier = capacityMultiplier;
        this.defaultPerformanceMultiplier = performanceMultiplier;
        this.capacityMultiplier = capacityMultiplier;
        this.performanceMultiplier = performanceMultiplier;
    }

    /** Lowercase name used for config tables and item IDs ({@code light} -> {@code light_battery}). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String itemName() {
        return key() + "_battery";
    }

    /** Multiplies a drone type's base (Standard-pack) capacity. */
    public double getCapacityMultiplier() {
        return capacityMultiplier;
    }

    /** Multiplies top speed and acceleration (weight penalty). */
    public double getPerformanceMultiplier() {
        return performanceMultiplier;
    }

    public double getDefaultCapacityMultiplier() {
        return defaultCapacityMultiplier;
    }

    public double getDefaultPerformanceMultiplier() {
        return defaultPerformanceMultiplier;
    }

    public void setMultipliers(double capacityMultiplier, double performanceMultiplier) {
        this.capacityMultiplier = capacityMultiplier;
        this.performanceMultiplier = performanceMultiplier;
    }

    /** Safe decode for synced/saved ordinals; null for "no battery" or garbage. */
    public static BatteryPack byIndex(int index) {
        BatteryPack[] packs = values();
        return index >= 0 && index < packs.length ? packs[index] : null;
    }
}
