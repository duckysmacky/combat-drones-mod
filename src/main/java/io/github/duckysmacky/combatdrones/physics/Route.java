package io.github.duckysmacky.combatdrones.physics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
    /** {@link Waypoint#dropLength} of a waypoint that drops nothing. */
    public static final double NO_DROP = -1;

    public static final class Waypoint {
        public final double x;
        public final double z;
        /** Blocks above the launch point. */
        public final double altitude;
        /** Speed flown on the way to this waypoint (and while circling it, if it's the last one). */
        public final Speed speed;
        /**
         * Bombers: what the bomb bay does as the plane passes this waypoint. {@link #NO_DROP}: nothing; 0: one bomb;
         * more: a carpet run, bombs released one after another while it flies straight on for this many blocks.
         */
        public final double dropLength;

        public Waypoint(double x, double z, double altitude) {
            this(x, z, altitude, Speed.FAST);
        }

        public Waypoint(double x, double z, double altitude, Speed speed) {
            this(x, z, altitude, speed, NO_DROP);
        }

        public Waypoint(double x, double z, double altitude, Speed speed, double dropLength) {
            this.x = x;
            this.z = z;
            this.altitude = altitude;
            this.speed = speed;
            this.dropLength = dropLength;
        }

        public boolean isDrop() {
            return dropLength >= 0;
        }

        public Waypoint withPosition(double newX, double newZ) {
            return new Waypoint(newX, newZ, altitude, speed, dropLength);
        }

        public Waypoint withAltitude(double newAltitude) {
            return new Waypoint(x, z, newAltitude, speed, dropLength);
        }

        public Waypoint withSpeed(Speed newSpeed) {
            return new Waypoint(x, z, altitude, newSpeed, dropLength);
        }

        public Waypoint withDrop(double newDropLength) {
            return new Waypoint(x, z, altitude, speed, newDropLength);
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

    /**
     * A runway to land on (Return). Coordinates are the threshold: the centre of the tail end, at deck height. Planes
     * take off and land along {@link #heading}, from the threshold toward the far end.
     */
    public static final class Runway {
        public final UUID id;
        public final double x;
        public final double y;
        public final double z;
        /** Vanilla yaw, a multiple of 90. */
        public final double heading;
        public final double length;
        public final double width;

        public Runway(UUID id, double x, double y, double z, double heading, double length, double width) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.heading = heading;
            this.length = length;
            this.width = width;
        }

        /** Unit vector along the runway (x, z). */
        public double dirX() {
            return -Math.sin(Math.toRadians(heading));
        }

        public double dirZ() {
            return Math.cos(Math.toRadians(heading));
        }

        /** Distance of (px, pz) past the threshold along the runway (negative: before it). */
        public double along(double px, double pz) {
            return (px - x) * dirX() + (pz - z) * dirZ();
        }

        /** Signed distance of (px, pz) from the centreline (positive: to the right, looking along the runway). */
        public double cross(double px, double pz) {
            return (px - x) * -dirZ() + (pz - z) * dirX();
        }

        /** World x / z of the point {@code along} blocks past the threshold and {@code cross} to the right. */
        public double pointX(double along, double cross) {
            return x + dirX() * along - dirZ() * cross;
        }

        public double pointZ(double along, double cross) {
            return z + dirZ() * along + dirX() * cross;
        }
    }

    private final List<Waypoint> waypoints;
    private final TerminalAction terminal;
    /** Orbit radius around the last waypoint for LOITER / LOOP endings, in blocks. */
    private final double loiterRadius;
    private final Target target;
    /** Where Return lands, or null for the unit's home runway. */
    private final Runway runway;

    public Route(List<Waypoint> waypoints, TerminalAction terminal) {
        this(waypoints, terminal, DEFAULT_LOITER_RADIUS);
    }

    public Route(List<Waypoint> waypoints, TerminalAction terminal, double loiterRadius) {
        this(waypoints, terminal, loiterRadius, null);
    }

    public Route(List<Waypoint> waypoints, TerminalAction terminal, double loiterRadius, Target target) {
        this(waypoints, terminal, loiterRadius, target, null);
    }

    public Route(List<Waypoint> waypoints, TerminalAction terminal, double loiterRadius, Target target, Runway runway) {
        this.waypoints = Collections.unmodifiableList(new ArrayList<>(waypoints));
        this.terminal = terminal;
        this.loiterRadius = loiterRadius;
        this.target = target;
        this.runway = runway;
    }

    /** Strike point for Destroy, or null if none is marked. */
    public Target getTarget() {
        return target;
    }

    public Route withTarget(Target newTarget) {
        return new Route(waypoints, terminal, loiterRadius, newTarget, runway);
    }

    /** Runway Return lands on, or null for the unit's home runway. */
    public Runway getRunway() {
        return runway;
    }

    public Route withRunway(Runway newRunway) {
        return new Route(waypoints, terminal, loiterRadius, target, newRunway);
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
        return new Route(raised, terminal, Math.max(loiterRadius, minRadius), target, runway);
    }

    /**
     * This route with every carpet run no longer than {@code maxCarpetLength}, and anything that isn't a real length
     * dropping nothing. A negative limit (a unit without a bomb bay) removes every drop.
     */
    public Route withDropLimit(double maxCarpetLength) {
        List<Waypoint> limited = new ArrayList<>(waypoints.size());
        for (Waypoint waypoint : waypoints) {
            boolean drops = maxCarpetLength >= 0 && waypoint.dropLength >= 0; // false for NaN too
            limited.add(waypoint.withDrop(drops ? Math.min(waypoint.dropLength, maxCarpetLength) : NO_DROP));
        }
        return new Route(limited, terminal, loiterRadius, target, runway);
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
