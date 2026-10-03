package io.github.duckysmacky.combatdrones.physics;

/**
 * Flies a {@link Route} by turning flight state into {@link FixedWingFlight.Controls}: banks toward the next waypoint,
 * pitches toward its altitude (never below the terrain floor it's given) and holds the leg's speed. Once the
 * waypoints are done it carries out the route's terminal action: orbit the last waypoint (loiter), dive onto a point
 * (dive / destroy), or fly home and land (return). Pure; the entity supplies terrain, fuel and home in a
 * {@link Situation}.
 */
public final class RouteAutopilot {
    static final double BANK_GAIN = 1.5;
    static final double DIVE_BANK_GAIN = 4.0;
    static final double PITCH_GAIN = 2.0;
    static final double MAX_CLIMB_PITCH = 20.0;

    /** Degrees of extra turn toward (or away from) the orbit centre per orbit radius of error. */
    static final double ORBIT_CORRECTION_DEG = 60.0;
    /** A strike pushes over into its dive once the target sits this far below the horizon. */
    static final double DIVE_START_DEG = 25.0;
    /** The dive only starts with the target within this many degrees of the nose. */
    static final double DIVE_ALIGN_DEG = 15.0;
    /** Height above the target a strike climbs to while extending for another pass. */
    static final double REATTACK_HEIGHT = 30.0;
    /** Returning units start their landing approach this close to home. */
    static final double LANDING_RADIUS = 48.0;
    /** Height above home a returning unit cruises at before the approach. */
    static final double RETURN_HEIGHT = 30.0;
    /** Landing glideslope: height above home per block of distance. */
    static final double GLIDESLOPE = 0.15;

    public enum Phase { ROUTE, LOITER, STRIKE, RETURN, LAND }

    /** What the unit knows about its surroundings this tick. */
    public static final class Situation {
        /** Launch altitude; waypoint altitudes are relative to it. */
        final double originY;
        /** Lowest absolute altitude allowed right now (terrain ahead plus clearance); ignored when diving or landing. */
        final double minSafeY;
        /** False means the engine is dead and the plane glides. */
        final boolean hasFuel;
        final double fuelFraction;
        /** Below this fuel fraction a loitering unit stops circling: warheads dive, everything else heads home. */
        final double lowFuelFraction;
        /** True for units with a warhead that goes off on impact (loitering munitions). */
        final boolean warhead;
        /** Ground height under the last waypoint (Dive aims here); NaN if unknown, then the launch altitude is used. */
        final double strikeGroundY;
        /** Where Return lands (the launch ramp); NaN X if unknown, then Return loiters instead. */
        final double homeX;
        final double homeY;
        final double homeZ;

        public Situation(double originY, double minSafeY, boolean hasFuel, double fuelFraction, double lowFuelFraction, boolean warhead,
                         double strikeGroundY, double homeX, double homeY, double homeZ) {
            this.originY = originY;
            this.minSafeY = minSafeY;
            this.hasFuel = hasFuel;
            this.fuelFraction = fuelFraction;
            this.lowFuelFraction = lowFuelFraction;
            this.warhead = warhead;
            this.strikeGroundY = strikeGroundY;
            this.homeX = homeX;
            this.homeY = homeY;
            this.homeZ = homeZ;
        }

