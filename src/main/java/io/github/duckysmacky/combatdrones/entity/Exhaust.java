package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.physics.CruiseMissileFlight;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Client-side trails, called from entity ticks on the client only. Uses the common {@code World} particle and sound
 * calls (no-ops on a server world), so nothing here touches client-only classes.
 */
final class Exhaust {
    private Exhaust() {
    }

    /**
     * Ramp unit in flight: munitions leave a thin smoke trail, other planes a faint exhaust haze; boosting pours out
     * thick smoke and flame, strung along the last tick's path so fast units leave a solid ribbon.
     */
    static void unitTrail(EntityRampUnit unit, DroneType type, boolean boosting) {
        if (unit instanceof EntityCruiseMissile) {
            missileTrail((EntityCruiseMissile) unit, boosting);
            return;
        }
        World world = unit.world;
        Random random = world.rand;
        Vec3d nose = Vec3d.fromPitchYaw(unit.rotationPitch, unit.rotationYaw);
        double tailOffset = type.getHitboxWidth() * 0.9;
        Vec3d tail = new Vec3d(unit.posX, unit.posY + unit.height / 2, unit.posZ).subtract(nose.scale(tailOffset));
        Vec3d previous = new Vec3d(unit.prevPosX, unit.prevPosY + unit.height / 2, unit.prevPosZ).subtract(nose.scale(tailOffset));
        if (boosting) {
            int puffs = 4;
            for (int index = 0; index < puffs; index++) {
                Vec3d at = interpolate(previous, tail, index / (double) puffs);
                spawn(world, EnumParticleTypes.SMOKE_LARGE, at, nose.scale(-0.08), random, 0.15);
                spawn(world, EnumParticleTypes.CLOUD, at, nose.scale(-0.05), random, 0.2);
            }
            spawn(world, EnumParticleTypes.FLAME, tail, nose.scale(-0.15), random, 0.05);
            spawn(world, EnumParticleTypes.FLAME, interpolate(previous, tail, 0.5), nose.scale(-0.12), random, 0.05);
        } else if (type.isImpactDetonates() || unit.ticksExisted % 4 == 0) {
            spawn(world, EnumParticleTypes.SMOKE_NORMAL, tail, nose.scale(-0.03), random, 0.05);
            if (type.isImpactDetonates()) {
                spawn(world, EnumParticleTypes.SMOKE_NORMAL, interpolate(previous, tail, 0.5), nose.scale(-0.03), random, 0.05);
            }
        }
    }

    /**
     * Cruise missile: on the pad, exhaust billowing out sideways from under it; climbing, flame under the tail and a
     * thick smoke column strung along its path (it should be seen from far away); diving, flame and smoke streaming
     * up from the tail, heavier while boosting.
     */
    private static void missileTrail(EntityCruiseMissile missile, boolean boosting) {
        World world = missile.world;
        Random random = world.rand;
        CruiseMissileFlight.Stage stage = missile.getStage();
        boolean diving = stage == CruiseMissileFlight.Stage.DIVE;
        // The tail is the bottom of the upright box while climbing, the top while diving nose-first
        double tailHeight = diving ? missile.height : 0;
        Vec3d tail = new Vec3d(missile.posX, missile.posY + tailHeight, missile.posZ);
        Vec3d previous = new Vec3d(missile.prevPosX, missile.prevPosY + tailHeight, missile.prevPosZ);
        Vec3d back = new Vec3d(0, diving ? 1 : -1, 0);
        if (stage == CruiseMissileFlight.Stage.IGNITION) {
            for (int index = 0; index < 3; index++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double speed = 0.08 + random.nextDouble() * 0.15;
                spawn(world, index == 0 ? EnumParticleTypes.CLOUD : EnumParticleTypes.SMOKE_LARGE, tail.add(0, 0.2, 0),
                        new Vec3d(Math.cos(angle) * speed, 0.02, Math.sin(angle) * speed), random, 0.4);
            }
            spawn(world, EnumParticleTypes.FLAME, tail, back.scale(0.05), random, 0.3);
            return;
        }
        int puffs = Math.max(2, (int) Math.ceil(tail.distanceTo(previous) * 2.5)) + (boosting ? 2 : 0);
        for (int index = 0; index < puffs; index++) {
            Vec3d at = interpolate(previous, tail, index / (double) puffs);
            spawn(world, EnumParticleTypes.CLOUD, at, back.scale(0.02), random, 0.5);
            spawn(world, EnumParticleTypes.SMOKE_LARGE, at, back.scale(0.04), random, 0.4);
        }
        spawn(world, EnumParticleTypes.FLAME, tail, back.scale(0.2), random, 0.2);
        spawn(world, EnumParticleTypes.FLAME, interpolate(previous, tail, 0.5), back.scale(0.15), random, 0.2);
    }

