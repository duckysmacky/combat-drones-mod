package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Collections;
import java.util.List;

/**
 * What this player's (or, with reconIntelForAllies, their team's) recon has spotted, as last sent by the server: for
 * the planner map and the camera / FPV markers.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class ClientIntel {
    private static List<IntelPacket.Entry> marks = Collections.emptyList();
    /** Lock progress on whatever is under the recon camera's crosshair, 0..1 (1 = locked). */
    private static float lockProgress;

    private ClientIntel() {
    }

    public static List<IntelPacket.Entry> getMarks() {
        return marks;
    }

    static void set(List<IntelPacket.Entry> newMarks) {
        marks = newMarks;
    }

    public static float getLockProgress() {
        return RemoteController.getUnit() == null ? 0 : lockProgress;
    }

    static void setLockProgress(float progress) {
        if (progress >= 1 && lockProgress < 1) {
            Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(ModSounds.DRONE_BEEP, 1.8F));
        }
        lockProgress = progress;
    }

    @SubscribeEvent
    public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        marks = Collections.emptyList();
        lockProgress = 0;
    }
}
