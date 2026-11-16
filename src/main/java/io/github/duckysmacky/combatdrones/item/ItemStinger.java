package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.client.Tooltips;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Stinger: a shoulder-fired anti-air launcher holding one missile (on the stack). Holding right-click aims it, as
 * vanilla item use with the bow's action: that slows the walk, lets the server know who is aiming and gives everyone
 * else the arms-forward pose. Seeking, locking, firing and reloading run in {@code entity.StingerServer}.
 */
public class ItemStinger extends ItemWithTooltip {
    private static final String TAG_LOADED = "Loaded";

    public ItemStinger() {
        setMaxStackSize(1);
    }

    public static boolean isLoaded(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean(TAG_LOADED);
    }

    public static void setLoaded(ItemStack stack, boolean loaded) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setBoolean(TAG_LOADED, loaded);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        // Main hand only: the other one steadies the tube
        if (hand != EnumHand.MAIN_HAND || player.getCooldownTracker().hasCooldown(this)) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }
        player.setActiveHand(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.BOW;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 72000;
    }

    // Left-click is the trigger: no swing, no punching, no digging

    @Override
    public boolean onEntitySwing(EntityLivingBase entity, ItemStack stack) {
        return true;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
        return true;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, EntityPlayer player) {
        return true;
    }

    @Override
    public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player) {
        return false;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        return 0;
    }

    /** Loading or firing changes the stack's data: that isn't a new item being drawn. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Tooltips.addSummary(getTranslationKey(), tooltip);
        tooltip.add(isLoaded(stack) ? TextFormatting.GREEN + I18n.format("tooltip.combat_drones.stinger.loaded")
                : TextFormatting.RED + I18n.format("tooltip.combat_drones.stinger.empty"));
        Tooltips.addDetails(getTranslationKey(), tooltip);
    }
}
