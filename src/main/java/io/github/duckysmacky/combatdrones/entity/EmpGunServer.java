package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemEmpGun;
import io.github.duckysmacky.combatdrones.item.ItemLauncher;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.network.LauncherInputPacket;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every held EMP Gun. Pressing the trigger with a cell loaded starts the charge, which the server times; once it
 * has run chargeSeconds the gun stays charged, and releasing the trigger fires an {@link EntityEmpOrb} along the
 * player's own look. Releasing earlier, switching item or slot, or dying lets the charge go without using the cell.
 * The client only sends the trigger's press and release and the reload key; raising the sight changes nothing here.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class EmpGunServer {
    /** Length of the charge whine as recorded, seconds; it's played faster or slower to fit chargeSeconds. */
    private static final float CHARGE_SOUND_SECONDS = 2.0F;
    /** The walking-speed modifier put on the shooter while charging (never saved: gone after a relog). */
    private static final UUID SLOWDOWN_ID = UUID.fromString("5e2b6a40-6f1c-4d0e-9a8e-2c4b7f1d3a91");

    private static final class State {
        /** Hotbar slot the gun is in: changing it puts everything down. */
        int slot;
        /** Ticks charged so far (held at the full charge), or -1 when not charging. */
        int charge = -1;
        /** Ticks left of a reload, 0 = not reloading. */
        int reloading;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private EmpGunServer() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        ItemStack stack = player.getHeldItemMainhand();
        State state = STATES.get(player.getUniqueID());
        boolean holding = stack.getItem() instanceof ItemEmpGun && player.isEntityAlive();
        if (state != null && (!holding || state.slot != player.inventory.currentItem)) {
            putDown(player, state);
            state = null;
        }
        if (!holding) {
            return;
        }
        if (state == null) {
            state = new State();
            state.slot = player.inventory.currentItem;
            STATES.put(player.getUniqueID(), state);
        }
        if (state.reloading > 0 && --state.reloading == 0) {
            LauncherReload.finish(player, stack);
        }
        if (state.charge >= 0 && state.charge < ItemEmpGun.chargeTicks()) {
            state.charge++;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.player.getUniqueID());
        slow(event.player, false);
    }

    /** The gun left the hand: a charge goes, and a reload under way is abandoned (the cell isn't used). */
    private static void putDown(EntityPlayerMP player, State state) {
        STATES.remove(player.getUniqueID());
        if (state.charge >= 0) {
            sound(player, ModSounds.EMP_GUN_CANCEL, 1.0F, 1.0F);
            slow(player, false);
        }
        if (state.reloading > 0) {
            LauncherReload.cancel(player, (ItemLauncher) ModContent.empGun);
        }
    }

    /** From the client: the trigger pressed, released or let go of without firing, or the reload key. */
    public static void onInput(EntityPlayerMP player, int action) {
        ItemStack stack = player.getHeldItemMainhand();
        State state = STATES.get(player.getUniqueID());
        if (!(stack.getItem() instanceof ItemEmpGun) || state == null || state.reloading > 0 || !player.isEntityAlive()) {
            return;
        }
        if (action == LauncherInputPacket.ACTION_PRESS && state.charge < 0) {
            if (!ItemLauncher.isLoaded(stack)) {
                player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.emp_gun.empty"), true);
                Feedback.deny(player);
                return;
            }
            state.charge = 0;
            slow(player, true);
            sound(player, ModSounds.EMP_GUN_CHARGE, 1.5F, CHARGE_SOUND_SECONDS / (float) ModConfig.gunChargeSeconds);
        } else if ((action == LauncherInputPacket.ACTION_RELEASE || action == LauncherInputPacket.ACTION_CANCEL) && state.charge >= 0) {
            boolean charged = state.charge >= ItemEmpGun.chargeTicks();
            state.charge = -1;
            slow(player, false);
            if (charged && action == LauncherInputPacket.ACTION_RELEASE && ItemLauncher.isLoaded(stack)) {
                fire(player, stack);
            } else {
                sound(player, ModSounds.EMP_GUN_CANCEL, 1.0F, 1.0F);
            }
        } else if (action == LauncherInputPacket.ACTION_RELOAD && state.charge < 0) {
            state.reloading = Math.max(0, LauncherReload.start(player, stack));
        }
    }

    private static void fire(EntityPlayerMP player, ItemStack stack) {
        Vec3d look = player.getLookVec();
        Vec3d muzzle = player.getPositionEyes(1.0F).add(look.scale(1.2));
        player.world.spawnEntity(new EntityEmpOrb(player.world, player.getUniqueID(), muzzle, look.scale(ModConfig.gunProjectileSpeed)));
        ItemLauncher.setLoaded(stack, false);
        sound(player, ModSounds.EMP_GUN_FIRE, 4.0F, 1.0F);
        EffectPacket.empGunFire(player.world, muzzle, look);
    }

    /** Slows the shooter down while the gun charges (vanilla walking speed attribute, synced to their client). */
    private static void slow(EntityPlayer player, boolean on) {
        IAttributeInstance speed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        speed.removeModifier(SLOWDOWN_ID);
        if (on && ModConfig.gunChargeSlowdown > 0) {
            speed.applyModifier(new AttributeModifier(SLOWDOWN_ID, "EMP Gun charging", -ModConfig.gunChargeSlowdown, 2).setSaved(false));
        }
    }

    private static void sound(EntityPlayerMP player, SoundEvent sound, float volume, float pitch) {
        player.world.playSound(null, player.posX, player.posY + player.getEyeHeight(), player.posZ, sound, SoundCategory.PLAYERS, volume, pitch);
    }
}
