package io.github.duckysmacky.combatdrones.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/** Line-of-fire checks that see both blocks and entities (vanilla's block raytrace ignores entities). */
public final class Raytrace {
    private Raytrace() {
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
        RayTraceResult result = world.rayTraceBlocks(start, end, false, true, false);
        Vec3d limit = result != null ? result.hitVec : end;
        double best = start.squareDistanceTo(limit);
        AxisAlignedBB area = new AxisAlignedBB(start.x, start.y, start.z, limit.x, limit.y, limit.z).grow(1);
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(ignored, area)) {
            if (!entity.canBeCollidedWith() || (ignored != null && entity.isRidingOrBeingRiddenBy(ignored))) {
                continue;
            }
            RayTraceResult intercept = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize()).calculateIntercept(start, limit);
            if (intercept != null && start.squareDistanceTo(intercept.hitVec) < best) {
                best = start.squareDistanceTo(intercept.hitVec);
                result = new RayTraceResult(entity, intercept.hitVec);
            }
        }
        return result;
    }
}
