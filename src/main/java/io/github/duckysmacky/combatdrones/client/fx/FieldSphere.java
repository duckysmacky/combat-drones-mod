package io.github.duckysmacky.combatdrones.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

/**
 * A glowing see-through sphere (EMP fields, the detector pulse): additive, full-bright, no depth write and both faces
 * drawn, so it reads the same from inside. Each vertex is brighter the more edge-on it is to the camera, which gives the
 * bubble its rim. Coordinates are camera-relative.
 */
public final class FieldSphere {
    private static final int RINGS = 18;
    private static final int SEGMENTS = 36;

    private FieldSphere() {
    }

    /**
     * @param faceAlpha alpha where the surface faces the camera
     * @param rimAlpha  alpha where it's seen edge-on
     * @param texture   tiled over the surface ({@code tiles} times round, half that pole to pole), or null for plain colour
     * @param scrollU   texture offset round the sphere, in tiles
     * @param scrollV   texture offset pole to pole, in tiles
     */
    public static void draw(double x, double y, double z, double radius, float[] color, float faceAlpha, float rimAlpha,
                            @Nullable ResourceLocation texture, float tiles, float scrollU, float scrollV) {
        if (radius <= 0.01) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        if (texture != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        } else {
            GlStateManager.disableTexture2D();
        }
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int ring = 0; ring < RINGS; ring++) {
            for (int segment = 0; segment < SEGMENTS; segment++) {
                vertex(buffer, x, y, z, radius, ring, segment, color, faceAlpha, rimAlpha, tiles, scrollU, scrollV);
                vertex(buffer, x, y, z, radius, ring + 1, segment, color, faceAlpha, rimAlpha, tiles, scrollU, scrollV);
                vertex(buffer, x, y, z, radius, ring + 1, segment + 1, color, faceAlpha, rimAlpha, tiles, scrollU, scrollV);
                vertex(buffer, x, y, z, radius, ring, segment + 1, color, faceAlpha, rimAlpha, tiles, scrollU, scrollV);
            }
        }
        tessellator.draw();
        if (texture == null) {
            GlStateManager.enableTexture2D();
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void vertex(BufferBuilder buffer, double x, double y, double z, double radius, int ring, int segment, float[] color,
                               float faceAlpha, float rimAlpha, float tiles, float scrollU, float scrollV) {
        double polar = Math.PI * ring / RINGS;
        double azimuth = 2 * Math.PI * segment / SEGMENTS;
        double nx = Math.sin(polar) * Math.cos(azimuth);
        double ny = Math.cos(polar);
        double nz = Math.sin(polar) * Math.sin(azimuth);
        double px = x + nx * radius;
        double py = y + ny * radius;
        double pz = z + nz * radius;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        double facing = distance < 1e-6 ? 1 : Math.abs(nx * px + ny * py + nz * pz) / distance;
        float alpha = (float) (rimAlpha + (faceAlpha - rimAlpha) * facing);
        buffer.pos(px, py, pz).tex(scrollU + tiles * segment / (float) SEGMENTS, scrollV + tiles * 0.5F * ring / RINGS)
                .color(color[0], color[1], color[2], alpha).endVertex();
    }
}
