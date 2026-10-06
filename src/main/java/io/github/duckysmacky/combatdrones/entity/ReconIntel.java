package io.github.duckysmacky.combatdrones.entity;

import com.mojang.authlib.GameProfile;
import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.Team;
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
 * A passive sighting holds the last-seen position for detectionDecaySeconds; an active lock (from the recon camera)
 * follows the target live for activeLockDurationSeconds. Marks belong to the unit's owner; teammates (same vanilla
 * team, unless it's in ignoredTeams) get sightings with reconIntelForAllies and locks with activeLockForAllies.
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
        /** Where the target is: the last sighting, or live while locked. */
        double x;
        double y;
        double z;
        /** Where it was last actually seen: all a teammate without shared locks gets. */
        double seenX;
        double seenY;
        double seenZ;
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
     * Whether {@code entity} is someone a recon owned by {@code owner} reports: any visible player (not spectating)
     * who isn't a teammate, or a non-teammate's stand-in body. Invisible players, remote pilots riding their units included,
     * stay hidden.
     */
    static boolean isEnemy(UUID owner, @Nullable Entity entity) {
        if (entity == null || !entity.isEntityAlive()) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            return !isTeammate(owner, player) && !player.isSpectator() && !player.isInvisible();
        }
        if (!(entity instanceof EntityPilotBody)) {
            return false;
        }
        UUID bodyOwner = ((EntityPilotBody) entity).getOwner();
        if (bodyOwner == null || owner.equals(bodyOwner)) {
            return bodyOwner == null;
        }
        // Bodies only exist while their player is online and remote, so the player can be looked up
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        EntityPlayer bodyPlayer = server == null ? null : server.getPlayerList().getPlayerByUUID(bodyOwner);
        return bodyPlayer == null || !isTeammate(owner, bodyPlayer);
    }

    /**
     * Whether {@code player} is on {@code owner}'s side: the owner, or a player on the owner's vanilla scoreboard team,
     * unless that team is in ignoredTeams (a free-for-all game typically puts everyone on one team). Teammates are never
     * spotted or locked; whether they get the owner's intel is up to the sharing settings.
     */
    static boolean isTeammate(UUID owner, EntityPlayer player) {
        if (owner.equals(player.getUniqueID())) {
            return true;
        }
        Team team = player.getTeam();
        if (team == null || isIgnoredTeam(team.getName())) {
            return false;
        }
        String ownerName = nameOf(owner);
        return ownerName != null && team.getMembershipCollection().contains(ownerName);
    }

    static boolean isIgnoredTeam(String teamName) {
        for (String ignored : ModConfig.ignoredTeams) {
            if (ignored.equalsIgnoreCase(teamName)) {
                return true;
            }
        }
        return false;
    }

    /** The owner's name, even while they're offline (scoreboard teams are kept by name). */
    @Nullable
    private static String nameOf(UUID playerId) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return null;
        }
        EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(playerId);
        if (online != null) {
            return online.getName();
        }
        GameProfile profile = server.getPlayerProfileCache().getProfileByUUID(playerId);
        return profile == null ? null : profile.getName();
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
        mark.seenX = target.posX;
        mark.seenY = target.posY;
        mark.seenZ = target.posZ;
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

    /** Sends a player the marks they may see: their own recon's, and their teammates' as far as those are shared. */
    private static void sync(EntityPlayerMP player, long now) {
        // The same target spotted by several recons shows once: the freshest (a lock beats any sighting)
        Map<UUID, IntelPacket.Entry> merged = new HashMap<>();
        for (Map.Entry<UUID, Map<UUID, Mark>> owner : MARKS.entrySet()) {
            boolean own = owner.getKey().equals(player.getUniqueID());
            if (!own && !isTeammate(owner.getKey(), player)) {
                continue;
            }
            boolean ownerViewing = RemoteSessions.isViewingUnit(owner.getKey());
            for (Mark mark : owner.getValue().values()) {
                IntelPacket.Entry entry = mark.dimension == player.dimension ? viewOf(mark, now, own, ownerViewing) : null;
                if (entry == null) {
                    continue;
                }
                IntelPacket.Entry known = merged.get(mark.targetId);
                IntelPacket.Entry best = known == null || (entry.locked && !known.locked)
                        || (entry.locked == known.locked && entry.freshness > known.freshness) ? entry : known;
                // Shown in the world if any owner's lock on it is
                boolean inWorld = entry.inWorld || (known != null && known.inWorld);
                merged.put(mark.targetId, inWorld && !best.inWorld ? withInWorld(best) : best);
            }
        }
        List<IntelPacket.Entry> entries = new ArrayList<>(merged.values());
        if (entries.size() > MAX_SENT_MARKS) {
            entries = entries.subList(0, MAX_SENT_MARKS);
        }
        if (entries.isEmpty() && !SHOWING.remove(player.getUniqueID())) {
            return;
        }
        if (!entries.isEmpty()) {
            SHOWING.add(player.getUniqueID());
        }
        ModNetwork.CHANNEL.sendTo(new IntelPacket(entries), player);
    }

    /**
     * What one viewer gets of a mark, or null for nothing: the owner ({@code own}) sees it all; a teammate gets the
     * lock with activeLockForAllies, else the last sighting with reconIntelForAllies while it's fresh. A lock shows in
     * the world outside cameras while its owner watches through a UAV ({@code ownerViewing}), or always with
     * alwaysShowActiveLock.
     */
    @Nullable
    static IntelPacket.Entry viewOf(Mark mark, long now, boolean own, boolean ownerViewing) {
        boolean locked = now <= mark.lockedUntil;
        boolean sighted = now <= mark.expiresAt;
        boolean inWorld = ModConfig.alwaysShowActiveLock || ownerViewing;
        if (locked && (own || ModConfig.activeLockForAllies)) {
            return new IntelPacket.Entry(mark.x, mark.y, mark.z, mark.name, true, 1, inWorld);
        }
        if (sighted && (own || ModConfig.reconIntelForAllies)) {
            float freshness = (float) Math.max(0, (mark.expiresAt - now) / (ModConfig.detectionDecaySeconds * 20));
            return new IntelPacket.Entry(mark.seenX, mark.seenY, mark.seenZ, mark.name, false, freshness, false);
        }
        return null;
    }

    private static IntelPacket.Entry withInWorld(IntelPacket.Entry entry) {
        return new IntelPacket.Entry(entry.x, entry.y, entry.z, entry.name, entry.locked, entry.freshness, true);
    }
}
