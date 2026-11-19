package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunwayOpsTest {
    /** Light Runway running east (yaw -90 = +X) from x = 100, deck at y 64. */
    static final Route.Runway LIGHT = new Route.Runway(new UUID(1, 2), 100, 64, 50, -90, 6, 2);
    /** Heavy Runway running north (yaw 180 = -Z). */
    static final Route.Runway HEAVY = new Route.Runway(new UUID(3, 4), -20, 70, 300, 180, 10, 3);

    /** Strike Plane, Attack Plane, Gunship, Stealth Bomber, Recon UAV top speeds. */
    static final double[] SPEEDS = {0.9, 1.4, 0.7, 0.72, 0.8};

    @Test
    void runwayCoordinates() {
        assertEquals(3, LIGHT.along(103, 50), 1e-9);
        assertEquals(103, LIGHT.pointX(3, 0), 1e-9);
        assertEquals(1, Math.abs(LIGHT.cross(103, 51)), 1e-9);
        assertEquals(LIGHT.cross(103, 51), LIGHT.cross(LIGHT.pointX(3, LIGHT.cross(103, 51)), LIGHT.pointZ(3, LIGHT.cross(103, 51))), 1e-9);
        assertEquals(4, HEAVY.along(-20, 296), 1e-9);
    }

    @Test
    void spoolsUpThenLiftsOffBeforeTheFarEnd() {
        for (Route.Runway runway : new Route.Runway[]{LIGHT, HEAVY}) {
            for (double maxSpeed : SPEEDS) {
                FixedWingFlight.State state = new FixedWingFlight.State();
                double seat = RunwayOps.seat(runway);
                state.position.set(runway.pointX(seat, 0), runway.y, runway.pointZ(seat, 0));
                int ticks = 0;
                while (!RunwayOps.takeoffRoll(state, runway, maxSpeed, ticks)) {
                    if (ticks < RunwayOps.SPOOL_TICKS) {
                        assertEquals(seat, runway.along(state.position.x, state.position.z), 1e-9, "stands still while spooling");
                    }
                    assertEquals(runway.y, state.position.y, 1e-9);
                    assertEquals(0, runway.cross(state.position.x, state.position.z), 1e-9);
                    ticks++;
                    assertTrue(ticks < 400, "never lifts off");
                }
                double along = runway.along(state.position.x, state.position.z);
                assertTrue(along <= runway.length, "rolled off the end: " + along + " at top speed " + maxSpeed);
                assertTrue(along > runway.length - 2.5, "lifts off well short: " + along);
                assertTrue(state.speed >= maxSpeed * RunwayOps.ROTATION_FRACTION - 1e-9);
                assertTrue(state.pitch < 0, "nose up at lift-off");
                assertEquals(runway.heading, state.heading, 1e-9);
            }
        }
    }

    /** Lifted off, the flight model climbs on without stalling at full power. */
    @Test
    void climbsAwayAfterLiftOff() {
        FixedWingFlight.Params params = new FixedWingFlight.Params(0.7, 0.035, 0.6);
        FixedWingFlight.State state = new FixedWingFlight.State();
        double seat = RunwayOps.seat(HEAVY);
        state.position.set(HEAVY.pointX(seat, 0), HEAVY.y, HEAVY.pointZ(seat, 0));
        int ticks = 0;
        while (!RunwayOps.takeoffRoll(state, HEAVY, params.maxSpeed(), ticks++)) {
            assertTrue(ticks < 400);
        }
        for (int tick = 0; tick < 100; tick++) {
            FixedWingFlight.step(state, RouteAutopilot.holdCourse(state, HEAVY.heading, HEAVY.y + RunwayOps.CLIMB_OUT_HEIGHT + 2,
                    Route.Speed.FAST.fraction, params, true), params);
            assertTrue(state.position.y >= HEAVY.y - 1e-9, "sank into the ground");
        }
        assertTrue(state.position.y > HEAVY.y + RunwayOps.CLIMB_OUT_HEIGHT - 1, "didn't climb out: " + state.position.y);
    }
}
