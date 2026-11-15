package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.entity.EntityEmpBlast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

/**
 * EMP Blast Gadget: right-click charges it up (unless buildUp is off) and sets off an EMP field round the user that
 * grows to its full radius ({@link EntityEmpBlast}).
 */
public class ItemEmpBlast extends ItemCharged {
    /** Length of the build-up whine as recorded, seconds; it's played faster or slower to fit buildUpSeconds. */
    private static final float CHARGE_SOUND_SECONDS = 1.0F;

    @Override
    protected int maxCharges() {
        return ModConfig.blastCharges;
    }

    @Override
    protected int reloadTicks() {
        return (int) Math.round(ModConfig.blastReloadSeconds * 20);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && spendCharge(player, stack)) {
            world.spawnEntity(new EntityEmpBlast(world, player));
            if (ModConfig.blastBuildUp) {
                world.playSound(null, player.posX, player.posY + player.height / 2, player.posZ, ModSounds.EMP_CHARGE, SoundCategory.PLAYERS, 1.5F,
                        CHARGE_SOUND_SECONDS / (float) ModConfig.blastBuildUpSeconds);
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
