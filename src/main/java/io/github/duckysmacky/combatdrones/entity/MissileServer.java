package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemMissileTablet;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.TabletActionPacket;
import io.github.duckysmacky.combatdrones.network.TabletStatusPacket;
import io.github.duckysmacky.combatdrones.physics.CruiseMissileFlight;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Missile Control Tablet and of cruise missiles between their entity phases: status for the tablet screen,
 * LAUNCH and strike orders (all re-validated here), and the store's timed transitions (standby running dry, an
 * missing entity written off).
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class MissileServer {
    private static final int TICK_INTERVAL = 5;
    /** Tablet packets of one kind closer together than this (ticks) are dropped. */
    private static final int ACTION_COOLDOWN_TICKS = 4;
    /** Slack after the climb should have ended before a missing missile is written off. */
    private static final int CLIMB_GRACE_TICKS = 600;
    private static final int STRIKE_DEADLINE_TICKS = 2600;

    /** Per player, per action: the world tick it was last accepted. */
    private static final Map<UUID, long[]> LAST_ACTION = new HashMap<>();

    private MissileServer() {
    }

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20);
    }

    public static CruiseMissileFlight.Launch launchSchedule() {
        return new CruiseMissileFlight.Launch(ticks(ModConfig.missileIgnitionSeconds), ticks(ModConfig.missileLiftoffSeconds),
                ticks(ModConfig.missileSlowClimbSeconds), ticks(ModConfig.missileFastClimbSeconds),
                ModConfig.missileLiftoffSpeed, ModConfig.missileSlowClimbSpeed, ModConfig.missileFastClimbSpeed);
    }

    /** Right-click with a linked tablet: show its screen. */
    public static void openTablet(EntityPlayerMP player, ItemStack tablet) {
        UUID padId = ItemMissileTablet.getLinkedPad(tablet);
        if (padId != null) {
            sendStatus(player, padId, ItemMissileTablet.getLinkedDimension(tablet), true);
        }
    }

    public static void onTabletAction(EntityPlayerMP player, TabletActionPacket.Action action, double targetX, double targetZ) {
        long now = player.world.getTotalWorldTime();
        long[] last = LAST_ACTION.computeIfAbsent(player.getUniqueID(), id -> new long[TabletActionPacket.Action.values().length]);
        if (now - last[action.ordinal()] < ACTION_COOLDOWN_TICKS && now >= last[action.ordinal()]) {
            return;
        }
        last[action.ordinal()] = now;
        ItemStack tablet = heldTablet(player);
        UUID padId = tablet == null ? null : ItemMissileTablet.getLinkedPad(tablet);
        if (padId == null) {
            return; // the screen stays up after the tablet was put away; nothing to answer
        }
        int dimension = ItemMissileTablet.getLinkedDimension(tablet);
        if (action == TabletActionPacket.Action.LAUNCH) {
            launch(player, padId, dimension);
        } else if (action == TabletActionPacket.Action.STRIKE) {
            orderStrike(player, padId, targetX, targetZ);
        }
        sendStatus(player, padId, dimension, false);
    }

    @Nullable
    private static ItemStack heldTablet(EntityPlayerMP player) {
        for (EnumHand hand : EnumHand.values()) {
            if (ItemMissileTablet.getLinkedPad(player.getHeldItem(hand)) != null) {
                return player.getHeldItem(hand);
            }
        }
        return null;
    }

    /** The pad entity if its area is loaded, else null. */
    @Nullable
    private static EntityMissilePad findPad(UUID padId, int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        Entity entity = world == null ? null : world.getEntityFromUuid(padId);
        return entity instanceof EntityMissilePad && !entity.isDead ? (EntityMissilePad) entity : null;
    }

    /** What's stopping the pad from launching, or READY. */
    private static TabletStatusPacket.State padState(EntityMissilePad pad) {
        DroneType type = pad.getMissileType();
        if (type == null) {
            return TabletStatusPacket.State.NO_MISSILE;
        }
        if (pad.getFuel() <= 0 || pad.getFuelFraction() < ModConfig.minLaunchFuel) {
            return TabletStatusPacket.State.NO_FUEL;
        }
        return pad.hasOpenSky() ? TabletStatusPacket.State.READY : TabletStatusPacket.State.SKY_BLOCKED;
    }

    private static void launch(EntityPlayerMP player, UUID padId, int dimension) {
        MissileStore store = MissileStore.get(player.world);
        MissileStore.Record previous = store.get(padId);
        EntityMissilePad pad = findPad(padId, dimension);
        if ((previous != null && previous.state.isActive()) || pad == null || !pad.isOwnedBy(player) || padState(pad) != TabletStatusPacket.State.READY) {
            return; // the status reply shows why
        }
        DroneType type = pad.getMissileType();
        long now = pad.world.getTotalWorldTime();
        long standbyTicks = (long) (pad.getFuel() / Math.max(1e-6, type.getDrainRatePerTick()));
        store.launch(pad, type.getId(), now, standbyTicks, now + launchSchedule().totalTicks() + CLIMB_GRACE_TICKS);
        pad.launch();
        player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.tablet.launched"), true);
    }

    /**
     * A strike on a point picked on the tablet map: a missile on standby, the point within range of its pad and inside
     * the world border. It appears over the point at once, armed but holding, and its owner takes control.
     */
    private static void orderStrike(EntityPlayerMP player, UUID padId, double targetX, double targetZ) {
        MissileStore store = MissileStore.get(player.world);
        MissileStore.Record record = store.get(padId);
        long now = player.world.getTotalWorldTime();
        if (record == null || stateOf(record, now) != TabletStatusPacket.State.STANDBY || !player.getUniqueID().equals(record.owner)
                || player.dimension != record.dimension || !Double.isFinite(targetX) || !Double.isFinite(targetZ)) {
            return;
        }
        double distance = Math.hypot(targetX - record.padX, targetZ - record.padZ);
        if (distance > ModConfig.strikeRange || !player.world.getWorldBorder().contains(new BlockPos(targetX, 0, targetZ))) {
            return;
        }
        DroneType type = DroneTypes.get(record.typeId);
        if (type == null) {
            store.finish(record.padId, MissileStore.State.LOST);
            return;
        }
        WorldServer world = (WorldServer) player.world;
        BlockPos column = new BlockPos(targetX, 0, targetZ);
        world.getChunk(column); // loads (or generates) it, so the height is real
        double y = world.getHeight(column).getY() + ModConfig.strikeAltitude;
        // "Up" in the missile's camera points away from the pad, so the map and the view line up
        float refHeading = (float) CruiseMissileFlight.headingTo(record.padX, record.padZ, targetX, targetZ);
        EntityCruiseMissile missile = EntityCruiseMissile.strike(world, type, record.owner, record.padId, targetX, y, targetZ, refHeading);
        world.spawnEntity(missile);
        store.striking(record, targetX, targetZ, now + STRIKE_DEADLINE_TICKS);
        String problem = RemoteSessions.startMissile(player, missile);
        if (problem != null) {
            player.sendStatusMessage(new TextComponentTranslation(problem), true);
        }
    }

    /** The tablet's state for a record, with the derived phases of an airborne missile. */
    private static TabletStatusPacket.State stateOf(MissileStore.Record record, long now) {
        switch (record.state) {
            case CLIMBING:
                return TabletStatusPacket.State.LAUNCHING;
            case AIRBORNE:
                return now < record.standbyEndTick ? TabletStatusPacket.State.STANDBY : TabletStatusPacket.State.LOST;
            case STRIKING:
                return TabletStatusPacket.State.STRIKING;
            case IMPACT:
                return TabletStatusPacket.State.IMPACT;
            case DESTROYED:
                return TabletStatusPacket.State.DESTROYED;
            default:
                return TabletStatusPacket.State.LOST;
        }
    }

    private static void sendStatus(EntityPlayerMP player, UUID padId, int dimension, boolean open) {
        TabletStatusPacket status = new TabletStatusPacket();
        status.open = open;
        status.strikeRange = (float) ModConfig.strikeRange;
        status.steerReach = (float) (ModConfig.strikeAltitude * Math.tan(Math.toRadians(ModConfig.steerConeDegrees)));
        long now = player.world.getTotalWorldTime();
        MissileStore.Record record = MissileStore.get(player.world).get(padId);
        EntityMissilePad pad = findPad(padId, dimension);
        if (pad != null) {
            status.padX = (int) Math.floor(pad.posX);
            status.padY = (int) Math.floor(pad.posY);
            status.padZ = (int) Math.floor(pad.posZ);
        } else if (record != null) {
            status.padX = (int) Math.floor(record.padX);
            status.padY = (int) Math.floor(record.padY);
            status.padZ = (int) Math.floor(record.padZ);
        }
        if (record != null && record.state.isActive()) {
            fillFromRecord(status, record, now);
        } else if (pad != null) {
            status.state = padState(pad);
            status.fuelFraction = pad.getFuelFraction();
        } else {
            status.state = record != null ? stateOf(record, now) : TabletStatusPacket.State.NO_PAD;
        }
        if (status.state.ordinal() >= TabletStatusPacket.State.IMPACT.ordinal() && pad != null && pad.isLoaded()) {
            // A finished strike with a fresh missile already on the pad: show the pad, ready for the next launch
            status.state = padState(pad);
            status.fuelFraction = pad.getFuelFraction();
        }
        ModNetwork.CHANNEL.sendTo(status, player);
    }

    private static void fillFromRecord(TabletStatusPacket status, MissileStore.Record record, long now) {
        status.state = stateOf(record, now);
        status.healthFraction = record.health;
        switch (status.state) {
            case LAUNCHING:
                CruiseMissileFlight.Launch launch = launchSchedule();
                int elapsed = (int) Math.max(0, now - record.launchTick);
                status.stage = launch.stageAt(elapsed).ordinal();
                status.ticksLeft = launch.ticksLeftInStage(elapsed);
                status.fuelFraction = 1;
                break;
            case STANDBY:
                status.ticksLeft = (int) (record.standbyEndTick - now);
                status.fuelFraction = (float) CruiseMissileFlight.standbyFuelFraction(now, record.readyTick, record.standbyEndTick);
                break;
            default:
                break;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCounter() % TICK_INTERVAL != 0) {
            return;
        }
        WorldServer overworld = server.getWorld(0);
        MissileStore store = MissileStore.get(overworld);
        long now = overworld.getTotalWorldTime();
        for (MissileStore.Record record : store.all()) {
            switch (record.state) {
                case CLIMBING:
                case STRIKING:
                    if (now > record.deadline) {
                        store.finish(record.padId, MissileStore.State.LOST);
                    }
                    break;
                case AIRBORNE:
                    if (now >= record.standbyEndTick) {
                        store.finish(record.padId, MissileStore.State.LOST);
                        tellOwner(server, record, "message.combat_drones.tablet.fuel_out");
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static void tellOwner(MinecraftServer server, MissileStore.Record record, String key) {
        EntityPlayerMP owner = record.owner == null ? null : server.getPlayerList().getPlayerByUUID(record.owner);
        if (owner != null) {
            owner.sendMessage(new TextComponentTranslation(key));
        }
    }
}
