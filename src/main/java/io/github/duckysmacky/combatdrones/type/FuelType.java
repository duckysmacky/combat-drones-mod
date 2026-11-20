package io.github.duckysmacky.combatdrones.type;

import java.util.Locale;

/**
 * Fuel for ramp-launched units and missiles: each vehicle class burns its own, derived from its type data so a new
 * variant needs no extra field. One item of the right fuel fills the tank completely.
 */
public enum FuelType {
    /** Piston-engined munitions (the Munition Ramp family). */
    AVGAS,
    /** Planes that fit the Light Runway. */
    JET_A1,
    /** Planes that need the Heavy Runway. */
    JP8,
    /** Pad-launched cruise missiles. */
    JP10;

    /** Item registry name ({@code JET_A1} -> {@code jet_a1_fuel}). */
    public String itemName() {
        return name().toLowerCase(Locale.ROOT) + "_fuel";
    }

    /** The fuel {@code type} burns; same split as the ramp kinds, plus missiles. */
    public static FuelType forUnit(DroneType type) {
        if (type.getCategory() == DroneType.Category.VERTICAL_STRIKE) {
            return JP10;
        }
        return type.isImpactDetonates() ? AVGAS : type.isHeavyRunway() ? JP8 : JET_A1;
    }
}
