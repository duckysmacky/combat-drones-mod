package io.github.duckysmacky.combatdrones.type;

/**
 * What a ramp unit carries into a fight: rounds or rockets left in each weapon station, and which rocket size its
 * rocket pod holds (one size at a time). Travels between the unit's item, its ramp and the flying unit.
 */
public final class Loadout {
    /** Weapon stations per airframe: the primary weapon and optional secondary and tertiary ones. */
    public static final int STATIONS = 3;

    private final int[] ammo = new int[STATIONS];
    private RocketType rocketKind = RocketType.LIGHT;
    /** Which bomb size a bomb bay holds (one size at a time, like a rocket pod). */
    private AerialBombType bombKind = AerialBombType.SMALL;

    public int getAmmo(int station) {
        return ammo[station];
    }

    public void setAmmo(int station, int count) {
        ammo[station] = Math.max(0, count);
    }

    public RocketType getRocketKind() {
        return rocketKind;
    }

    public void setRocketKind(RocketType kind) {
        rocketKind = kind;
    }

    public AerialBombType getBombKind() {
        return bombKind;
    }

    public void setBombKind(AerialBombType kind) {
        bombKind = kind;
    }

    public boolean isEmpty() {
        for (int count : ammo) {
            if (count > 0) {
                return false;
            }
        }
        return true;
    }

    /** Rounds or rockets {@code station} holds when full, with the rocket size currently loaded. */
    public int capacity(DroneType type, int station) {
        DroneType.WeaponType weapon = type.getWeapon(station);
        int units = type.getStationCapacity(station);
        switch (weapon) {
            case NONE:
                return 0;
            case ROCKET_POD:
                return rocketKind.capacityIn(units);
            case BOMB_BAY:
                return bombKind.capacityIn(units);
            default:
                return units;
        }
    }

    /** Trims every station to what fits (after a config change or a type switch). */
    public Loadout clampTo(DroneType type) {
        for (int station = 0; station < STATIONS; station++) {
            ammo[station] = Math.min(ammo[station], capacity(type, station));
        }
        return this;
    }
}
