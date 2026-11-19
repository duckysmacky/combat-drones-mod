package io.github.duckysmacky.combatdrones.physics;

/**
 * Planes on a runway: the takeoff roll, and (with {@link RouteAutopilot}) the approach and the roll-out after
 * landing. Pure; positions are in blocks, along-track distances measured from the runway's threshold (its tail end).
 */
public final class RunwayOps {
    /** Lift-off speed as a share of top speed: clear of the stall (0.4) with room to climb. */
    static final double ROTATION_FRACTION = 0.5;
    /** Engine spool-up on the spot before the roll starts. */
    static final int SPOOL_TICKS = 20;
    /** The roll reaches lift-off speed this far before the far end. */
    static final double ROLL_MARGIN = 1.0;
    /** Height over the deck a plane climbs out to, straight ahead, before it turns onto its route. */
    public static final double CLIMB_OUT_HEIGHT = 8.0;
    /** Pitch it lifts off with (nose up). */
    static final double LIFTOFF_PITCH = -8.0;

    private RunwayOps() {
    }

    /** Along-track spot a parked plane sits on and starts its roll from: far enough in for a long fuselage. */
    public static double seat(Route.Runway runway) {
        return runway.length * 0.25;
    }

    /**
     * One tick of the takeoff run, {@code ticks} after the launch: spool up on the seat, then roll down the
     * centreline at constant acceleration, sized so the plane reaches lift-off speed {@link #ROLL_MARGIN} before the
     * far end whatever its airframe. Returns true on the tick it lifts off (state set up to fly on from there).
     */
    public static boolean takeoffRoll(FixedWingFlight.State state, Route.Runway runway, double maxSpeed, int ticks) {
        double rotation = maxSpeed * ROTATION_FRACTION;
        double rollLength = Math.max(1, runway.length - ROLL_MARGIN - seat(runway));
        if (ticks >= SPOOL_TICKS) {
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
}
