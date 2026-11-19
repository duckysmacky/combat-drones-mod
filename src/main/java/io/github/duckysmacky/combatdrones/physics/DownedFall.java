package io.github.duckysmacky.combatdrones.physics;

import javax.vecmath.Vector3d;

/**
 * An uncontrolled fall: a vehicle that was shot down or knocked out by an EMP. Forward speed bleeds away, gravity
 * pulls it into an arc, and a shot-down plane rolls over and slowly spirals. Pure math in blocks and ticks, shared by
 * ramp units and hand-flown drones (and the pilot's client prediction).
 */
public final class DownedFall {
    private DownedFall() {
    }

    /** Per-tick fall settings. */
    public static final class Params {
        /** Share of horizontal speed kept per tick. */
        public final double keep;
        /** Downward acceleration, blocks/tick^2. */
        public final double gravity;
        /** Fastest fall, blocks/tick. */
        public final double maxFall;
        /** Roll, degrees per tick (0 = none). */
        public final double rollRate;
        /** Turn of the flight path, degrees per tick (0 = straight on). */
        public final double turnRate;

        public Params(double keep, double gravity, double maxFall, double rollRate, double turnRate) {
            this.keep = keep;
            this.gravity = gravity;
            this.maxFall = maxFall;
            this.rollRate = rollRate;
            this.turnRate = turnRate;
        }

        /**
         * From config units: {@code drag} is the share of horizontal speed lost per second, the rest per second
         * (blocks/s^2, blocks/s, degrees/s).
         */
        public static Params perSecond(double drag, double fallAcceleration, double maxFallSpeed, double rollDegrees, double turnDegrees) {
            return new Params(Math.pow(1 - Math.min(Math.max(drag, 0), 0.999), 1 / 20.0), fallAcceleration / 400, maxFallSpeed / 20,
                    rollDegrees / 20, turnDegrees / 20);
        }
    }

    /** What one tick of falling did to the attitude. */
    public static final class Attitude {
        /** Direction of travel over the ground, degrees (0 = +Z, clockwise), as {@code FixedWingFlight}. */
        public double heading;
        /** Nose-down positive, following the flight path. */
        public double pitch;
        public double bank;
        /** Speed along the path, blocks/tick. */
        public double speed;
    }

    /**
     * Advances {@code velocity} (blocks/tick, changed in place) by one tick and works out the attitude that goes with it.
     * {@code heading} and {@code bank} are the current ones: a vehicle falling straight down keeps its heading.
     */
    public static Attitude step(Vector3d velocity, double heading, double bank, Params params) {
        double turn = Math.toRadians(params.turnRate);
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        // Clockwise as seen from above, like a positive heading change
        double x = (velocity.x * cos - velocity.z * sin) * params.keep;
        double z = (velocity.x * sin + velocity.z * cos) * params.keep;
        velocity.x = x;
        velocity.z = z;
        velocity.y = Math.max(velocity.y - params.gravity, -params.maxFall);

        Attitude attitude = new Attitude();
        double across = Math.sqrt(x * x + z * z);
        attitude.heading = across > 1e-4 ? Math.toDegrees(Math.atan2(-x, z)) : heading;
        attitude.pitch = Math.toDegrees(Math.atan2(-velocity.y, across));
        attitude.bank = params.rollRate == 0 ? bank * 0.9 : wrap(bank + params.rollRate);
        attitude.speed = Math.sqrt(across * across + velocity.y * velocity.y);
        return attitude;
    }

    static double wrap(double degrees) {
        double wrapped = degrees % 360;
        if (wrapped >= 180) {
            wrapped -= 360;
        } else if (wrapped < -180) {
            wrapped += 360;
        }
        return wrapped;
    }
}
