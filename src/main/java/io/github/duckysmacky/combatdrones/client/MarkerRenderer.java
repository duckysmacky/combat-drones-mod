package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/**
 * In-world overlays while looking through one of your own units or flying a drone in FPV: a red cross in a square
 * over the point a unit is diving on, and a red box around every player your (or, with reconIntelForAllies, your
 * team's) recon has spotted (brighter and named when locked). Drawn through
 * terrain and grown with distance so they stay readable from altitude.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class MarkerRenderer {
    private MarkerRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityRampUnit unit = RemoteController.getUnit();
        if (unit == null && PilotController.getPilotedDrone() == null) {
            return;
        }
        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(3.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        BlockPos point = unit == null ? null : unit.getStrikePoint();
        if (point != null) {
            strikeCross(buffer, point.getX() + 0.5 - renderManager.viewerPosX, point.getY() + 0.05 - renderManager.viewerPosY,
                    point.getZ() + 0.5 - renderManager.viewerPosZ);
        }
        for (IntelPacket.Entry mark : ClientIntel.getMarks()) {
            float alpha = mark.locked ? 1.0F : 0.35F + 0.5F * Math.max(0, Math.min(1, mark.freshness));
            box(buffer, mark.x - renderManager.viewerPosX, mark.y - renderManager.viewerPosY, mark.z - renderManager.viewerPosZ, alpha);
        }
        tessellator.draw();
        GlStateManager.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
        // Names of locked targets, as nameplates facing the camera
        for (IntelPacket.Entry mark : ClientIntel.getMarks()) {
            if (mark.locked) {
                double x = mark.x - renderManager.viewerPosX;
                double y = mark.y - renderManager.viewerPosY;
                double z = mark.z - renderManager.viewerPosZ;
                float above = (float) (2.0 * scaleFor(x, y, z)) + 0.5F;
                EntityRenderer.drawNameplate(Minecraft.getMinecraft().fontRenderer, mark.name, (float) x, (float) y + above, (float) z, 0,
                        renderManager.playerViewY, renderManager.playerViewX, false, false);
            }
        }
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** How much bigger than life-size a marker is drawn at this camera-relative offset. */
    private static double scaleFor(double x, double y, double z) {
        return Math.max(1.0, Math.sqrt(x * x + y * y + z * z) * 0.02);
    }

    private static void strikeCross(BufferBuilder buffer, double x, double y, double z) {
        double size = Math.max(1.5, Math.sqrt(x * x + y * y + z * z) * 0.03);
        line(buffer, x - size, y, z - size, x + size, y, z + size, 1);
        line(buffer, x - size, y, z + size, x + size, y, z - size, 1);
        line(buffer, x - size, y, z - size, x + size, y, z - size, 1);
        line(buffer, x + size, y, z - size, x + size, y, z + size, 1);
        line(buffer, x + size, y, z + size, x - size, y, z + size, 1);
        line(buffer, x - size, y, z + size, x - size, y, z - size, 1);
        line(buffer, x, y, z, x, y + size * 2, z, 1);
    }

    /** Player-sized box outline (grown with distance) standing at the given camera-relative feet position. */
    private static void box(BufferBuilder buffer, double x, double y, double z, float alpha) {
        double scale = scaleFor(x, y, z);
        double half = 0.45 * scale;
        double top = y + 1.9 * scale;
        double[][] corners = {{x - half, z - half}, {x + half, z - half}, {x + half, z + half}, {x - half, z + half}};
        for (int index = 0; index < 4; index++) {
            double[] from = corners[index];
            double[] to = corners[(index + 1) % 4];
            line(buffer, from[0], y, from[1], to[0], y, to[1], alpha);
            line(buffer, from[0], top, from[1], to[0], top, to[1], alpha);
            line(buffer, from[0], y, from[1], from[0], top, from[1], alpha);
        }
    }

    private static void line(BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2, float alpha) {
        buffer.pos(x1, y1, z1).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
        buffer.pos(x2, y2, z2).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
    }
}
