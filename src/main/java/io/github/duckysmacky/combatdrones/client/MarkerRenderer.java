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
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * In-world overlays, drawn through terrain: a red cross in a square over the point your unit is diving on (in its
 * camera), and the recon intel you may see wherever you are: an ESP box around every locked-on target (with its name)
 * and a dot above every tracked one. Players are red, anything else yellow. Locked-on targets (and every target with
 * live tracking) are drawn where this client sees them, when it tracks them; otherwise at the server's position.
 * Grown with distance so they stay readable from altitude.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class MarkerRenderer {
    private static final float[] PLAYER_COLOR = {0.85F, 0.31F, 0.24F};
    private static final float[] OTHER_COLOR = {0.95F, 0.80F, 0.24F};
    /** Size of a target without a client-side entity to measure (a player). */
    private static final double DEFAULT_WIDTH = 0.6;
    private static final double DEFAULT_HEIGHT = 1.8;

    private MarkerRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityRampUnit unit = RemoteController.getUnit();
        BlockPos point = unit == null ? null : unit.getStrikePoint();
        List<IntelPacket.Entry> marks = ClientIntel.getMarks();
        if (point == null && marks.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        RenderManager renderManager = minecraft.getRenderManager();
        // Camera-relative feet position, width and height of each mark's target
        List<double[]> places = new ArrayList<>(marks.size());
        for (IntelPacket.Entry mark : marks) {
            places.add(place(minecraft, renderManager, mark, event.getPartialTicks()));
        }
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(3.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        if (point != null) {
            strikeCross(buffer, point.getX() + 0.5 - renderManager.viewerPosX, point.getY() + 0.05 - renderManager.viewerPosY,
                    point.getZ() + 0.5 - renderManager.viewerPosZ);
        }
        for (int index = 0; index < marks.size(); index++) {
            if (marks.get(index).locked) {
                double[] place = places.get(index);
                box(buffer, place[0], place[1], place[2], place[3], place[4], colorOf(marks.get(index)));
            }
        }
        tessellator.draw();
        GlStateManager.glLineWidth(1.0F);
        for (int index = 0; index < marks.size(); index++) {
            if (!marks.get(index).locked) {
                double[] place = places.get(index);
                dot(tessellator, renderManager, place[0], place[1] + place[4], place[2], colorOf(marks.get(index)));
            }
        }
        GlStateManager.enableTexture2D();
        // Names of locked-on targets, as nameplates facing the camera
        for (int index = 0; index < marks.size(); index++) {
            if (marks.get(index).locked) {
                double[] place = places.get(index);
                float above = (float) ((place[4] + 0.1) * scaleFor(place[0], place[1], place[2])) + 0.5F;
                EntityRenderer.drawNameplate(minecraft.fontRenderer, marks.get(index).name, (float) place[0], (float) place[1] + above,
                        (float) place[2], 0, renderManager.playerViewY, renderManager.playerViewX, false, false);
            }
        }
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** {x, y, z, width, height}: the live entity if the mark follows it and this client has it, else the server's position. */
    private static double[] place(Minecraft minecraft, RenderManager renderManager, IntelPacket.Entry mark, float partialTicks) {
        Entity entity = mark.live && minecraft.world != null ? minecraft.world.getEntityByID(mark.entityId) : null;
        if (entity == null || entity.isDead) {
            return new double[]{mark.x - renderManager.viewerPosX, mark.y - renderManager.viewerPosY, mark.z - renderManager.viewerPosZ,
                    DEFAULT_WIDTH, DEFAULT_HEIGHT};
        }
        return new double[]{entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - renderManager.viewerPosX,
                entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - renderManager.viewerPosY,
                entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - renderManager.viewerPosZ,
                entity.width, entity.height};
    }

    private static float[] colorOf(IntelPacket.Entry mark) {
        return mark.player ? PLAYER_COLOR : OTHER_COLOR;
    }

    /** How much bigger than life-size a marker is drawn at this camera-relative offset. */
    private static double scaleFor(double x, double y, double z) {
        return Math.max(1.0, Math.sqrt(x * x + y * y + z * z) * 0.02);
    }

    private static void strikeCross(BufferBuilder buffer, double x, double y, double z) {
        double size = Math.max(1.5, Math.sqrt(x * x + y * y + z * z) * 0.03);
        line(buffer, x - size, y, z - size, x + size, y, z + size, PLAYER_COLOR);
        line(buffer, x - size, y, z + size, x + size, y, z - size, PLAYER_COLOR);
        line(buffer, x - size, y, z - size, x + size, y, z - size, PLAYER_COLOR);
        line(buffer, x + size, y, z - size, x + size, y, z + size, PLAYER_COLOR);
        line(buffer, x + size, y, z + size, x - size, y, z + size, PLAYER_COLOR);
        line(buffer, x - size, y, z + size, x - size, y, z - size, PLAYER_COLOR);
        line(buffer, x, y, z, x, y + size * 2, z, PLAYER_COLOR);
    }

    /** ESP box: the target's hitbox outline, a little loose and grown with distance, at the camera-relative feet position. */
    private static void box(BufferBuilder buffer, double x, double y, double z, double width, double height, float[] color) {
        double scale = scaleFor(x, y, z);
        double half = (width / 2 + 0.1) * scale;
        double top = y + (height + 0.1) * scale;
        double[][] corners = {{x - half, z - half}, {x + half, z - half}, {x + half, z + half}, {x - half, z + half}};
        for (int index = 0; index < 4; index++) {
            double[] from = corners[index];
            double[] to = corners[(index + 1) % 4];
            line(buffer, from[0], y, from[1], to[0], y, to[1], color);
            line(buffer, from[0], top, from[1], to[0], top, to[1], color);
            line(buffer, from[0], y, from[1], from[0], top, from[1], color);
        }
    }

    /** Tracking dot: a diamond facing the camera, floating over the camera-relative head position, about the same size on screen at any range. */
    private static void dot(Tessellator tessellator, RenderManager renderManager, double x, double headY, double z, float[] color) {
        double distance = Math.sqrt(x * x + headY * headY + z * z);
        double size = Math.max(0.15, distance * 0.012);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, headY + 0.4 + size * 1.5, z);
        GlStateManager.rotate(-renderManager.playerViewY, 0, 1, 0);
        GlStateManager.rotate(renderManager.playerViewX, 1, 0, 0);
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(0, size, 0).color(color[0], color[1], color[2], 0.9F).endVertex();
        buffer.pos(-size, 0, 0).color(color[0], color[1], color[2], 0.9F).endVertex();
        buffer.pos(0, -size, 0).color(color[0], color[1], color[2], 0.9F).endVertex();
        buffer.pos(size, 0, 0).color(color[0], color[1], color[2], 0.9F).endVertex();
        tessellator.draw();
        GlStateManager.popMatrix();
    }

    private static void line(BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2, float[] color) {
        buffer.pos(x1, y1, z1).color(color[0], color[1], color[2], 1.0F).endVertex();
        buffer.pos(x2, y2, z2).color(color[0], color[1], color[2], 1.0F).endVertex();
    }
}
