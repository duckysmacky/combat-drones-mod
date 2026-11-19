package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.item.ItemLauncher;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * Server: loading a launcher's round from the inventory (creative: free). While it runs the vanilla cooldown sweep is
 * over the launcher, which is also what the client times its reload animation by. The round is only taken when the
 * reload finishes; the caller counts the ticks down and cancels it if the launcher leaves the hand.
 */
final class LauncherReload {
    private LauncherReload() {
    }

    /** Starts loading the held, empty {@code stack}: ticks the reload takes, or -1 if there is nothing to do or no round. */
    static int start(EntityPlayerMP player, ItemStack stack) {
        ItemLauncher launcher = (ItemLauncher) stack.getItem();
        if (ItemLauncher.isLoaded(stack)) {
            return -1;
        }
        if (!player.capabilities.isCreativeMode && !player.inventory.hasItemStack(new ItemStack(launcher.ammo()))) {
            ResourceLocation id = launcher.getRegistryName();
            player.sendStatusMessage(new TextComponentTranslation("message." + id.getNamespace() + "." + id.getPath() + ".no_ammo"), true);
            Feedback.deny(player);
            return -1;
        }
        player.resetActiveHand();
        int ticks = launcher.reloadTicks();
        player.world.playSound(null, player.posX, player.posY + player.getEyeHeight(), player.posZ, launcher.reloadSound(), SoundCategory.PLAYERS, 1.0F, 1.0F);
        if (ticks == 0) {
            finish(player, stack);
        } else {
            player.getCooldownTracker().setCooldown(launcher, ticks);
        }
        return ticks;
    }

    static void finish(EntityPlayerMP player, ItemStack stack) {
        // Checked again: the round may have been dropped meanwhile
        if (player.capabilities.isCreativeMode || player.inventory.clearMatchingItems(((ItemLauncher) stack.getItem()).ammo(), -1, 1, null) > 0) {
            ItemLauncher.setLoaded(stack, true);
        }
    }

    /** The launcher left the hand mid-reload: the sweep goes, the round isn't used. */
    static void cancel(EntityPlayerMP player, ItemLauncher launcher) {
        player.getCooldownTracker().removeCooldown(launcher);
    }
}
