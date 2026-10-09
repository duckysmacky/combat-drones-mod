package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.network.PlannerServer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/**
 * Mission Planner: right-click a loaded Launch Ramp to link it (handled by the ramp entity); right-click the air to
 * open the map of your units, on linked ramps and in flight, to plan, launch and re-route them, or, after "Mark
 * target", to mark a Destroy target.
 */
public class ItemMissionPlanner extends ItemWithTooltip {
    public ItemMissionPlanner() {
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote && !PlannerServer.tryMark((EntityPlayerMP) player)) {
            PlannerServer.openAll((EntityPlayerMP) player, -1);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
}
