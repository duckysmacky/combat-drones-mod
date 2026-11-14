package io.github.duckysmacky.combatdrones.client.hud;

import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The OSD's own bitmap font and icons, from {@code textures/gui/osd.png} (drawn by {@code tools/art/gen_ui.py}):
 * 5x7 capitals with a black outline baked in, so they read on any background and tinting leaves the outline black.
 * Lower case draws as capitals. Text with characters the atlas doesn't have falls back to the vanilla font.
 */
public final class OsdFont {
    private static final ResourceLocation ATLAS = new ResourceLocation(CombatDrones.MOD_ID, "textures/gui/osd.png");
    private static final float ATLAS_SIZE = 256.0F;
    private static final int CELL = 10;
    private static final int COLUMNS = 16;
    private static final int DEGREE_CELL = 64;
    private static final int ICON_START = 96;
    /** Glyph pitch in GUI pixels: 5 wide plus a gap. {@link #WIDE} spaces them out like an analog OSD. */
    public static final int ADVANCE = 6;
    public static final int WIDE = 8;
    public static final int HEIGHT = 7;

    /** Icons, in atlas order (the ICONS table in gen_ui.py). */
    public enum Icon {
        BATTERY_4, BATTERY_3, BATTERY_2, BATTERY_1, BATTERY_0, SIGNAL_4, SIGNAL_3, SIGNAL_2, SIGNAL_1, SIGNAL_0, TIMER, HOME,
        SPEED, ALTITUDE, FUEL, HEALTH, WARNING, LOCK, ROCKET, BULLET, BOMB, SHELL, ARROW_UP, ARROW_DOWN, LINK, CAMERA,
        THROTTLE, HEADING, TARGET, GRENADE
    }

    private OsdFont() {
    }

    private static int cell(char character) {
        char upper = Character.toUpperCase(character);
        if (upper >= 0x20 && upper <= 0x5F) {
            return upper - 0x20;
        }
        return upper == '°' ? DEGREE_CELL : -1;
    }

    public static boolean supports(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (cell(text.charAt(index)) < 0) {
                return false;
            }
        }
        return true;
    }

    public static int width(String text, int advance) {
        if (!supports(text)) {
            return Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
        }
        return text.isEmpty() ? 0 : text.length() * advance - (advance - 5);
    }

    /** Draws text with its top-left at (x, y) and returns its width. */
    public static int draw(String text, float x, float y, int argb, int advance) {
        if (!supports(text)) {
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(text, x, y, argb);
            return Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
        }
        begin(argb);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character != ' ') {
                quad(buffer, cell(character), x + index * advance, y);
            }
        }
        Tessellator.getInstance().draw();
        end();
        return width(text, advance);
    }

    /** Draws a 7x7 icon with its top-left at (x, y). */
    public static void icon(Icon icon, float x, float y, int argb) {
        begin(argb);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        quad(buffer, ICON_START + icon.ordinal(), x, y);
        Tessellator.getInstance().draw();
        end();
    }

    private static void begin(int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ATLAS);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        int alpha = argb >>> 24;
        GlStateManager.color((argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (alpha == 0 ? 255 : alpha) / 255.0F);
    }

    private static void end() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** One atlas cell: the glyph sits one pixel in, inside its outline, so the cell is drawn one pixel up and left. */
    private static void quad(BufferBuilder buffer, int cell, float x, float y) {
        float u = cell % COLUMNS * CELL / ATLAS_SIZE;
        float v = cell / COLUMNS * CELL / ATLAS_SIZE;
        float size = CELL / ATLAS_SIZE;
        float left = x - 1;
        float top = y - 1;
        buffer.pos(left, top + CELL, 0).tex(u, v + size).endVertex();
        buffer.pos(left + CELL, top + CELL, 0).tex(u + size, v + size).endVertex();
        buffer.pos(left + CELL, top, 0).tex(u + size, v).endVertex();
        buffer.pos(left, top, 0).tex(u, v).endVertex();
    }
}
