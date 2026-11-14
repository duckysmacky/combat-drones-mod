package io.github.duckysmacky.combatdrones.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * The one look every HUD and gadget screen is drawn with: the colours (all from the art palette), outlined lines
 * that read on sky and snow, framed panels, bars and OSD text. Colours are ARGB.
 */
public final class Ui {
    public static final int TEXT = 0xFFF4F5F0;
    public static final int DIM = 0xFFA3ABB3;
    public static final int FAINT = 0xFF646D7A;
    public static final int OK = 0xFF7AD070;
    public static final int WARN = 0xFFF2CC3C;
    public static final int DANGER = 0xFFD94F3C;
    public static final int FRIENDLY = 0xFF4F96E6;
    public static final int SELECT = 0xFFFBE678;
    /** Head-up display symbology. */
    public static final int HUD_GREEN = 0xFF7AD070;
    /** The dark that backs lines and bars, and panel fills. */
    public static final int SHADE = 0xC00E0F12;
    public static final int PANEL = 0xD817191D;
    public static final int PANEL_SOLID = 0xFF17191D;
    public static final int FRAME = 0xFF5A626C;
    public static final int FRAME_LIGHT = 0xFF727A84;
    public static final int FRAME_DARK = 0xFF23272E;
    public static final int SCREEN = 0xFF0B0E16;
    /** Text line pitch on the OSD. */
    public static final int LINE = 10;

    private Ui() {
    }

    public static int alpha(int argb, float alpha) {
        return Math.round((argb >>> 24) * Math.max(0, Math.min(1, alpha))) << 24 | argb & 0xFFFFFF;
    }

    public static void rect(int left, int top, int right, int bottom, int argb) {
        Gui.drawRect(left, top, right, bottom, argb);
    }

    /** A one-pixel horizontal line from x0 to x1 (exclusive) with a dark outline. */
    public static void hline(int x0, int x1, int y, int argb) {
        rect(x0 - 1, y - 1, x1 + 1, y + 2, SHADE);
        rect(x0, y, x1, y + 1, argb);
    }

    public static void vline(int x, int y0, int y1, int argb) {
        rect(x - 1, y0 - 1, x + 2, y1 + 1, SHADE);
        rect(x, y0, x + 1, y1, argb);
    }

    /** A filled marker with a dark outline. */
    public static void block(int left, int top, int right, int bottom, int argb) {
        rect(left - 1, top - 1, right + 1, bottom + 1, SHADE);
        rect(left, top, right, bottom, argb);
    }

    public static void outline(int x, int y, int width, int height, int argb) {
        rect(x, y, x + width, y + 1, argb);
        rect(x, y + height - 1, x + width, y + height, argb);
        rect(x, y + 1, x + 1, y + height - 1, argb);
        rect(x + width - 1, y + 1, x + width, y + height - 1, argb);
    }

    /** A panel: dark fill, a frame lit from the top left like a vanilla widget. */
    public static void panel(int x, int y, int width, int height) {
        rect(x, y, x + width, y + height, PANEL);
        frame(x, y, width, height);
    }

    public static void frame(int x, int y, int width, int height) {
        rect(x, y, x + width, y + 1, FRAME_LIGHT);
        rect(x, y + 1, x + 1, y + height, FRAME_LIGHT);
        rect(x + 1, y + height - 1, x + width, y + height, FRAME_DARK);
        rect(x + width - 1, y + 1, x + width, y + height - 1, FRAME_DARK);
    }

    /** A gauge bar: dark backing, filled left to right. */
    public static void bar(int x, int y, int width, int height, double fraction, int argb) {
        rect(x - 1, y - 1, x + width + 1, y + height + 1, SHADE);
        rect(x, y, x + (int) Math.round(width * Math.max(0, Math.min(1, fraction))), y + height, argb);
    }

