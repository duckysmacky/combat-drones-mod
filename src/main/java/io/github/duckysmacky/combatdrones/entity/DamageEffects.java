package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.Fx;
import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

/** Hit and wreck feedback shared by every drone entity, so all aerial units read the same when shot. */
final class DamageEffects {
    /** Entity status byte telling clients the drone was hit (red flash). */
    static final byte HIT_STATUS = 2;
    static final int HIT_FLASH_TICKS = 6;

    private DamageEffects() {
    }

    /** Server: a hit that didn't destroy the drone. */
    static void hit(Entity drone) {
        drone.world.setEntityState(drone, HIT_STATUS);
        ((WorldServer) drone.world).spawnParticle(EnumParticleTypes.CRIT, drone.posX, drone.posY + drone.height / 2, drone.posZ,
                8, drone.width / 3, drone.height / 3, drone.width / 3, 0.3);
        drone.world.playSound(null, drone.posX, drone.posY, drone.posZ, ModSounds.DRONE_HIT, SoundCategory.NEUTRAL,
                1.0F, 0.9F + drone.world.rand.nextFloat() * 0.2F);
    }

    /** Server: destroyed without a blast: a puff and a crack, nothing left behind. */
    static void wreck(Entity drone) {
        ((WorldServer) drone.world).spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, drone.posX, drone.posY + drone.height / 2, drone.posZ,
                10, 0.2, 0.2, 0.2, 0.03);
        drone.world.playSound(null, drone.posX, drone.posY, drone.posZ, ModSounds.DRONE_HIT, SoundCategory.NEUTRAL, 1.2F, 0.6F);
    }

    /** Server: shot down and going down: a burst of flame and smoke and a heavy crack. */
    static void goingDown(Entity drone) {
        WorldServer world = (WorldServer) drone.world;
        double y = drone.posY + drone.height / 2;
        world.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, drone.posX, y, drone.posZ, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(EnumParticleTypes.FLAME, drone.posX, y, drone.posZ, 12, drone.width / 3, drone.height / 3, drone.width / 3, 0.05);
        world.playSound(null, drone.posX, drone.posY, drone.posZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.NEUTRAL, 1.5F, 1.4F);
        hit(drone);
    }

    /** Client, every tick while shot down: a thick black smoke trail with fire licking out of it, scaled to the airframe. */
    static void burningTrail(Entity drone) {
        float size = Math.max(drone.width, drone.height);
        int puffs = 1 + Math.round(size);
        for (int i = 0; i < puffs; i++) {
            double x = drone.posX + (drone.world.rand.nextDouble() - 0.5) * drone.width;
            double y = drone.posY + drone.world.rand.nextDouble() * drone.height;
            double z = drone.posZ + (drone.world.rand.nextDouble() - 0.5) * drone.width;
            CombatDrones.proxy.fx(drone.world, Fx.DARK_SMOKE, x, y, z, 0, 0.04, 0, 0.7F + size * 0.35F);
            if (drone.world.rand.nextBoolean()) {
                CombatDrones.proxy.fx(drone.world, Fx.FIRE, x, y, z, 0, 0.02, 0, 0.45F + size * 0.2F);
            }
        }
    }

    /** Client: smoke trail once badly damaged, so everyone can see the drone has been hit. */
    static void damageSmoke(Entity drone, float healthFraction) {
        if (healthFraction >= 0.5F || drone.world.rand.nextFloat() > (healthFraction < 0.25F ? 0.8F : 0.35F)) {
            return;
        }
        double x = drone.posX + (drone.world.rand.nextDouble() - 0.5) * drone.width;
        double z = drone.posZ + (drone.world.rand.nextDouble() - 0.5) * drone.width;
        boolean burning = healthFraction < 0.25F;
        CombatDrones.proxy.fx(drone.world, burning ? Fx.DARK_SMOKE : Fx.SMOKE, x, drone.posY + drone.height / 2, z, 0, 0.02, 0, burning ? 0.6F : 0.45F);
        if (burning) {
            CombatDrones.proxy.fx(drone.world, Fx.FIRE, x, drone.posY + drone.height / 2, z, 0, 0.01, 0, 0.4F); // on fire
        }
    }
}
