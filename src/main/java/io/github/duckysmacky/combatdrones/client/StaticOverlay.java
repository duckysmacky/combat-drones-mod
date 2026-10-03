package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Random;

/**
 * Analog FPV goggle noise: a low-res random grey texture stretched over the screen, re-rolled every other frame.
 * Faint on a good link, heavy near the edge of signal range. Drawn under the HUD so the OSD stays readable.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class StaticOverlay {
    private static final int NOISE_WIDTH = 128;
    private static final int NOISE_HEIGHT = 72;
    private static final double BASE_ALPHA = 0.02;
    private static final double MAX_EXTRA_ALPHA = 0.9;

    private static final Random RANDOM = new Random();
    private static DynamicTexture noise;
    private static int frame;

    private StaticOverlay() {
    }

    @SubscribeEvent
    public static void onRenderPre(RenderGameOverlayEvent.Pre event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || PilotController.getPilotedDrone() == null) {
            return;
        }
        // Quadratic: faint on a decent link, clearly building from mid-signal (70% -> 8%, 50% -> 22%, 30% -> 44%)
        double alpha = (BASE_ALPHA + Math.pow(1 - PilotController.getSignalQuality(), 2) * MAX_EXTRA_ALPHA) * ModConfig.staticIntensity;
        if (alpha <= 0.001) {
            return;
        }
        if (noise == null) {
            noise = new DynamicTexture(NOISE_WIDTH, NOISE_HEIGHT);
        }
        if (frame++ % 2 == 0) {
            int[] pixels = noise.getTextureData();
            for (int index = 0; index < pixels.length; index++) {
                int grey = RANDOM.nextInt(256);
                pixels[index] = 0xFF000000 | grey << 16 | grey << 8 | grey;
            }
            noise.updateDynamicTexture();
        }
        drawFullScreen(event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight(), (float) Math.min(1, alpha));
    }

    private static void drawFullScreen(int width, int height, float alpha) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        GlStateManager.bindTexture(noise.getGlTextureId());
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(0, height, -90).tex(0, 1).endVertex();
        buffer.pos(width, height, -90).tex(1, 1).endVertex();
        buffer.pos(width, 0, -90).tex(1, 0).endVertex();
        buffer.pos(0, 0, -90).tex(0, 0).endVertex();
        tessellator.draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
    }
}
