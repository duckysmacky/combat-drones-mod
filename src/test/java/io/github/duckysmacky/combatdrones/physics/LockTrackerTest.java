package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockTrackerTest {
    @Test
    void locksAfterHoldingOnOneTargetAndOnlyOnce() {
        LockTracker tracker = new LockTracker(3);
        assertFalse(tracker.update(7));
        assertFalse(tracker.update(7));
        assertTrue(tracker.update(7));
        assertEquals(1.0, tracker.getProgress(), 1e-9);
        assertFalse(tracker.update(7), "holding on after the lock doesn't lock again");
    }

    @Test
    void switchingOrLosingTheTargetStartsOver() {
        LockTracker tracker = new LockTracker(3);
        tracker.update(7);
        tracker.update(7);
        assertFalse(tracker.update(8));
        assertEquals(1.0 / 3, tracker.getProgress(), 1e-9);
        tracker.update(LockTracker.NONE);
        assertEquals(0, tracker.getProgress(), 1e-9);
        tracker.update(8);
        tracker.update(8);
        assertTrue(tracker.update(8));
    }

    @Test
    void angleBetweenDirections() {
        assertEquals(90, LockTracker.angleBetween(1, 0, 0, 0, 0, 1), 1e-9);
        assertEquals(0, LockTracker.angleBetween(0, -1, 0, 0, -5, 0), 1e-9);
        assertEquals(180, LockTracker.angleBetween(1, 0, 0, -1, 0, 0), 1e-9);
    }
}
