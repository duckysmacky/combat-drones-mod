package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * Analog FPV goggle noise: a low-res random grey texture stretched over the screen, re-rolled every other frame.
 * Faint on a good link, heavy near the edge of signal range. The Gunship's gunner seat gets an old CRT feed instead:
 * light noise, scanlines, a slow rolling band and the odd tear line. Drawn under the HUD so the OSD stays readable.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class StaticOverlay {
    private static final int NOISE_WIDTH = 128;
    private static final int NOISE_HEIGHT = 72;
    private static final double BASE_ALPHA = 0.02;
    private static final double MAX_EXTRA_ALPHA = 0.9;
    /** Gunner CRT: noise strength, one dark scanline every this many real pixels, rolling band period and height. */
    private static final double CRT_NOISE_ALPHA = 0.03;
    private static final int SCANLINE_SPACING_PX = 3;
    private static final int SCANLINE_ALPHA = 60;
    private static final long ROLL_PERIOD_MS = 6000;
    private static final double ROLL_BAND_SHARE = 0.18;
    private static final float TEAR_CHANCE = 0.012F;

    private static final Random RANDOM = new Random();
    private static DynamicTexture noise;
    private static int frame;
    private static int tearFrames;
    private static double tearY;

    private StaticOverlay() {
    }

    @SubscribeEvent
    public static void onRenderPre(RenderGameOverlayEvent.Pre event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        if (RemoteController.isGunner()) {
            drawGunnerCrt(event.getResolution());
            return;
        }
        if (PilotController.getPilotedDrone() == null) {
            return;
        }
        // Quadratic: faint on a decent link, clearly building from mid-signal (70% -> 8%, 50% -> 22%, 30% -> 44%)
        double alpha = (BASE_ALPHA + Math.pow(1 - PilotController.getSignalQuality(), 2) * MAX_EXTRA_ALPHA) * ModConfig.staticIntensity;
        if (alpha <= 0.001) {
            return;
        }
        rollNoise();
        drawFullScreen(event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight(), (float) Math.min(1, alpha));
    }

    private static void rollNoise() {
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
    }

    private static void drawGunnerCrt(ScaledResolution resolution) {
        double intensity = ModConfig.staticIntensity;
        if (intensity <= 0) {
            return;
        }
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        rollNoise();
        drawFullScreen(width, height, (float) Math.min(1, CRT_NOISE_ALPHA * intensity));

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableAlpha();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        // Scanlines in real pixels, so they stay fine at any GUI scale
        double pixel = 1.0 / resolution.getScaleFactor();
        int lineAlpha = (int) Math.min(255, SCANLINE_ALPHA * intensity);
        for (double y = 0; y < height; y += SCANLINE_SPACING_PX * pixel) {
            quad(buffer, 0, y, width, y + pixel, 0, lineAlpha, lineAlpha);
        }
        // A faint bright band rolling down the picture
        double band = height * ROLL_BAND_SHARE;
        double top = (System.currentTimeMillis() % ROLL_PERIOD_MS) / (double) ROLL_PERIOD_MS * (height + band) - band;
        int bandAlpha = (int) Math.min(255, 14 * intensity);
        quad(buffer, 0, top, width, top + band / 2, 0xFFFFFF, 0, bandAlpha);
        quad(buffer, 0, top + band / 2, width, top + band, 0xFFFFFF, bandAlpha, 0);
        // Now and then a few frames of a torn, bright line
        if (tearFrames <= 0 && RANDOM.nextFloat() < TEAR_CHANCE) {
            tearFrames = 2 + RANDOM.nextInt(4);
            tearY = RANDOM.nextDouble() * height;
        }
        if (tearFrames > 0) {
            tearFrames--;
            int tearAlpha = (int) Math.min(255, 45 * intensity);
            double thickness = (1 + RANDOM.nextInt(3)) * pixel * 2;
            quad(buffer, 0, tearY, width, tearY + thickness, 0xFFFFFF, tearAlpha, tearAlpha);
            quad(buffer, 0, tearY + thickness, width, tearY + thickness * 3, 0, tearAlpha, tearAlpha);
        }
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /** A full-width strip of one colour, its alpha fading from the top edge's to the bottom edge's. */
    private static void quad(BufferBuilder buffer, double left, double top, double right, double bottom, int rgb, int topAlpha, int bottomAlpha) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        buffer.pos(left, bottom, -90).color(red, green, blue, bottomAlpha).endVertex();
        buffer.pos(right, bottom, -90).color(red, green, blue, bottomAlpha).endVertex();
        buffer.pos(right, top, -90).color(red, green, blue, topAlpha).endVertex();
        buffer.pos(left, top, -90).color(red, green, blue, topAlpha).endVertex();
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
