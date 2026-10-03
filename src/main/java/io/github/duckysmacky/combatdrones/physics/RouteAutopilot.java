package io.github.duckysmacky.combatdrones.physics;

/**
 * Flies a {@link Route} by turning flight state into {@link FixedWingFlight.Controls}: banks toward the next waypoint,
 * pitches toward its altitude (never below the terrain floor it's given), holds cruise speed, and orbits the last
 * waypoint once the route is done (loiter). Pure; the entity supplies the terrain floor and fuel state.
 */
public final class RouteAutopilot {
    static final double BANK_GAIN = 1.5;
    static final double PITCH_GAIN = 2.0;
    static final double MAX_CLIMB_PITCH = 20.0;

    /** Degrees of extra turn toward (or away from) the orbit centre per orbit radius of error. */
    static final double ORBIT_CORRECTION_DEG = 60.0;

    /** Controls for this tick plus the waypoint index to aim at next (== route size once orbiting). */
    public static final class Command {
        public final FixedWingFlight.Controls controls;
        public final int waypointIndex;

        Command(FixedWingFlight.Controls controls, int waypointIndex) {
            this.controls = controls;
            this.waypointIndex = waypointIndex;
        }
    }

    private RouteAutopilot() {
    }

    /**
     * @param originY   launch altitude; waypoint altitudes are relative to it
     * @param minSafeY  lowest absolute altitude allowed right now (terrain ahead plus clearance)
     * @param hasFuel   false means the engine is dead and the plane glides
     */
    public static Command guide(FixedWingFlight.State state, Route route, int waypointIndex, double originY, double minSafeY,
                                FixedWingFlight.Params params, boolean hasFuel) {
        if (route.size() == 0) {
            return new Command(hold(state, minSafeY, params, hasFuel), waypointIndex);
        }
        int index = Math.min(waypointIndex, route.size());
        if (index < route.size()) {
            Route.Waypoint next = route.get(index);
            double acceptRadius = Math.max(6, 8 * state.speed);
            if (Math.hypot(next.x - state.position.x, next.z - state.position.z) < acceptRadius) {
                index++;
            }
        }

        double desiredHeading;
        Route.Waypoint target;
        if (index < route.size()) {
            target = route.get(index);
            desiredHeading = bearing(state, target.x, target.z);
        } else {
            // Loiter: circle the last waypoint clockwise, steering in or out to hold the radius
            target = route.get(route.size() - 1);
            double distance = Math.hypot(target.x - state.position.x, target.z - state.position.z);
            double radius = route.getLoiterRadius();
            double correction = FixedWingFlight.clamp((distance - radius) / radius * ORBIT_CORRECTION_DEG, 70);
            desiredHeading = bearing(state, target.x, target.z) - 90 + correction;
        }
        double bank = FixedWingFlight.clamp(FixedWingFlight.wrap(desiredHeading - state.heading) * BANK_GAIN, FixedWingFlight.MAX_BANK_DEG);
        double targetY = Math.max(originY + target.altitude, minSafeY);
        return new Command(controls(state, bank, targetY, target.speed.fraction, params, hasFuel), index);
    }

    /** No route: fly straight and level (above the floor). */
    private static FixedWingFlight.Controls hold(FixedWingFlight.State state, double minSafeY, FixedWingFlight.Params params, boolean hasFuel) {
        return controls(state, 0, Math.max(state.position.y, minSafeY), Route.Speed.FAST.fraction, params, hasFuel);
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
