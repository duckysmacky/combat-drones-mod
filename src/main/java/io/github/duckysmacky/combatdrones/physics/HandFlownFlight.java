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
    /**
     * ACRO/ANGLE/HOVER: FPV quad modes, cycled by the pilot. VELOCITY: DJI-style "GPS" flight, where sticks command
     * a horizontal velocity relative to the heading and releasing them holds position.
     */
    public enum Mode { ACRO, ANGLE, HOVER, VELOCITY }

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
    /** VELOCITY: how far the airframe visibly leans into its acceleration. Cosmetic; the physics is GPS-assisted. */
    static final double VELOCITY_MAX_TILT_DEG = 25.0;
    /** VELOCITY: yaw rate at full input. High because the mouse maps 1:1 onto it (like vanilla mouse look). */
    static final double VELOCITY_YAW_RATE_DEG = 30.0;
    /** VELOCITY: speeding up uses this share of the type's acceleration; braking uses all of it (quick stops). */
    static final double VELOCITY_ACCEL_FRACTION = 0.35;

    private static final Vector3d FORWARD = new Vector3d(0, 0, 1);
    private static final Vector3d UP = new Vector3d(0, 1, 0);
    private static final Vector3d RIGHT = new Vector3d(-1, 0, 0);

    /** Per-type tuning, taken from the drone type data. */
    public static final class Params {
        final double maxSpeed;
        final double acceleration;
        final double thrustAccel;
        final double drag;

        /**
         * @param maxSpeed     blocks/tick, hard cap
         * @param acceleration blocks/tick² of climb at full throttle, on top of what cancels gravity
         */
        public Params(double maxSpeed, double acceleration) {
            this.maxSpeed = maxSpeed;
            this.acceleration = acceleration;
            this.thrustAccel = GRAVITY + acceleration;
            // Linear drag sized so full thrust can't push past maxSpeed even before the hard clamp
            this.drag = thrustAccel / maxSpeed;
        }
    }

    /** Pilot intent for one tick. Axes are already clamped by whoever builds it. */
    public static final class Input {
        public static final Input MOTORS_OFF = new Input(0, 0, 0, 0, 0, Mode.ANGLE, false);
        /** Neutral sticks in HOVER: what an unattended drone with running motors flies (position/altitude hold). */
        public static final Input HOLD_POSITION = new Input(0, 0, 0, 0, 0, Mode.HOVER, true);
        /** Neutral sticks in VELOCITY: an unattended DJI-style drone brakes and holds position. */
        public static final Input VELOCITY_HOLD = new Input(0, 0, 0, 0, 0, Mode.VELOCITY, true);

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

    /**
     * Advances {@code state} by one tick. Block collisions are applied afterwards by the caller.
     *
     * @return thrust used this tick as a fraction of maximum (0..1), which drives battery drain and motor sound
     */
    public static double step(State state, Input input, Params params) {
        if (input.motorsOn && input.mode == Mode.VELOCITY) {
            return stepVelocity(state, input, params);
        }
        if (input.motorsOn) {
            updateAttitude(state.orientation, input);
        }

        Vector3d up = rotate(state.orientation, UP);
        Vector3d acceleration = new Vector3d(0, -GRAVITY, 0);
        double thrust = 0;
        if (input.motorsOn) {
            thrust = input.mode == Mode.HOVER
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
        return thrust / params.thrustAccel;
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

    /**
     * DJI-style flight. Horizontal velocity tracks the stick command (rotated by heading) at the type's acceleration,
     * like a GPS-stabilized consumer drone; altitude uses the hover climb controller pushing straight up. The tilt is
     * only visual, leaning into the acceleration so the drone and its non-gimballed parts look right.
     */
    private static double stepVelocity(State state, Input input, Params params) {
        // Heading is kept as a pure yaw (twist about world up), separate from the lean, so leaning into a strafe can
        // never turn the drone or the camera that follows its heading
        Quat4d heading = headingTwist(state.orientation);
        if (input.yaw != 0) {
            Quat4d yaw = new Quat4d();
            yaw.set(new AxisAngle4d(UP, Math.toRadians(-input.yaw * VELOCITY_YAW_RATE_DEG)));
            yaw.mul(heading);
            heading = yaw;
            heading.normalize();
        }
        Vector3d forward = rotate(heading, FORWARD);
        Vector3d right = rotate(heading, RIGHT);

        // Commanded horizontal velocity; diagonal input isn't faster than straight
        double forwardCommand = input.pitch;
        double rightCommand = input.roll;
        double commandLength = Math.hypot(forwardCommand, rightCommand);
        if (commandLength > 1) {
            forwardCommand /= commandLength;
            rightCommand /= commandLength;
        }
        double wantedX = (forward.x * forwardCommand + right.x * rightCommand) * params.maxSpeed;
        double wantedZ = (forward.z * forwardCommand + right.z * rightCommand) * params.maxSpeed;

        Vector3d velocity = state.velocity;
        double rawDeltaX = wantedX - velocity.x;
        double rawDeltaZ = wantedZ - velocity.z;
        double effort = Math.min(1, Math.hypot(rawDeltaX, rawDeltaZ) / params.acceleration);
        // Split the change into slowing down (along current motion, fast brake) and everything else (gentle build-up)
        double speed = Math.hypot(velocity.x, velocity.z);
        double alongX = speed > 1e-9 ? velocity.x / speed : 0;
        double alongZ = speed > 1e-9 ? velocity.z / speed : 0;
        double parallel = rawDeltaX * alongX + rawDeltaZ * alongZ;
        double perpendicularX = rawDeltaX - parallel * alongX;
        double perpendicularZ = rawDeltaZ - parallel * alongZ;
        double gain = params.acceleration * VELOCITY_ACCEL_FRACTION;
        parallel = parallel < 0 ? Math.max(parallel, -params.acceleration) : Math.min(parallel, gain);
        double perpendicularLength = Math.hypot(perpendicularX, perpendicularZ);
        if (perpendicularLength > gain) {
            perpendicularX *= gain / perpendicularLength;
            perpendicularZ *= gain / perpendicularLength;
        }
        double deltaX = parallel * alongX + perpendicularX;
        double deltaZ = parallel * alongZ + perpendicularZ;
        double deltaLength = Math.hypot(deltaX, deltaZ);
        velocity.x += deltaX;
        velocity.z += deltaZ;

        double lift = Math.max(0, Math.min(climbAccel(velocity.y, input.vertical, params), params.thrustAccel));
        velocity.y += lift - GRAVITY;
        velocity.y -= params.drag * velocity.y;
        double totalSpeed = velocity.length();
        if (totalSpeed > params.maxSpeed) {
            velocity.scale(params.maxSpeed / totalSpeed);
        }
        state.position.add(velocity);

        // Visual lean into the acceleration: a swing about the horizontal axis perpendicular to it (tips the top toward
        // the acceleration), eased on its own and recombined with the exact heading
        Quat4d targetSwing = new Quat4d(0, 0, 0, 1);
        if (deltaLength > 1e-9) {
            Vector3d axis = new Vector3d(deltaZ / deltaLength, 0, -deltaX / deltaLength); // UP x direction
            targetSwing.set(new AxisAngle4d(axis, Math.toRadians(effort * VELOCITY_MAX_TILT_DEG)));
        }
        Quat4d currentSwing = new Quat4d(state.orientation);
        Quat4d inverseTwist = headingTwist(state.orientation);
        inverseTwist.inverse();
        currentSwing.mul(inverseTwist);
        Quat4d swing = slerp(currentSwing, targetSwing, LEVEL_RATE);
        swing.mul(heading);
        state.orientation.set(swing);
        state.orientation.normalize();

        return Math.min(1, (lift + effort * params.acceleration) / params.thrustAccel);
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
        // Tilt compensation: only the vertical component of thrust fights gravity
        double thrust = climbAccel(verticalSpeed, vertical, params) / Math.max(upY, 0.2);
        return Math.max(0, Math.min(thrust, params.thrustAccel));
    }

    /** Upward acceleration that drives vertical speed toward the commanded climb rate (unclamped). */
    private static double climbAccel(double verticalSpeed, double vertical, Params params) {
        double targetSpeed = vertical * HOVER_MAX_CLIMB;
        return GRAVITY + params.drag * verticalSpeed + HOVER_CLIMB_GAIN * (targetSpeed - verticalSpeed);
    }

    /**
     * The heading part of an orientation: its twist about world up, in the decomposition orientation = swing * twist
     * where the swing tilts about a horizontal axis. Unlike {@link #headingOf}, a combined pitch + roll lean doesn't
     * leak into it.
     */
    static Quat4d headingTwist(Quat4d orientation) {
        Quat4d twist = new Quat4d(0, orientation.y, 0, orientation.w);
        if (Math.abs(twist.y) < 1e-12 && Math.abs(twist.w) < 1e-12) {
            return new Quat4d(0, 0, 0, 1); // flipped exactly upside down: heading undefined
        }
        twist.normalize();
        return twist;
    }

    /** Minecraft yaw (degrees) of {@link #headingTwist}: a stable heading for cameras and compasses. */
    public static double headingYaw(Quat4d orientation) {
        Quat4d twist = headingTwist(orientation);
        return wrapDegrees(-Math.toDegrees(2 * Math.atan2(twist.y, twist.w)));
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
