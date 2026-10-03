package io.github.duckysmacky.combatdrones.physics;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

/**
 * Quadcopter flight model for hand-flown drones, in Minecraft units (blocks, ticks) but with no Minecraft
 * dependency. One {@link #step} = one game tick. Shared by the server (authoritative) and the pilot's client
 * (prediction), so both must call it with identical inputs to stay in sync.
 *
 * <p>Body frame at identity orientation: forward = +Z, up = +Y, right = -X (Minecraft faces +Z at yaw 0).
 */
public final class HandFlownFlight {
    public enum Mode { ACRO, ANGLE, HOVER }

    /** Matches vanilla entity gravity, so drones fall like everything else. */
    public static final double GRAVITY = 0.08;
    // ponytail: feel constants are global; promote them to drone type JSON if types need distinct handling
    static final double ACRO_RATE_DEG = 10.0;
    static final double YAW_RATE_DEG = 6.0;
    static final double MAX_TILT_DEG = 45.0;
    /** Fraction of the remaining attitude error closed per tick in ANGLE/HOVER. */
    static final double LEVEL_RATE = 0.25;
    static final double HOVER_MAX_CLIMB = 0.3;
    static final double HOVER_CLIMB_GAIN = 0.5;
    /** Fraction of horizontal velocity removed per tick in HOVER ("position hold"). */
    static final double HOVER_BRAKE = 0.1;

    private static final Vector3d FORWARD = new Vector3d(0, 0, 1);
    private static final Vector3d UP = new Vector3d(0, 1, 0);
    private static final Vector3d RIGHT = new Vector3d(-1, 0, 0);

    /** Per-type tuning, taken from the drone type data. */
    public static final class Params {
        final double maxSpeed;
        final double thrustAccel;
        final double drag;

        /**
         * @param maxSpeed     blocks/tick, hard cap
         * @param acceleration blocks/tick² of climb at full throttle, on top of what cancels gravity
         */
        public Params(double maxSpeed, double acceleration) {
            this.maxSpeed = maxSpeed;
            this.thrustAccel = GRAVITY + acceleration;
            // Linear drag sized so full thrust can't push past maxSpeed even before the hard clamp
            this.drag = thrustAccel / maxSpeed;
        }
    }

    /** Pilot intent for one tick. Axes are already clamped by whoever builds it. */
    public static final class Input {
        public static final Input IDLE = new Input(0, 0, 0, 0, 0, Mode.ANGLE, false);

        /** 0..1, held level (ACRO/ANGLE). */
        public final double throttle;
        /** -1..1, momentary climb command (HOVER only). */
        public final double vertical;
        /** -1..1, positive = nose down. */
        public final double pitch;
        /** -1..1, positive = roll right. */
        public final double roll;
        /** -1..1, positive = yaw right. */
        public final double yaw;
        public final Mode mode;
        public final boolean motorsOn;

        public Input(double throttle, double vertical, double pitch, double roll, double yaw, Mode mode, boolean motorsOn) {
            this.throttle = throttle;
            this.vertical = vertical;
            this.pitch = pitch;
            this.roll = roll;
            this.yaw = yaw;
            this.mode = mode;
            this.motorsOn = motorsOn;
        }
    }

    /** Mutable flight state. Copy before stepping when you need the old value. */
    public static final class State {
        public final Vector3d position = new Vector3d();
        public final Vector3d velocity = new Vector3d();
        public final Quat4d orientation = new Quat4d(0, 0, 0, 1);

        public State copy() {
            State copy = new State();
            copy.position.set(position);
            copy.velocity.set(velocity);
            copy.orientation.set(orientation);
            return copy;
        }
    }

    private HandFlownFlight() {
    }

    /** Advances {@code state} by one tick. Block collisions are applied afterwards by the caller. */
    public static void step(State state, Input input, Params params) {
        if (input.motorsOn) {
            updateAttitude(state.orientation, input);
        }

        Vector3d up = rotate(state.orientation, UP);
        Vector3d acceleration = new Vector3d(0, -GRAVITY, 0);
        if (input.motorsOn) {
            double thrust = input.mode == Mode.HOVER
                    ? hoverThrust(state.velocity.y, up.y, input.vertical, params)
                    : input.throttle * params.thrustAccel;
            up.scale(thrust);
            acceleration.add(up);
        }

        Vector3d velocity = state.velocity;
        velocity.add(acceleration);
        velocity.scaleAdd(-params.drag, velocity, velocity);
        if (input.motorsOn && input.mode == Mode.HOVER) {
            velocity.x *= 1 - HOVER_BRAKE;
            velocity.z *= 1 - HOVER_BRAKE;
        }
        double speed = velocity.length();
        if (speed > params.maxSpeed) {
            velocity.scale(params.maxSpeed / speed);
        }
        state.position.add(velocity);
    }

