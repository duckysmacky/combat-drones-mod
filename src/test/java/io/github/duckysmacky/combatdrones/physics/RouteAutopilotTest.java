package io.github.duckysmacky.combatdrones.physics;

import io.github.duckysmacky.combatdrones.physics.Route.TerminalAction;
import io.github.duckysmacky.combatdrones.physics.Route.Waypoint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteAutopilotTest {
    private static final FixedWingFlight.Params PLANE = new FixedWingFlight.Params(1.0, 0.1);
    private static final double ORIGIN_Y = 64;

    private static FixedWingFlight.State launched() {
        FixedWingFlight.State state = new FixedWingFlight.State();
        state.position.set(0, ORIGIN_Y, 0);
        state.speed = 0.8;
        return state;
    }

    private static Route route(double x, double z, double altitude) {
        return new Route(Collections.singletonList(new Waypoint(x, z, altitude)), TerminalAction.LOITER);
    }

    /** Flies until the waypoint is passed or time runs out; returns the tick it happened, or -1. */
    private static int flyUntilReached(FixedWingFlight.State state, Route route, double minSafeY, int maxTicks) {
        int index = 0;
        for (int tick = 0; tick < maxTicks; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.guide(state, route, index, ORIGIN_Y, minSafeY, PLANE, true);
            index = command.waypointIndex;
            if (index > 0) {
                return tick;
            }
            FixedWingFlight.step(state, command.controls, PLANE);
        }
        return -1;
    }

    @Test
    void reachesAWaypointBehindItAtTheRightAltitude() {
        FixedWingFlight.State state = launched();
        // Behind and to the side of the launch heading, 30 blocks up: needs a turn and a climb
        int tick = flyUntilReached(state, route(150, -100, 30), 0, 1200);
        assertTrue(tick > 0, "should reach the waypoint");
        assertEquals(ORIGIN_Y + 30, state.position.y, 4);
    }

    @Test
    void loitersInACircleAroundTheLastWaypoint() {
        FixedWingFlight.State state = launched();
        Route route = route(0, 80, 20);
        int index = 0;
        for (int tick = 0; tick < 1500; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.guide(state, route, index, ORIGIN_Y, 0, PLANE, true);
            index = command.waypointIndex;
            FixedWingFlight.step(state, command.controls, PLANE);
            if (tick > 1200) {
                double distance = Math.hypot(state.position.x, state.position.z - 80);
                assertTrue(distance > 10 && distance < 45, "orbit drifted to " + distance);
            }
        }
        assertEquals(1, index, "should be orbiting after the only waypoint");
    }

    @Test
    void terrainFloorOverridesAWaypointThatIsTooLow() {
        FixedWingFlight.State state = launched();
        Route route = route(0, 2000, 10);
        int index = 0;
        for (int tick = 0; tick < 500; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.guide(state, route, index, ORIGIN_Y, 120, PLANE, true);
            index = command.waypointIndex;
            FixedWingFlight.step(state, command.controls, PLANE);
        }
        assertTrue(state.position.y > 115, "should climb over the terrain floor, got " + state.position.y);
    }

    @Test
    void slowerLegsFlySlower() {
        FixedWingFlight.State slow = launched();
        FixedWingFlight.State fast = launched();
        Route slowRoute = new Route(Collections.singletonList(new Waypoint(0, 3000, 0, Route.Speed.SLOW)), TerminalAction.LOITER);
        Route fastRoute = new Route(Collections.singletonList(new Waypoint(0, 3000, 0, Route.Speed.FAST)), TerminalAction.LOITER);
        for (int tick = 0; tick < 300; tick++) {
            FixedWingFlight.step(slow, RouteAutopilot.guide(slow, slowRoute, 0, ORIGIN_Y, 0, PLANE, true).controls, PLANE);
            FixedWingFlight.step(fast, RouteAutopilot.guide(fast, fastRoute, 0, ORIGIN_Y, 0, PLANE, true).controls, PLANE);
        }
        assertEquals(0.5, slow.speed, 0.05);
        assertEquals(0.8, fast.speed, 0.05);
    }

    @Test
    void loiterRadiusIsRespected() {
        FixedWingFlight.State state = launched();
        Route route = new Route(Collections.singletonList(new Waypoint(0, 80, 20, Route.Speed.MEDIUM)), TerminalAction.LOITER, 60);
        int index = 0;
        for (int tick = 0; tick < 2500; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.guide(state, route, index, ORIGIN_Y, 0, PLANE, true);
            index = command.waypointIndex;
            FixedWingFlight.step(state, command.controls, PLANE);
            if (tick > 2000) {
                double distance = Math.hypot(state.position.x, state.position.z - 80);
                assertTrue(distance > 45 && distance < 75, "wide orbit drifted to " + distance);
            }
        }
    }

    @Test
    void withoutFuelItGlidesDown() {
        FixedWingFlight.State state = launched();
        Route route = route(0, 500, 30);
        for (int tick = 0; tick < 200; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.guide(state, route, 0, ORIGIN_Y, 0, PLANE, false);
            assertTrue(!command.controls.engineOn);
            FixedWingFlight.step(state, command.controls, PLANE);
        }
        assertTrue(state.position.y < ORIGIN_Y - 10);
    }

    private static RouteAutopilot.Situation situation(double fuelFraction, boolean warhead) {
        return new RouteAutopilot.Situation(ORIGIN_Y, 0, true, fuelFraction, 0.15, warhead, ORIGIN_Y, 0, ORIGIN_Y, 0);
    }

    /** Flies until the plane reaches the ground plane at {@code groundY}; returns where it touched, or null. */
    private static FixedWingFlight.State flyToGround(FixedWingFlight.State state, Route route, RouteAutopilot.Situation situation,
                                                     double groundY, int maxTicks) {
        int index = 0;
        for (int tick = 0; tick < maxTicks; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.guide(state, route, index, situation, PLANE);
            index = command.waypointIndex;
            FixedWingFlight.step(state, command.controls, PLANE);
            if (state.position.y <= groundY) {
                return state;
            }
        }
        return null;
    }

    @Test
    void diveHitsTheGroundUnderTheLastWaypoint() {
        FixedWingFlight.State state = launched();
        Route route = new Route(Collections.singletonList(new Waypoint(0, 150, 40)), TerminalAction.DIVE);
        FixedWingFlight.State impact = flyToGround(state, route, situation(1, true), ORIGIN_Y, 2000);
        assertTrue(impact != null, "should dive into the ground");
        assertTrue(Math.hypot(impact.position.x, impact.position.z - 150) < 3, "should hit within 3 blocks of the point, hit " + impact.position);
    }

    @Test
    void destroyHitsTheMarkedTarget() {
        FixedWingFlight.State state = launched();
        Route route = new Route(Collections.singletonList(new Waypoint(0, 100, 40)), TerminalAction.DESTROY)
                .withTarget(new Route.Target(120, ORIGIN_Y - 5, 220));
        FixedWingFlight.State impact = flyToGround(state, route, situation(1, true), ORIGIN_Y - 5, 3000);
        assertTrue(impact != null, "should reach the target");
        assertTrue(Math.hypot(impact.position.x - 120, impact.position.z - 220) < 3, "should hit within 3 blocks of the target, hit " + impact.position);
    }

    @Test
    void lowFuelEndsTheLoiter() {
        Route route = route(0, 10, 30);
        FixedWingFlight.State munition = launched();
        munition.position.set(0, ORIGIN_Y + 30, 10);
        assertEquals(RouteAutopilot.Phase.STRIKE, RouteAutopilot.guide(munition, route, 1, situation(0.1, true), PLANE).phase);
        assertEquals(RouteAutopilot.Phase.LOITER, RouteAutopilot.guide(munition, route, 1, situation(0.5, true), PLANE).phase);
        FixedWingFlight.State gunship = launched();
        gunship.position.set(0, ORIGIN_Y + 30, 10);
        assertEquals(RouteAutopilot.Phase.LAND, RouteAutopilot.guide(gunship, route, 1, situation(0.1, false), PLANE).phase);
    }

    @Test
    void returnLandsSlowlyNearHome() {
        FixedWingFlight.State state = launched();
        Route route = new Route(Collections.singletonList(new Waypoint(300, 300, 40)), TerminalAction.RETURN);
        FixedWingFlight.State touchdown = flyToGround(state, route, situation(1, false), ORIGIN_Y, 4000);
        assertTrue(touchdown != null, "should come down");
        assertTrue(Math.hypot(touchdown.position.x, touchdown.position.z) < 40, "should land near home");
        assertTrue(touchdown.speed < 0.75 * PLANE.maxSpeed, "should touch down slowly, was " + touchdown.speed);
    }

    @Test
    void fastMunitionComesRoundWideEnoughToDive() {
        // Turn circle wider than the dive cone: it has to extend before turning back, or it circles forever
        FixedWingFlight.Params fast = new FixedWingFlight.Params(1.5, 0.15);
        FixedWingFlight.State state = launched();
        state.position.set(80, ORIGIN_Y + 30, 0);
        state.heading = 90;
        state.speed = 1.2;
        Route route = new Route(Collections.singletonList(new Waypoint(0, 0, 30)), TerminalAction.DESTROY)
                .withTarget(new Route.Target(-20, ORIGIN_Y - 1, -60));
        int index = 0;
        for (int tick = 0; tick < 3000 && state.position.y > ORIGIN_Y - 1; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.guide(state, route, index, situation(1, true), fast);
            index = command.waypointIndex;
            FixedWingFlight.step(state, command.controls, fast);
        }
        assertTrue(state.position.y <= ORIGIN_Y - 1, "should dive into the target");
        assertTrue(Math.hypot(state.position.x + 20, state.position.z + 60) < 3, "should hit within 3 blocks, hit " + state.position);
    }

    @Test
    void loopFliesTheRouteAgain() {
        FixedWingFlight.State state = launched();
        Route route = new Route(Arrays.asList(new Waypoint(0, 150, 30), new Waypoint(150, 150, 30)), TerminalAction.LOOP);
        int index = 0;
        boolean wrapped = false;
        for (int tick = 0; tick < 1500 && !wrapped; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.guide(state, route, index, situation(1, false), PLANE);
            wrapped = index == 1 && command.waypointIndex == 0;
            index = command.waypointIndex;
            assertEquals(RouteAutopilot.Phase.ROUTE, command.phase);
            FixedWingFlight.step(state, command.controls, PLANE);
        }
        assertTrue(wrapped, "should head back to the first waypoint after the last");
    }

    @Test
    void lowFuelLoopHeadsHome() {
        FixedWingFlight.State state = launched();
        state.position.set(150, ORIGIN_Y + 30, 150);
        Route route = new Route(Arrays.asList(new Waypoint(0, 150, 30), new Waypoint(150, 150, 30)), TerminalAction.LOOP);
        RouteAutopilot.Phase phase = RouteAutopilot.guide(state, route, 1, situation(0.1, false), PLANE).phase;
        assertEquals(RouteAutopilot.Phase.RETURN, phase);
    }

    @Test
    void singleWaypointLoopCirclesAtTheLoiterRadius() {
        FixedWingFlight.State state = launched();
        Route route = new Route(Collections.singletonList(new Waypoint(0, 80, 20)), TerminalAction.LOOP, 50);
        int index = 0;
        double nearest = Double.MAX_VALUE;
        double farthest = 0;
        for (int tick = 0; tick < 2000; tick++) {
            RouteAutopilot.Command command = RouteAutopilot.guide(state, route, index, situation(1, false), PLANE);
            index = command.waypointIndex;
            FixedWingFlight.step(state, command.controls, PLANE);
            if (tick > 1400) {
                double distance = Math.hypot(state.position.x, state.position.z - 80);
                nearest = Math.min(nearest, distance);
                farthest = Math.max(farthest, distance);
            }
        }
        assertTrue(nearest > 38 && farthest < 62, "should circle at about 50 blocks, was " + nearest + ".." + farthest);
    }
}
