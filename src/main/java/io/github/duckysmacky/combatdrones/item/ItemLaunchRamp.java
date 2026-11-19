package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityRunway;
import io.github.duckysmacky.combatdrones.entity.Feedback;
import net.minecraft.util.SoundCategory;
import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.List;

/**
 * Places a Munition Ramp on top of a block, aimed exactly where the player is looking, or a runway: starting at the
 * clicked block and running the compass direction the player faces, if the strip has room.
 */
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
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        double x = pos.getX() + hitX;
        double z = pos.getZ() + hitZ;
        if (kind.isRunway()) {
            EnumFacing heading = player.getHorizontalFacing();
            List<BlockPos> cells = EntityRunway.footprint(kind, pos.up(), heading);
            if (!EntityRunway.fits(world, cells)) {
                player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.runway.no_space"), true);
                Feedback.deny(player);
                return EnumActionResult.FAIL;
            }
            BlockPos first = cells.get(0);
            BlockPos last = cells.get(cells.size() - 1);
            x = (first.getX() + last.getX()) / 2.0 + 0.5;
            z = (first.getZ() + last.getZ()) / 2.0 + 0.5;
            world.spawnEntity(new EntityRunway(world, kind, x, pos.getY() + 1, z, heading, player));
        } else {
            world.spawnEntity(new EntityLaunchRamp(world, kind, x, pos.getY() + 1, z, player.rotationYaw));
        }
        world.playSound(null, x, pos.getY() + 1, z, ModSounds.RAMP_PLACE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        if (!player.capabilities.isCreativeMode) {
            player.getHeldItem(hand).shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }
}
