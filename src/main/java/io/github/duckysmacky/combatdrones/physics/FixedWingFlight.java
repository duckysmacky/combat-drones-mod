package io.github.duckysmacky.combatdrones.physics;

import javax.vecmath.Vector3d;

/**
 * Kinematic fixed-wing model for ramp-launched units (loitering munitions, gunship planes, recon UAV), in Minecraft
 * units (blocks, ticks) with no Minecraft dependency. Controlled through target bank/pitch and throttle, which the
 * route autopilot sets now and a manual pilot will set in M6. Angles use Minecraft conventions: heading is vanilla
 * yaw (0 = +Z, grows clockwise), pitch is positive nose-down, bank is positive right-wing-down.
 */
public final class FixedWingFlight {
    static final double MAX_BANK_DEG = 45.0;
    static final double ROLL_RATE_DEG = 4.0;
    /** Nose-up limit. */
    static final double MAX_PITCH_DEG = 30.0;
    /** Nose-down limit: steeper than the climb, so strikes can dive onto a point. */
    static final double MAX_DIVE_PITCH_DEG = 60.0;
    static final double PITCH_RATE_DEG = 2.0;
    static final double MAX_TURN_RATE_DEG = 4.0;
    /** Coordinated-turn "gravity" (blocks/tick²): turn rate = TURN_GRAVITY · tan(bank) / speed. */
    static final double TURN_GRAVITY = 0.04;
    /** Gravity along the flight path (vanilla gravity): diving speeds the plane up, climbing slows it down. */
    static final double PATH_GRAVITY = 0.08;
    /** Below this share of top speed the wing stalls: the nose drops and the plane sinks. */
    static final double STALL_FRACTION = 0.4;
    /** Nose-down pitch that keeps an unpowered plane above stall speed. */
    public static final double GLIDE_PITCH_DEG = 15.0;
    static final double STALL_SINK = 0.5;
    /** Dives may exceed top speed by this factor. */
    static final double DIVE_OVERSPEED = 1.5;

    /** Per-type tuning, taken from the drone type data. */
    public static final class Params {
        final double maxSpeed;
        final double acceleration;
        final double drag;
        /** Multiplies roll, pitch and turn rates: rocket-like munitions turn far tighter than planes. */
        final double agility;

        public Params(double maxSpeed, double acceleration) {
            this(maxSpeed, acceleration, 1.0);
        }

        /**
         * @param maxSpeed     level-flight top speed at full throttle, blocks/tick
         * @param acceleration blocks/tick² of thrust at full throttle
         * @param agility      1 = plane-like; higher rolls, pitches and turns faster
         */
        public Params(double maxSpeed, double acceleration, double agility) {
            this.maxSpeed = maxSpeed;
            this.acceleration = acceleration;
            this.agility = agility;
            // Quadratic drag sized so full throttle in level flight settles exactly at maxSpeed; low at low speed, so
            // an unpowered glide keeps flying instead of stalling out
            this.drag = acceleration / (maxSpeed * maxSpeed);
        }

        /** Boosted engine: top speed times {@code multiplier} (thrust scaled to match, so drag balances there). */
        public Params boosted(double multiplier) {
            return new Params(maxSpeed * multiplier, acceleration * multiplier * multiplier, agility);
        }

        public double maxSpeed() {
            return maxSpeed;
        }

        public double stallSpeed() {
            return maxSpeed * STALL_FRACTION;
        }
    }

    public static final class Controls {
        public final double targetBank;
        public final double targetPitch;
        public final double throttle;
        public final boolean engineOn;

        public Controls(double targetBank, double targetPitch, double throttle, boolean engineOn) {
            this.targetBank = targetBank;
            this.targetPitch = targetPitch;
            this.throttle = throttle;
            this.engineOn = engineOn;
        }
    }

    /** Mutable flight state. {@link #velocity} is the last step's movement, for collision handling. */
    public static final class State {
        public final Vector3d position = new Vector3d();
        public final Vector3d velocity = new Vector3d();
        public double heading;
        public double pitch;
        public double bank;
        public double speed;
    }

