package io.github.duckysmacky.combatdrones.physics;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

/**
 * Cruise missile flight, in Minecraft units (blocks, ticks) with no Minecraft dependency: the staged vertical launch
 * and the steered dive. Angles use Minecraft conventions (yaw 0 = +Z, growing clockwise; pitch positive nose-down).
 *
 * <p>The dive is described by a fixed reference heading and two tilts away from straight down: {@code tiltForward}
 * toward the reference heading and {@code tiltRight} to its right, kept inside a circular cone. Steering in tilts
 * instead of yaw/pitch avoids the yaw singularity at straight down, where the missile spends most of its dive.
 */
public final class CruiseMissileFlight {
    /** Launch stages, in order; HOLD (armed over the strike point) and DIVE are the separate strike phase. */
    public enum Stage {
        IGNITION, LIFTOFF, SLOW_CLIMB, FAST_CLIMB, DONE, HOLD, DIVE;

        /** Over the strike point: nose straight down, steered by tilt. */
        public boolean isStrike() {
            return this == HOLD || this == DIVE;
        }
    }

    /** Stage lengths (ticks) and the climb speed (blocks/tick) reached at the end of each moving stage. */
    public static final class Launch {
        final int ignitionTicks;
        final int liftoffTicks;
        final int slowClimbTicks;
        final int fastClimbTicks;
        final double liftoffSpeed;
        final double slowClimbSpeed;
        final double fastClimbSpeed;

        public Launch(int ignitionTicks, int liftoffTicks, int slowClimbTicks, int fastClimbTicks,
                      double liftoffSpeed, double slowClimbSpeed, double fastClimbSpeed) {
            this.ignitionTicks = Math.max(0, ignitionTicks);
            this.liftoffTicks = Math.max(1, liftoffTicks);
            this.slowClimbTicks = Math.max(1, slowClimbTicks);
            this.fastClimbTicks = Math.max(1, fastClimbTicks);
            this.liftoffSpeed = liftoffSpeed;
            this.slowClimbSpeed = slowClimbSpeed;
            this.fastClimbSpeed = fastClimbSpeed;
        }

        public int totalTicks() {
            return ignitionTicks + liftoffTicks + slowClimbTicks + fastClimbTicks;
        }

        /** Stage at {@code elapsed} ticks since ignition started. */
        public Stage stageAt(int elapsed) {
            int end = ignitionTicks;
            if (elapsed < end) {
                return Stage.IGNITION;
            }
            end += liftoffTicks;
            if (elapsed < end) {
                return Stage.LIFTOFF;
            }
            end += slowClimbTicks;
            if (elapsed < end) {
                return Stage.SLOW_CLIMB;
            }
            end += fastClimbTicks;
            return elapsed < end ? Stage.FAST_CLIMB : Stage.DONE;
        }

        /** Ticks left in the stage {@code elapsed} falls in (0 once the climb is done). */
        public int ticksLeftInStage(int elapsed) {
            int end = 0;
            for (int length : new int[] {ignitionTicks, liftoffTicks, slowClimbTicks, fastClimbTicks}) {
                end += length;
                if (elapsed < end) {
                    return end - elapsed;
                }
            }
            return 0;
        }

        /**
         * Climb speed (blocks/tick, up) at {@code elapsed}: still on the pad during ignition, then ramping linearly
         * through the liftoff, slow and fast climb speeds.
         */
        public double climbSpeed(int elapsed) {
            int time = elapsed - ignitionTicks;
            if (time < 0) {
                return 0;
            }
            if (time < liftoffTicks) {
                return lerp(0, liftoffSpeed, time / (double) liftoffTicks);
            }
            time -= liftoffTicks;
            if (time < slowClimbTicks) {
                return lerp(liftoffSpeed, slowClimbSpeed, time / (double) slowClimbTicks);
            }
            time -= slowClimbTicks;
            return lerp(slowClimbSpeed, fastClimbSpeed, Math.min(1, time / (double) fastClimbTicks));
        }

        /** Height gained from ignition to the end of the climb (blocks). */
        public double climbHeight() {
            double height = 0;
            for (int tick = 0; tick < totalTicks(); tick++) {
                height += climbSpeed(tick);
            }
            return height;
        }
    }

