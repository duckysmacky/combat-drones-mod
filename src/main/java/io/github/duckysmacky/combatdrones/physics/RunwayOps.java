package io.github.duckysmacky.combatdrones.physics;

/**
 * Planes on a runway: the takeoff roll, and (with {@link RouteAutopilot}) the approach and the roll-out after
 * landing. Pure; positions are in blocks, along-track distances measured from the runway's threshold (its tail end).
 */
public final class RunwayOps {
    /** The roll reaches lift-off speed this far before the far end. */
    static final double ROLL_MARGIN = 1.0;
    /** Pitch it lifts off with (nose up). */
    static final double LIFTOFF_PITCH = -8.0;

    private RunwayOps() {
    }

    /** Along-track spot a parked plane sits on and starts its roll from: far enough in for a long fuselage. */
    public static double seat(Route.Runway runway) {
        return runway.length * 0.25;
    }

    /**
     * One tick of the takeoff run, {@code ticks} after the launch: spool up on the seat for {@code spoolTicks}, then
     * roll down the centreline at constant acceleration, sized so the plane reaches {@code liftoffFraction} of its top
     * speed {@link #ROLL_MARGIN} before the far end whatever its airframe. Returns true on the tick it lifts off (state
     * set up to fly on from there).
     */
    public static boolean takeoffRoll(FixedWingFlight.State state, Route.Runway runway, double maxSpeed, int ticks, int spoolTicks,
                                      double liftoffFraction) {
        double rotation = maxSpeed * liftoffFraction;
        double rollLength = Math.max(1, runway.length - ROLL_MARGIN - seat(runway));
        if (ticks >= spoolTicks) {
            // Per-tick steps: n ticks at +a each cover a n (n + 1) / 2 blocks, with n = v / a
            state.speed = Math.min(rotation, state.speed + rotation * rotation / Math.max(rotation, 2 * rollLength - rotation));
        }
        double along = runway.along(state.position.x, state.position.z) + state.speed;
        state.heading = runway.heading;
        state.bank = 0;
        state.pitch = 0;
        state.velocity.set(runway.dirX() * state.speed, 0, runway.dirZ() * state.speed);
        state.position.set(runway.pointX(along, 0), runway.y, runway.pointZ(along, 0));
        if (state.speed < rotation) {
            return false;
        }
        state.pitch = LIFTOFF_PITCH;
        return true;
    }

    // ---- landing --------------------------------------------------------------------------------------------------

    /** The approach fix: this far behind the threshold, on the extended centreline. */
    static final double FINAL_LENGTH = 40.0;
    /** Glideslope: height over the deck per block before the threshold. */
    static final double GLIDESLOPE = 0.2;
    /** Height error whose pitch matches the glide angle, so the plane flies down the slope instead of above it. */
    static final double GLIDE_LEAD = Math.toDegrees(Math.atan(GLIDESLOPE)) / RouteAutopilot.PITCH_GAIN;
    /** Lowest the approach aims over the ground short of the threshold, so it never drags its belly on it. */
    static final double APPROACH_FLOOR = 1.0;
    /** Over the runway it aims this far under the deck, to sink onto it. */
    static final double FLARE_AIM = 1.0;
    /** On final: degrees of heading toward the centreline per block off it, at most this many. */
    static final double INTERCEPT_GAIN = 6.0;
    static final double FINAL_MAX_CORRECTION_DEG = 30.0;
    /** Lining up behind the runway: it steers for the centreline this far ahead of itself. */
    static final double LOOKAHEAD = 20.0;
    /** Headed for the runway but not on final: it keeps lining up while at least this far out, else goes round. */
    static final double INTERCEPT_START = 10.0;
    /** Flying away, it goes this far past the fix before turning back. */
    static final double TURN_ROOM = 15.0;
    /** On final: within this much of the runway heading, and this close to the centreline (widening further out). */
    static final double ESTABLISHED_HEADING_DEG = 30.0;
    static final double ESTABLISHED_CROSS = 4.0;
    /**
     * On final only while it can still get down: at most this many blocks over the deck per block before the threshold,
     * plus a fixed margin. Higher, diving for the runway only brings it over the deck too high and fast, so it goes
     * round instead and comes in from the fix at the approach height.
     */
    static final double ESTABLISHED_DESCENT = 0.25;
    static final double ESTABLISHED_HEIGHT = 4.0;
    /** Share of the runway it may still touch down on; floating past it means a go-around. */
    static final double TOUCHDOWN_ZONE = 0.6;
    /** Captured onto the deck from at most this high over it, this far off its heading and this fast. */
    static final double CAPTURE_HEIGHT = 2.5;
    static final double CAPTURE_HEADING_DEG = 25.0;
    public static final double CAPTURE_SPEED_FRACTION = 0.75;
    /** Circle flown over a runway it can't land on yet. */
    static final double HOLDING_RADIUS = 40.0;

