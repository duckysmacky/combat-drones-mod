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
     * RETURN; recon: LOOP / RETURN (see {@link RouteValidator#allowedTerminals}).
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

    private final List<Waypoint> waypoints;
    private final TerminalAction terminal;
    /** Orbit radius around the last waypoint for LOITER / LOOP endings, in blocks. */
    private final double loiterRadius;

    public Route(List<Waypoint> waypoints, TerminalAction terminal) {
        this(waypoints, terminal, DEFAULT_LOITER_RADIUS);
    }

    public Route(List<Waypoint> waypoints, TerminalAction terminal, double loiterRadius) {
        this.waypoints = Collections.unmodifiableList(new ArrayList<>(waypoints));
        this.terminal = terminal;
        this.loiterRadius = loiterRadius;
    }

    public double getLoiterRadius() {
        return loiterRadius;
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
