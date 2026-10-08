package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Places a Munition or Plane Ramp on top of a block, aimed exactly where the player is looking. */
public class ItemLaunchRamp extends ItemWithTooltip {
    private final EntityLaunchRamp.Kind kind;

    public ItemLaunchRamp(EntityLaunchRamp.Kind kind) {
        this.kind = kind;
        setMaxStackSize(4);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (facing != EnumFacing.UP) {
            return EnumActionResult.FAIL;
        }
        if (!world.isRemote) {
            world.spawnEntity(new EntityLaunchRamp(world, kind, pos.getX() + hitX, pos.getY() + 1, pos.getZ() + hitZ, player.rotationYaw));
            if (!player.capabilities.isCreativeMode) {
                player.getHeldItem(hand).shrink(1);
            }
        }
        return EnumActionResult.SUCCESS;
    }
}