    /**
     * Fly back and land on {@code runway}: out to the approach fix behind it, turn onto the extended centreline, then
     * down the glideslope at Slow speed (phase LAND) until {@link #captured}. Off the centreline or past the touchdown
     * zone it goes round again from the fix. A runway it can't use yet ({@code free} false) is circled overhead.
     */
    static RouteAutopilot.Command approach(FixedWingFlight.State state, Route.Runway runway, boolean free, double minSafeY,
                                           FixedWingFlight.Params params, boolean hasFuel, int index) {
        double x = state.position.x;
        double z = state.position.z;
        double along = runway.along(x, z);
        double cross = runway.cross(x, z);
        double cruiseY = Math.max(runway.y + RouteAutopilot.RETURN_HEIGHT, minSafeY);
        if (!free) {
            double centreX = runway.pointX(runway.length / 2, 0);
            double centreZ = runway.pointZ(runway.length / 2, 0);
            double distance = Math.hypot(centreX - x, centreZ - z);
            double correction = FixedWingFlight.clamp((distance - HOLDING_RADIUS) / HOLDING_RADIUS * RouteAutopilot.ORBIT_CORRECTION_DEG, 70);
            double bank = RouteAutopilot.bankToward(state, RouteAutopilot.bearing(state, centreX, centreZ) - 90 + correction);
            return new RouteAutopilot.Command(RouteAutopilot.controls(state, bank, cruiseY, Route.Speed.MEDIUM.fraction, params, hasFuel),
                    index, RouteAutopilot.Phase.RETURN);
        }
        double headingError = Math.abs(FixedWingFlight.wrap(state.heading - runway.heading));
        boolean inZone = along >= -FINAL_LENGTH * 1.5 && along <= runway.length * TOUCHDOWN_ZONE;
        boolean established = inZone && headingError < ESTABLISHED_HEADING_DEG
                && Math.abs(cross) < ESTABLISHED_CROSS + Math.max(0, -along) * 0.15
                && state.position.y - runway.y <= Math.max(0, -along) * ESTABLISHED_DESCENT + ESTABLISHED_HEIGHT;
        if (established) {
            double heading = runway.heading + FixedWingFlight.clamp(-cross * INTERCEPT_GAIN, FINAL_MAX_CORRECTION_DEG);
            // On the slope the autopilot's height-error pitch alone would sit high: aim under it by the glide angle's worth
            // (feed-forward). Near the threshold the floor takes over: the plane levels off and floats onto the deck.
            double slopeY = -along * GLIDESLOPE;
            double targetY = along >= 0 ? runway.y - FLARE_AIM
                    : slopeY > APPROACH_FLOOR ? runway.y + slopeY - GLIDE_LEAD : runway.y + APPROACH_FLOOR;
            return new RouteAutopilot.Command(RouteAutopilot.controls(state, RouteAutopilot.bankToward(state, heading), targetY,
                    Route.Speed.SLOW.fraction, params, hasFuel), index, RouteAutopilot.Phase.LAND);
        }
        double approachY = Math.max(runway.y + FINAL_LENGTH * GLIDESLOPE, minSafeY);
        // Behind the runway: steer for a point on the centreline a little ahead of where it is (turning round first if
        // it's flying away), which brings it onto the centreline facing the runway. Flying away it keeps going until
        // it's well past the fix, so it has room to turn round and settle before final.
        boolean towardRunway = headingError < 90;
        if (along < (towardRunway ? -INTERCEPT_START : -FINAL_LENGTH - TURN_ROOM)) {
            double aim = Math.min(along + LOOKAHEAD, 0);
            double bank = RouteAutopilot.bankToward(state, RouteAutopilot.bearing(state, runway.pointX(aim, 0), runway.pointZ(aim, 0)));
            double targetY = Math.min(cruiseY, Math.max(approachY, runway.y + -along * GLIDESLOPE));
            return new RouteAutopilot.Command(RouteAutopilot.controls(state, bank, targetY, Route.Speed.MEDIUM.fraction, params, hasFuel),
                    index, RouteAutopilot.Phase.RETURN);
        }
        // Ahead of it, beside it, or too close to line up: out past the fix, coming down to the approach height
        double fixX = runway.pointX(-FINAL_LENGTH - TURN_ROOM - 10, 0);
        double fixZ = runway.pointZ(-FINAL_LENGTH - TURN_ROOM - 10, 0);
        double toFix = Math.hypot(fixX - x, fixZ - z);
        double targetY = toFix > RouteAutopilot.LANDING_RADIUS + FINAL_LENGTH ? cruiseY : approachY;
        double bank = RouteAutopilot.bankToward(state, RouteAutopilot.bearing(state, fixX, fixZ));
        return new RouteAutopilot.Command(RouteAutopilot.controls(state, bank, targetY, Route.Speed.MEDIUM.fraction, params, hasFuel),
                index, RouteAutopilot.Phase.RETURN);
    }

