package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.client.Tooltips;
import io.github.duckysmacky.combatdrones.entity.EntityMissilePad;
import io.github.duckysmacky.combatdrones.entity.MissileServer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Missile Control Tablet: linked to one Missile Launch Pad (right-click the pad), then opened from anywhere (right-click the
 * air) to launch its missile, watch it climb and wait, pick the strike point and take control of the dive.
 */
public class ItemMissileTablet extends Item {
    private static final String TAG_PAD = "LinkedPad";
    private static final String TAG_X = "PadX";
    private static final String TAG_Y = "PadY";
    private static final String TAG_Z = "PadZ";
    private static final String TAG_DIMENSION = "PadDim";

    public ItemMissileTablet() {
        setMaxStackSize(1);
    }

    @Nullable
    public static UUID getLinkedPad(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return stack.getItem() == ModContent.missileTablet && tag != null && tag.hasUniqueId(TAG_PAD) ? tag.getUniqueId(TAG_PAD) : null;
    }

    /** Dimension of the linked pad (only meaningful when linked). */
    public static int getLinkedDimension(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? 0 : tag.getInteger(TAG_DIMENSION);
    }

    public static void link(ItemStack stack, EntityMissilePad pad) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setUniqueId(TAG_PAD, pad.getUniqueID());
        tag.setInteger(TAG_X, (int) Math.floor(pad.posX));
        tag.setInteger(TAG_Y, (int) Math.floor(pad.posY));
        tag.setInteger(TAG_Z, (int) Math.floor(pad.posZ));
        tag.setInteger(TAG_DIMENSION, pad.world.provider.getDimension());
        stack.setTagCompound(tag);
    }

    /** True if the player holds (either hand) a tablet linked to {@code padId}. */
    public static boolean isHoldingLinked(EntityPlayer player, UUID padId) {
        for (EnumHand hand : EnumHand.values()) {
            if (padId.equals(getLinkedPad(player.getHeldItem(hand)))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            if (getLinkedPad(stack) == null) {
                player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.tablet.not_linked"), true);
            } else {
                MissileServer.openTablet((EntityPlayerMP) player, stack);
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Tooltips.addSummary(getTranslationKey(), tooltip);
        NBTTagCompound tag = stack.getTagCompound();
        if (getLinkedPad(stack) != null && tag != null) {
            tooltip.add(TextFormatting.GREEN + I18n.format("item.combat_drones.missile_control_tablet.linked", tag.getInteger(TAG_X), tag.getInteger(TAG_Y),
                    tag.getInteger(TAG_Z)));
        } else {
            tooltip.add(TextFormatting.GRAY + I18n.format("item.combat_drones.missile_control_tablet.not_linked"));
        }
        Tooltips.addDetails(getTranslationKey(), tooltip);
    }
}
