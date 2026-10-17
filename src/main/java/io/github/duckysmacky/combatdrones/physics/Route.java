package io.github.duckysmacky.combatdrones.physics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A planned flight for a ramp-launched unit: waypoints (absolute X/Z, altitude above the launch point) plus what to do
 * after the last one. Immutable; edits produce a new route.
 */
public final class Route {
    /**
     * What a unit does once its waypoints are done. Munitions: LOITER / DIVE / DESTROY; gunship planes: LOITER /
     * RETURN; recon: LOITER / LOOP / RETURN (see {@link RouteValidator#allowedTerminals}).
     */
    public enum TerminalAction { LOITER, DIVE, DESTROY, RETURN, LOOP }

    /** Speed to fly a leg at, as a share of the unit's top speed. Slower legs burn less fuel. */
    public enum Speed {
        SLOW(0.5), MEDIUM(0.65), FAST(0.8);

        public final double fraction;

        Speed(double fraction) {
            this.fraction = fraction;
        }
    }

    public static final double DEFAULT_LOITER_RADIUS = 24.0;
    public static final double MIN_LOITER_RADIUS = 12.0;
    public static final double MAX_LOITER_RADIUS = 160.0;

    public static final class Waypoint {
        public final double x;
        public final double z;
        /** Blocks above the launch point. */
        public final double altitude;
        /** Speed flown on the way to this waypoint (and while circling it, if it's the last one). */
        public final Speed speed;

        public Waypoint(double x, double z, double altitude) {
            this(x, z, altitude, Speed.FAST);
        }

        public Waypoint(double x, double z, double altitude, Speed speed) {
            this.x = x;
            this.z = z;
            this.altitude = altitude;
            this.speed = speed;
        }

        public Waypoint withPosition(double newX, double newZ) {
            return new Waypoint(newX, newZ, altitude, speed);
        }

        public Waypoint withAltitude(double newAltitude) {
            return new Waypoint(x, z, newAltitude, speed);
        }

        public Waypoint withSpeed(Speed newSpeed) {
            return new Waypoint(x, z, altitude, newSpeed);
        }
    }

    /** A fixed point to strike (Destroy): captured when marked, never a moving entity. */
    public static final class Target {
        public final double x;
        /** Absolute block Y. NaN when picked on the map, until the server resolves the ground height. */
        public final double y;
        public final double z;

        public Target(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final List<Waypoint> waypoints;
    private final TerminalAction terminal;
    /** Orbit radius around the last waypoint for LOITER / LOOP endings, in blocks. */
    private final double loiterRadius;
    private final Target target;

    public Route(List<Waypoint> waypoints, TerminalAction terminal) {
        this(waypoints, terminal, DEFAULT_LOITER_RADIUS);
    }

    public Route(List<Waypoint> waypoints, TerminalAction terminal, double loiterRadius) {
        this(waypoints, terminal, loiterRadius, null);
    }

    public Route(List<Waypoint> waypoints, TerminalAction terminal, double loiterRadius, Target target) {
        this.waypoints = Collections.unmodifiableList(new ArrayList<>(waypoints));
        this.terminal = terminal;
        this.loiterRadius = loiterRadius;
        this.target = target;
    }

    /** Strike point for Destroy, or null if none is marked. */
    public Target getTarget() {
        return target;
    }

    public Route withTarget(Target newTarget) {
        return new Route(waypoints, terminal, loiterRadius, newTarget);
    }

    public double getLoiterRadius() {
        return loiterRadius;
    }

    /** True if the route ends circling its last waypoint: Loiter, or a one-waypoint Loop. */
    public boolean endsInOrbit() {
        return !waypoints.isEmpty() && (terminal == TerminalAction.LOITER || (terminal == TerminalAction.LOOP && waypoints.size() == 1));
    }

    /** This route with its orbit raised and widened to at least a unit type's minimum height and radius. */
    public Route withLoiterMinimums(double minAltitude, double minRadius) {
        List<Waypoint> raised = waypoints;
        if (endsInOrbit()) {
            raised = new ArrayList<>(waypoints);
            Waypoint last = raised.get(raised.size() - 1);
            raised.set(raised.size() - 1, last.withAltitude(Math.max(last.altitude, minAltitude)));
        }
        return new Route(raised, terminal, Math.max(loiterRadius, minRadius), target);
    }

    public static Route empty(TerminalAction terminal) {
        return new Route(Collections.emptyList(), terminal);
    }

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public TerminalAction getTerminal() {
        return terminal;
    }

    public int size() {
        return waypoints.size();
    }

    public Waypoint get(int index) {
        return waypoints.get(index);
    }

    /** Horizontal path length from the given start through every waypoint, in blocks. */
    public double length(double startX, double startZ) {
        double length = 0;
        double x = startX;
        double z = startZ;
        for (Waypoint waypoint : waypoints) {
            length += Math.hypot(waypoint.x - x, waypoint.z - z);
            x = waypoint.x;
            z = waypoint.z;
        }
        return length;
    }
}