    /** Dive tuning: speeds from the drone type, cone and steering from the config. */
    public static final class DiveParams {
        final double maxSpeed;
        final double acceleration;
        final double boostMultiplier;
        final double coneDegrees;
        final double steerRateDegrees;

        public DiveParams(double maxSpeed, double acceleration, double boostMultiplier, double coneDegrees, double steerRateDegrees) {
            this.maxSpeed = maxSpeed;
            this.acceleration = acceleration;
            this.boostMultiplier = boostMultiplier;
            this.coneDegrees = coneDegrees;
            this.steerRateDegrees = steerRateDegrees;
        }
    }

    /** Mutable dive state. {@link #velocity} is the last step's movement. */
    public static final class Dive {
        public double refHeading;
        public double tiltForward;
        public double tiltRight;
        public double speed;
        public final Vector3d velocity = new Vector3d();
    }

    private static final Vector3d BODY_UP = new Vector3d(0, 1, 0);

    private CruiseMissileFlight() {
    }

    /**
     * One dive tick: tilt toward the stick (rates, -1..1, times the steer rate, kept inside the cone), speed toward
     * top or boosted speed, then the velocity along the nose.
     *
     * @param stickForward positive tilts the nose toward the reference heading (screen up in the missile camera)
     * @param stickRight   positive tilts it to the right (screen right)
     */
    public static void step(Dive dive, double stickForward, double stickRight, boolean boost, DiveParams params) {
        steer(dive, stickForward, stickRight, params);
        double target = params.maxSpeed * (boost ? params.boostMultiplier : 1);
        double acceleration = params.acceleration * (boost ? params.boostMultiplier : 1);
        dive.speed = dive.speed < target ? Math.min(target, dive.speed + acceleration) : Math.max(target, dive.speed - acceleration);
        Vector3d direction = direction(dive);
        dive.velocity.set(direction.x * dive.speed, direction.y * dive.speed, direction.z * dive.speed);
    }

    /** Tilt only (the hold before the dive): toward the stick, kept inside the cone. */
    public static void steer(Dive dive, double stickForward, double stickRight, DiveParams params) {
        dive.tiltForward += clamp(stickForward) * params.steerRateDegrees;
        dive.tiltRight += clamp(stickRight) * params.steerRateDegrees;
        double tilt = Math.hypot(dive.tiltForward, dive.tiltRight);
        if (tilt > params.coneDegrees) {
            double scale = params.coneDegrees / tilt;
            dive.tiltForward *= scale;
            dive.tiltRight *= scale;
        }
    }

    /** Unit vector along the nose. */
    public static Vector3d direction(Dive dive) {
        return HandFlownFlight.forwardOf(orientation(dive.refHeading, dive.tiltForward, dive.tiltRight));
    }

    /**
     * Airframe orientation: nose straight down with the reference heading as "up" in its camera, then tilted. The
     * forward tilt raises the nose toward the heading (pitch); the right tilt swings it about the body's up axis,
     * which at straight down is a sideways swing, not a spin.
     */
    public static Quat4d orientation(double refHeading, double tiltForward, double tiltRight) {
        Quat4d result = HandFlownFlight.fromEuler(refHeading, 90 - tiltForward, 0);
        Quat4d swing = new Quat4d();
        swing.set(new AxisAngle4d(BODY_UP, Math.toRadians(-tiltRight)));
        result.mul(swing);
        return result;
    }

    /** Share of a full standby tank left at {@code now}: full until {@code readyTick}, empty at {@code standbyEndTick}. */
    public static double standbyFuelFraction(long now, long readyTick, long standbyEndTick) {
        if (standbyEndTick <= readyTick) {
            return 0;
        }
        return Math.max(0, Math.min(1, (standbyEndTick - Math.max(now, readyTick)) / (double) (standbyEndTick - readyTick)));
    }

    /** Heading (Minecraft yaw, degrees) from one point to another; 0 when they coincide. */
    public static double headingTo(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return dx == 0 && dz == 0 ? 0 : Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static double lerp(double from, double to, double alpha) {
        return from + (to - from) * alpha;
    }

    private static double clamp(double value) {
        return Double.isNaN(value) ? 0 : Math.max(-1, Math.min(1, value));
    }
}
