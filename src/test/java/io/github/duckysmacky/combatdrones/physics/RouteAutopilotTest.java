package io.github.duckysmacky.combatdrones.physics;

import io.github.duckysmacky.combatdrones.physics.Route.TerminalAction;
import io.github.duckysmacky.combatdrones.physics.Route.Waypoint;
import org.junit.jupiter.api.Test;

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
}