    /** Low, slow and lined up over the touchdown zone: the runway takes it from here (roll-out). */
    public static boolean captured(FixedWingFlight.State state, Route.Runway runway, double maxSpeed) {
        double along = runway.along(state.position.x, state.position.z);
        double height = state.position.y - runway.y;
        return along >= -2 && along <= runway.length * TOUCHDOWN_ZONE
                && Math.abs(runway.cross(state.position.x, state.position.z)) < runway.width / 2 + 1
                && Math.abs(FixedWingFlight.wrap(state.heading - runway.heading)) < CAPTURE_HEADING_DEG
                && height < CAPTURE_HEIGHT && height > -1
                && state.speed < maxSpeed * CAPTURE_SPEED_FRACTION;
    }

    /**
     * One tick of the roll-out: eases onto the centreline, the deck and the runway heading, braking to a stop
     * {@link #ROLL_MARGIN} before the far end. Returns true once stopped.
     */
    public static boolean rollout(FixedWingFlight.State state, Route.Runway runway) {
        double along = runway.along(state.position.x, state.position.z);
        double cross = runway.cross(state.position.x, state.position.z) * 0.7;
        double stopAt = runway.length - ROLL_MARGIN;
        double remaining = stopAt - along;
        if (remaining > 0.05) {
            state.speed = Math.max(0, Math.min(state.speed - state.speed * state.speed / (2 * remaining), remaining));
        } else {
            state.speed = 0;
        }
        along += state.speed;
        state.heading = runway.heading + FixedWingFlight.wrap(state.heading - runway.heading) * 0.6;
        state.bank *= 0.5;
        state.pitch *= 0.5;
        double y = FixedWingFlight.approach(state.position.y, runway.y, 0.3);
        state.velocity.set(runway.pointX(along, cross) - state.position.x, y - state.position.y, runway.pointZ(along, cross) - state.position.z);
        state.position.set(runway.pointX(along, cross), y, runway.pointZ(along, cross));
        return state.speed < 0.01;
    }
}
