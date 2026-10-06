package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.LockProgressPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.physics.LockTracker;
import io.github.duckysmacky.combatdrones.network.RemoteSessionPacket;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketSetPassengers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of remote view and "Take control" for ramp units. Vanilla only streams chunks and entities around a
 * player's own entity, so during a session the real player rides the unit, invisible, while an
 * {@link EntityPilotBody} stands in for them at home and takes their hits. Ending a session (exit, death, logout,
 * the unit dying, dropping the Mission Planner) always puts the player back at home first.
 *
 * <p>The player's home is also written to their persistent NBT, so a server crash mid-session still returns them
 * home on their next login.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class RemoteSessions {
    private static final String HOME_TAG = CombatDrones.MOD_ID + ":RemoteHome";

    private static final class Session {
        final EntityPlayerMP player;
        /** The ramp unit or hand-flown drone the player rides. */
        final Entity vehicle;
        /** A hand-flown drone flown in FPV (its own checks decide the link), not a ramp unit session. */
        final boolean drone;
        final boolean control;
        final EntityPilotBody body;
        final boolean wasInvisible;
        // Active lock (sensor units): latest camera direction from the client and the lock in progress
        final LockTracker lock = new LockTracker(ModConfig.activeLockHoldTicks);
        @Nullable
        Vec3d lookDirection;
        long lookTick;
        float sentProgress;

        Session(EntityPlayerMP player, Entity vehicle, boolean drone, boolean control, EntityPilotBody body, boolean wasInvisible) {
            this.player = player;
            this.vehicle = vehicle;
            this.drone = drone;
            this.control = control;
            this.body = body;
            this.wasInvisible = wasInvisible;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Camera directions older than this (ticks) no longer count toward a lock. */
    private static final int LOOK_STALE_TICKS = 5;
    /** Blocks added around a target's hitbox when checking whether the recon camera is on it. */
    private static final double LOCK_BOX_MARGIN = 1.5;
    /** How long after a session teleport newly sent chunks still get their entities re-checked for the player. */
    private static final int RETRACK_WINDOW_TICKS = 200;
    /** Players a session recently teleported, until when (server tick). */
    private static final Map<UUID, Integer> RETRACK_UNTIL = new HashMap<>();
    /** Of those, players sent a chunk this tick. */
    private static final Set<UUID> RETRACK_NOW = new HashSet<>();
    /** Set while the body forwards a hit to its player, so the "remote players can't be hurt" rule lets it through. */
    private static boolean forwardingBodyDamage;

    private RemoteSessions() {
    }

    /**
     * Starts viewing (or, with {@code control}, flying) {@code unit}. Returns why it can't, as a translation key, or
     * null on success. Ownership and the held planner are checked here; the caller only finds the unit.
     */
    @Nullable
    public static String start(EntityPlayerMP player, EntityRampUnit unit, boolean control) {
        DroneType type = unit.getDroneType();
        if (unit.isDead || type == null || unit.world != player.world || !player.getUniqueID().equals(unit.getOwner())) {
            return "message.combat_drones.planner.unit_gone";
        }
        if (!isHoldingPlanner(player)) {
            return "message.combat_drones.remote.need_planner";
        }
        if (control && (type.getControlScheme() == null || !type.getControlScheme().isFlyable())) {
            return "message.combat_drones.remote.not_flyable";
        }
        Session other = sessionOf(unit);
        if (other != null && other.player != player) {
            return "message.combat_drones.remote.busy";
        }
        Session current = SESSIONS.get(player.getUniqueID());
        if (current != null) {
            if (current.vehicle == unit || current.drone) {
                end(player, null);
            } else {
                // Switching units: hop straight across, keeping the body and the saved home
                current.player.dismountRidingEntity();
                SESSIONS.remove(player.getUniqueID());
                mount(new Session(player, unit, false, control, current.body, current.wasInvisible));
                return null;
            }
        }
        begin(player, unit, false, control);
        return null;
    }

    /**
     * Server: a pilot takes off with a hand-flown drone (FPV). Same stand-in body as for ramp units, so the drone can
     * fly as far as its signal reaches: vanilla only streams the world and entities around the player's own entity.
     * The drone's own pilot checks (goggles, remote, signal from home) keep deciding when the link ends.
     */
    public static void startDrone(EntityPlayerMP player, EntityHandFlownDrone drone) {
        if (SESSIONS.containsKey(player.getUniqueID())) {
            end(player, null);
        }
        begin(player, drone, true, true);
    }

    /** Saves home, leaves a body there, hides the player and puts them on the vehicle. */
    private static void begin(EntityPlayerMP player, Entity vehicle, boolean drone, boolean control) {
        if (player.isRiding()) {
            player.dismountRidingEntity();
        }
        NBTTagCompound home = new NBTTagCompound();
        home.setDouble("X", player.posX);
        home.setDouble("Y", player.posY);
        home.setDouble("Z", player.posZ);
        home.setFloat("Yaw", player.rotationYaw);
        home.setFloat("Pitch", player.rotationPitch);
        player.getEntityData().setTag(HOME_TAG, home);

        EntityPilotBody body = new EntityPilotBody(player.world, player);
        Session session = new Session(player, vehicle, drone, control, body, player.isInvisible());
        SESSIONS.put(player.getUniqueID(), session);
        player.world.spawnEntity(body);
        player.setInvisible(true);
        mount(session);
    }

    /** Where the player left their body (server), or null when they aren't in a session. */
    @Nullable
    public static Vec3d homeOf(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!SESSIONS.containsKey(player.getUniqueID()) || !data.hasKey(HOME_TAG)) {
            return null;
        }
        NBTTagCompound home = data.getCompoundTag(HOME_TAG);
        return new Vec3d(home.getDouble("X"), home.getDouble("Y"), home.getDouble("Z"));
    }

    /** True while the player (online or not, by ID) watches or flies one of their ramp units (not an FPV drone). */
    static boolean isViewingUnit(UUID playerId) {
        Session session = SESSIONS.get(playerId);
        return session != null && !session.drone;
    }

    /** True while the player flies a hand-flown drone from a body (as opposed to a ramp unit session). */
    public static boolean isDroneSession(EntityPlayer player) {
        Session session = SESSIONS.get(player.getUniqueID());
        return session != null && session.drone;
    }

    private static void mount(Session session) {
        SESSIONS.put(session.player.getUniqueID(), session);
        EntityPlayerMP player = session.player;
        player.connection.setPlayerLocation(session.vehicle.posX, session.vehicle.posY, session.vehicle.posZ, player.rotationYaw, player.rotationPitch);
        retrackAfterTeleport(player);
        player.startRiding(session.vehicle, true);
        NBTTagCompound home = player.getEntityData().getCompoundTag(HOME_TAG);
        ModNetwork.CHANNEL.sendTo(new RemoteSessionPacket(session.vehicle.getEntityId(), session.control, session.drone,
                home.getDouble("X"), home.getDouble("Y"), home.getDouble("Z")), player);
    }

    /** Ends the player's session, if any, and puts them back where they left their body. */
    public static void end(EntityPlayerMP player, @Nullable String message) {
        Session session = SESSIONS.remove(player.getUniqueID());
        if (session == null) {
            return;
        }
        if (player.getRidingEntity() == session.vehicle) {
            player.dismountRidingEntity();
            // Tell the client about the dismount before the teleport, or it would apply the teleport while still
            // riding and snap back onto the unit. Watchers too: they'd lose the teleport the same way and, once the
            // unit is gone, see the (now visible) player standing where it went down.
            SPacketSetPassengers passengers = new SPacketSetPassengers(session.vehicle);
            player.connection.sendPacket(passengers);
            player.getServerWorld().getEntityTracker().sendToTracking(session.vehicle, passengers);
        }
        returnHome(player);
        player.setInvisible(session.wasInvisible);
        session.body.setDead();
        if (session.drone && session.vehicle instanceof EntityHandFlownDrone) {
            // Ended from here (death, logout, drone gone): the drone lets go of its pilot too. Ending from the drone's
            // side has already cleared its pilot, so this does nothing then.
            ((EntityHandFlownDrone) session.vehicle).releasePilot(player);
        }
        if (player.hasDisconnected()) {
            return; // logging out: they're saved at home, nobody left to tell
        }
        ModNetwork.CHANNEL.sendTo(new RemoteSessionPacket(-1, false, false, 0, 0, 0), player);
        if (message != null) {
            player.sendStatusMessage(new TextComponentTranslation(message), true);
        }
    }

    /** Teleports the player to their saved home and forgets it. */
    private static void returnHome(EntityPlayerMP player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(HOME_TAG)) {
            return;
        }
        NBTTagCompound home = data.getCompoundTag(HOME_TAG);
        data.removeTag(HOME_TAG);
        player.connection.setPlayerLocation(home.getDouble("X"), home.getDouble("Y"), home.getDouble("Z"), home.getFloat("Yaw"), home.getFloat("Pitch"));
        player.fallDistance = 0;
        retrackAfterTeleport(player);
    }

    /**
     * Vanilla quirk: after a teleport the tracker checks which entities the player should see right away, before the
     * chunks there count as sent, so it skips them all. Moving entities re-check themselves soon; still ones (launch
     * ramps, stand-in bodies) stayed invisible until the player walked a few blocks. So for a while after a session
     * teleport, every chunk sent to the player triggers a fresh check.
     */
    private static void retrackAfterTeleport(EntityPlayerMP player) {
        RETRACK_UNTIL.put(player.getUniqueID(), player.server.getTickCounter() + RETRACK_WINDOW_TICKS);
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        if (RETRACK_UNTIL.containsKey(event.getPlayer().getUniqueID())) {
            RETRACK_NOW.add(event.getPlayer().getUniqueID());
        }
    }

    private static void retrackWatchers(MinecraftServer server) {
        for (UUID playerId : RETRACK_NOW) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player != null) {
                player.getServerWorld().getEntityTracker().updateVisibility(player);
            }
        }
        RETRACK_NOW.clear();
        RETRACK_UNTIL.values().removeIf(until -> server.getTickCounter() > until);
    }

    public static boolean isRemote(EntityPlayer player) {
        return SESSIONS.containsKey(player.getUniqueID());
    }

    /** The player flying {@code unit} under "Take control", or null if it's on autopilot. */
    @Nullable
    public static EntityPlayerMP controllerOf(EntityRampUnit unit) {
        Session session = sessionOf(unit);
        return session != null && session.control && !session.drone ? session.player : null;
    }

    @Nullable
    private static Session sessionOf(Entity unit) {
        for (Session session : SESSIONS.values()) {
            if (session.vehicle == unit) {
                return session;
            }
        }
        return null;
    }

    static boolean hasBody(EntityPilotBody body) {
        for (Session session : SESSIONS.values()) {
            if (session.body == body) {
                return true;
            }
        }
        return false;
    }

    /** A unit is about to leave the world (destroyed, detonating): get its rider home before anything goes off. */
    static void onUnitRemoved(Entity unit) {
        Session session = sessionOf(unit);
        if (session != null) {
            end(session.player, "message.combat_drones.remote.unit_lost");
        }
    }

    /** Damage to a stand-in body goes to its real player. Returns whether there was one to hurt. */
    static boolean hurtThroughBody(EntityPilotBody body, DamageSource source, float amount) {
        for (Session session : SESSIONS.values()) {
            if (session.body == body) {
                forwardingBodyDamage = true;
                try {
                    session.player.attackEntityFrom(source, amount);
                } finally {
                    forwardingBodyDamage = false;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isHoldingPlanner(EntityPlayer player) {
        for (EnumHand hand : EnumHand.values()) {
            if (player.getHeldItem(hand).getItem() == ModContent.missionPlanner) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && !RETRACK_UNTIL.isEmpty()) {
            retrackWatchers(server);
        }
        if (SESSIONS.isEmpty()) {
            return;
        }
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            EntityPlayerMP player = session.player;
            if (!player.isEntityAlive() || player.hasDisconnected()) {
                end(player, null);
            } else if (session.vehicle.isDead || session.vehicle.world != player.world) {
                end(player, "message.combat_drones.remote.unit_lost");
            } else if (!session.drone && !isHoldingPlanner(player)) {
                // (A drone's own pilot checks run in its tick: goggles, remote, signal from home)
                end(player, "message.combat_drones.remote.need_planner");
            } else if (player.getRidingEntity() != session.vehicle || session.body.isDead) {
                end(player, "message.combat_drones.remote.link_lost");
            } else {
                // Potion updates reset the invisible flag whenever any effect changes
                player.setInvisible(true);
                updateLock(session);
            }
        }
    }

    /** Server: a viewer's camera direction (sensor units only; anything else is ignored). */
    public static void onReconLook(EntityPlayerMP player, int unitId, Vec3d direction) {
        Session session = SESSIONS.get(player.getUniqueID());
        DroneType type = session == null || !(session.vehicle instanceof EntityRampUnit) ? null : ((EntityRampUnit) session.vehicle).getDroneType();
        if (type == null || !type.isSensor() || session.vehicle.getEntityId() != unitId
                || !Double.isFinite(direction.lengthSquared()) || direction.lengthSquared() < 1e-6) {
            return;
        }
        session.lookDirection = direction.normalize();
        session.lookTick = player.world.getTotalWorldTime();
    }

    /**
     * Runs the active lock for a recon viewer: the enemy closest to the camera's line (within lockConeDegrees, in
     * range and line of sight) is the candidate; holding it for activeLockHoldTicks locks it.
     */
    private static void updateLock(Session session) {
        if (!(session.vehicle instanceof EntityRampUnit)) {
            return;
        }
        DroneType type = ((EntityRampUnit) session.vehicle).getDroneType();
        UUID owner = ((EntityRampUnit) session.vehicle).getOwner();
        if (type == null || !type.isSensor() || owner == null) {
            return;
        }
        boolean fresh = session.lookDirection != null && session.player.world.getTotalWorldTime() - session.lookTick <= LOOK_STALE_TICKS;
        Entity candidate = fresh ? lockCandidate(session, owner) : null;
        if (session.lock.update(candidate == null ? LockTracker.NONE : candidate.getEntityId())) {
            ReconIntel.lock(owner, candidate);
            session.player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.recon.locked", candidate.getName()), true);
        }
        float progress = (float) session.lock.getProgress();
        // Only on change, in 5% steps
        if (Math.abs(progress - session.sentProgress) >= 0.05F || (progress == 0) != (session.sentProgress == 0) || (progress >= 1) != (session.sentProgress >= 1)) {
            session.sentProgress = progress;
            ModNetwork.CHANNEL.sendTo(new LockProgressPacket(progress), session.player);
        }
    }

    @Nullable
    private static Entity lockCandidate(Session session, UUID owner) {
        EntityRampUnit unit = (EntityRampUnit) session.vehicle;
        Vec3d eye = ReconIntel.sensorPosition(unit);
        double range = ModConfig.activeLockRange;
        List<Entity> targets = new ArrayList<>(unit.world.playerEntities);
        for (Session other : SESSIONS.values()) {
            if (other.body.world == unit.world) {
                targets.add(other.body); // stand-in bodies only exist during sessions
            }
        }
        Entity best = null;
        double bestAngle = Double.MAX_VALUE;
        Vec3d rayEnd = eye.add(session.lookDirection.scale(range));
        for (Entity target : targets) {
            if (!ReconIntel.isEnemy(owner, target) || target.getDistanceSq(eye.x, eye.y, eye.z) > range * range) {
                continue;
            }
            Vec3d toTarget = new Vec3d(target.posX - eye.x, target.posY + target.height / 2 - eye.y, target.posZ - eye.z);
            double angle = LockTracker.angleBetween(session.lookDirection.x, session.lookDirection.y, session.lookDirection.z,
                    toTarget.x, toTarget.y, toTarget.z);
            // Under the crosshair: within the cone, or the camera line passes through the target's (generously grown) box
            boolean aimed = angle <= ModConfig.lockConeDegrees
                    || target.getEntityBoundingBox().grow(LOCK_BOX_MARGIN).calculateIntercept(eye, rayEnd) != null;
            if (aimed && angle < bestAngle && ReconIntel.hasLineOfSight(unit.world, eye, target)) {
                best = target;
                bestAngle = angle;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Runs before vanilla saves the player and removes their mount, so they're saved at home, unit untouched
        if (event.player instanceof EntityPlayerMP) {
            end((EntityPlayerMP) event.player, null);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // A crash mid-session saved them at the unit: send them home
        if (event.player instanceof EntityPlayerMP && event.player.getEntityData().hasKey(HOME_TAG)) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            player.dismountRidingEntity();
            player.setInvisible(false);
            returnHome(player);
        }
    }

    /** Dying mid-session: back home first, so the items drop there and not under the unit. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP) {
            end((EntityPlayerMP) event.getEntityLiving(), null);
        }
    }

    /**
     * The rider can't be hurt where the unit is; their body takes the hits instead. Attacks aimed at the rider's
     * hitbox are really aimed at the unit it overlaps, so those go to the unit (explosions already hit it directly).
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP) || forwardingBodyDamage) {
            return;
        }
        Session session = SESSIONS.get(event.getEntityLiving().getUniqueID());
        if (session == null) {
            return;
        }
        event.setCanceled(true);
        DamageSource source = event.getSource();
        if (source.getTrueSource() != null && source.getTrueSource() != session.player && !source.isExplosion()) {
            session.vehicle.attackEntityFrom(source, event.getAmount());
        }
    }

    /**
     * Second line of defence: some mods hurt entities without going through the attack event. Whatever reaches the
     * rider (except hits relayed from their body) is dropped here; hits on the vehicle never hurt its pilot.
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!forwardingBodyDamage && event.getEntityLiving() instanceof EntityPlayer && isRemote((EntityPlayer) event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!forwardingBodyDamage && event.getEntityLiving() instanceof EntityPlayer && isRemote((EntityPlayer) event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (isRemote(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onXpPickup(PlayerPickupXpEvent event) {
        if (isRemote(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent event) {
        if (event.isCancelable() && isRemote(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (isRemote(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    /** Mobs near the unit ignore the invisible rider. */
    @SubscribeEvent
    public static void onSetTarget(LivingSetAttackTargetEvent event) {
        if (event.getTarget() instanceof EntityPlayer && isRemote((EntityPlayer) event.getTarget()) && event.getEntityLiving() instanceof EntityLiving) {
            ((EntityLiving) event.getEntityLiving()).setAttackTarget(null);
        }
    }
}
