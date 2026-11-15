package io.github.duckysmacky.combatdrones.client.fx;

import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Pilot Detector pulses: a thin green shell racing out to the scan's range and fading as it goes. */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class ScanWave {
    /** palette green 4 */
    private static final float[] COLOR = {0x7a / 255F, 0xd0 / 255F, 0x70 / 255F};
    private static final long DURATION_MILLIS = 1200;
    private static final List<ScanWave> WAVES = new ArrayList<>();

    private final Vec3d at;
    private final double range;
    private final long start = Minecraft.getSystemTime();

    private ScanWave(Vec3d at, double range) {
        this.at = at;
        this.range = range;
    }

    public static void start(Vec3d at, double range) {
        WAVES.add(new ScanWave(at, range));
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        if (WAVES.isEmpty()) {
            return;
        }
        RenderManager view = Minecraft.getMinecraft().getRenderManager();
        long now = Minecraft.getSystemTime();
        for (Iterator<ScanWave> iterator = WAVES.iterator(); iterator.hasNext(); ) {
            ScanWave wave = iterator.next();
            float age = (now - wave.start) / (float) DURATION_MILLIS;
            if (age >= 1) {
                iterator.remove();
                continue;
            }
            Vec3d at = wave.at;
            // Fast out of the gate, easing into the range; the second, fainter shell trails a little behind
            double reach = wave.range * (1 - Math.pow(1 - age, 3));
            float fade = 1 - age;
            FieldSphere.draw(at.x - view.viewerPosX, at.y - view.viewerPosY, at.z - view.viewerPosZ, reach, COLOR,
                    0.02F * fade, 0.55F * fade, null, 1, 0, 0);
            FieldSphere.draw(at.x - view.viewerPosX, at.y - view.viewerPosY, at.z - view.viewerPosZ, reach * 0.85, COLOR,
                    0.0F, 0.25F * fade, null, 1, 0, 0);
        }
    }

    @SubscribeEvent
    public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        WAVES.clear();
    }
}
