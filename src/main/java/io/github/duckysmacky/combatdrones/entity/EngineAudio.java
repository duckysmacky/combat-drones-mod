package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.network.EngineSoundPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lets players hear planes beyond the distance the server streams entities to them (its entity view distance,
 * ~144 blocks by default): twice a second each player gets the running ramp units within their {@code motorSoundRange}
 * that their client doesn't track. Entities a client does track play their engine sound normally.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class EngineAudio {
    private static final int INTERVAL_TICKS = 10;

    private EngineAudio() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCounter() % INTERVAL_TICKS != 0) {
            return;
        }
        for (WorldServer world : server.worlds) {
            List<EntityRampUnit> running = new ArrayList<>();
            for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityRampUnit && !entity.isDead && ((EntityRampUnit) entity).getFuelFraction() > 0) {
                    running.add((EntityRampUnit) entity);
                }
            }
            if (running.isEmpty()) {
                continue;
            }
            for (EntityPlayer player : world.playerEntities) {
                List<EngineSoundPacket.Entry> audible = new ArrayList<>();
                for (EntityRampUnit unit : running) {
                    DroneType type = unit.getDroneType();
                    double range = type == null ? 64 : type.getMotorSoundRange();
                    Set<? extends EntityPlayer> trackers = world.getEntityTracker().getTrackingPlayers(unit);
                    if (type == null || unit.isRidingOrBeingRiddenBy(player) || trackers.contains(player) || player.getDistanceSq(unit) > range * range) {
                        continue;
                    }
                    Vec3d motion = Vec3d.fromPitchYaw(unit.rotationPitch, unit.rotationYaw).scale(unit.getSyncedSpeed());
                    audible.add(new EngineSoundPacket.Entry(unit.getEntityId(), type.getId(), (float) unit.posX, (float) unit.posY, (float) unit.posZ,
                            (float) motion.x, (float) motion.y, (float) motion.z, unit.isBoosting()));
                }
                if (!audible.isEmpty()) {
                    ModNetwork.CHANNEL.sendTo(new EngineSoundPacket(audible), (EntityPlayerMP) player);
                }
            }
        }
    }
}