    private static void updateAttitude(Quat4d orientation, Input input) {
        if (input.mode == Mode.ACRO) {
            rotateBody(orientation, RIGHT, -input.pitch * ACRO_RATE_DEG);
            rotateBody(orientation, FORWARD, input.roll * ACRO_RATE_DEG);
            rotateBody(orientation, UP, -input.yaw * YAW_RATE_DEG);
            orientation.normalize();
            return;
        }
        // ANGLE/HOVER: sticks command a tilt relative to the current heading; ease toward it
        Quat4d target = headingOf(orientation);
        rotateBody(target, UP, -input.yaw * YAW_RATE_DEG);
        rotateBody(target, RIGHT, -input.pitch * MAX_TILT_DEG);
        rotateBody(target, FORWARD, input.roll * MAX_TILT_DEG);
        orientation.set(slerp(orientation, target, LEVEL_RATE));
        orientation.normalize();
    }

    /** Spherical interpolation that always takes the short way round. Inputs are not modified. */
    public static Quat4d slerp(Quat4d from, Quat4d to, double alpha) {
        Quat4d target = new Quat4d(to);
        if (from.x * to.x + from.y * to.y + from.z * to.z + from.w * to.w < 0) {
            target.negate();
        }
        Quat4d result = new Quat4d();
        result.interpolate(from, target, alpha);
        return result;
    }

    /** Vertical-speed controller: thrust that drives vertical speed toward the commanded climb rate. */
    private static double hoverThrust(double verticalSpeed, double upY, double vertical, Params params) {
        double targetSpeed = vertical * HOVER_MAX_CLIMB;
        double wantedAccel = GRAVITY + params.drag * verticalSpeed + HOVER_CLIMB_GAIN * (targetSpeed - verticalSpeed);
        // Tilt compensation: only the vertical component of thrust fights gravity
        double thrust = wantedAccel / Math.max(upY, 0.2);
        return Math.max(0, Math.min(thrust, params.thrustAccel));
    }

    /** Yaw-only rotation matching the horizontal direction of the body's forward axis. */
    static Quat4d headingOf(Quat4d orientation) {
        Vector3d forward = rotate(orientation, FORWARD);
        // Rotating +Z about +Y by θ gives (sin θ, 0, cos θ). atan2(0, 0) = 0 covers straight up/down
        double heading = Math.atan2(forward.x, forward.z);
        Quat4d result = new Quat4d();
        result.set(new AxisAngle4d(0, 1, 0, heading));
        return result;
    }

    private static void rotateBody(Quat4d orientation, Vector3d bodyAxis, double degrees) {
        if (degrees == 0) {
            return;
        }
        Quat4d delta = new Quat4d();
        delta.set(new AxisAngle4d(bodyAxis, Math.toRadians(degrees)));
        orientation.mul(delta);
    }

    /**
     * Minecraft-convention Euler angles in degrees: {yaw, pitch, roll}. Yaw grows clockwise seen from above
     * (vanilla {@code rotationYaw}), pitch is positive nose-down (vanilla {@code rotationPitch}), roll is
     * positive right-wing-down.
     */
    public static double[] toEuler(Quat4d orientation) {
        Vector3d forward = rotate(orientation, FORWARD);
        double yaw = Math.toDegrees(Math.atan2(-forward.x, forward.z));
        double pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -forward.y))));
        // Whatever is left after undoing yaw and pitch is a pure rotation about the forward axis
        Quat4d rollOnly = fromEuler(yaw, pitch, 0);
        rollOnly.inverse();
        rollOnly.mul(orientation);
        double roll = Math.toDegrees(2 * Math.atan2(rollOnly.z, rollOnly.w));
        return new double[] {yaw, pitch, wrapDegrees(roll)};
    }

    /** Inverse of {@link #toEuler}. */
    public static Quat4d fromEuler(double yaw, double pitch, double roll) {
        Quat4d result = new Quat4d(0, 0, 0, 1);
        rotateBody(result, UP, -yaw);
        rotateBody(result, RIGHT, -pitch);
        rotateBody(result, FORWARD, roll);
        return result;
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360;
        if (wrapped >= 180) {
            wrapped -= 360;
        } else if (wrapped < -180) {
            wrapped += 360;
        }
        return wrapped;
    }

    public static Vector3d rotate(Quat4d orientation, Vector3d vector) {
        Matrix3d matrix = new Matrix3d();
        matrix.set(orientation);
        Vector3d result = new Vector3d(vector);
        matrix.transform(result);
        return result;
    }

    public static Vector3d forwardOf(Quat4d orientation) {
        return rotate(orientation, FORWARD);
    }

    public static Vector3d upOf(Quat4d orientation) {
        return rotate(orientation, UP);
    }
}
