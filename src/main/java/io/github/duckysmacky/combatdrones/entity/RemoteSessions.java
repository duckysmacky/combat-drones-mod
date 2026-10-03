package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.RemoteSessionPacket;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketSetPassengers;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
        final EntityRampUnit unit;
        final boolean control;
        final EntityPilotBody body;
        final boolean wasInvisible;

        Session(EntityPlayerMP player, EntityRampUnit unit, boolean control, EntityPilotBody body, boolean wasInvisible) {
            this.player = player;
            this.unit = unit;
            this.control = control;
            this.body = body;
            this.wasInvisible = wasInvisible;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
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
            if (current.unit == unit) {
                end(player, null);
            } else {
                // Switching units: hop straight across, keeping the body and the saved home
                current.player.dismountRidingEntity();
                SESSIONS.remove(player.getUniqueID());
                mount(new Session(player, unit, control, current.body, current.wasInvisible));
                return null;
            }
        }
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
        Session session = new Session(player, unit, control, body, player.isInvisible());
        SESSIONS.put(player.getUniqueID(), session);
        player.world.spawnEntity(body);
        player.setInvisible(true);
        mount(session);
        return null;
    }

    private static void mount(Session session) {
        SESSIONS.put(session.player.getUniqueID(), session);
        EntityPlayerMP player = session.player;
        player.connection.setPlayerLocation(session.unit.posX, session.unit.posY, session.unit.posZ, player.rotationYaw, player.rotationPitch);
        player.startRiding(session.unit, true);
        ModNetwork.CHANNEL.sendTo(new RemoteSessionPacket(session.unit.getEntityId(), session.control), player);
    }

    /** Ends the player's session, if any, and puts them back where they left their body. */
    public static void end(EntityPlayerMP player, @Nullable String message) {
        Session session = SESSIONS.remove(player.getUniqueID());
        if (session == null) {
            return;
        }
        if (player.getRidingEntity() == session.unit) {
            player.dismountRidingEntity();
            // Tell the client about the dismount before the teleport, or it would apply the teleport while still
            // riding and snap back onto the unit
            player.connection.sendPacket(new SPacketSetPassengers(session.unit));
        }
        returnHome(player);
        player.setInvisible(session.wasInvisible);
        session.body.setDead();
        ModNetwork.CHANNEL.sendTo(new RemoteSessionPacket(-1, false), player);
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
    }

    public static boolean isRemote(EntityPlayer player) {
        return SESSIONS.containsKey(player.getUniqueID());
    }

    /** The player flying {@code unit} under "Take control", or null if it's on autopilot. */
    @Nullable
    public static EntityPlayerMP controllerOf(EntityRampUnit unit) {
        Session session = sessionOf(unit);
        return session != null && session.control ? session.player : null;
    }

    @Nullable
    private static Session sessionOf(EntityRampUnit unit) {
        for (Session session : SESSIONS.values()) {
            if (session.unit == unit) {
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
    static void onUnitRemoved(EntityRampUnit unit) {
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
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) {
            return;
        }
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            EntityPlayerMP player = session.player;
            if (!player.isEntityAlive() || player.hasDisconnected()) {
                end(player, null);
            } else if (session.unit.isDead || session.unit.world != player.world) {
                end(player, "message.combat_drones.remote.unit_lost");
            } else if (!isHoldingPlanner(player)) {
                end(player, "message.combat_drones.remote.need_planner");
            } else if (player.getRidingEntity() != session.unit || session.body.isDead) {
                end(player, "message.combat_drones.remote.link_lost");
            }
        }
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
            session.unit.attackEntityFrom(source, event.getAmount());
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
