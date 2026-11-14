package io.github.duckysmacky.combatdrones.client.fx;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.client.hud.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What a blast or a gun does to the viewer and the air: the camera shakes (more the closer and bigger it was), the
 * screen flashes white for a nearby explosion, and rounds leave short glowing tracer streaks. All client-side.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class Impact {
    /** Shake at full strength, degrees; it falls with the square of what's left, so it dies away quickly. */
    private static final float MAX_SHAKE_DEGREES = 2.6F;
    private static final float SHAKE_DECAY = 0.82F;
    private static final float FLASH_DECAY = 0.7F;
    private static final int TRACER_TICKS = 3;
    private static final List<double[]> TRACERS = new ArrayList<>();
    private static float shake;
    private static float flash;

    private Impact() {
    }

    /** Adds camera shake, 0..1 (it doesn't stack past 1). */
    public static void shake(float amount) {
        shake = Math.min(1.0F, Math.max(shake, amount));
    }

    /** A white flash over the screen, 0..1. */
    public static void flash(float amount) {
        flash = Math.min(1.0F, Math.max(flash, amount));
    }

    /** A blast of this power went off this far from the eye: shake and, close in, flash. */
    public static void blast(float power, double distance) {
        double reach = 10 + power * 7;
        if (distance < reach) {
            shake((float) (Math.min(1.0, 0.25 + power / 10.0) * (1 - distance / reach)));
        }
        double flashReach = 3 + power * 2.5;
        if (distance < flashReach) {
            flash((float) (0.7 * (1 - distance / flashReach)));
        }
    }

    /** A round's glowing streak from muzzle to where it went. */
    public static void tracer(Vec3d from, Vec3d to) {
        TRACERS.add(new double[] {from.x, from.y, from.z, to.x, to.y, to.z, 0});
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getMinecraft().isGamePaused()) {
            return;
        }
        shake = shake < 0.01F ? 0 : shake * SHAKE_DECAY;
        flash = flash < 0.01F ? 0 : flash * FLASH_DECAY;
        for (Iterator<double[]> iterator = TRACERS.iterator(); iterator.hasNext(); ) {
            if (++iterator.next()[6] > TRACER_TICKS) {
                iterator.remove();
            }
        }
    }

    /** After the flight cameras have set their attitude, so the shake rides on top of it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        if (shake <= 0) {
            return;
        }
        float strength = shake * shake * MAX_SHAKE_DEGREES;
        double time = (Minecraft.getSystemTime() % 100000) / 1000.0;
        event.setYaw(event.getYaw() + (float) Math.sin(time * 61) * strength);
        event.setPitch(event.getPitch() + (float) Math.sin(time * 73 + 1.3) * strength);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 47 + 2.1) * strength * 0.6F);
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL && flash > 0.01F) {
            GlStateManager.disableAlpha();
            Ui.rect(0, 0, event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight(), Ui.alpha(0xFFFFF6D8, flash * 0.6F));
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        if (TRACERS.isEmpty()) {
            return;
        }
        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        float skyLight = OpenGlHelper.lastBrightnessY;
        float blockLight = OpenGlHelper.lastBrightnessX;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GlStateManager.glLineWidth(2.5F);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (double[] tracer : TRACERS) {
            // The streak runs down the round's path: bright head, fading tail
            double head = Math.min(1, (tracer[6] + event.getPartialTicks()) / TRACER_TICKS);
            double tail = Math.max(0, head - 0.45);
            float alpha = (float) (0.9 * (1 - head * 0.6));
            buffer.pos(tracer[0] + (tracer[3] - tracer[0]) * tail - renderManager.viewerPosX, tracer[1] + (tracer[4] - tracer[1]) * tail - renderManager.viewerPosY,
                    tracer[2] + (tracer[5] - tracer[2]) * tail - renderManager.viewerPosZ).color(1.0F, 0.54F, 0.16F, 0.0F).endVertex();
            buffer.pos(tracer[0] + (tracer[3] - tracer[0]) * head - renderManager.viewerPosX, tracer[1] + (tracer[4] - tracer[1]) * head - renderManager.viewerPosY,
                    tracer[2] + (tracer[5] - tracer[2]) * head - renderManager.viewerPosZ).color(1.0F, 0.88F, 0.54F, alpha).endVertex();
        }
        Tessellator.getInstance().draw();
        GlStateManager.glLineWidth(1.0F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, blockLight, skyLight);
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
