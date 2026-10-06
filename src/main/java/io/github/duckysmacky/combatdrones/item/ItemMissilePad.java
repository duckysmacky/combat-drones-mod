package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.entity.EntityMissilePad;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Places a Missile Launch Pad on top of the clicked block; the placer owns it. */
public class ItemMissilePad extends ItemWithTooltip {
    public ItemMissilePad() {
        setMaxStackSize(4);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (facing != EnumFacing.UP) {
            return EnumActionResult.FAIL;
        }
        if (!world.isRemote) {
            world.spawnEntity(new EntityMissilePad(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, player.rotationYaw, player.getUniqueID()));
            if (!player.capabilities.isCreativeMode) {
                player.getHeldItem(hand).shrink(1);
            }
        }
        return EnumActionResult.SUCCESS;
    }
}
