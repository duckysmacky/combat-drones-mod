package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.client.hud.UiSounds;
import io.github.duckysmacky.combatdrones.item.ItemEmpGun;
import io.github.duckysmacky.combatdrones.item.ItemLauncher;
import io.github.duckysmacky.combatdrones.item.ItemStinger;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.LauncherInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;

/**
 * The launcher ({@link ItemLauncher}) in this player's hands: sends the trigger (attack key, pressed and released) and
 * the reload key to the server. For the Stinger it also keeps what the server says the seeker is doing, and plays its
 * tones (beeps quickening as the lock builds, a steady tone once locked). Raising it is vanilla item use;
 * {@link #scopeAmount} eases the sight in and out for the camera.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class LauncherController {
    /** Ticks the scope takes to come up or down. */
    private static final float SCOPE_TICKS = 4;
    /** Seek beeps come this many ticks apart at first, then faster as the lock builds. */
    private static final int MAX_BEEP_TICKS = 10;
    private static final int MIN_BEEP_TICKS = 2;

    private static int targetId = -1;
    private static float progress;
    private static boolean fireHeld;
    /** Ticks the trigger has been held for (0 = not held). */
    private static int triggerTicks;
    private static boolean reloadHeld;
    private static int beepDelay;
    private static float scope;
    private static float prevScope;
    @Nullable
    private static ISound lockTone;

    private LauncherController() {
    }

    /** The launcher in the main hand, or an empty stack. */
    public static ItemStack held() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getHeldItemMainhand();
        return stack.getItem() instanceof ItemLauncher ? stack : ItemStack.EMPTY;
    }

    /** How much the held launcher's sight magnifies (1 with none). */
    public static float zoom() {
        ItemStack stack = held();
        return stack.isEmpty() ? 1 : ((ItemLauncher) stack.getItem()).zoom();
    }

    /** Holding the launcher up (right-click held), in any view. */
    public static boolean isAiming() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        return !held().isEmpty() && player.isHandActive() && player.getActiveHand() == EnumHand.MAIN_HAND;
    }

    /** 0 = hip, 1 = looking through the scope (first person only), eased. */
    public static float scopeAmount(float partialTicks) {
        float amount = prevScope + (scope - prevScope) * partialTicks;
        return amount * amount * (3 - 2 * amount);
    }

    /** 0..1 through a reload (0 when not reloading). */
    public static float reloadProgress(float partialTicks) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        ItemStack stack = held();
        float left = stack.isEmpty() ? 0 : player.getCooldownTracker().getCooldown(stack.getItem(), partialTicks);
        return left <= 0 ? 0 : 1 - left;
    }

    /**
     * 0..1 through an EMP Gun's charge, as this client times it (from its own trigger and the local chargeSeconds):
     * only for the sight, the server decides when the shot is charged. 0 when not charging.
     */
    public static float chargeProgress(float partialTicks) {
        ItemStack stack = held();
        if (!(stack.getItem() instanceof ItemEmpGun) || triggerTicks <= 0 || !ItemLauncher.isLoaded(stack) || reloadProgress(partialTicks) > 0) {
            return 0;
        }
        return Math.min(1, (triggerTicks + partialTicks) / ItemEmpGun.chargeTicks());
    }

    /** The Stinger's seeker: 0 = it has nothing, up to 1 = locked. */
    public static float getProgress() {
        return held().getItem() instanceof ItemStinger ? progress : 0;
    }

    /** The aircraft the seeker is on, if this client can see it. */
    @Nullable
    public static Entity getTarget() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return getProgress() <= 0 || targetId < 0 || minecraft.world == null ? null : minecraft.world.getEntityByID(targetId);
    }

    static void onState(int target, float newProgress) {
        if (newProgress > 0 && progress <= 0) {
            beepDelay = 0;
        } else if (newProgress <= 0 && progress > 0 && isAiming()) {
            UiSounds.play(UiSounds.LOCK_LOST);
        }
        targetId = target;
        progress = newProgress;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean holding = minecraft.player != null && !held().isEmpty() && minecraft.currentScreen == null;
        prevScope = scope;
        boolean scoped = holding && isAiming() && minecraft.gameSettings.thirdPersonView == 0;
        scope = Math.max(0, Math.min(1, scope + (scoped ? 1 : -1) / SCOPE_TICKS));
        // Edge-detected on the key state: vanilla swallows attack clicks while an item is in use
        boolean fire = holding && minecraft.gameSettings.keyBindAttack.isKeyDown();
        boolean reload = holding && DroneKeys.RELOAD.isKeyDown();
        if (fire != fireHeld) {
            // Let go because a screen opened or the launcher was put away: that doesn't fire
            int action = fire ? LauncherInputPacket.ACTION_PRESS : holding ? LauncherInputPacket.ACTION_RELEASE : LauncherInputPacket.ACTION_CANCEL;
            ModNetwork.CHANNEL.sendToServer(new LauncherInputPacket(action));
        }
        triggerTicks = fire ? triggerTicks + 1 : 0;
        if (reload && !reloadHeld) {
            ModNetwork.CHANNEL.sendToServer(new LauncherInputPacket(LauncherInputPacket.ACTION_RELOAD));
        }
        fireHeld = fire;
        reloadHeld = reload;
        tones(minecraft, holding && isAiming() ? getProgress() : 0);
    }

    private static void tones(Minecraft minecraft, float lock) {
        if (lock > 0 && lock < 1 && --beepDelay <= 0) {
            UiSounds.play(UiSounds.STINGER_SEEK, 0.85F + lock * 0.5F);
            beepDelay = Math.round(MAX_BEEP_TICKS - (MAX_BEEP_TICKS - MIN_BEEP_TICKS) * lock);
        }
        boolean locked = lock >= 1;
        if (locked && (lockTone == null || !minecraft.getSoundHandler().isSoundPlaying(lockTone))) {
            lockTone = new PositionedSoundRecord(new ResourceLocation(CombatDrones.MOD_ID, UiSounds.STINGER_LOCK), SoundCategory.MASTER, 0.7F, 1.0F,
                    true, 0, ISound.AttenuationType.NONE, 0, 0, 0);
            minecraft.getSoundHandler().playSound(lockTone);
        } else if (!locked && lockTone != null) {
            minecraft.getSoundHandler().stopSound(lockTone);
            lockTone = null;
        }
    }

    @SubscribeEvent
    public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        targetId = -1;
        progress = 0;
        scope = 0;
        prevScope = 0;
    }
}
