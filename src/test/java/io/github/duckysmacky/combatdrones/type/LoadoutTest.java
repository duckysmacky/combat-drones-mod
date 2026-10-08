package io.github.duckysmacky.combatdrones.type;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
