package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RechargeTest {
    @Test
    void fullStaysFull() {
        assertEquals(1, Recharge.available(1, 0, 5000, 1, 600));
        assertEquals(0, Recharge.ticksToNext(1, 0, 5000, 1, 600));
    }

    @Test
    void spendingFromFullStartsTheReloadNow() {
        assertArrayEquals(new long[] {0, 1000}, Recharge.spend(1, 0, 1000, 1, 600));
        assertEquals(0, Recharge.available(0, 1000, 1599, 1, 600));
        assertEquals(1, Recharge.ticksToNext(0, 1000, 1599, 1, 600));
        assertEquals(1, Recharge.available(0, 1000, 1600, 1, 600));
    }

    @Test
    void chargesComeBackOneAtATimeUpToMax() {
        assertEquals(1, Recharge.available(0, 0, 150, 3, 100));
        assertEquals(50, Recharge.ticksToNext(0, 0, 150, 3, 100));
        assertEquals(3, Recharge.available(0, 0, 10_000, 3, 100));
    }

    @Test
    void spendingMidReloadKeepsItsProgress() {
        // 2 of 3 since tick 0; at 150 one came back (3 = full) -> spend from full restarts
        assertArrayEquals(new long[] {2, 150}, Recharge.spend(2, 0, 150, 3, 100));
        // 1 of 3 since 0; at 150: 2 ready, 50 ticks into the next -> 1 left, reload counted from 100
        assertArrayEquals(new long[] {1, 100}, Recharge.spend(1, 0, 150, 3, 100));
        assertEquals(50, Recharge.ticksToNext(1, 100, 150, 3, 100));
    }

    @Test
    void lowerMaxClampsAndAFutureStartGainsNothing() {
        assertEquals(1, Recharge.available(3, 0, 0, 1, 100));
        assertEquals(0, Recharge.available(0, 9000, 100, 1, 100));
    }
}
