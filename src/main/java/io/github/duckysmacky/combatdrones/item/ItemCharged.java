package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.client.Tooltips;
import io.github.duckysmacky.combatdrones.entity.Feedback;
import io.github.duckysmacky.combatdrones.physics.Recharge;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A hand-held gadget with a few charges that come back one at a time ({@link Recharge}). The count and when its
 * reload started live on the stack (a fresh stack is full); when the last charge is spent the vanilla cooldown sweep
 * covers the icon until the next one is back.
 */
public abstract class ItemCharged extends ItemWithTooltip {
    private static final String TAG_CHARGES = "Charges";
    private static final String TAG_SINCE = "Since";

    protected ItemCharged() {
        setMaxStackSize(1);
    }

    protected abstract int maxCharges();

    protected abstract int reloadTicks();

    /** Server: uses a charge if one is ready; otherwise tells the player how long to wait. */
    protected boolean spendCharge(EntityPlayer player, ItemStack stack) {
        long now = player.world.getTotalWorldTime();
        NBTTagCompound tag = stack.getTagCompound();
        int stored = tag != null && tag.hasKey(TAG_CHARGES) ? tag.getInteger(TAG_CHARGES) : maxCharges();
        long since = tag != null ? tag.getLong(TAG_SINCE) : now;
        if (Recharge.available(stored, since, now, maxCharges(), reloadTicks()) <= 0) {
            long wait = Recharge.ticksToNext(stored, since, now, maxCharges(), reloadTicks());
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.gadget.recharging", (wait + 19) / 20), true);
            Feedback.deny(player);
            return false;
        }
        long[] spent = Recharge.spend(stored, since, now, maxCharges(), reloadTicks());
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(TAG_CHARGES, (int) spent[0]);
        tag.setLong(TAG_SINCE, spent[1]);
        if (spent[0] <= 0) {
            player.getCooldownTracker().setCooldown(this, (int) Recharge.ticksToNext((int) spent[0], spent[1], now, maxCharges(), reloadTicks()));
        }
        return true;
    }

    /** A stack whose reload started in the future (brought from another world) restarts it now. */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        NBTTagCompound tag = stack.getTagCompound();
        if (!world.isRemote && tag != null && tag.getLong(TAG_SINCE) > world.getTotalWorldTime()) {
            tag.setLong(TAG_SINCE, world.getTotalWorldTime());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Tooltips.addSummary(getTranslationKey(), tooltip);
        NBTTagCompound tag = stack.getTagCompound();
        if (world != null && tag != null && tag.hasKey(TAG_CHARGES)) {
            long now = world.getTotalWorldTime();
            int stored = tag.getInteger(TAG_CHARGES);
            long since = tag.getLong(TAG_SINCE);
            int ready = Recharge.available(stored, since, now, maxCharges(), reloadTicks());
            long wait = Recharge.ticksToNext(stored, since, now, maxCharges(), reloadTicks());
            tooltip.add((ready > 0 ? TextFormatting.GREEN : TextFormatting.RED) + I18n.format("tooltip.combat_drones.gadget.charges", ready, maxCharges())
                    + (wait > 0 ? TextFormatting.GRAY + " " + I18n.format("tooltip.combat_drones.gadget.next", (wait + 19) / 20) : ""));
        } else {
            tooltip.add(TextFormatting.GREEN + I18n.format("tooltip.combat_drones.gadget.charges", maxCharges(), maxCharges()));
        }
        Tooltips.addDetails(getTranslationKey(), tooltip);
    }
}
