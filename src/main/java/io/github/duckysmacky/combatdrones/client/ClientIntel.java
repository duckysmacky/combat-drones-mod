package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.client.hud.UiSounds;
import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import io.github.duckysmacky.combatdrones.network.LockWarningPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * What this player's (or their team's) recon UAVs track, as last sent by the server: tracking dots and lock-ons for the
 * planner map and the in-world markers, plus the lock-on progress in the UAV or Recon Drone camera.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class ClientIntel {
    private static List<IntelPacket.Entry> marks = Collections.emptyList();
    private static long marksReceived;
    /** Lock-on progress from the UAV or Recon Drone camera, 0..1 (0 = no attempt, 1 = just locked on). */
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
    /** What the warning is about: a UAV or Recon Drone after this player, or a Stinger after their aircraft ({@code LockWarningPacket.*}). */
    private static int warningSource;
    /** When the Pilot Detector dot on this player's stand-in body runs out (ms), or 0. Apart from lock-ons, so neither clears the other. */
    private static long scannedUntil;
    /** Pilots found by Pilot Detector scans (own or a teammate's), each until its time runs out (ms). */
    private static final List<IntelPacket.Entry> detections = new ArrayList<>();
    private static final List<Long> detectionsUntil = new ArrayList<>();

    private ClientIntel() {
    }

    public static List<IntelPacket.Entry> getMarks() {
        return marks;
    }

    static void set(List<IntelPacket.Entry> newMarks) {
        marks = newMarks;
        marksReceived = Minecraft.getSystemTime();
    }

    /** Pilot Detector dots still up (live: drawn on the body where this client has it). */
    public static List<IntelPacket.Entry> getDetections() {
        long now = Minecraft.getSystemTime();
        Iterator<Long> until = detectionsUntil.iterator();
        Iterator<IntelPacket.Entry> entry = detections.iterator();
        while (until.hasNext()) {
            entry.next();
            if (until.next() <= now) {
                until.remove();
                entry.remove();
            }
        }
        return detections;
    }

    /** A scan's finds: a dot each for {@code ticks}, replacing an older dot on the same body. A beep if this player found someone. */
    static void addDetections(List<IntelPacket.Entry> found, int ticks, boolean own) {
        long until = Minecraft.getSystemTime() + ticks * 50L;
        for (IntelPacket.Entry entry : found) {
            for (int index = detections.size() - 1; index >= 0; index--) {
                if (detections.get(index).entityId == entry.entityId) {
                    detections.remove(index);
                    detectionsUntil.remove(index);
                }
            }
            detections.add(entry);
            detectionsUntil.add(until);
        }
        if (own && !found.isEmpty()) {
            Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(ModSounds.DETECTOR_CONTACT, 1.0F));
        }
    }

    /** Milliseconds left on a lock-on mark, counting down from when the server sent it. */
    public static long millisLeft(IntelPacket.Entry mark) {
        return Math.max(0, mark.ticksLeft * 50L - (Minecraft.getSystemTime() - marksReceived));
    }

    public static float getLockProgress() {
        return inCamera() ? lockProgress : 0;
    }

    /** Watching a remote unit's camera or flying an FPV drone: where a lock-on can run. */
    private static boolean inCamera() {
        return RemoteController.getUnit() != null || PilotController.getPilotedDrone() != null;
    }

    static void setLockProgress(float progress) {
        boolean inCamera = inCamera();
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
    static void setWarning(boolean locked, float value, int source) {
        long now = Minecraft.getSystemTime();
        if (source == LockWarningPacket.DETECTOR) {
            scannedUntil = value > 0 ? Math.max(scannedUntil, now + (long) (value * 1000)) : 0;
            return;
        }
        if (value > 0) {
            warningSource = source;
        }
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

    /** True when the warning up now is a Stinger's on this player's aircraft rather than a UAV's on them. */
    public static boolean isStingerWarning() {
        return warningSource == LockWarningPacket.STINGER;
    }

    /** True when the warning up now is a hand-flown Recon Drone's rather than a Recon UAV's. */
    public static boolean isReconDroneWarning() {
        return warningSource == LockWarningPacket.RECON_DRONE;
    }

    /** Milliseconds left on the lock-on on this player, or 0. */
    public static long getLockedMillisLeft() {
        return Math.max(0, lockedUntil - Minecraft.getSystemTime());
    }

    /** Milliseconds left on the Pilot Detector dot on this player's stand-in body, or 0. */
    public static long getScannedMillisLeft() {
        return Math.max(0, scannedUntil - Minecraft.getSystemTime());
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
        // A missile launcher locked on their aircraft: a fast, steady alarm for as long as it holds
        if (isStingerWarning() && getLockedMillisLeft() > 0 && --warningBeepDelay <= 0) {
            UiSounds.play(UiSounds.DENY, 1.3F);
            warningBeepDelay = 3;
        }
    }

    private static int beepTicks(float progress) {
        return Math.round(MAX_BEEP_TICKS - (MAX_BEEP_TICKS - MIN_BEEP_TICKS) * progress);
    }

    @SubscribeEvent
    public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        marks = Collections.emptyList();
        scannedUntil = 0;
        detections.clear();
        detectionsUntil.clear();
        lockProgress = 0;
        warningUntil = 0;
        lockedUntil = 0;
    }
}
