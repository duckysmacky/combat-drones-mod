package io.github.duckysmacky.combatdrones.type;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadoutTest {
    private static final DroneType STRIKE_PLANE = DroneType.parse("strike_plane", new StringReader("{"
            + "\"category\": \"RAMP_AUTOPILOT\", \"maxSpeed\": 0.9, \"acceleration\": 0.05, \"batteryOrFuelCapacity\": 6000,"
            + "\"drainRatePerTick\": 1, \"maxHP\": 100, \"defaultCruiseAltitude\": 40, \"controlScheme\": \"STICK\","
            + "\"weaponType\": \"ROCKET_POD\", \"payloadCapacity\": 32}"));

    @Test
    void rocketPodCapacityDependsOnTheLoadedSize() {
        Loadout loadout = new Loadout();
        assertEquals(32, loadout.capacity(STRIKE_PLANE, 0));
        loadout.setRocketKind(RocketType.HEAVY);
        assertEquals(16, loadout.capacity(STRIKE_PLANE, 0));
        assertEquals(0, loadout.capacity(STRIKE_PLANE, 1), "no secondary weapon");
    }

    private static String gunship(String secondary) {
        return "{\"category\": \"RAMP_AUTOPILOT\", \"maxSpeed\": 0.6, \"acceleration\": 0.03, \"batteryOrFuelCapacity\": 7200,"
                + "\"drainRatePerTick\": 1, \"maxHP\": 250, \"defaultCruiseAltitude\": 80, \"controlScheme\": \"GUNNER\","
                + "\"weaponType\": \"CANNON\", \"payloadCapacity\": 64" + secondary + "}";
    }

    @Test
    void secondaryStationHasItsOwnMagazine() {
        DroneType type = DroneType.parse("gunship", new StringReader(gunship(", \"secondaryWeapon\": \"ROCKET_POD\", \"secondaryCapacity\": 16")));
        Loadout loadout = new Loadout();
        loadout.setRocketKind(RocketType.HEAVY);
        assertEquals(64, loadout.capacity(type, 0));
        assertEquals(8, loadout.capacity(type, 1));
        assertEquals(1, type.stationOf(DroneType.WeaponType.ROCKET_POD));
        assertEquals(-1, type.stationOf(DroneType.WeaponType.GUN_MOUNT));
        assertTrue(type.getControlScheme().isFlyable());
    }

    @Test
    void secondaryWeaponNeedsACapacityAndMustDiffer() {
        assertThrows(IllegalArgumentException.class,
                () -> DroneType.parse("gunship", new StringReader(gunship(", \"secondaryWeapon\": \"ROCKET_POD\""))));
        assertThrows(IllegalArgumentException.class,
                () -> DroneType.parse("gunship", new StringReader(gunship(", \"secondaryWeapon\": \"CANNON\", \"secondaryCapacity\": 8"))));
    }

    @Test
    void clampTrimsToWhatFits() {
        Loadout loadout = new Loadout();
        loadout.setRocketKind(RocketType.HEAVY);
        loadout.setAmmo(0, 30);
        loadout.setAmmo(1, 5);
        loadout.clampTo(STRIKE_PLANE);
        assertEquals(16, loadout.getAmmo(0));
        assertEquals(0, loadout.getAmmo(1));
    }
}
