package io.github.duckysmacky.combatdrones.physics;

/**
 * Homing for a guided missile flying at a constant speed: each tick it turns toward where its target will be when it
 * gets there (the target's position led by its velocity), by no more than its turn rate.
 */
public final class MissileGuidance {
    private MissileGuidance() {
    }

    /**
     * The missile's velocity for the next tick.
     *
     * @param velocity     current velocity {x, y, z} (its direction is the heading; zero = pointed straight at the aim point)
     * @param toTarget     target position minus missile position
     * @param targetMotion target velocity, blocks/tick
     * @param speed        missile speed, blocks/tick
     * @param maxTurn      most it can turn this tick, degrees
     */
    public static double[] steer(double[] velocity, double[] toTarget, double[] targetMotion, double speed, double maxTurn) {
        double distance = length(toTarget);
        // First-order lead: where the target is after the time the missile needs to cover today's distance
        double lead = speed > 0 ? distance / speed : 0;
        double[] aim = normalize(new double[] {toTarget[0] + targetMotion[0] * lead, toTarget[1] + targetMotion[1] * lead,
                toTarget[2] + targetMotion[2] * lead});
        double[] heading = normalize(velocity);
        if (aim == null) {
            aim = heading;
        }
        if (heading == null) {
            heading = aim;
        }
        if (heading == null) {
            return new double[] {0, 0, 0};
        }
        double angle = Math.acos(Math.max(-1, Math.min(1, heading[0] * aim[0] + heading[1] * aim[1] + heading[2] * aim[2])));
        double limit = Math.toRadians(maxTurn);
        double[] direction = aim;
        if (angle > limit) {
            // Part of the aim direction square to the heading: the way to turn (anything square to it when dead astern)
            double along = heading[0] * aim[0] + heading[1] * aim[1] + heading[2] * aim[2];
            double[] side = normalize(new double[] {aim[0] - heading[0] * along, aim[1] - heading[1] * along, aim[2] - heading[2] * along});
            if (side == null) {
                side = Math.abs(heading[1]) < 0.9 ? normalize(new double[] {-heading[2], 0, heading[0]}) : new double[] {1, 0, 0};
            }
            double cos = Math.cos(limit);
            double sin = Math.sin(limit);
            direction = new double[] {heading[0] * cos + side[0] * sin, heading[1] * cos + side[1] * sin, heading[2] * cos + side[2] * sin};
        }
        return new double[] {direction[0] * speed, direction[1] * speed, direction[2] * speed};
    }

    private static double length(double[] vector) {
        return Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
    }

    /** Unit vector, or null for (nearly) zero. */
    private static double[] normalize(double[] vector) {
        double length = length(vector);
        return length < 1.0E-9 ? null : new double[] {vector[0] / length, vector[1] / length, vector[2] / length};
    }
}
