package io.github.duckysmacky.combatdrones.physics;

import java.util.EnumSet;
import java.util.Set;

/** Server-side checks for a submitted route. Returns a translation key describing the problem, or null if valid. */
public final class RouteValidator {
    public static final class Limits {
        final int maxWaypoints;
        /** Waypoint altitude bounds, blocks above the launch point. */
        final double minAltitude;
        final double maxAltitude;
        final double maxLength;
        final double minLoiterRadius;
        final double maxLoiterRadius;

        public Limits(int maxWaypoints, double minAltitude, double maxAltitude, double maxLength, double minLoiterRadius, double maxLoiterRadius) {
            this.maxWaypoints = maxWaypoints;
            this.minAltitude = minAltitude;
            this.maxAltitude = maxAltitude;
            this.maxLength = maxLength;
            this.minLoiterRadius = minLoiterRadius;
            this.maxLoiterRadius = maxLoiterRadius;
        }
    }

    private RouteValidator() {
    }

    /** Terminal actions a unit may be given: warheads strike, armed planes come home, recon patrols. */
    public static Set<Route.TerminalAction> allowedTerminals(boolean armed, boolean hasWeapon) {
        if (!armed) {
            return EnumSet.of(Route.TerminalAction.LOITER, Route.TerminalAction.LOOP, Route.TerminalAction.RETURN);
        }
        if (hasWeapon) {
            return EnumSet.of(Route.TerminalAction.LOITER, Route.TerminalAction.RETURN);
        }
        return EnumSet.of(Route.TerminalAction.LOITER, Route.TerminalAction.DIVE, Route.TerminalAction.DESTROY);
    }

    public static String validate(Route route, double startX, double startZ, Limits limits, Set<Route.TerminalAction> allowedTerminals) {
        // A Return with nothing on the way (a ferry to another runway) needs no waypoints
        if (route.size() == 0 && route.getTerminal() != Route.TerminalAction.RETURN) {
            return "message.combat_drones.route.empty";
        }
        if (route.size() > limits.maxWaypoints) {
            return "message.combat_drones.route.too_many_waypoints";
        }
        for (Route.Waypoint waypoint : route.getWaypoints()) {
            if (!Double.isFinite(waypoint.x) || !Double.isFinite(waypoint.z) || !Double.isFinite(waypoint.altitude)) {
                return "message.combat_drones.route.invalid";
            }
            if (waypoint.altitude < limits.minAltitude || waypoint.altitude > limits.maxAltitude) {
                return "message.combat_drones.route.altitude";
            }
        }
        if (route.length(startX, startZ) > limits.maxLength) {
            return "message.combat_drones.route.too_long";
        }
        if (!(route.getLoiterRadius() >= limits.minLoiterRadius && route.getLoiterRadius() <= limits.maxLoiterRadius)) {
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
