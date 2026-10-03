package io.github.duckysmacky.combatdrones.physics;

import io.github.duckysmacky.combatdrones.physics.Route.TerminalAction;
import io.github.duckysmacky.combatdrones.physics.Route.Waypoint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RouteValidatorTest {
    private static final RouteValidator.Limits LIMITS = new RouteValidator.Limits(3, 100, 500);
    private static final Set<TerminalAction> MUNITION = RouteValidator.allowedTerminals(true, false);

    private static String validate(TerminalAction terminal, Waypoint... waypoints) {
        return RouteValidator.validate(new Route(Arrays.asList(waypoints), terminal), 0, 0, LIMITS, MUNITION);
    }

    @Test
    void acceptsAReasonableRoute() {
        assertNull(validate(TerminalAction.DIVE, new Waypoint(100, 0, 30), new Waypoint(100, 100, 40)));
    }

    @Test
    void rejectsEmptyAndOversizedRoutes() {
        assertEquals("message.combat_drones.route.empty", RouteValidator.validate(Route.empty(TerminalAction.LOITER), 0, 0, LIMITS, MUNITION));
        Waypoint point = new Waypoint(10, 10, 30);
        assertEquals("message.combat_drones.route.too_many_waypoints", validate(TerminalAction.LOITER, point, point, point, point));
    }

    @Test
    void rejectsBadAltitudeAndLength() {
        assertEquals("message.combat_drones.route.altitude", validate(TerminalAction.LOITER, new Waypoint(10, 10, 2)));
        assertEquals("message.combat_drones.route.altitude", validate(TerminalAction.LOITER, new Waypoint(10, 10, 150)));
        assertEquals("message.combat_drones.route.too_long", validate(TerminalAction.LOITER, new Waypoint(400, 0, 30), new Waypoint(0, 0, 30)));
        assertEquals("message.combat_drones.route.invalid", validate(TerminalAction.LOITER, new Waypoint(Double.NaN, 0, 30)));
    }

    @Test
    void terminalActionMustSuitTheUnit() {
        assertEquals("message.combat_drones.route.bad_terminal", validate(TerminalAction.LOOP, new Waypoint(10, 10, 30)));
        Set<TerminalAction> recon = RouteValidator.allowedTerminals(false, false);
        assertNull(RouteValidator.validate(new Route(Collections.singletonList(new Waypoint(10, 10, 30)), TerminalAction.LOOP), 0, 0, LIMITS, recon));
    }

    @Test
    void lengthIncludesTheLegFromLaunch() {
        Route route = new Route(Arrays.asList(new Waypoint(30, 40, 20), new Waypoint(30, 0, 20)), TerminalAction.LOITER);
        assertEquals(90, route.length(0, 0), 1e-9);
    }
}
