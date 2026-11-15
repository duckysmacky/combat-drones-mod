package io.github.duckysmacky.combatdrones.physics;

/**
 * How a free-falling bomb travels after release: the same per-tick steps the bomb entity takes (gravity, move, drag),
 * so the planner's landing estimate and the bombsight agree with what actually falls.
 */
public final class BombBallistics {
    /** Blocks/tick^2. */
    public static final double GRAVITY = 0.04;
    /** Share of velocity kept each tick. */
    public static final double DRAG = 0.98;
    /** A bomb goes off by itself after this many ticks. */
    public static final int FUSE_TICKS = 200;

    /** Where a bomb comes down: how far ahead of the release point, and after how many ticks. */
    public static final class Fall {
        public final double forward;
        public final int ticks;

        Fall(double forward, int ticks) {
            this.forward = forward;
            this.ticks = ticks;
        }
    }

    private BombBallistics() {
    }

    /**
     * A bomb released {@code height} blocks above the ground, moving {@code forwardSpeed} along the ground and
     * {@code verticalSpeed} upward (blocks/tick). A bomb still in the air when its fuse runs out stops there.
     */
    public static Fall fall(double forwardSpeed, double verticalSpeed, double height) {
        double forward = 0;
        double fallen = 0;
        int ticks = 0;
        while (fallen < height && ticks < FUSE_TICKS) {
            verticalSpeed -= GRAVITY;
            fallen -= verticalSpeed;
            forward += forwardSpeed;
            forwardSpeed *= DRAG;
            verticalSpeed *= DRAG;
            ticks++;
        }
        return new Fall(forward, ticks);
    }
}
