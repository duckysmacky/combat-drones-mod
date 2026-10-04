package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side intel from sensor units (the recon UAV): who each owner's recon has spotted, where, and until when.
 * Marks belong to the unit's owner alone. A passive sighting holds the last-seen position for detectionDecaySeconds;
 * an active lock (from the recon camera) follows the target live for activeLockDurationSeconds.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class ReconIntel {
    private static final int SWEEP_INTERVAL_TICKS = 10;
    private static final int SYNC_INTERVAL_TICKS = 10;
    /** Most marks sent to one owner per update. */
    private static final int MAX_SENT_MARKS = 64;

    static final class Mark {
        final UUID targetId;
        String name;
        int dimension;
        double x;
        double y;
        double z;
        long expiresAt;
        long lockedUntil;

        Mark(UUID targetId) {
            this.targetId = targetId;
        }
    }

    private static final Map<UUID, Map<UUID, Mark>> MARKS = new HashMap<>();
    /** Owners whose client currently shows marks, so they get one last (empty) update when the marks run out. */
    private static final Set<UUID> SHOWING = new HashSet<>();

    private ReconIntel() {
    }

    /** Server, from a flying unit's tick: a sensor unit sweeps for enemies in line of sight every half second. */
    static void sweep(EntityRampUnit unit, DroneType type) {
        UUID owner = unit.getOwner();
        if (!type.isSensor() || owner == null || unit.ticksExisted % SWEEP_INTERVAL_TICKS != 0) {
            return;
        }
        double radius = ModConfig.detectionSweepRadius;
        Vec3d eye = sensorPosition(unit);
        AxisAlignedBB area = new AxisAlignedBB(eye.x, eye.y, eye.z, eye.x, eye.y, eye.z).grow(radius);
        for (Entity target : unit.world.getEntitiesWithinAABB(Entity.class, area, entity -> isEnemy(owner, entity))) {
            double reach = target.isSneaking() ? radius / 2 : radius;
            if (target.getDistanceSq(eye.x, eye.y, eye.z) <= reach * reach && hasLineOfSight(unit.world, eye, target)) {
                spot(owner, target, false);
            }
        }
    }

    static Vec3d sensorPosition(EntityRampUnit unit) {
        return new Vec3d(unit.posX, unit.posY + unit.height / 2, unit.posZ);
    }

    /**
     * Whether {@code entity} is someone a recon owned by {@code owner} reports: any other visible player (not
     * spectating), or another player's stand-in body. Invisible players, remote pilots riding their units included,
     * stay hidden.
     */
    static boolean isEnemy(UUID owner, @Nullable Entity entity) {
        if (entity == null || !entity.isEntityAlive()) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            return !owner.equals(player.getUniqueID()) && !player.isSpectator() && !player.isInvisible();
        }
        return entity instanceof EntityPilotBody && !owner.equals(((EntityPilotBody) entity).getOwner());
    }

    /** Seen if either the head or the middle of the body is in view (a fence or half-wall doesn't hide them). */
    static boolean hasLineOfSight(World world, Vec3d from, Entity target) {
        Vec3d eyes = new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        Vec3d middle = new Vec3d(target.posX, target.posY + target.height / 2, target.posZ);
        return world.rayTraceBlocks(from, eyes, false, true, false) == null || world.rayTraceBlocks(from, middle, false, true, false) == null;
    }

    /** Active lock: the target is tracked live for activeLockDurationSeconds. */
    static void lock(UUID owner, Entity target) {
        spot(owner, target, true);
    }

    private static void spot(UUID owner, Entity target, boolean lock) {
        long now = clock();
        Mark mark = MARKS.computeIfAbsent(owner, key -> new HashMap<>()).computeIfAbsent(target.getUniqueID(), Mark::new);
        mark.name = target.getName();
        mark.dimension = target.dimension;
        mark.x = target.posX;
        mark.y = target.posY;
        mark.z = target.posZ;
        // A passive sighting never cuts a lock short
        mark.expiresAt = Math.max(mark.expiresAt, now + (long) (ModConfig.detectionDecaySeconds * 20));
        if (lock) {
            mark.lockedUntil = now + (long) (ModConfig.activeLockDurationSeconds * 20);
        }
    }

    private static long clock() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? 0 : server.getTickCounter();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCounter() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        long now = server.getTickCounter();
        for (Iterator<Map.Entry<UUID, Map<UUID, Mark>>> owners = MARKS.entrySet().iterator(); owners.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, Mark>> entry = owners.next();
            Map<UUID, Mark> marks = entry.getValue();
            marks.values().removeIf(mark -> now > mark.expiresAt && now > mark.lockedUntil);
            for (Mark mark : marks.values()) {
                if (now <= mark.lockedUntil) {
                    follow(server, mark);
                }
            }
            if (marks.isEmpty()) {
                owners.remove();
            }
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            sync(player, now);
        }
    }

    /** A locked mark moves with its target, wherever it goes (as long as it's loaded somewhere). */
    private static void follow(MinecraftServer server, Mark mark) {
        Entity target = server.getPlayerList().getPlayerByUUID(mark.targetId);
        if (target == null) {
            WorldServer world = server.getWorld(mark.dimension);
            target = world == null ? null : world.getEntityFromUuid(mark.targetId);
        }
        if (target != null && target.isEntityAlive()) {
            mark.dimension = target.dimension;
            mark.x = target.posX;
            mark.y = target.posY;
            mark.z = target.posZ;
        }
    }

    private static void sync(EntityPlayerMP player, long now) {
        Map<UUID, Mark> marks = MARKS.get(player.getUniqueID());
        List<IntelPacket.Entry> entries = new ArrayList<>();
        if (marks != null) {
            for (Mark mark : marks.values()) {
                if (mark.dimension != player.dimension || entries.size() >= MAX_SENT_MARKS) {
                    continue;
                }
                boolean locked = now <= mark.lockedUntil;
                float remaining = locked ? 1 : (float) Math.max(0, (mark.expiresAt - now) / (ModConfig.detectionDecaySeconds * 20));
                entries.add(new IntelPacket.Entry(mark.x, mark.y, mark.z, mark.name, locked, remaining));
            }
        }
        if (entries.isEmpty() && !SHOWING.remove(player.getUniqueID())) {
            return;
        }
        if (!entries.isEmpty()) {
            SHOWING.add(player.getUniqueID());
        }
        ModNetwork.CHANNEL.sendTo(new IntelPacket(entries), player);
    }
}
