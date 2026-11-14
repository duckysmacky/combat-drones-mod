package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.client.hud.UiSounds;
import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Collections;
import java.util.List;

/**
 * What this player's (or their team's) recon UAVs track, as last sent by the server: tracking dots and lock-ons for the
 * planner map and the in-world markers, plus the lock-on progress in the UAV camera.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class ClientIntel {
    private static List<IntelPacket.Entry> marks = Collections.emptyList();
    private static long marksReceived;
    /** Lock-on progress from the UAV camera, 0..1 (0 = no attempt, 1 = just locked on). */
    private static float lockProgress;
    /** Ticks to the next lock-on beep. */
    private static int beepDelay;
    /** Lock-on beeps speed up from this many ticks apart to MIN_BEEP_TICKS as the lock builds. */
    private static final int MAX_BEEP_TICKS = 10;
    private static final int MIN_BEEP_TICKS = 2;
    /** This player is being locked on: progress, and until when the warning stays up without news (ms). */
    private static float warningProgress;
    private static long warningUntil;
    private static int warningBeepDelay;
    /** How long a "being locked" warning outlives its last update, in case the end never arrives. */
    private static final long WARNING_TIMEOUT_MILLIS = 600;
    /** When the lock-on on this player runs out (ms), or 0. */
    private static long lockedUntil;

    private ClientIntel() {
    }

    public static List<IntelPacket.Entry> getMarks() {
        return marks;
    }

    static void set(List<IntelPacket.Entry> newMarks) {
        marks = newMarks;
        marksReceived = Minecraft.getSystemTime();
    }

    /** Milliseconds left on a lock-on mark, counting down from when the server sent it. */
    public static long millisLeft(IntelPacket.Entry mark) {
        return Math.max(0, mark.ticksLeft * 50L - (Minecraft.getSystemTime() - marksReceived));
    }

    public static float getLockProgress() {
        return RemoteController.getUnit() == null ? 0 : lockProgress;
    }

    static void setLockProgress(float progress) {
        boolean inCamera = RemoteController.getUnit() != null;
        if (progress >= 1 && lockProgress < 1) {
            Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(ModSounds.DRONE_BEEP, 1.8F));
        } else if (progress <= 0 && lockProgress > 0 && lockProgress < 1 && inCamera) {
            // An attempt that ended without locking on
            UiSounds.play(UiSounds.LOCK_LOST);
        } else if (progress > 0 && lockProgress <= 0) {
            beepDelay = 0;
        }
        lockProgress = progress;
    }

    /** From the server: a lock-on on this player is building ({@code locked} false) or holds ({@code locked} true). */
    static void setWarning(boolean locked, float value) {
        long now = Minecraft.getSystemTime();
        if (locked) {
            if (value > 0 && now >= lockedUntil) {
                Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(ModSounds.DRONE_BEEP, 0.6F));
            }
            lockedUntil = value > 0 ? now + (long) (value * 1000) : 0;
        } else {
            warningProgress = value;
            warningUntil = value > 0 ? now + WARNING_TIMEOUT_MILLIS : 0;
        }
    }

    /** Progress of a lock-on building on this player, or 0 when none is. */
    public static float getWarningProgress() {
        return Minecraft.getSystemTime() < warningUntil ? warningProgress : 0;
    }

    /** Milliseconds left on the lock-on on this player, or 0. */
    public static long getLockedMillisLeft() {
        return Math.max(0, lockedUntil - Minecraft.getSystemTime());
    }

    /** A new camera session: no attempt carried over from the last one. */
    static void resetLock() {
        lockProgress = 0;
    }

    /** While a lock-on builds: ticks that climb in pitch and come faster and faster. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (getLockProgress() > 0 && lockProgress < 1 && --beepDelay <= 0) {
            UiSounds.play(UiSounds.LOCK_TICK, 0.8F + lockProgress * 0.6F);
            beepDelay = beepTicks(lockProgress);
        }
        // Being locked on: lower, harsher ticks, the same quickening
        float warning = getWarningProgress();
        if (warning > 0 && --warningBeepDelay <= 0) {
            UiSounds.play(UiSounds.DENY, 0.7F + warning * 0.4F);
            warningBeepDelay = beepTicks(warning);
        }
    }

    private static int beepTicks(float progress) {
        return Math.round(MAX_BEEP_TICKS - (MAX_BEEP_TICKS - MIN_BEEP_TICKS) * progress);
    }

    @SubscribeEvent
    public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        marks = Collections.emptyList();
        lockProgress = 0;
        warningUntil = 0;
        lockedUntil = 0;
    }
}
