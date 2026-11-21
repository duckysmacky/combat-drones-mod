package io.github.duckysmacky.combatdrones.item;

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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pilot Detector: right-click sends out a pulse that finds every enemy flying something remotely (their stand-in body)
 * within range and puts a red dot over the body for a while, seen through walls by the scanner (and teammates with
 * dotsForAllies). Everyone near hears the ping and sees the pulse; each pilot found gets a SCANNED banner.
 */
public class ItemPilotDetector extends ItemCharged {
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
        List<IntelPacket.Entry> found = new ArrayList<>(bodies.size());
        for (EntityPilotBody body : bodies) {
            found.add(new IntelPacket.Entry(body.posX, body.posY, body.posZ, body.getEntityId(), "", false, true, true, 0));
        }
        int ticks = (int) Math.round(ModConfig.detectorDotSeconds * 20);
        if (!found.isEmpty()) {
            Set<EntityPlayerMP> warned = new HashSet<>();
            for (EntityPilotBody body : bodies) {
                EntityPlayerMP pilot = ReconIntel.warneeOf(body);
                if (pilot != null && warned.add(pilot)) {
                    ModNetwork.CHANNEL.sendTo(new LockWarningPacket(true, ticks / 20.0F, LockWarningPacket.DETECTOR), pilot);
                }
            }
            ModNetwork.CHANNEL.sendTo(new DetectorPacket(found, ticks, true), player);
            if (ModConfig.detectorDotsForAllies) {
                for (EntityPlayer other : world.playerEntities) {
                    if (other != player && other instanceof EntityPlayerMP && Teams.isTeammate(scanner, other)) {
                        ModNetwork.CHANNEL.sendTo(new DetectorPacket(found, ticks, false), (EntityPlayerMP) other);
                    }
                }
            }
        }
        player.sendStatusMessage(found.isEmpty() ? new TextComponentTranslation("message.combat_drones.detector.none")
                : new TextComponentTranslation("message.combat_drones.detector.found", found.size()), true);
        world.playSound(null, player.posX, player.posY + player.getEyeHeight(), player.posZ, ModSounds.DETECTOR_PING, SoundCategory.PLAYERS,
                (float) Math.max(1, range / 16), 1.0F);
        EffectPacket.scan(world, player.getPositionVector().add(0, player.height / 2, 0), range);
    }
}
