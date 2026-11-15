package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BombBallisticsTest {
    @Test
    void droppedOnTheGroundLandsWhereItIs() {
        BombBallistics.Fall fall = BombBallistics.fall(0.8, 0, 0);
        assertEquals(0, fall.forward);
        assertEquals(0, fall.ticks);
    }

    @Test
    void firstTicksFollowTheEntitySteps() {
        // Tick 1: falls 0.04, moves 0.5 forward. Tick 2: falls (0.04 * 0.98 + 0.04), moves 0.5 * 0.98.
        BombBallistics.Fall fall = BombBallistics.fall(0.5, 0, 0.1);
        assertEquals(2, fall.ticks);
        assertEquals(0.5 + 0.5 * 0.98, fall.forward, 1e-9);
    }

    @Test
    void higherAndFasterCarriesFurther() {
        BombBallistics.Fall low = BombBallistics.fall(0.5, 0, 40);
        BombBallistics.Fall high = BombBallistics.fall(0.5, 0, 80);
        BombBallistics.Fall fast = BombBallistics.fall(0.8, 0, 80);
        assertTrue(high.ticks > low.ticks && high.forward > low.forward);
        assertTrue(fast.forward > high.forward);
        assertEquals(high.ticks, fast.ticks, "forward speed doesn't change the fall time");
        // Drag caps the throw: it can never pass speed / (1 - drag)
        assertTrue(fast.forward < 0.8 / (1 - BombBallistics.DRAG));
    }

    @Test
    void theFuseEndsTheFall() {
        assertEquals(BombBallistics.FUSE_TICKS, BombBallistics.fall(0.5, 0, 10000).ticks);
    }
}
