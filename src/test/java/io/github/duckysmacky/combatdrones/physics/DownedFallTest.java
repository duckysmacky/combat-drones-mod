package io.github.duckysmacky.combatdrones.physics;

import io.github.duckysmacky.combatdrones.physics.DownedFall.Attitude;
import io.github.duckysmacky.combatdrones.physics.DownedFall.Params;
import org.junit.jupiter.api.Test;

import javax.vecmath.Vector3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownedFallTest {
    private static final Params SHOT_DOWN = Params.perSecond(0.35, 8, 30, 240, 25);
    private static final Params EMPED = Params.perSecond(0.25, 6, 20, 0, 0);

    @Test
    void fallsInAnArcNotStraightDown() {
        Vector3d velocity = new Vector3d(0, 0, 1.0); // level, heading 0 at 1 block/tick
        Attitude first = DownedFall.step(velocity, 0, 0, SHOT_DOWN);
        double horizontal = Math.hypot(velocity.x, velocity.z);
        assertTrue(first.pitch > 0 && first.pitch < 10, "starts almost level");
        Attitude later = first;
        for (int tick = 0; tick < 40; tick++) {
            later = DownedFall.step(velocity, later.heading, later.bank, SHOT_DOWN);
        }
        assertTrue(Math.hypot(velocity.x, velocity.z) < horizontal, "loses forward speed");
        assertTrue(Math.hypot(velocity.x, velocity.z) > 0.2, "still moving forward after 2 s");
        assertTrue(later.pitch > first.pitch + 20, "nose drops into the fall");
    }

    @Test
    void fallSpeedIsCapped() {
        Vector3d velocity = new Vector3d(0, 0, 0.5);
        for (int tick = 0; tick < 400; tick++) {
            DownedFall.step(velocity, 0, 0, SHOT_DOWN);
        }
        assertEquals(-30 / 20.0, velocity.y, 1e-9);
    }

    @Test
    void headingFollowsTheSpiralingPath() {
        Vector3d velocity = new Vector3d(0, 0, 1.0);
        Attitude attitude = DownedFall.step(velocity, 0, 0, SHOT_DOWN);
        assertEquals(25 / 20.0, attitude.heading, 1e-6);
        // The heading still points along the travel over the ground
        double radians = Math.toRadians(attitude.heading);
        double horizontal = Math.hypot(velocity.x, velocity.z);
        assertEquals(-Math.sin(radians) * horizontal, velocity.x, 1e-9);
        assertEquals(Math.cos(radians) * horizontal, velocity.z, 1e-9);
    }

    @Test
    void shotDownRollsAndEmpedLevelsOut() {
        Attitude rolling = DownedFall.step(new Vector3d(0, 0, 1), 0, 170, SHOT_DOWN);
        assertEquals(-178, rolling.bank, 1e-9); // 170 + 12, wrapped
        Attitude emped = DownedFall.step(new Vector3d(0, 0, 1), 0, 30, EMPED);
        assertEquals(27, emped.bank, 1e-9);
        assertEquals(0, emped.heading, 1e-9);
    }

    @Test
    void dragIsPerSecond() {
        Vector3d velocity = new Vector3d(0, 0, 1.0);
        for (int tick = 0; tick < 20; tick++) {
            DownedFall.step(velocity, 0, 0, EMPED);
        }
        assertEquals(0.75, Math.hypot(velocity.x, velocity.z), 1e-9);
    }

    @Test
    void straightDownKeepsHeading() {
        Attitude attitude = DownedFall.step(new Vector3d(0, -1, 0), 42, 0, EMPED);
        assertEquals(42, attitude.heading, 1e-9);
        assertEquals(90, attitude.pitch, 1e-9);
    }
}
