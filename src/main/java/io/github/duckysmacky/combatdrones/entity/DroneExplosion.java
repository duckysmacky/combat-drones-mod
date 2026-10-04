package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;

/**
 * Explosion whose damage is credited to whoever flew the drone, so PvP kills count for the pilot.
 * Vanilla would credit the (non-living) drone entity, i.e. nobody.
 */
public class DroneExplosion extends Explosion {
    private final EntityLivingBase placedBy;

    private DroneExplosion(WorldServer world, Entity source, @Nullable EntityLivingBase placedBy, double x, double y, double z, float power, boolean damagesTerrain) {
        super(world, source, x, y, z, power, false, damagesTerrain);
        this.placedBy = placedBy;
    }

    @Override
    @Nullable
    public EntityLivingBase getExplosivePlacedBy() {
        return placedBy != null ? placedBy : super.getExplosivePlacedBy();
    }

    /** Same flow as {@code WorldServer.newExplosion}, with our subclass and the global terrain switch. */
    public static void detonate(WorldServer world, Entity source, @Nullable EntityLivingBase placedBy, double x, double y, double z, float power) {
        detonate(world, source, placedBy, x, y, z, power, 0, 0);
    }

    /**
     * Explosion plus a shrapnel burst: living things within {@code shrapnelRadius} and in line of sight take up to
     * {@code shrapnelDamage}, falling off linearly with distance. Vanilla blast damage drops off steeply, so this is
     * what makes a near miss with a kamikaze drone dangerous.
     */
    public static void detonate(WorldServer world, Entity source, @Nullable EntityLivingBase placedBy, double x, double y, double z, float power,
                                float shrapnelDamage, float shrapnelRadius) {
        boolean damagesTerrain = ModConfig.blockDamageEnabled;
        DroneExplosion explosion = new DroneExplosion(world, source, placedBy, x, y, z, power, damagesTerrain);
        if (ForgeEventFactory.onExplosionStart(world, explosion)) {
            return;
        }
        explosion.doExplosionA();
        explosion.doExplosionB(false);
        if (!damagesTerrain) {
            explosion.clearAffectedBlockPositions();
        }
        sendToNearbyPlayers(world, explosion, x, y, z, power);
        EffectPacket.explosion(world, x, y, z, power);
        if (shrapnelDamage > 0 && shrapnelRadius > 0) {
            throwShrapnel(world, source, placedBy, new Vec3d(x, y, z), shrapnelDamage, shrapnelRadius);
        }
    }

    private static void sendToNearbyPlayers(WorldServer world, DroneExplosion explosion, double x, double y, double z, float power) {
        for (EntityPlayer player : world.playerEntities) {
            if (player.getDistanceSq(x, y, z) < 4096.0D) {
                ((EntityPlayerMP) player).connection.sendPacket(new SPacketExplosion(x, y, z, power,
                        explosion.getAffectedBlockPositions(), explosion.getPlayerKnockbackMap().get(player)));
            }
        }
    }

    private static void throwShrapnel(WorldServer world, Entity source, @Nullable EntityLivingBase placedBy, Vec3d center, float damage, float radius) {
        DamageSource shrapnel = new EntityDamageSourceIndirect("combat_drones.shrapnel", source, placedBy).setExplosion();
        AxisAlignedBB area = new AxisAlignedBB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        for (EntityLivingBase target : world.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
            Vec3d eyes = target.getPositionEyes(1.0F);
            double distance = eyes.distanceTo(center);
            if (distance > radius || world.rayTraceBlocks(center, eyes, false, true, false) != null) {
                continue;
            }
            // The blast just hit them too; without this their hurt cooldown would swallow most of the shrapnel
            target.hurtResistantTime = 0;
            target.attackEntityFrom(shrapnel, (float) (damage * (1 - distance / radius)));
        }
    }
}
