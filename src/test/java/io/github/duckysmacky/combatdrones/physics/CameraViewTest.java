package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraViewTest {
    @Test
    void levelCameraSeesAheadNotBehind() {
        // Yaw 0 looks toward +Z
        assertTrue(CameraView.contains(0, 0, 0, 0, 10, 30, 20));
        assertFalse(CameraView.contains(0, 0, 0, 0, -10, 30, 20));
        assertTrue(CameraView.contains(0, 0, 5, 0, 10, 30, 20), "26.6 degrees to the side fits a 30 degree half-width");
        assertFalse(CameraView.contains(0, 0, 0, 5, 10, 30, 20), "26.6 degrees up doesn't fit a 20 degree half-height");
    }

    @Test
    void cameraLookingStraightDown() {
        // Pitch 90 looks down; yaw 0 puts +Z at the top of the screen
        assertTrue(CameraView.contains(0, 90, 0, -50, 0, 30, 20));
        assertTrue(CameraView.contains(0, 90, 25, -50, 0, 30, 20), "26.6 degrees across");
        assertFalse(CameraView.contains(0, 90, 0, -50, 25, 30, 20), "26.6 degrees toward the top edge");
        assertFalse(CameraView.contains(0, 90, 0, 50, 0, 30, 20));
    }
}