        /** Plenty of fuel, no home and no known ground: just flying the route. */
        static Situation basic(double originY, double minSafeY, boolean hasFuel) {
            return new Situation(originY, minSafeY, hasFuel, 1, 0, false, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        boolean hasHome() {
            return !Double.isNaN(homeX);
        }
    }

    /** Controls for this tick, the waypoint index to aim at next (== route size once done) and the current phase. */
    public static final class Command {
        public final FixedWingFlight.Controls controls;
        public final int waypointIndex;
        public final Phase phase;

        Command(FixedWingFlight.Controls controls, int waypointIndex, Phase phase) {
            this.controls = controls;
            this.waypointIndex = waypointIndex;
            this.phase = phase;
        }
    }

    private RouteAutopilot() {
    }

    /** Route flight without terminal-action context (no home, full fuel). */
    public static Command guide(FixedWingFlight.State state, Route route, int waypointIndex, double originY, double minSafeY,
                                FixedWingFlight.Params params, boolean hasFuel) {
        return guide(state, route, waypointIndex, Situation.basic(originY, minSafeY, hasFuel), params);
    }

    public static Command guide(FixedWingFlight.State state, Route route, int waypointIndex, Situation situation, FixedWingFlight.Params params) {
        if (route.size() == 0) {
            return new Command(hold(state, situation, params), waypointIndex, Phase.ROUTE);
        }
        int index = Math.min(waypointIndex, route.size());
        if (index < route.size()) {
            Route.Waypoint next = route.get(index);
            double acceptRadius = Math.max(6, 8 * state.speed);
            if (Math.hypot(next.x - state.position.x, next.z - state.position.z) < acceptRadius) {
                index++;
            }
        }
        if (index < route.size()) {
            Route.Waypoint target = route.get(index);
            double bank = bankToward(state, bearing(state, target.x, target.z));
            double targetY = Math.max(situation.originY + target.altitude, situation.minSafeY);
            return new Command(controls(state, bank, targetY, target.speed.fraction, params, situation.hasFuel), index, Phase.ROUTE);
        }

        Route.Waypoint last = route.get(route.size() - 1);
        double groundY = Double.isNaN(situation.strikeGroundY) ? situation.originY : situation.strikeGroundY;
        boolean lowFuel = situation.fuelFraction < situation.lowFuelFraction;
        switch (route.getTerminal()) {
            case DIVE:
                return new Command(strike(state, last.x, groundY, last.z, situation.minSafeY, params, situation.hasFuel), index, Phase.STRIKE);
            case DESTROY: {
                Route.Target target = route.getTarget();
                FixedWingFlight.Controls controls = target != null && !Double.isNaN(target.y)
                        ? strike(state, target.x, target.y, target.z, situation.minSafeY, params, situation.hasFuel)
                        : strike(state, last.x, groundY, last.z, situation.minSafeY, params, situation.hasFuel);
                return new Command(controls, index, Phase.STRIKE);
            }
            case RETURN:
                if (situation.hasHome()) {
                    return returnHome(state, situation, params, index);
                }
                break;
            default:
                // LOITER (and LOOP until the recon milestone): circle, until fuel runs low
                if (lowFuel && situation.warhead) {
                    return new Command(strike(state, last.x, groundY, last.z, situation.minSafeY, params, situation.hasFuel), index, Phase.STRIKE);
                }
                if (lowFuel && situation.hasHome()) {
                    return returnHome(state, situation, params, index);
                }
                break;
        }
        return new Command(orbit(state, route, last, situation, params), index, Phase.LOITER);
    }

    /** Circle the last waypoint clockwise, steering in or out to hold the route's loiter radius. */
    private static FixedWingFlight.Controls orbit(FixedWingFlight.State state, Route route, Route.Waypoint center, Situation situation,
                                                  FixedWingFlight.Params params) {
        double distance = Math.hypot(center.x - state.position.x, center.z - state.position.z);
        double radius = route.getLoiterRadius();
        double correction = FixedWingFlight.clamp((distance - radius) / radius * ORBIT_CORRECTION_DEG, 70);
        double bank = bankToward(state, bearing(state, center.x, center.z) - 90 + correction);
        double targetY = Math.max(situation.originY + center.altitude, situation.minSafeY);
        return controls(state, bank, targetY, center.speed.fraction, params, situation.hasFuel);
    }

    /**
     * Fly onto a fixed point and hit it: hold altitude (above the terrain floor) on the approach, push over into a dive
     * once the target is {@link #DIVE_START_DEG} below the horizon and lined up on the nose, and follow the line of
     * sight down at full power. If it isn't lined up by then it extends out and comes round for another pass. Also used for a manually designated point.
     */
    public static FixedWingFlight.Controls strike(FixedWingFlight.State state, double targetX, double targetY, double targetZ, double minSafeY,
                                                  FixedWingFlight.Params params, boolean hasFuel) {
        double bearing = bearing(state, targetX, targetZ);
        double bank = bankToward(state, bearing);
        double horizontal = Math.hypot(targetX - state.position.x, targetZ - state.position.z);
        double lineOfSight = Math.toDegrees(Math.atan2(state.position.y - targetY, horizontal)); // positive = below the horizon
        boolean linedUp = Math.abs(FixedWingFlight.wrap(bearing - state.heading)) <= DIVE_ALIGN_DEG || horizontal <= 3;
        if (lineOfSight < DIVE_START_DEG) {
            // Approach: turn toward the target at height until it slides into the dive cone
            return controls(state, bank, Math.max(state.position.y, minSafeY), 1, params, hasFuel);
        }
        if (!linedUp) {
            // Inside the cone but not pointing at it (overshot, or arrived from the side): diving now would spiral in,
            // and turning tightly just circles it. Fly straight out of the cone, climbing, then come back round.
            return controls(state, 0, Math.max(targetY + REATTACK_HEIGHT, minSafeY), 1, params, hasFuel);
        }
        // In the dive, steer harder: small heading errors become misses once the point is close
        bank = FixedWingFlight.clamp(FixedWingFlight.wrap(bearing - state.heading) * DIVE_BANK_GAIN, FixedWingFlight.MAX_BANK_DEG);
        // Lead the line of sight slightly so the nose doesn't trail behind it as it steepens
        double pitch = Math.max(-FixedWingFlight.MAX_PITCH_DEG, Math.min(FixedWingFlight.MAX_DIVE_PITCH_DEG,
                lineOfSight + (lineOfSight - state.pitch) * 0.5));
        return new FixedWingFlight.Controls(bank, pitch, hasFuel ? 1 : 0, hasFuel);
    }

    /** Head home at a safe height; close in, fly a shallow glideslope down onto the ground at home at slow speed. */
    private static Command returnHome(FixedWingFlight.State state, Situation situation, FixedWingFlight.Params params, int index) {
        double distance = Math.hypot(situation.homeX - state.position.x, situation.homeZ - state.position.z);
        double bank = bankToward(state, bearing(state, situation.homeX, situation.homeZ));
        if (distance > LANDING_RADIUS) {
            double targetY = Math.max(situation.homeY + RETURN_HEIGHT, situation.minSafeY);
            return new Command(controls(state, bank, targetY, Route.Speed.MEDIUM.fraction, params, situation.hasFuel), index, Phase.RETURN);
        }
        // Aim a little below the ground so it actually touches down instead of skimming
        double targetY = situation.homeY + distance * GLIDESLOPE - 2;
        return new Command(controls(state, bank, targetY, Route.Speed.SLOW.fraction, params, situation.hasFuel), index, Phase.LAND);
    }

    /** No route: fly straight and level (above the floor). */
    private static FixedWingFlight.Controls hold(FixedWingFlight.State state, Situation situation, FixedWingFlight.Params params) {
        return controls(state, 0, Math.max(state.position.y, situation.minSafeY), Route.Speed.FAST.fraction, params, situation.hasFuel);
    }

    private static double bankToward(FixedWingFlight.State state, double desiredHeading) {
        return FixedWingFlight.clamp(FixedWingFlight.wrap(desiredHeading - state.heading) * BANK_GAIN, FixedWingFlight.MAX_BANK_DEG);
    }

    private static FixedWingFlight.Controls controls(FixedWingFlight.State state, double bank, double targetY, double speedFraction,
                                                     FixedWingFlight.Params params, boolean hasFuel) {
        if (!hasFuel) {
            return new FixedWingFlight.Controls(bank, FixedWingFlight.GLIDE_PITCH_DEG, 0, false);
        }
        // Nose up (negative pitch) when below the target altitude
        double pitch = FixedWingFlight.clamp(-(targetY - state.position.y) * PITCH_GAIN, MAX_CLIMB_PITCH);
        double cruise = params.maxSpeed * speedFraction;
        double climbBoost = Math.max(0, -pitch) / MAX_CLIMB_PITCH * 0.3;
        // Feed-forward: the throttle that exactly balances drag at the target speed, plus a correction toward it
        double balance = speedFraction * speedFraction;
        double throttle = Math.max(0, Math.min(1, balance + (cruise - state.speed) * 5 + climbBoost));
        return new FixedWingFlight.Controls(bank, pitch, throttle, true);
    }

    /** Vanilla yaw pointing from the plane toward (x, z). */
    static double bearing(FixedWingFlight.State state, double x, double z) {
        return Math.toDegrees(Math.atan2(-(x - state.position.x), z - state.position.z));
    }
}
