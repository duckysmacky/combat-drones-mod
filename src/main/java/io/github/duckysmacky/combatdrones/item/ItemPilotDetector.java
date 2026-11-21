package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.entity.EntityPilotBody;
import io.github.duckysmacky.combatdrones.entity.ReconIntel;
import io.github.duckysmacky.combatdrones.entity.Teams;
import io.github.duckysmacky.combatdrones.network.DetectorPacket;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import io.github.duckysmacky.combatdrones.network.LockWarningPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pilot Detector: right-click sends out a pulse that finds every enemy flying something remotely (their stand-in body)
 * within range and puts a red dot over the body for a while, seen through walls by the scanner (and teammates with
 * dotsForAllies), live or refreshed every pingIntervalSeconds. Everyone near hears the ping and sees the pulse; each pilot found gets a SCANNED banner.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public class ItemPilotDetector extends ItemCharged {
    private static final List<Scan> SCANS = new ArrayList<>();

    @Override
    protected int maxCharges() {
        return ModConfig.detectorCharges;
    }

    @Override
    protected int reloadTicks() {
        return (int) Math.round(ModConfig.detectorReloadSeconds * 20);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && spendCharge(player, stack)) {
            scan((EntityPlayerMP) player);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    private static void scan(EntityPlayerMP player) {
        World world = player.world;
        double range = ModConfig.detectorRange;
        UUID scanner = player.getUniqueID();
        List<EntityPilotBody> bodies = world.getEntitiesWithinAABB(EntityPilotBody.class, player.getEntityBoundingBox().grow(range),
                body -> body != null && body.isEntityAlive() && body.getOwner() != null && body.getDistanceSq(player) <= range * range
                        && !Teams.isTeammate(scanner, body.getOwner()));
        boolean live = pingTicks() <= 0;
        List<IntelPacket.Entry> found = entries(bodies, live);
        int ticks = (int) Math.round(ModConfig.detectorDotSeconds * 20);
        if (!found.isEmpty()) {
            List<UUID> viewers = new ArrayList<>();
            viewers.add(scanner);
            Set<EntityPlayerMP> warned = new HashSet<>();
            for (EntityPilotBody body : bodies) {
                EntityPlayerMP pilot = ReconIntel.warneeOf(body);
                if (pilot != null && warned.add(pilot)) {
                    ModNetwork.CHANNEL.sendTo(new LockWarningPacket(true, ticks / 20.0F, LockWarningPacket.DETECTOR), pilot);
                }
            }
            ModNetwork.CHANNEL.sendTo(new DetectorPacket(found, ticks, true, live), player);
            if (ModConfig.detectorDotsForAllies) {
                for (EntityPlayer other : world.playerEntities) {
                    if (other != player && other instanceof EntityPlayerMP && Teams.isTeammate(scanner, other)) {
                        ModNetwork.CHANNEL.sendTo(new DetectorPacket(found, ticks, false, live), (EntityPlayerMP) other);
                        viewers.add(other.getUniqueID());
                    }
                }
            }
            if (!live) {
                List<Integer> bodyIds = new ArrayList<>(bodies.size());
                for (EntityPilotBody body : bodies) {
                    bodyIds.add(body.getEntityId());
                }
                SCANS.add(new Scan(viewers, bodyIds, world.provider.getDimension(), ticks, pingTicks()));
            }
        }
        player.sendStatusMessage(found.isEmpty() ? new TextComponentTranslation("message.combat_drones.detector.none")
                : new TextComponentTranslation("message.combat_drones.detector.found", found.size()), true);
        world.playSound(null, player.posX, player.posY + player.getEyeHeight(), player.posZ, ModSounds.DETECTOR_PING, SoundCategory.PLAYERS,
                (float) Math.max(1, range / 16), 1.0F);
        EffectPacket.scan(world, player.getPositionVector().add(0, player.height / 2, 0), range);
    }

    /** Ticks between pings of a scan's dots; 0 = live (dots follow the bodies on the client, no pings). */
    private static int pingTicks() {
        return ModConfig.detectorPingIntervalSeconds <= 0 ? 0 : Math.max(1, (int) Math.round(ModConfig.detectorPingIntervalSeconds * 20));
    }

    private static List<IntelPacket.Entry> entries(List<EntityPilotBody> bodies, boolean live) {
        List<IntelPacket.Entry> found = new ArrayList<>(bodies.size());
        for (EntityPilotBody body : bodies) {
            found.add(new IntelPacket.Entry(body.posX, body.posY, body.posZ, body.getEntityId(), "", false, true, live, 0));
        }
        return found;
    }

    /** With a ping interval: sends each running scan's viewers its bodies' fresh positions at every ping until it runs out. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (event.phase != TickEvent.Phase.END || server == null || SCANS.isEmpty()) {
            return;
        }
        SCANS.removeIf(scan -> --scan.ticksLeft <= 0);
        for (Scan scan : SCANS) {
            if (--scan.untilPing > 0) {
                continue;
            }
            scan.untilPing = Math.max(1, pingTicks());
            WorldServer world = server.getWorld(scan.dimension);
            List<EntityPilotBody> bodies = new ArrayList<>();
            for (int id : scan.bodyIds) {
                Entity body = world == null ? null : world.getEntityByID(id);
                if (body instanceof EntityPilotBody && body.isEntityAlive()) {
                    bodies.add((EntityPilotBody) body);
                }
            }
            if (bodies.isEmpty()) {
                continue;
            }
            List<IntelPacket.Entry> found = entries(bodies, false);
            for (UUID viewer : scan.viewers) {
                EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(viewer);
                if (player != null) {
                    ModNetwork.CHANNEL.sendTo(new DetectorPacket(found, scan.ticksLeft, false, false), player);
                }
            }
        }
    }

    /** A scan whose dots are pinged: who sees them, which bodies, in which dimension, ticks left on it and to the next ping. */
    private static final class Scan {
        final List<UUID> viewers;
        final List<Integer> bodyIds;
        final int dimension;
        int ticksLeft;
        int untilPing;

        Scan(List<UUID> viewers, List<Integer> bodyIds, int dimension, int ticksLeft, int untilPing) {
            this.viewers = viewers;
            this.bodyIds = bodyIds;
            this.dimension = dimension;
            this.ticksLeft = ticksLeft;
            this.untilPing = untilPing;
        }
    }
}
