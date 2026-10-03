package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.client.Tooltips;
import io.github.duckysmacky.combatdrones.type.BombType;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** A droppable bomb of one size, loaded into a DJI-style drone's bomb bay. */
public class ItemBomb extends Item {
    private final BombType type;

    public ItemBomb(BombType type, int maxStackSize) {
        this.type = type;
        setMaxStackSize(maxStackSize);
    }

    public BombType getBombType() {
        return type;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Tooltips.addSummary(getTranslationKey(), tooltip);
        tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.bomb.stats",
                type.getBlastPower(), type.getBayUnits(), Math.round(type.getWeight() * 100)));
        Tooltips.addDetails("tooltip.combat_drones.bomb", tooltip);
    }
}
