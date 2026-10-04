package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.client.Tooltips;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** A battery pack of one size. Charge is a 0..1 fraction in NBT; no tag means a fresh, full pack. */
public class ItemBattery extends Item {
    private static final String TAG_CHARGE = "Charge";

    private final BatteryPack pack;

    public ItemBattery(BatteryPack pack) {
        this.pack = pack;
        // Fresh (tag-less) packs stack for easy kit handouts; partly used packs carry a charge tag and don't merge
        setMaxStackSize(16);
    }

    public BatteryPack getPack() {
        return pack;
    }

    public static double getCharge(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_CHARGE) ? Math.max(0, Math.min(1, tag.getDouble(TAG_CHARGE))) : 1.0;
    }

    public static ItemStack withCharge(Item batteryItem, double charge) {
        ItemStack stack = new ItemStack(batteryItem);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble(TAG_CHARGE, Math.max(0, Math.min(1, charge)));
        stack.setTagCompound(tag);
        return stack;
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return getCharge(stack) < 1.0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - getCharge(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        // What it is, its charge, then (with Shift) its stats before the how-to lines
        Tooltips.addSummary(getTranslationKey(), tooltip);
        tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.battery.charge", Math.round(getCharge(stack) * 100)));
        if (GuiScreen.isShiftKeyDown()) {
            tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.battery.multipliers",
                    pack.getCapacityMultiplier(), Math.round(pack.getPerformanceMultiplier() * 100)));
        }
        Tooltips.addDetails(getTranslationKey(), tooltip);
    }
}
