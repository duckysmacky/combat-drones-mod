package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockChargesTest {
    @Test
    void spendsAndReloadsOneAtATime() {
        LockCharges charges = new LockCharges(3);
        assertTrue(charges.spend());
        assertTrue(charges.spend());
        assertEquals(1, charges.getCharges());
        for (int tick = 0; tick < 10; tick++) {
            charges.tick(3, 10, false);
        }
        assertEquals(2, charges.getCharges(), "one charge per reload, not all spent ones at once");
        for (int tick = 0; tick < 10; tick++) {
            charges.tick(3, 10, false);
        }
        assertEquals(3, charges.getCharges());
        charges.tick(3, 10, false);
        assertEquals(3, charges.getCharges(), "never above the maximum");
    }

    @Test
    void emptyCantSpendAndPausedDoesntReload() {
        LockCharges charges = new LockCharges(1);
        assertTrue(charges.spend());
        assertFalse(charges.spend());
        for (int tick = 0; tick < 20; tick++) {
            charges.tick(1, 10, true);
        }
        assertEquals(0, charges.getCharges());
        assertEquals(0, charges.getReload());
    }

    @Test
    void clampsToALoweredMaximum() {
        LockCharges charges = new LockCharges(3);
        charges.tick(1, 10, false);
        assertEquals(1, charges.getCharges());
    }
}
