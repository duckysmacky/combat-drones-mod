package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
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
 * Red cross in a square over the point the viewed unit is diving on (a clicked point or the autopilot's strike
 * target), drawn through terrain and grown with distance so it stays readable from altitude.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class StrikeMarkerRenderer {
    private static final float RED = 1.0F;
    private static final float GREEN = 0.15F;
    private static final float BLUE = 0.15F;

    private StrikeMarkerRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityRampUnit unit = RemoteController.getUnit();
        BlockPos point = unit == null ? null : unit.getStrikePoint();
        if (point == null) {
            return;
        }
        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        double x = point.getX() + 0.5 - renderManager.viewerPosX;
        double y = point.getY() + 0.05 - renderManager.viewerPosY;
        double z = point.getZ() + 0.5 - renderManager.viewerPosZ;
        double size = Math.max(1.5, Math.sqrt(x * x + y * y + z * z) * 0.03);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.glLineWidth(3.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        line(buffer, x - size, y, z - size, x + size, y, z + size);
        line(buffer, x - size, y, z + size, x + size, y, z - size);
        line(buffer, x - size, y, z - size, x + size, y, z - size);
        line(buffer, x + size, y, z - size, x + size, y, z + size);
        line(buffer, x + size, y, z + size, x - size, y, z + size);
        line(buffer, x - size, y, z + size, x - size, y, z - size);
        line(buffer, x, y, z, x, y + size * 2, z);
        tessellator.draw();
        GlStateManager.glLineWidth(1.0F);
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static void line(BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2) {
        buffer.pos(x1, y1, z1).color(RED, GREEN, BLUE, 1.0F).endVertex();
        buffer.pos(x2, y2, z2).color(RED, GREEN, BLUE, 1.0F).endVertex();
    }
}
