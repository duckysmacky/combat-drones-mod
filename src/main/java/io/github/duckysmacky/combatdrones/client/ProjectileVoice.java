package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityBomb;
import io.github.duckysmacky.combatdrones.entity.EntityRocket;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;

/**
 * A rocket, shell or bomb in flight, heard by where it is: so the players it's coming down on hear it coming and from
 * where, not just the blast. One loop per kind, with the engines' falloff and Doppler (rising as it closes in). Shells
 * and bombs whistle lower as they fall. Cut the moment it hits: the blast takes over.
 */
final class ProjectileVoice extends EngineVoice {
    private final Entity projectile;
    private final double basePitch;
    /** True for what falls rather than flies under power: its whistle sinks over the fall. */
    private final boolean falling;

    private ProjectileVoice(Entity projectile, String event, double range, double basePitch, boolean falling) {
        super(DroneType.Sound.single(event), range);
        this.projectile = projectile;
        this.basePitch = basePitch;
        this.falling = falling;
    }

    /** The voice for a projectile, or null if it isn't one that makes a sound in flight. */
    @Nullable
    static ProjectileVoice of(Entity entity) {
        if (entity instanceof EntityRocket) {
            switch (((EntityRocket) entity).getKind()) {
                case ROCKET:
                    return new ProjectileVoice(entity, "projectile.rocket", 96, 1.0, false);
                case HEAVY_ROCKET:
                    return new ProjectileVoice(entity, "projectile.rocket_heavy", 112, 1.0, false);
                case CANNON_SHELL:
                    return new ProjectileVoice(entity, "projectile.cannon", 64, 1.0, false);
                case HOWITZER_SHELL:
                    return new ProjectileVoice(entity, "projectile.howitzer", 160, 1.0, false);
                default:
                    return null;
            }
        }
        if (entity instanceof EntityBomb) {
            switch (((EntityBomb) entity).getBombType()) {
                case LIGHT:
                    return new ProjectileVoice(entity, "projectile.bomb", 40, 1.35, true);
                case MEDIUM:
                    return new ProjectileVoice(entity, "projectile.bomb", 56, 1.05, true);
                default:
                    return new ProjectileVoice(entity, "projectile.bomb_heavy", 72, 1.0, true);
            }
        }
        return null;
    }

    @Override
    boolean isRunning() {
        return !projectile.isDead;
    }

    @Override
    Vec3d position() {
        return projectile.getPositionVector();
    }

    @Override
    double enginePitch() {
        // A falling whistle sinks over its first 1.5 s
        return falling ? basePitch * (1.1 - 0.25 * Math.min(1, projectile.ticksExisted / 30.0)) : basePitch;
    }
}
