package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemStinger;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.network.LauncherInputPacket;
import io.github.duckysmacky.combatdrones.network.LockWarningPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.StingerStatePacket;
import io.github.duckysmacky.combatdrones.physics.LockOnAttempt;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every held Stinger. While its holder aims (uses the item) with a missile loaded, the aircraft nearest the
 * middle of the scope inside the seeker circle, in range and in sight, is sought: kept there for lockSeconds it is
 * locked, and stays locked while it stays there. The server works from the player's own look direction, so the client
 * only sends the trigger and the reload key. Never players: only flying vehicles the launcher doesn't spare.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class StingerServer {
    /** Ticks the target may slip out of the circle (or behind something) before the seeker gives up. */
    private static final int GRACE_TICKS = 5;
    private static final int WARNING_INTERVAL_TICKS = 5;
    /** How long a target's "locked" warning stays up without news, seconds: refreshed while the lock or a missile lasts. */
    private static final float LOCKED_WARNING_SECONDS = 1.0F;

    private static final class State {
        /** Hotbar slot the launcher is in: changing it puts everything down. */
        int slot;
        @Nullable
        LockOnAttempt seeking;
        /** Entity ID of the locked target, or -1. */
        int locked = -1;
        int lostTicks;
        /** Ticks left of a reload, 0 = not reloading. */
        int reloading;
        int sentTarget = -1;
        float sentProgress;
        @Nullable
        EntityPlayerMP warned;
        boolean warnedLocked;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private StingerServer() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        ItemStack stack = player.getHeldItemMainhand();
        State state = STATES.get(player.getUniqueID());
        boolean holding = stack.getItem() instanceof ItemStinger && player.isEntityAlive();
        if (state != null && (!holding || state.slot != player.inventory.currentItem)) {
            putDown(player, state);
            state = null;
        }
        if (!holding) {
            return;
        }
        if (state == null) {
            state = new State();
            state.slot = player.inventory.currentItem;
            STATES.put(player.getUniqueID(), state);
        }
        if (state.reloading > 0 && --state.reloading == 0) {
            LauncherReload.finish(player, stack);
        }
        boolean aiming = state.reloading == 0 && ItemStinger.isLoaded(stack) && player.isHandActive() && player.getActiveHand() == EnumHand.MAIN_HAND;
        seek(player, state, aiming);
        report(player, state);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        State state = STATES.remove(event.player.getUniqueID());
        if (state != null) {
            warn(state, null, false, 0);
        }
    }

    /** The launcher left the hand: the lock goes, and a reload under way is abandoned (the missile isn't used). */
    private static void putDown(EntityPlayerMP player, State state) {
        STATES.remove(player.getUniqueID());
        if (state.reloading > 0) {
            LauncherReload.cancel(player, (ItemStinger) ModContent.stinger);
        }
        warn(state, null, false, 0);
        if (state.sentTarget >= 0 || state.sentProgress > 0) {
            ModNetwork.CHANNEL.sendTo(new StingerStatePacket(-1, 0), player);
        }
    }

    private static void seek(EntityPlayerMP player, State state, boolean aiming) {
        if (!aiming) {
            state.seeking = null;
            state.locked = -1;
            return;
        }
        if (state.locked >= 0) {
            Entity target = player.world.getEntityByID(state.locked);
            if (lockable(player, target) && inSeeker(player, target) >= 0) {
                state.lostTicks = 0;
            } else if (++state.lostTicks > GRACE_TICKS) {
                state.locked = -1;
            }
            return;
        }
        if (state.seeking != null && state.seeking.isRunning()) {
            int id = state.seeking.getTarget();
            Entity target = player.world.getEntityByID(id);
            LockOnAttempt.Result result = state.seeking.tick(lockable(player, target) && inSeeker(player, target) >= 0, true);
            if (result == LockOnAttempt.Result.LOCKED) {
                state.locked = id;
                state.lostTicks = 0;
            }
            return;
        }
        Entity candidate = candidate(player);
        if (candidate != null) {
            state.seeking = new LockOnAttempt((int) Math.round(ModConfig.stingerLockSeconds * 20), GRACE_TICKS);
            state.seeking.start(candidate.getEntityId());
        }
    }

    /** Tells the shooter's client what the seeker is on and how far along (on change), and warns the target. */
    private static void report(EntityPlayerMP player, State state) {
        boolean seeking = state.seeking != null && state.seeking.isRunning();
        int target = state.locked >= 0 ? state.locked : seeking ? state.seeking.getTarget() : -1;
        // Just started reads as a sliver, so the client knows the seeker has something
        float progress = state.locked >= 0 ? 1 : seeking ? Math.max(0.01F, (float) state.seeking.getProgress()) : 0;
        if (target != state.sentTarget || Math.abs(progress - state.sentProgress) >= 0.05F || (progress == 0) != (state.sentProgress == 0)
                || (progress >= 1) != (state.sentProgress >= 1)) {
            state.sentTarget = target;
            state.sentProgress = progress;
            ModNetwork.CHANNEL.sendTo(new StingerStatePacket(target, progress), player);
        }
        Entity entity = target >= 0 && ModConfig.stingerWarnTarget ? player.world.getEntityByID(target) : null;
        warn(state, entity == null ? null : warneeOf(entity), state.locked >= 0, progress);
    }

    /**
     * Keeps the player whose aircraft is being sought or locked told, every few ticks, and tells the last one when it
     * stops.
     */
    private static void warn(State state, @Nullable EntityPlayerMP warnee, boolean locked, float progress) {
        if (state.warned != null && (state.warned != warnee || state.warnedLocked != locked) && !state.warned.hasDisconnected()) {
            ModNetwork.CHANNEL.sendTo(new LockWarningPacket(state.warnedLocked, 0, LockWarningPacket.STINGER), state.warned);
        }
        if (warnee != null && (warnee != state.warned || locked != state.warnedLocked || warnee.ticksExisted % WARNING_INTERVAL_TICKS == 0)) {
            ModNetwork.CHANNEL.sendTo(new LockWarningPacket(locked, locked ? LOCKED_WARNING_SECONDS : progress, LockWarningPacket.STINGER), warnee);
        }
        state.warned = warnee;
        state.warnedLocked = locked;
    }

    /** A missile in flight keeps its target's warning up. */
    static void warnInbound(Entity target) {
        EntityPlayerMP warnee = ModConfig.stingerWarnTarget ? warneeOf(target) : null;
        if (warnee != null) {
            ModNetwork.CHANNEL.sendTo(new LockWarningPacket(true, LOCKED_WARNING_SECONDS, LockWarningPacket.STINGER), warnee);
        }
    }

    /** Whoever answers for an aircraft: its owner, if online (only they can fly or watch it). */
    @Nullable
    private static EntityPlayerMP warneeOf(Entity vehicle) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        UUID owner = Emp.ownerOf(vehicle);
        return server == null || owner == null ? null : server.getPlayerList().getPlayerByUUID(owner);
    }

    /** From the client: the trigger (pressed) or the reload key. */
    public static void onInput(EntityPlayerMP player, int action) {
        ItemStack stack = player.getHeldItemMainhand();
        State state = STATES.get(player.getUniqueID());
        if (!(stack.getItem() instanceof ItemStinger) || state == null || state.reloading > 0 || !player.isEntityAlive()) {
            return;
        }
        if (action == LauncherInputPacket.ACTION_PRESS) {
            fire(player, state, stack);
        } else if (action == LauncherInputPacket.ACTION_RELOAD) {
            state.reloading = Math.max(0, LauncherReload.start(player, stack));
        }
    }

    private static void fire(EntityPlayerMP player, State state, ItemStack stack) {
        Entity target = state.locked >= 0 ? player.world.getEntityByID(state.locked) : null;
        String refused = !ItemStinger.isLoaded(stack) ? "message.combat_drones.stinger.empty"
                : target == null && !ModConfig.stingerAllowDumbFire ? "message.combat_drones.stinger.no_lock" : null;
        if (refused != null) {
            player.sendStatusMessage(new TextComponentTranslation(refused), true);
            Feedback.deny(player);
            return;
        }
        Vec3d look = player.getLookVec();
        Vec3d muzzle = player.getPositionEyes(1.0F).add(look.scale(1.2));
        player.world.spawnEntity(new EntityRocket(player.world, player, player.getUniqueID(), muzzle, look.scale(ModConfig.stingerMissileSpeed),
                EntityRocket.Kind.STINGER, (float) ModConfig.stingerBlastPower, (float) ModConfig.stingerDamage)
                .home(target, ModConfig.stingerTurnDegreesPerTick, (int) Math.round(ModConfig.stingerFuelSeconds * 20)));
        ItemStinger.setLoaded(stack, false);
        state.seeking = null;
        state.locked = -1;
        player.world.playSound(null, player.posX, player.posY + player.getEyeHeight(), player.posZ, ModSounds.STINGER_FIRE, SoundCategory.PLAYERS, 4.0F, 1.0F);
        EffectPacket.stingerLaunch(player.world, muzzle, look);
    }

    /** A flying vehicle this player's launcher will go for. */
    private static boolean lockable(EntityPlayerMP player, @Nullable Entity entity) {
        return entity != null && entity.isEntityAlive() && Emp.isVehicle(entity)
                && !Emp.spared(player.getUniqueID(), Emp.ownerOf(entity), ModConfig.stingerSpares);
    }

    /** Degrees between the player's line of sight and {@code target} if it is inside the seeker circle, in range and in sight; else -1. */
    private static double inSeeker(EntityPlayerMP player, Entity target) {
        Vec3d eye = player.getPositionEyes(1.0F);
        double range = ModConfig.stingerLockRange;
        if (target.getDistanceSq(eye.x, eye.y, eye.z) > range * range) {
            return -1;
        }
        Vec3d look = player.getLookVec();
        double angle = LockOnAttempt.angleBetween(look.x, look.y, look.z, target.posX - eye.x, target.posY + target.height / 2 - eye.y, target.posZ - eye.z);
        return angle <= ModConfig.stingerLockConeDegrees && ReconIntel.hasLineOfSight(player.world, eye, target) ? angle : -1;
    }

    /** The lockable aircraft nearest the middle of the scope, or null. */
    @Nullable
    private static Entity candidate(EntityPlayerMP player) {
        Entity best = null;
        double bestAngle = Double.MAX_VALUE;
        AxisAlignedBB area = player.getEntityBoundingBox().grow(ModConfig.stingerLockRange);
        for (Entity entity : player.world.getEntitiesWithinAABB(Entity.class, area, entity -> lockable(player, entity))) {
            double angle = inSeeker(player, entity);
            if (angle >= 0 && angle < bestAngle) {
                best = entity;
                bestAngle = angle;
            }
        }
        return best;
    }
}
