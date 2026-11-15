package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.client.Tooltips;
import io.github.duckysmacky.combatdrones.entity.EntityEmpStation;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** Places an EMP Field Station on top of the clicked block, switched off, with the charge the item carries. */
public class ItemEmpStation extends ItemWithTooltip {
    private static final String TAG_CHARGE = "Charge";

    public ItemEmpStation() {
        setMaxStackSize(1);
    }

    /** A fresh station is fully charged. */
    public static float getCharge(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_CHARGE) ? tag.getFloat(TAG_CHARGE) : EntityEmpStation.MAX_CHARGE;
    }

    public static ItemStack withCharge(ItemStack stack, float charge) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setFloat(TAG_CHARGE, charge);
        return stack;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (facing != EnumFacing.UP) {
            return EnumActionResult.FAIL;
        }
        if (!world.isRemote) {
            ItemStack stack = player.getHeldItem(hand);
            world.spawnEntity(new EntityEmpStation(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, player.rotationYaw, player.getUniqueID(),
                    getCharge(stack)));
            world.playSound(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, ModSounds.RAMP_PLACE, SoundCategory.PLAYERS, 0.8F, 1.1F);
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Tooltips.addSummary(getTranslationKey(), tooltip);
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.combat_drones.emp_station.charge", Math.round(getCharge(stack))));
        Tooltips.addDetails(getTranslationKey(), tooltip);
    }
}
