package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.LockOnSettings;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import io.github.duckysmacky.combatdrones.network.LockWarningPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side intel from sensor units (the recon UAV). Tracking dots: every update a flying UAV marks the enemies in
 * its sight (with trackingNeedsPilot only while its owner watches, and only those on screen); a dot lasts until the
 * next update. Lock-ons (from the camera, see {@link RemoteSessions}) follow their target live for
 * lockOnDurationSeconds, seen or not. Owners see their own; teammates (same vanilla team, unless it's in ignoredTeams)
 * get dots with trackingDotsForAllies and lock-ons with lockOnForAllies.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class ReconIntel {
    private static final int SYNC_INTERVAL_TICKS = 10;
    /** How often a live (interval 0) tracker looks around; in between the client follows the target itself. */
    private static final int LIVE_SWEEP_TICKS = 10;
    /** Most marks sent to one player per update. */
    private static final int MAX_SENT_MARKS = 64;

    /** One target as the intel knows it: where it is (a snapshot for a dot, live for a lock-on). */
    static final class Mark {
        final UUID targetId;
        final int entityId;
        final String name;
        final boolean player;
        /** The player told about a lock-on on this target (the target, or a stand-in body's pilot), or null. */
        @Nullable
        final UUID warnee;
        int dimension;
        double x;
        double y;
        double z;

        Mark(UUID targetId, int entityId, String name, boolean player, @Nullable UUID warnee, int dimension, double x, double y, double z) {
            this.targetId = targetId;
            this.entityId = entityId;
            this.name = name;
            this.player = player;
            this.warnee = warnee;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static Mark of(Entity target) {
            UUID warnee = target instanceof EntityPlayer ? target.getUniqueID() : target instanceof EntityPilotBody ? ((EntityPilotBody) target).getOwner() : null;
            return new Mark(target.getUniqueID(), target.getEntityId(), target.getName(), warnee != null, warnee, target.dimension,
                    target.posX, target.posY, target.posZ);
        }
    }

    /** One UAV's dots from its last update. */
    static final class Dots {
        final UUID owner;
        final int dimension;
        /** Server tick after which the dots are dropped if no update replaced them (the unit is gone). */
        final long until;
        final List<Mark> marks;

        Dots(UUID owner, int dimension, long until, List<Mark> marks) {
            this.owner = owner;
            this.dimension = dimension;
            this.until = until;
            this.marks = marks;
        }
    }

    static final class LockOn {
        final Mark mark;
        final long until;
        /** The UAV that locked it, for its active lock-on count. */
        final UUID source;
        /** The source's lockOnForAllies and lockedWarning when it locked on. */
        final boolean forAllies;
        final boolean warn;
        /** What kind of vehicle the source is, for the target's banner ({@code LockWarningPacket.UAV} or {@code RECON_DRONE}). */
        final int warnSource;

        LockOn(Mark mark, long until, UUID source, boolean forAllies, boolean warn, int warnSource) {
            this.mark = mark;
            this.until = until;
            this.source = source;
            this.forAllies = forAllies;
            this.warn = warn;
            this.warnSource = warnSource;
        }
    }

    /** By UAV. */
    private static final Map<UUID, Dots> DOTS = new HashMap<>();
    /** By owner, then target. */
    private static final Map<UUID, Map<UUID, LockOn>> LOCK_ONS = new HashMap<>();
    /** Locked-on players and the server tick their banner was last told to run until. */
    private static final Map<UUID, Long> WARNED_UNTIL = new HashMap<>();
    /** Players whose client currently shows marks, so they get one last (empty) update when the marks run out. */
    private static final Set<UUID> SHOWING = new HashSet<>();

    private ReconIntel() {
    }

    /** Server, from a flying unit's tick: a sensor unit updates its tracking dots. */
    static void sweep(EntityRampUnit unit, DroneType type) {
        UUID owner = unit.getOwner();
        if (!type.isSensor() || owner == null) {
            return;
        }
        // No dots without a watcher, at once rather than at the next update
        if (!ModConfig.trackingDotsEnabled || (ModConfig.trackingNeedsPilot && !RemoteSessions.isWatched(unit))) {
            DOTS.remove(unit.getUniqueID());
            return;
        }
        int interval = sweepInterval();
        if (unit.ticksExisted % interval != 0) {
            return;
        }
        double radius = ModConfig.trackingRange;
        Vec3d eye = sensorPosition(unit);
        AxisAlignedBB area = new AxisAlignedBB(eye.x, eye.y, eye.z, eye.x, eye.y, eye.z).grow(radius);
        List<Mark> seen = new ArrayList<>();
        for (Entity target : unit.world.getEntitiesWithinAABB(Entity.class, area, entity -> isEnemy(owner, entity, ModConfig.uavLockOn.trackNonPlayers))) {
            double reach = target.isSneaking() ? radius / 2 : radius;
            if (target.getDistanceSq(eye.x, eye.y, eye.z) <= reach * reach
                    && (!ModConfig.trackingNeedsPilot || RemoteSessions.isOnScreen(unit, eye, target))
                    && hasLineOfSight(unit.world, eye, target)) {
                seen.add(Mark.of(target));
            }
        }
        DOTS.put(unit.getUniqueID(), new Dots(owner, unit.dimension, clock() + interval + SYNC_INTERVAL_TICKS, seen));
    }

    private static int sweepInterval() {
        return ModConfig.trackingIntervalSeconds <= 0 ? LIVE_SWEEP_TICKS : Math.max(1, (int) Math.round(ModConfig.trackingIntervalSeconds * 20));
    }

    static Vec3d sensorPosition(Entity unit) {
        return new Vec3d(unit.posX, unit.posY + unit.height / 2, unit.posZ);
    }

    /**
     * Whether {@code entity} is someone a recon owned by {@code owner} tracks: any visible player (not spectating)
     * who isn't a teammate, a non-teammate's stand-in body, or with {@code nonPlayers} (trackNonPlayers) any other living
     * thing. Invisible players, remote pilots riding their units included, stay hidden.
     */
    static boolean isEnemy(UUID owner, @Nullable Entity entity, boolean nonPlayers) {
        if (entity == null || !entity.isEntityAlive()) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            return !Teams.isTeammate(owner, player) && !player.isSpectator() && !player.isInvisible();
        }
        if (!(entity instanceof EntityPilotBody)) {
            return nonPlayers && entity instanceof EntityLivingBase && !(entity instanceof EntityArmorStand) && !entity.isInvisible();
        }
        UUID bodyOwner = ((EntityPilotBody) entity).getOwner();
        if (bodyOwner == null || owner.equals(bodyOwner)) {
            return bodyOwner == null;
        }
        // Bodies only exist while their player is online and remote, so the player can be looked up
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        EntityPlayer bodyPlayer = server == null ? null : server.getPlayerList().getPlayerByUUID(bodyOwner);
        return bodyPlayer == null || !Teams.isTeammate(owner, bodyPlayer);
    }

    /** Seen if either the head or the middle of the body is in view (a fence or half-wall doesn't hide them). */
    static boolean hasLineOfSight(World world, Vec3d from, Entity target) {
        Vec3d eyes = new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        Vec3d middle = new Vec3d(target.posX, target.posY + target.height / 2, target.posZ);
        // Raytrace.blocks: vanilla's raytrace gives up past ~115 blocks diagonally and would report a clear view
        return Raytrace.blocks(world, from, eyes) == null || Raytrace.blocks(world, from, middle) == null;
    }

    /** A lock-on by {@code source} (a recon vehicle): the target is followed live for lockOnDurationSeconds. */
    static void lock(UUID owner, Entity target, UUID source, int warnSource, LockOnSettings settings) {
        addLockOn(owner, new LockOn(Mark.of(target), clock() + settings.durationTicks(), source, settings.lockOnForAllies, settings.lockedWarning,
                warnSource));
    }

    static void addLockOn(UUID owner, LockOn lockOn) {
        LOCK_ONS.computeIfAbsent(owner, key -> new HashMap<>()).put(lockOn.mark.targetId, lockOn);
    }

    /** Whether {@code owner} has a running lock-on on {@code target}. */
    static boolean isLockedBy(UUID owner, UUID target) {
        Map<UUID, LockOn> locks = LOCK_ONS.get(owner);
        LockOn lockOn = locks == null ? null : locks.get(target);
        return lockOn != null && clock() <= lockOn.until;
    }

    /** Running lock-ons made by one UAV. */
    static int activeLockOns(UUID source) {
        return countLockOns(LOCK_ONS.values(), source, clock());
    }

    static int countLockOns(Collection<Map<UUID, LockOn>> locks, UUID source, long now) {
        int count = 0;
        for (Map<UUID, LockOn> owned : locks) {
            for (LockOn lockOn : owned.values()) {
                if (now <= lockOn.until && source.equals(lockOn.source)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** The longest-running lock-on with a banner (lockedWarning) anyone has on {@code player} (or their stand-in body), or null. */
    @Nullable
    private static LockOn longestLockOn(UUID player, long now) {
        LockOn longest = null;
        for (Map<UUID, LockOn> owned : LOCK_ONS.values()) {
            for (LockOn lockOn : owned.values()) {
                if (lockOn.warn && lockOn.until > now && player.equals(lockOn.mark.warnee) && (longest == null || lockOn.until > longest.until)) {
                    longest = lockOn;
                }
            }
        }
        return longest;
    }

    /** The player to warn about a lock-on building on {@code target}: the target itself, or a stand-in body's pilot. */
    @Nullable
    static EntityPlayerMP warneeOf(Entity target) {
        if (target instanceof EntityPlayerMP) {
            return (EntityPlayerMP) target;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        UUID pilot = target instanceof EntityPilotBody ? ((EntityPilotBody) target).getOwner() : null;
        return server == null || pilot == null ? null : server.getPlayerList().getPlayerByUUID(pilot);
    }

    /** Tells each locked-on player how long it has left, when that changes (a new lock-on, or the last one ending). */
    private static void warnLocked(EntityPlayerMP player, long now) {
        LockOn longest = longestLockOn(player.getUniqueID(), now);
        long until = longest == null ? now : longest.until;
        Long told = WARNED_UNTIL.get(player.getUniqueID());
        if (until > now ? told == null || told != until : told != null) {
            if (until > now) {
                WARNED_UNTIL.put(player.getUniqueID(), until);
            } else {
                WARNED_UNTIL.remove(player.getUniqueID());
            }
            ModNetwork.CHANNEL.sendTo(new LockWarningPacket(true, (until - now) / 20.0F,
                    longest == null ? LockWarningPacket.UAV : longest.warnSource), player);
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
        DOTS.values().removeIf(dots -> now > dots.until);
        for (Iterator<Map<UUID, LockOn>> owners = LOCK_ONS.values().iterator(); owners.hasNext(); ) {
            Map<UUID, LockOn> locks = owners.next();
            locks.values().removeIf(lockOn -> now > lockOn.until);
            for (LockOn lockOn : locks.values()) {
                follow(server, lockOn.mark);
            }
            if (locks.isEmpty()) {
                owners.remove();
            }
        }
        boolean live = ModConfig.trackingIntervalSeconds <= 0;
        WARNED_UNTIL.keySet().removeIf(id -> server.getPlayerList().getPlayerByUUID(id) == null);
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            sync(player, now, live);
            warnLocked(player, now);
        }
    }

    /** A locked-on mark moves with its target, wherever it goes (as long as it's loaded somewhere). */
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

    /** Sends a player the marks they may see: their own UAVs', and their teammates' as far as those are shared. */
    private static void sync(EntityPlayerMP player, long now, boolean live) {
        // The same target marked by several UAVs shows once; a lock-on beats a dot
        Map<UUID, IntelPacket.Entry> merged = new HashMap<>();
        for (Dots dots : DOTS.values()) {
            if (dots.dimension == player.dimension && sees(player, dots.owner, ModConfig.trackingDotsForAllies)) {
                for (Mark mark : dots.marks) {
                    merged.putIfAbsent(mark.targetId, entry(mark, false, live, 0));
                }
            }
        }
        for (Map.Entry<UUID, Map<UUID, LockOn>> owner : LOCK_ONS.entrySet()) {
            for (LockOn lockOn : owner.getValue().values()) {
                if (!sees(player, owner.getKey(), lockOn.forAllies)) {
                    continue;
                }
                IntelPacket.Entry known = merged.get(lockOn.mark.targetId);
                // Several lock-ons on one target: the one that lasts longest
                if (now <= lockOn.until && lockOn.mark.dimension == player.dimension && (known == null || known.ticksLeft < lockOn.until - now)) {
                    merged.put(lockOn.mark.targetId, entry(lockOn.mark, true, true, (int) (lockOn.until - now)));
                }
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

    /** The owner sees all of their intel; a teammate only what's shared with allies. */
    private static boolean sees(EntityPlayerMP player, UUID owner, boolean sharedWithAllies) {
        return owner.equals(player.getUniqueID()) || (sharedWithAllies && Teams.isTeammate(owner, player));
    }

    private static IntelPacket.Entry entry(Mark mark, boolean locked, boolean live, int ticksLeft) {
        return new IntelPacket.Entry(mark.x, mark.y, mark.z, mark.entityId, mark.name, locked, mark.player, live, ticksLeft);
    }
}
