package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DroneSystemsTest {
    @Test
    void signalIsPerfectUntilFalloffThenLinear() {
        assertEquals(1, DroneSystems.signalQuality(0, 100), 1e-9);
        assertEquals(1, DroneSystems.signalQuality(60, 100), 1e-9);
        assertEquals(0.5, DroneSystems.signalQuality(80, 100), 1e-9);
        assertEquals(0, DroneSystems.signalQuality(100, 100), 1e-9);
        assertEquals(0, DroneSystems.signalQuality(150, 100), 1e-9);
    }

    @Test
    void drainHasAnIdleFloor() {
        assertEquals(0.2, DroneSystems.drainPerTick(1, 0), 1e-9);
        assertEquals(0.5, DroneSystems.drainPerTick(1, 0.5), 1e-9);
        assertEquals(2, DroneSystems.drainPerTick(2, 1), 1e-9);
    }
}
