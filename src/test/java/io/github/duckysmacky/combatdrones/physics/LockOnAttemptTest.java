package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LockOnAttemptTest {
    @Test
    void locksAfterHoldingOnTheTarget() {
        LockOnAttempt attempt = new LockOnAttempt(3, 2);
        assertEquals(LockOnAttempt.Result.IDLE, attempt.tick(true, true));
        attempt.start(7);
        assertEquals(LockOnAttempt.Result.RUNNING, attempt.tick(true, true));
        assertEquals(LockOnAttempt.Result.RUNNING, attempt.tick(true, true));
        assertEquals(2.0 / 3, attempt.getProgress(), 1e-9);
        assertEquals(LockOnAttempt.Result.LOCKED, attempt.tick(true, true));
        assertFalse(attempt.isRunning());
        assertEquals(LockOnAttempt.Result.IDLE, attempt.tick(true, true), "a lock reports once");
    }

    @Test
    void releasingTheTriggerFailsAtOnce() {
        LockOnAttempt attempt = new LockOnAttempt(3, 2);
        attempt.start(7);
        attempt.tick(true, true);
        assertEquals(LockOnAttempt.Result.FAILED, attempt.tick(true, false));
        assertEquals(0, attempt.getProgress(), 1e-9);
    }

    @Test
    void briefMissesAreForgivenLongOnesFail() {
        LockOnAttempt attempt = new LockOnAttempt(3, 2);
        attempt.start(7);
        attempt.tick(true, true);
        assertEquals(LockOnAttempt.Result.RUNNING, attempt.tick(false, true));
        assertEquals(LockOnAttempt.Result.RUNNING, attempt.tick(false, true));
        assertEquals(1.0 / 3, attempt.getProgress(), 1e-9, "missed ticks don't count toward the lock");
        attempt.tick(true, true);
        attempt.tick(false, true);
        attempt.tick(false, true);
        assertEquals(LockOnAttempt.Result.FAILED, attempt.tick(false, true));
    }

    @Test
    void angleBetweenDirections() {
        assertEquals(90, LockOnAttempt.angleBetween(1, 0, 0, 0, 0, 1), 1e-9);
        assertEquals(0, LockOnAttempt.angleBetween(0, -1, 0, 0, -5, 0), 1e-9);
        assertEquals(180, LockOnAttempt.angleBetween(1, 0, 0, -1, 0, 0), 1e-9);
    }
}
