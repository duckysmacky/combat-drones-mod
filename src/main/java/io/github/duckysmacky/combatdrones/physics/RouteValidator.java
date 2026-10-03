package io.github.duckysmacky.combatdrones.physics;

import java.util.EnumSet;
import java.util.Set;

/** Server-side checks for a submitted route. Returns a translation key describing the problem, or null if valid. */
public final class RouteValidator {
    /** Lowest waypoint altitude above the launch point; below this a plane would scrape the ramp's surroundings. */
    public static final double MIN_ALTITUDE = 5;

    public static final class Limits {
        final int maxWaypoints;
        final double maxAltitude;
        final double maxLength;

        public Limits(int maxWaypoints, double maxAltitude, double maxLength) {
            this.maxWaypoints = maxWaypoints;
            this.maxAltitude = maxAltitude;
            this.maxLength = maxLength;
        }
    }

    private RouteValidator() {
    }

    /** Terminal actions a unit may be given: warheads strike, armed planes come home, recon patrols. */
    public static Set<Route.TerminalAction> allowedTerminals(boolean armed, boolean hasWeapon) {
        if (!armed) {
            return EnumSet.of(Route.TerminalAction.LOOP, Route.TerminalAction.RETURN);
        }
        if (hasWeapon) {
            return EnumSet.of(Route.TerminalAction.LOITER, Route.TerminalAction.RETURN);
        }
        return EnumSet.of(Route.TerminalAction.LOITER, Route.TerminalAction.DIVE, Route.TerminalAction.DESTROY);
    }

    public static String validate(Route route, double startX, double startZ, Limits limits, Set<Route.TerminalAction> allowedTerminals) {
        if (route.size() == 0) {
            return "message.combat_drones.route.empty";
        }
        if (route.size() > limits.maxWaypoints) {
            return "message.combat_drones.route.too_many_waypoints";
        }
        for (Route.Waypoint waypoint : route.getWaypoints()) {
            if (!Double.isFinite(waypoint.x) || !Double.isFinite(waypoint.z) || !Double.isFinite(waypoint.altitude)) {
                return "message.combat_drones.route.invalid";
            }
            if (waypoint.altitude < MIN_ALTITUDE || waypoint.altitude > limits.maxAltitude) {
                return "message.combat_drones.route.altitude";
            }
        }
        if (route.length(startX, startZ) > limits.maxLength) {
            return "message.combat_drones.route.too_long";
        }
        if (!(route.getLoiterRadius() >= Route.MIN_LOITER_RADIUS && route.getLoiterRadius() <= Route.MAX_LOITER_RADIUS)) {
            return "message.combat_drones.route.loiter_radius";
        }
        if (route.getTerminal() == null || !allowedTerminals.contains(route.getTerminal())) {
            return "message.combat_drones.route.bad_terminal";
        }
        Route.Target target = route.getTarget();
        if (target != null && !(Double.isFinite(target.x) && Double.isFinite(target.y) && Double.isFinite(target.z))) {
            return "message.combat_drones.route.invalid";
        }
        if (route.getTerminal() == Route.TerminalAction.DESTROY) {
            if (target == null) {
                return "message.combat_drones.route.no_target";
            }
            if (Math.hypot(target.x - startX, target.z - startZ) > limits.maxLength) {
                return "message.combat_drones.route.target_too_far";
            }
        }
        return null;
    }
}
