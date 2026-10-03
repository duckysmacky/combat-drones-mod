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

    public static final class Waypoint {
        public final double x;
        public final double z;
        /** Blocks above the launch point. */
        public final double altitude;

        public Waypoint(double x, double z, double altitude) {
            this.x = x;
            this.z = z;
            this.altitude = altitude;
        }
    }

    private final List<Waypoint> waypoints;
    private final TerminalAction terminal;

    public Route(List<Waypoint> waypoints, TerminalAction terminal) {
        this.waypoints = Collections.unmodifiableList(new ArrayList<>(waypoints));
        this.terminal = terminal;
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
