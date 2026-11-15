package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldContactTest {
    @Test
    void boxInsideOrAroundTheCentreTouches() {
        assertTrue(FieldContact.touches(0, 0, 0, 1, -0.1, -0.1, -0.1, 0.1, 0.1, 0.1));
        assertTrue(FieldContact.touches(0, 0, 0, 1, -5, -5, -5, 5, 5, 5));
    }

    @Test
    void edgeContactCountsByTheNearestPoint() {
        // Wing poking in: box centre 8.4 away, near face at 7.9 -> inside a radius 8
        assertTrue(FieldContact.touches(0, 0, 0, 8, 7.9, -0.25, -0.5, 8.9, 0.25, 0.5));
        assertFalse(FieldContact.touches(0, 0, 0, 8, 8.1, -0.25, -0.5, 9.1, 0.25, 0.5));
        // Off a corner: nearest point (6, 6, 0) is 8.49 away
        assertFalse(FieldContact.touches(0, 0, 0, 8, 6, 6, -1, 7, 7, 1));
        assertTrue(FieldContact.touches(0, 0, 0, 8.5, 6, 6, -1, 7, 7, 1));
    }
}
