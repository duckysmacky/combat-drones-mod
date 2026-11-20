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

    /** Config defaults (runways.spoolUpSeconds 3, liftoffSpeed 0.5, climbOutHeight 8). */
    static final int SPOOL = 60;
    static final double LIFTOFF = 0.5;
    static final double CLIMB_OUT = 8;

    @Test
    void spoolsUpThenLiftsOffBeforeTheFarEnd() {
        for (double liftoff : new double[]{0.45, LIFTOFF, 1.0}) {
        for (Route.Runway runway : new Route.Runway[]{LIGHT, HEAVY}) {
            for (double maxSpeed : SPEEDS) {
                FixedWingFlight.State state = new FixedWingFlight.State();
                double seat = RunwayOps.seat(runway);
                state.position.set(runway.pointX(seat, 0), runway.y, runway.pointZ(seat, 0));
                int ticks = 0;
                while (!RunwayOps.takeoffRoll(state, runway, maxSpeed, ticks, SPOOL, liftoff)) {
                    if (ticks < SPOOL) {
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
                assertTrue(state.speed >= maxSpeed * liftoff - 1e-9);
                assertTrue(state.pitch < 0, "nose up at lift-off");
                assertEquals(runway.heading, state.heading, 1e-9);
                assertTrue(ticks >= SPOOL, "lifted off while still spooling up");
            }
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
        while (!RunwayOps.takeoffRoll(state, HEAVY, params.maxSpeed(), ticks++, SPOOL, LIFTOFF)) {
            assertTrue(ticks < 400);
        }
        for (int tick = 0; tick < 100; tick++) {
            FixedWingFlight.step(state, RouteAutopilot.holdCourse(state, HEAVY.heading, HEAVY.y + CLIMB_OUT + 2,
                    Route.Speed.FAST.fraction, params, true), params);
            assertTrue(state.position.y >= HEAVY.y - 1e-9, "sank into the ground");
        }
        assertTrue(state.position.y > HEAVY.y + CLIMB_OUT - 1, "didn't climb out: " + state.position.y);
    }

    /** Strike Plane, Attack Plane, Gunship, Stealth Bomber, Recon UAV. */
    static final FixedWingFlight.Params[] PLANES = {
            new FixedWingFlight.Params(0.9, 0.05, 1.15, 1.25), new FixedWingFlight.Params(1.4, 0.09, 1.8),
            new FixedWingFlight.Params(0.7, 0.035, 0.6), new FixedWingFlight.Params(0.72, 0.036, 0.5), new FixedWingFlight.Params(0.8, 0.05, 1.2)};

    private static RouteAutopilot.Situation situation(Route.Runway runway, boolean free) {
        return new RouteAutopilot.Situation(runway.y, 0, true, 1, 0.15, false, Double.NaN, runway, free);
    }

    /**
     * Flies "Return to runway" from {@code (x, y, z)} on {@code heading} over flat ground at deck level; returns the
     * along-track spot it stopped on, or fails if it hits the ground first or never gets down.
     */
    private static double landFrom(Route.Runway runway, FixedWingFlight.Params params, double x, double y, double z, double heading) {
        FixedWingFlight.State state = new FixedWingFlight.State();
        state.position.set(x, y, z);
        state.heading = heading;
        state.speed = params.maxSpeed() * 0.65;
        for (int tick = 0; tick < 6000; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.returnToRunway(state, situation(runway, true), params, 0);
            FixedWingFlight.step(state, command.controls, params);
            if (command.phase == RouteAutopilot.Phase.LAND && RunwayOps.captured(state, runway, params.maxSpeed())) {
                for (int roll = 0; roll < 200; roll++) {
                    if (RunwayOps.rollout(state, runway)) {
                        assertEquals(runway.y, state.position.y, 1e-6);
                        assertTrue(Math.abs(runway.cross(state.position.x, state.position.z)) < runway.width / 2, "stopped off the deck");
                        return runway.along(state.position.x, state.position.z);
                    }
                }
                throw new AssertionError("never stopped");
            }
            assertTrue(state.position.y > runway.y, "hit the ground at " + state.position + " tick " + tick);
        }
        throw new AssertionError("never landed, ended at " + state.position);
    }

    @Test
    void landsOnTheRunwayFromAnyDirection() {
        double[][] starts = {{0, -300}, {0, 300}, {300, 0}, {-300, 0}, {200, 200}, {-150, 40}, {20, 10}};
        for (Route.Runway runway : new Route.Runway[]{LIGHT, HEAVY}) {
            for (FixedWingFlight.Params params : PLANES) {
                for (double[] start : starts) {
                    for (double heading : new double[]{0, 90, 180, -90}) {
                        double stop = landFrom(runway, params, runway.x + start[0], runway.y + 30, runway.z + start[1], heading);
                        assertTrue(stop > 0 && stop <= runway.length - RunwayOps.ROLL_MARGIN + 1e-6,
                                "stopped at " + stop + " on a " + runway.length + "-block runway");
                    }
                }
            }
        }
    }

    @Test
    void circlesARunwayItCantUseYet() {
        FixedWingFlight.Params params = PLANES[0];
        FixedWingFlight.State state = new FixedWingFlight.State();
        state.position.set(LIGHT.x - 200, LIGHT.y + 30, LIGHT.z);
        state.speed = 0.6;
        double closest = Double.MAX_VALUE;
        for (int tick = 0; tick < 3000; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.returnToRunway(state, situation(LIGHT, false), params, 0);
            assertEquals(RouteAutopilot.Phase.RETURN, command.phase);
            FixedWingFlight.step(state, command.controls, params);
            if (tick > 1500) {
                closest = Math.min(closest, Math.hypot(state.position.x - LIGHT.x, state.position.z - LIGHT.z));
                assertTrue(state.position.y > LIGHT.y + 20, "should hold its height over the runway");
            }
        }
        assertTrue(closest > 20 && closest < 60, "should circle round it, closest " + closest);
    }

    @Test
    void notCapturedWhenFastHighOrCrossing() {
        FixedWingFlight.State state = new FixedWingFlight.State();
        state.position.set(LIGHT.pointX(1, 0), LIGHT.y + 1, LIGHT.pointZ(1, 0));
        state.heading = LIGHT.heading;
        state.speed = 0.4;
        assertTrue(RunwayOps.captured(state, LIGHT, 0.9));
        state.speed = 0.8;
        assertTrue(!RunwayOps.captured(state, LIGHT, 0.9), "too fast");
        state.speed = 0.4;
        state.position.y = LIGHT.y + 4;
        assertTrue(!RunwayOps.captured(state, LIGHT, 0.9), "too high");
        state.position.y = LIGHT.y + 1;
        state.heading = LIGHT.heading + 90;
        assertTrue(!RunwayOps.captured(state, LIGHT, 0.9), "crossing it");
    }
}
