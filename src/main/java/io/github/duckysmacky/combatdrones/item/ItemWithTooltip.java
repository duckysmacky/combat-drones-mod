package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.client.Tooltips;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** Plain mod item with the standard summary + SHIFT-details tooltip from the lang file. */
public class ItemWithTooltip extends Item {
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Tooltips.addSummary(getTranslationKey(), tooltip);
        Tooltips.addDetails(getTranslationKey(), tooltip);
    }
}