    /** Four corner brackets round a box centred on (centerX, centerY). */
    public static void brackets(int centerX, int centerY, int halfWidth, int halfHeight, int arm, int argb) {
        for (int sideX = -1; sideX <= 1; sideX += 2) {
            for (int sideY = -1; sideY <= 1; sideY += 2) {
                int cornerX = centerX + sideX * halfWidth;
                int cornerY = centerY + sideY * halfHeight;
                hline(Math.min(cornerX, cornerX - sideX * arm), Math.max(cornerX, cornerX - sideX * arm) + 1, cornerY, argb);
                vline(cornerX, Math.min(cornerY, cornerY - sideY * arm), Math.max(cornerY, cornerY - sideY * arm) + 1, argb);
            }
        }
    }

    public static int text(String text, float x, float y, int argb) {
        return OsdFont.draw(text, x, y, argb, OsdFont.ADVANCE);
    }

    public static int textRight(String text, float right, float y, int argb) {
        return OsdFont.draw(text, right - OsdFont.width(text, OsdFont.ADVANCE), y, argb, OsdFont.ADVANCE);
    }

    public static int textCentered(String text, float centerX, float y, int argb) {
        return OsdFont.draw(text, centerX - OsdFont.width(text, OsdFont.ADVANCE) / 2.0F, y, argb, OsdFont.ADVANCE);
    }

    /** Centred text at a multiple of its size (titles, countdowns). */
    public static void textScaled(String text, float centerX, float y, float scale, int argb) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, y, 0);
        GlStateManager.scale(scale, scale, 1);
        textCentered(text, 0, 0, argb);
        GlStateManager.popMatrix();
    }

    public static int width(String text) {
        return OsdFont.width(text, OsdFont.ADVANCE);
    }

    /** An icon followed by a value: returns the width used. */
    public static int labelled(OsdFont.Icon icon, String text, float x, float y, int argb) {
        OsdFont.icon(icon, x, y, argb);
        return 10 + text(text, x + 10, y, argb);
    }

    /** As {@link #labelled}, ending at {@code right}. */
    public static void labelledRight(OsdFont.Icon icon, String text, float right, float y, int argb) {
        labelled(icon, text, right - width(text) - 10, y, argb);
    }

    /** True for half of every blink period: 2 per second for warnings, 1 for status. */
    public static boolean blink(int perSecond) {
        return Minecraft.getSystemTime() / (500 / perSecond) % 2 == 0;
    }

    public static long percent(double fraction) {
        return Math.round(Math.max(0, Math.min(1, fraction)) * 100);
    }

    /** Untextured, blended lines until {@link #endLines}: for rotated or round symbology rects can't draw. */
    public static BufferBuilder beginLines(float width) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(width);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        return buffer;
    }

    public static void endLines() {
        Tessellator.getInstance().draw();
        GlStateManager.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
    }

    public static void line(BufferBuilder buffer, double x0, double y0, double x1, double y1, int argb) {
        int alpha = argb >>> 24;
        int red = argb >> 16 & 0xFF;
        int green = argb >> 8 & 0xFF;
        int blue = argb & 0xFF;
        buffer.pos(x0, y0, 0).color(red, green, blue, alpha).endVertex();
        buffer.pos(x1, y1, 0).color(red, green, blue, alpha).endVertex();
    }

    public static void circle(BufferBuilder buffer, double centerX, double centerY, double radius, int segments, int argb) {
        for (int step = 0; step < segments; step++) {
            double from = Math.PI * 2 * step / segments;
            double to = Math.PI * 2 * (step + 1) / segments;
            line(buffer, centerX + Math.cos(from) * radius, centerY + Math.sin(from) * radius,
                    centerX + Math.cos(to) * radius, centerY + Math.sin(to) * radius, argb);
        }
    }

    /** A quad whose alpha runs from one edge to the opposite one: screen-edge flashes and vignettes. */
    public static void fade(double x0, double y0, double x1, double y1, int rgb, int alphaStart, int alphaEnd, boolean vertical) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(x0, y1, 0).color(red, green, blue, vertical ? alphaEnd : alphaStart).endVertex();
        buffer.pos(x1, y1, 0).color(red, green, blue, alphaEnd).endVertex();
        buffer.pos(x1, y0, 0).color(red, green, blue, vertical ? alphaStart : alphaEnd).endVertex();
        buffer.pos(x0, y0, 0).color(red, green, blue, alphaStart).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}
