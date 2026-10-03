package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketExplosion;
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
        for (EntityPlayer player : world.playerEntities) {
            if (player.getDistanceSq(x, y, z) < 4096.0D) {
                ((EntityPlayerMP) player).connection.sendPacket(new SPacketExplosion(x, y, z, power,
                        explosion.getAffectedBlockPositions(), explosion.getPlayerKnockbackMap().get(player)));
            }
        }
    }
}
