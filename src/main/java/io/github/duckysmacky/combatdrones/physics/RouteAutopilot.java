package io.github.duckysmacky.combatdrones.physics;

/**
 * Flies a {@link Route} by turning flight state into {@link FixedWingFlight.Controls}: banks toward the next waypoint,
 * pitches toward its altitude (never below the terrain floor it's given) and holds the leg's speed. Once the
 * waypoints are done it carries out the route's terminal action: orbit the last waypoint (loiter), fly the route
 * again (loop), dive onto a point (dive / destroy), or fly home and land (return). Pure; the entity supplies terrain, fuel and home in a
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
    /** A strike only extends (flies straight out to come round again) once the target is this far off the nose. */
    static final double EXTEND_BEARING_DEG = 90.0;
    /** The dive only starts with the target within this many degrees of the nose. */
    static final double DIVE_ALIGN_DEG = 15.0;
    /** Height above the target a strike climbs to while extending for another pass. */
    static final double REATTACK_HEIGHT = 30.0;
    /** Speed share flown while lining up a strike: slower turns tighter; the dive itself goes in at full power. */
    static final double STRIKE_APPROACH_SPEED = Route.Speed.MEDIUM.fraction;
    /** A pursuit dives straight down the line of sight once the target is within this many degrees of the nose. */
    static final double PURSUIT_ALIGN_DEG = 20.0;
    /** A pursuit only dives on a target at least this much shallower than the steepest dive. */
    static final double PURSUIT_DIVE_MARGIN_DEG = 5.0;
    /** Speed share a pursuit slows to while turning in: turn radius grows with the square of speed. */
    static final double PURSUIT_TURN_SPEED = 0.55;
    /** Steepest descent while turning in, so a target too steep to dive on is spiralled down to instead of looped. */
    static final double PURSUIT_SPIRAL_PITCH = 35.0;
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
        boolean lowFuel = situation.fuelFraction < situation.lowFuelFraction;
        // Loop: start over from the first waypoint, until fuel runs low (then the terminal handling below sends it home)
        // (A one-waypoint loop has nothing to go round: it circles that waypoint instead, like Loiter.)
        if (index >= route.size() && route.getTerminal() == Route.TerminalAction.LOOP && route.size() > 1 && !lowFuel) {
            index = 0;
        }
        if (index < route.size()) {
            Route.Waypoint target = route.get(index);
            double bank = bankToward(state, bearing(state, target.x, target.z));
            double targetY = Math.max(situation.originY + target.altitude, situation.minSafeY);
            return new Command(controls(state, bank, targetY, target.speed.fraction, params, situation.hasFuel), index, Phase.ROUTE);
        }

        Route.Waypoint last = route.get(route.size() - 1);
        double groundY = Double.isNaN(situation.strikeGroundY) ? situation.originY : situation.strikeGroundY;
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
                // LOITER, or LOOP once fuel is low: circle, until fuel runs low / without a home to go to
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
     * sight down at full power. If it isn't lined up, it first extends out (far enough to turn back in a straight line)
     * and comes round for another pass. Designated points use {@link #pursue} instead.
     */
    public static FixedWingFlight.Controls strike(FixedWingFlight.State state, double targetX, double targetY, double targetZ, double minSafeY,
                                                  FixedWingFlight.Params params, boolean hasFuel) {
        double bearing = bearing(state, targetX, targetZ);
        double bank = bankToward(state, bearing);
        double horizontal = Math.hypot(targetX - state.position.x, targetZ - state.position.z);
        double lineOfSight = Math.toDegrees(Math.atan2(state.position.y - targetY, horizontal)); // positive = below the horizon
        double headingError = Math.abs(FixedWingFlight.wrap(bearing - state.heading));
        boolean linedUp = headingError <= DIVE_ALIGN_DEG || horizontal <= 3;
        // Target behind it and too close to come round in time (inside the dive cone plus a turn's width): fly straight
        // out first, climbing. Turning now would just circle it. Anything less far off the nose: keep turning in.
        double coneDistance = Math.max(0, state.position.y - targetY) / Math.tan(Math.toRadians(DIVE_START_DEG));
        double turnDiameter = 2 * state.speed * state.speed
                / (FixedWingFlight.TURN_GRAVITY * params.agility * Math.tan(Math.toRadians(FixedWingFlight.MAX_BANK_DEG)));
        // The nose can't come down to the line of sight before it's over the target (lined up too late, too close)
        double catchUpTicks = Math.max(0, lineOfSight - state.pitch) / (FixedWingFlight.PITCH_RATE_DEG * params.agility);
        boolean tooSteep = lineOfSight >= DIVE_START_DEG && horizontal > 3 && state.speed * catchUpTicks > horizontal;
        if ((headingError > EXTEND_BEARING_DEG || tooSteep) && horizontal < coneDistance + turnDiameter) {
            return controls(state, 0, Math.max(targetY + REATTACK_HEIGHT, minSafeY), STRIKE_APPROACH_SPEED, params, hasFuel);
        }
        if (lineOfSight < DIVE_START_DEG || !linedUp) {
            // Approach: turn toward the target at height until it's on the nose and inside the dive cone
            return controls(state, bank, Math.max(state.position.y, minSafeY), STRIKE_APPROACH_SPEED, params, hasFuel);
        }
        // In the dive, steer harder: small heading errors become misses once the point is close
        bank = FixedWingFlight.clamp(FixedWingFlight.wrap(bearing - state.heading) * DIVE_BANK_GAIN, FixedWingFlight.MAX_BANK_DEG);
        // Lead the line of sight slightly so the nose doesn't trail behind it as it steepens
        double pitch = Math.max(-FixedWingFlight.MAX_PITCH_DEG, Math.min(FixedWingFlight.MAX_DIVE_PITCH_DEG,
                lineOfSight + (lineOfSight - state.pitch) * 0.5));
        return new FixedWingFlight.Controls(bank, pitch, hasFuel ? 1 : 0, hasFuel);
    }

    /**
     * Go straight for a point picked from the camera (click-designate). Unlike {@link #strike} it never climbs or
     * holds height to line up a dive: it turns in right away, slowed for a tight turn, descending toward the target
     * along the line of sight (never below it, nor below the terrain floor) at most {@link #PURSUIT_SPIRAL_PITCH}
     * steep, and once the target is on the nose and shallow enough it dives down the line of sight at full power. Only
     * a target behind it or too steep to dive on, and too close to come round onto, makes it fly on straight first,
     * and only as far as that low, slow turn needs.
     */
    public static FixedWingFlight.Controls pursue(FixedWingFlight.State state, double targetX, double targetY, double targetZ, double minSafeY,
                                                  FixedWingFlight.Params params, boolean hasFuel) {
        double bearingError = FixedWingFlight.wrap(bearing(state, targetX, targetZ) - state.heading);
        double horizontal = Math.hypot(targetX - state.position.x, targetZ - state.position.z);
        double lineOfSight = Math.toDegrees(Math.atan2(state.position.y - targetY, horizontal)); // positive = below the horizon
        // Steeper than the airframe can dive: overshoot and come round lower rather than miss short of it
        boolean reachable = lineOfSight <= FixedWingFlight.MAX_DIVE_PITCH_DEG - PURSUIT_DIVE_MARGIN_DEG;
        if (Math.abs(bearingError) <= PURSUIT_ALIGN_DEG && reachable) {
            double bank = FixedWingFlight.clamp(bearingError * DIVE_BANK_GAIN, FixedWingFlight.MAX_BANK_DEG);
            double pitch = Math.max(-FixedWingFlight.MAX_PITCH_DEG, Math.min(FixedWingFlight.MAX_DIVE_PITCH_DEG,
                    lineOfSight + (lineOfSight - state.pitch) * 0.5));
            return new FixedWingFlight.Controls(bank, pitch, hasFuel ? 1 : 0, hasFuel);
        }
        // Too close to come round onto it (behind, or too steep): straight on for just that far, still descending
        double steepest = Math.toRadians(FixedWingFlight.MAX_DIVE_PITCH_DEG - PURSUIT_DIVE_MARGIN_DEG);
        double coneDistance = Math.max(0, state.position.y - targetY) / Math.tan(steepest);
        double turnDiameter = 2 * state.speed * state.speed
                / (FixedWingFlight.TURN_GRAVITY * params.agility * Math.tan(Math.toRadians(FixedWingFlight.MAX_BANK_DEG)));
        boolean comeRound = (Math.abs(bearingError) > EXTEND_BEARING_DEG || !reachable) && horizontal < coneDistance + turnDiameter;
        double bank = comeRound ? 0 : FixedWingFlight.clamp(bearingError * BANK_GAIN, FixedWingFlight.MAX_BANK_DEG);
        double pitch = Math.max(-MAX_CLIMB_PITCH, Math.min(PURSUIT_SPIRAL_PITCH, lineOfSight));
        pitch = Math.min(pitch, (state.position.y - minSafeY) * PITCH_GAIN);
        double throttle = Math.max(0, Math.min(1, PURSUIT_TURN_SPEED * PURSUIT_TURN_SPEED
                + (params.maxSpeed * PURSUIT_TURN_SPEED - state.speed) * 5));
        return new FixedWingFlight.Controls(bank, pitch, hasFuel ? throttle : 0, hasFuel);
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
