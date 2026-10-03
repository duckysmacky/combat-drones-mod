package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.ModSounds;
import net.minecraft.entity.Entity;
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
        drone.world.playSound(null, drone.posX, drone.posY, drone.posZ, ModSounds.DRONE_HIT, SoundCategory.NEUTRAL,
                1.0F, 0.9F + drone.world.rand.nextFloat() * 0.2F);
    }

    /** Server: destroyed without a blast: a puff and a crack, nothing left behind. */
    static void wreck(Entity drone) {
        ((WorldServer) drone.world).spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, drone.posX, drone.posY + drone.height / 2, drone.posZ,
                10, 0.2, 0.2, 0.2, 0.03);
        drone.world.playSound(null, drone.posX, drone.posY, drone.posZ, ModSounds.DRONE_HIT, SoundCategory.NEUTRAL, 1.2F, 0.6F);
    }

    /** Client: smoke trail once badly damaged, so everyone can see the drone has been hit. */
    static void damageSmoke(Entity drone, float healthFraction) {
        if (healthFraction >= 0.5F || drone.world.rand.nextFloat() > (healthFraction < 0.25F ? 0.8F : 0.35F)) {
            return;
        }
        EnumParticleTypes smoke = healthFraction < 0.25F ? EnumParticleTypes.SMOKE_LARGE : EnumParticleTypes.SMOKE_NORMAL;
        drone.world.spawnParticle(smoke, drone.posX + (drone.world.rand.nextDouble() - 0.5) * drone.width, drone.posY + drone.height / 2,
                drone.posZ + (drone.world.rand.nextDouble() - 0.5) * drone.width, 0, 0.02, 0);
    }
}
