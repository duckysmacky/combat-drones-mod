package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.client.Tooltips;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

public class ItemBlockWithTooltip extends ItemBlock {
    public ItemBlockWithTooltip(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Tooltips.addSummary(getTranslationKey(), tooltip);
        Tooltips.addDetails(getTranslationKey(), tooltip);
    }
}
