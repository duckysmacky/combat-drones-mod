package io.github.duckysmacky.combatdrones.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/** Line-of-fire checks that see both blocks and entities (vanilla's block raytrace ignores entities). */
public final class Raytrace {
    /** Longest leg handed to vanilla's block raytrace: it gives up after 200 block steps (~115 blocks diagonally). */
    private static final double LEG_LENGTH = 64.0;

    private Raytrace() {
    }

    /**
     * First block on the line from {@code start} to {@code end}, at any length. Vanilla's raytrace stops after 200 block
     * steps and reports a miss, so long lines are traced in legs.
     */
    @Nullable
    public static RayTraceResult blocks(World world, Vec3d start, Vec3d end) {
        Vec3d line = end.subtract(start);
        int legs = Math.max(1, (int) Math.ceil(line.length() / LEG_LENGTH));
        Vec3d from = start;
        for (int leg = 1; leg <= legs; leg++) {
            Vec3d to = leg == legs ? end : start.add(line.scale((double) leg / legs));
            RayTraceResult hit = world.rayTraceBlocks(from, to, false, true, false);
            if (hit != null) {
                return hit;
            }
            from = to;
        }
        return null;
    }

    /** Where the line from {@code start} to {@code end} first hits a block or entity, or null if nothing. */
    @Nullable
    public static Vec3d firstHit(World world, Vec3d start, Vec3d end, @Nullable Entity ignored) {
        RayTraceResult result = firstResult(world, start, end, ignored);
        return result == null ? null : result.hitVec;
    }

    /**
     * First block or entity on the line from {@code start} to {@code end}. {@code ignored} (the shooter) and anything
     * riding it or ridden by it are skipped, so a unit never hits itself or its remote pilot.
     */
    @Nullable
    public static RayTraceResult firstResult(World world, Vec3d start, Vec3d end, @Nullable Entity ignored) {
        return firstResult(world, start, end, ignored, 0);
    }

    /** As {@link #firstResult(World, Vec3d, Vec3d, Entity)}, with entity hitboxes grown by {@code margin} blocks. */
    @Nullable
    public static RayTraceResult firstResult(World world, Vec3d start, Vec3d end, @Nullable Entity ignored, double margin) {
        RayTraceResult result = blocks(world, start, end);
        Vec3d limit = result != null ? result.hitVec : end;
        double best = start.squareDistanceTo(limit);
        AxisAlignedBB area = new AxisAlignedBB(start.x, start.y, start.z, limit.x, limit.y, limit.z).grow(1 + margin);
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(ignored, area)) {
            // isRidingSameEntity, not isRidingOrBeingRiddenBy: the latter only looks at the entity's own passengers,
            // so a unit's remote pilot (riding it) would count as a target right on top of it
            if (!entity.canBeCollidedWith() || (ignored != null && entity.isRidingSameEntity(ignored))) {
                continue;
            }
            RayTraceResult intercept = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize() + margin).calculateIntercept(start, limit);
            if (intercept != null && start.squareDistanceTo(intercept.hitVec) < best) {
                best = start.squareDistanceTo(intercept.hitVec);
                result = new RayTraceResult(entity, intercept.hitVec);
            }
        }
        return result;
    }
}
