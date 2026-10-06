package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import javax.vecmath.Vector3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CruiseMissileFlightTest {
    /** The spec defaults: 3 / 2 / 5 / 10 s, climbing to 0.15, 0.4 and 1.6 blocks/tick. */
    private static final CruiseMissileFlight.Launch LAUNCH = new CruiseMissileFlight.Launch(60, 40, 100, 200, 0.15, 0.4, 1.6);
    private static final CruiseMissileFlight.DiveParams DIVE = new CruiseMissileFlight.DiveParams(0.8, 0.05, 2, 30, 1.5);

    @Test
    void stagesFollowTheSchedule() {
        assertEquals(CruiseMissileFlight.Stage.IGNITION, LAUNCH.stageAt(0));
        assertEquals(CruiseMissileFlight.Stage.IGNITION, LAUNCH.stageAt(59));
        assertEquals(CruiseMissileFlight.Stage.LIFTOFF, LAUNCH.stageAt(60));
        assertEquals(CruiseMissileFlight.Stage.SLOW_CLIMB, LAUNCH.stageAt(100));
        assertEquals(CruiseMissileFlight.Stage.FAST_CLIMB, LAUNCH.stageAt(200));
        assertEquals(CruiseMissileFlight.Stage.DONE, LAUNCH.stageAt(400));
        assertEquals(40, LAUNCH.ticksLeftInStage(60));
        assertEquals(1, LAUNCH.ticksLeftInStage(399));
    }

    @Test
    void sitsOnThePadThenClimbsAboutTheSpecHeight() {
        assertEquals(0, LAUNCH.climbSpeed(30));
        assertTrue(LAUNCH.climbSpeed(150) > LAUNCH.climbSpeed(80));
        // ~3 (liftoff) + ~27 (slow climb) + ~200 (fast climb) blocks
        double height = LAUNCH.climbHeight();
        assertTrue(height > 200 && height < 260, "climb height " + height);
    }

    @Test
    void untouchedDiveGoesStraightDownAtTopSpeed() {
        CruiseMissileFlight.Dive dive = new CruiseMissileFlight.Dive();
        dive.speed = 0.8;
        CruiseMissileFlight.step(dive, 0, 0, false, DIVE);
        assertEquals(0, dive.velocity.x, 1e-9);
        assertEquals(-0.8, dive.velocity.y, 1e-9);
        assertEquals(0, dive.velocity.z, 1e-9);
    }

    @Test
    void steeringTiltsTowardHeadingAndRight() {
        // Reference heading 0 faces +Z; Minecraft's right of that is -X
        CruiseMissileFlight.Dive dive = new CruiseMissileFlight.Dive();
        dive.speed = 0.8;
        CruiseMissileFlight.step(dive, 1, 0, false, DIVE);
        assertTrue(dive.velocity.z > 0.01 && Math.abs(dive.velocity.x) < 1e-9, "forward " + dive.velocity);
        dive.tiltForward = 0;
        CruiseMissileFlight.step(dive, 0, 1, false, DIVE);
        assertTrue(dive.velocity.x < -0.01 && Math.abs(dive.velocity.z) < 1e-9, "right " + dive.velocity);
    }

    @Test
    void tiltStaysInsideTheCone() {
        CruiseMissileFlight.Dive dive = new CruiseMissileFlight.Dive();
        for (int tick = 0; tick < 200; tick++) {
            CruiseMissileFlight.step(dive, 1, 1, false, DIVE);
        }
        assertEquals(30, Math.hypot(dive.tiltForward, dive.tiltRight), 1e-6);
        Vector3d nose = CruiseMissileFlight.direction(dive);
        double offVertical = Math.toDegrees(Math.acos(-nose.y));
        assertEquals(30, offVertical, 0.5);
    }

    @Test
    void boostSpeedsUpTheDive() {
        CruiseMissileFlight.Dive dive = new CruiseMissileFlight.Dive();
        dive.speed = 0.8;
        for (int tick = 0; tick < 100; tick++) {
            CruiseMissileFlight.step(dive, 0, 0, true, DIVE);
        }
        assertEquals(1.6, dive.speed, 1e-9);
    }

    @Test
    void standbyFuelRunsDownAfterPreparing() {
        assertEquals(1, CruiseMissileFlight.standbyFuelFraction(50, 100, 700), 1e-9);
        assertEquals(0.5, CruiseMissileFlight.standbyFuelFraction(400, 100, 700), 1e-9);
        assertEquals(0, CruiseMissileFlight.standbyFuelFraction(900, 100, 700), 1e-9);
    }

    @Test
    void headingToMatchesMinecraftYaw() {
        assertEquals(0, CruiseMissileFlight.headingTo(0, 0, 0, 10), 1e-9);
        assertEquals(90, CruiseMissileFlight.headingTo(0, 0, -10, 0), 1e-9);
    }
}
