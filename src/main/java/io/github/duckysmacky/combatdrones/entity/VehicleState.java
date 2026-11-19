package io.github.duckysmacky.combatdrones.entity;

/**
 * What a flying vehicle is up to: flying normally, knocked out for a while by an EMP (motors off, gliding down, no
 * control), or shot down (burning, falling for good, no control). Synced to clients for effects and the pilot's HUD.
 */
public enum VehicleState {
    FLYING, EMPED, SHOT_DOWN;

    public static VehicleState byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : FLYING;
    }
}