    private FixedWingFlight() {
    }

    /**
     * Manual flight, angle-style: the sticks set the attitude to hold (full deflection = maximum bank or pitch) and
     * the plane levels itself when they're released.
     *
     * @param pitchStick -1..1, positive = nose down
     * @param rollStick  -1..1, positive = bank right
     * @param throttle   0..1
     */
    public static Controls fromStick(double pitchStick, double rollStick, double throttle, boolean hasFuel) {
        double pitch = clamp(pitchStick, 1);
        return new Controls(clamp(rollStick, 1) * MAX_BANK_DEG, pitch * (pitch > 0 ? MAX_DIVE_PITCH_DEG : MAX_PITCH_DEG),
                hasFuel ? Math.max(0, Math.min(1, throttle)) : 0, hasFuel);
    }

    /**
     * Manual flight, free (acro-like): the sticks set roll and pitch rates (full deflection = the airframe's maximum
     * rate) and the attitude simply stays put when they're released. Bank and pitch limits still apply.
     */
    public static Controls fromRates(State state, Params params, double pitchStick, double rollStick, double throttle, boolean hasFuel) {
        return new Controls(state.bank + clamp(rollStick, 1) * ROLL_RATE_DEG * params.agility,
                state.pitch + clamp(pitchStick, 1) * PITCH_RATE_DEG * params.agility,
                hasFuel ? Math.max(0, Math.min(1, throttle)) : 0, hasFuel);
    }

    /** Advances one tick. Returns the thrust used as a fraction of full throttle (fuel drain, engine sound). */
    public static double step(State state, Controls controls, Params params) {
        state.bank = clamp(approach(state.bank, controls.targetBank, ROLL_RATE_DEG * params.agility), MAX_BANK_DEG);

        double targetPitch = controls.targetPitch;
        boolean stalled = state.speed < params.stallSpeed();
        if (stalled) {
            targetPitch = Math.max(targetPitch, 15.0); // a stalled wing drops the nose to regain speed
        }
        state.pitch = Math.max(-MAX_PITCH_DEG, Math.min(MAX_DIVE_PITCH_DEG, approach(state.pitch, targetPitch, PITCH_RATE_DEG * params.agility)));

        double turnRate = Math.toDegrees(TURN_GRAVITY * params.agility * Math.tan(Math.toRadians(state.bank)) / Math.max(state.speed, 0.1));
        state.heading = wrap(state.heading + clamp(turnRate, MAX_TURN_RATE_DEG * params.agility));

        double throttle = controls.engineOn ? Math.max(0, Math.min(1, controls.throttle)) : 0;
        double pitchRadians = Math.toRadians(state.pitch);
        state.speed += throttle * params.acceleration - params.drag * state.speed * state.speed + PATH_GRAVITY * Math.sin(pitchRadians);
        state.speed = Math.max(0, Math.min(state.speed, params.maxSpeed * DIVE_OVERSPEED));

        double headingRadians = Math.toRadians(state.heading);
        double horizontal = Math.cos(pitchRadians) * state.speed;
        state.velocity.set(-Math.sin(headingRadians) * horizontal, -Math.sin(pitchRadians) * state.speed, Math.cos(headingRadians) * horizontal);
        if (stalled) {
            state.velocity.y -= (params.stallSpeed() - state.speed) * STALL_SINK;
        }
        state.position.add(state.velocity);
        return throttle;
    }

    static double approach(double current, double target, double maxStep) {
        return current + Math.max(-maxStep, Math.min(maxStep, target - current));
    }

    static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    /** Wraps an angle to [-180, 180). */
    public static double wrap(double degrees) {
        double wrapped = degrees % 360;
        if (wrapped >= 180) {
            wrapped -= 360;
        } else if (wrapped < -180) {
            wrapped += 360;
        }
        return wrapped;
    }
}