    /** Booster lighting up: a roar and a burst of smoke at the tail. */
    static void boostStart(EntityRampUnit unit) {
        unit.world.playSound(unit.posX, unit.posY, unit.posZ, ModSounds.UNIT_BOOST, SoundCategory.NEUTRAL, 4.5F,
                0.95F + unit.world.rand.nextFloat() * 0.1F, false);
        Vec3d nose = Vec3d.fromPitchYaw(unit.rotationPitch, unit.rotationYaw);
        Vec3d tail = new Vec3d(unit.posX, unit.posY + unit.height / 2, unit.posZ).subtract(nose.scale(unit.width * 0.9));
        for (int index = 0; index < 12; index++) {
            spawn(unit.world, EnumParticleTypes.EXPLOSION_NORMAL, tail, nose.scale(-0.1), unit.world.rand, 0.3);
        }
    }

    /** Rocket motor: flame at the nozzle and a thick smoke trail; a launch blast on its first tick. */
    static void rocketTrail(Entity rocket) {
        World world = rocket.world;
        Random random = world.rand;
        Vec3d velocity = new Vec3d(rocket.motionX, rocket.motionY, rocket.motionZ);
        Vec3d back = velocity.lengthSquared() > 1.0E-6 ? velocity.normalize().scale(-1) : Vec3d.ZERO;
        Vec3d here = rocket.getPositionVector();
        if (rocket.ticksExisted <= 1) {
            for (int index = 0; index < 6; index++) {
                spawn(world, EnumParticleTypes.EXPLOSION_NORMAL, here, back.scale(0.1), random, 0.3);
            }
        }
        for (int index = 0; index < 3; index++) {
            Vec3d at = here.subtract(velocity.scale(index / 3.0));
            spawn(world, EnumParticleTypes.SMOKE_LARGE, at, back.scale(0.02), random, 0.06);
            spawn(world, EnumParticleTypes.CLOUD, at, Vec3d.ZERO, random, 0.08);
        }
        spawn(world, EnumParticleTypes.FLAME, here.add(back.scale(0.3)), back.scale(0.1), random, 0.03);
    }

    /** Cannon shell: a thin glowing tracer line, no smoke. */
    static void shellTracer(Entity shell) {
        World world = shell.world;
        Vec3d velocity = new Vec3d(shell.motionX, shell.motionY, shell.motionZ);
        Vec3d here = shell.getPositionVector();
        for (int index = 0; index < 3; index++) {
            spawn(world, EnumParticleTypes.FIREWORKS_SPARK, here.subtract(velocity.scale(index / 3.0)), Vec3d.ZERO, world.rand, 0.02);
        }
    }

    private static Vec3d interpolate(Vec3d from, Vec3d to, double fraction) {
        return from.add(to.subtract(from).scale(fraction));
    }

    private static void spawn(World world, EnumParticleTypes type, Vec3d at, Vec3d velocity, Random random, double jitter) {
        world.spawnParticle(type, true, at.x + (random.nextDouble() - 0.5) * jitter, at.y + (random.nextDouble() - 0.5) * jitter,
                at.z + (random.nextDouble() - 0.5) * jitter, velocity.x, velocity.y, velocity.z);
    }
}
