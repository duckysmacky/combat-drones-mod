package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityBomb;
import io.github.duckysmacky.combatdrones.entity.EntityRocket;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.function.Function;

/** Dropped bombs and rockets: their geo model, nose along the direction of travel. Rockets get an exhaust glow. */
public class RenderProjectile<T extends Entity> extends Render<T> {
    private final Function<T, String> modelName;
    private final boolean exhaust;

    private RenderProjectile(RenderManager renderManager, Function<T, String> modelName, boolean exhaust) {
        super(renderManager);
        this.modelName = modelName;
        this.exhaust = exhaust;
        shadowSize = 0.15F;
    }

    public static RenderProjectile<EntityBomb> bomb(RenderManager renderManager) {
        return new RenderProjectile<>(renderManager, bomb -> bomb.getBombType().itemName(), false);
    }

    public static RenderProjectile<EntityRocket> rocket(RenderManager renderManager) {
        return new RenderProjectile<>(renderManager, rocket -> "rocket", true);
    }

    @Override
    public void doRender(T entity, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + entity.height / 2, z);
        double horizontal = Math.sqrt(entity.motionX * entity.motionX + entity.motionZ * entity.motionZ);
        boolean moving = horizontal + Math.abs(entity.motionY) > 1.0E-3;
        float yaw = moving ? (float) Math.toDegrees(Math.atan2(-entity.motionX, entity.motionZ)) : entity.rotationYaw;
        float pitch = moving ? (float) -Math.toDegrees(Math.atan2(entity.motionY, horizontal)) : entity.rotationPitch;
        GlStateManager.rotate(-yaw, 0, 1, 0);
        GlStateManager.rotate(pitch, 1, 0, 0);
        String model = modelName.apply(entity);
        GeoModels.render(model, 1.0F, 1.0F, 1.0F);
        if (exhaust) {
            renderExhaust(GeoModels.bounds(model)[2], entity.ticksExisted + partialTicks);
        }
        GlStateManager.popMatrix();
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    /** Additive, full-bright flame cross behind the nozzle, flickering a little. */
    private static void renderExhaust(float tail, float time) {
        float length = 0.35F + 0.08F * (float) Math.sin(time * 2.3F);
        float radius = 0.07F;
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int arm = 0; arm < 4; arm++) {
            double angle = arm * Math.PI / 4;
            double dx = Math.cos(angle) * radius;
            double dy = Math.sin(angle) * radius;
            buffer.pos(dx, dy, tail).color(1.0F, 0.85F, 0.45F, 0.9F).endVertex();
            buffer.pos(-dx, -dy, tail).color(1.0F, 0.85F, 0.45F, 0.9F).endVertex();
            buffer.pos(0, 0, tail - length).color(1.0F, 0.35F, 0.05F, 0.0F).endVertex();
        }
        Tessellator.getInstance().draw();
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(T entity) {
        return null; // GeoModels binds the texture
    }
}
