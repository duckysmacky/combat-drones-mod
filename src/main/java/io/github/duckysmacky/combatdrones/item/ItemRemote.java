package io.github.duckysmacky.combatdrones.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** FPV Remote: remembers which drone it's bound to. Linking itself happens on the drone entity. */
public class ItemRemote extends Item {
    private static final String TAG_DRONE = "LinkedDrone";
    private static final String TAG_DRONE_NAME = "LinkedDroneName";

    public ItemRemote() {
        setMaxStackSize(1);
    }

    @Nullable
    public static UUID getLinkedDrone(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasUniqueId(TAG_DRONE) ? tag.getUniqueId(TAG_DRONE) : null;
    }

    public static void link(ItemStack stack, UUID droneId, String droneTranslationKey) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setUniqueId(TAG_DRONE, droneId);
        tag.setString(TAG_DRONE_NAME, droneTranslationKey);
        stack.setTagCompound(tag);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        NBTTagCompound tag = stack.getTagCompound();
        if (getLinkedDrone(stack) != null && tag != null) {
            tooltip.add(I18n.format("item.combat_drones.fpv_remote.linked", I18n.format(tag.getString(TAG_DRONE_NAME))));
        } else {
            tooltip.add(I18n.format("item.combat_drones.fpv_remote.not_linked"));
        }
    }
}
