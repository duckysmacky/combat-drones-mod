package io.github.duckysmacky.combatdrones.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LineClipTest {
    @Test
    void insideLineIsUnchanged() {
        assertArrayEquals(new double[] {1, 1, 5, 5}, LineClip.clip(1, 1, 5, 5, 0, 0, 10, 10), 1e-9);
    }

    @Test
    void lineLeavingTheBoxIsCutAtTheEdge() {
        assertArrayEquals(new double[] {5, 5, 10, 5}, LineClip.clip(5, 5, 20, 5, 0, 0, 10, 10), 1e-9);
        assertArrayEquals(new double[] {0, 0, 10, 10}, LineClip.clip(-5, -5, 15, 15, 0, 0, 10, 10), 1e-9);
    }

    @Test
    void lineOutsideIsDropped() {
        assertNull(LineClip.clip(-5, -5, -1, 20, 0, 0, 10, 10));
        assertNull(LineClip.clip(11, 0, 11, 10, 0, 0, 10, 10));
    }
}
