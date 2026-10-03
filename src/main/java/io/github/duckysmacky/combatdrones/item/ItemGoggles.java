package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.client.DroneKeys;
import io.github.duckysmacky.combatdrones.client.Tooltips;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** FPV Goggles: a cosmetic helmet; wearing them is what lets a player enter FPV. */
public class ItemGoggles extends ItemArmor {
    public ItemGoggles(ArmorMaterial material) {
        super(material, 0, EntityEquipmentSlot.HEAD);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Tooltips.addSummary(getTranslationKey(), tooltip);
        Tooltips.addDetails(getTranslationKey(), tooltip, Tooltips.keyName(DroneKeys.TOGGLE_FPV));
    }
}
