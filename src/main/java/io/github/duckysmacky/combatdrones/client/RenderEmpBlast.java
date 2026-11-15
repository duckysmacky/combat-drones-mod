package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.client.fx.EmpField;
import io.github.duckysmacky.combatdrones.entity.EntityEmpBlast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * An EMP blast: while it builds up, a small crackling glow round the user (not drawn for the user's own first-person
 * view, where it would fill the screen); then the field, growing to its full size and fading. Worked out from the
 * entity's age, so every client draws the same field.
 */
public class RenderEmpBlast extends Render<EntityEmpBlast> {
    private static final double CHARGE_GLOW_RADIUS = 0.7;

    public RenderEmpBlast(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0;
    }

    @Override
    public void doRender(EntityEmpBlast blast, double x, double y, double z, float entityYaw, float partialTicks) {
        float age = blast.ticksExisted + partialTicks;
        int build = blast.getBuildTicks();
        int expand = blast.getExpandTicks();
        if (age < build) {
            Minecraft minecraft = Minecraft.getMinecraft();
            boolean ownView = minecraft.player != null && minecraft.player.getEntityId() == blast.getUserId() && minecraft.gameSettings.thirdPersonView == 0;
            if (!ownView) {
                float progress = age / build;
                EmpField.draw(x, y, z, CHARGE_GLOW_RADIUS * (0.4 + 0.6 * progress), age * 3, 0.3F + 0.7F * progress);
            }
            return;
        }
        float since = age - build;
        float strength = since <= expand ? 1 : Math.max(0, 1 - (since - expand) / EntityEmpBlast.FADE_TICKS);
        if (strength > 0) {
            EmpField.draw(x, y, z, EntityEmpBlast.radiusAt(since, expand, blast.getMaxRadius()), age, strength);
        }
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityEmpBlast blast) {
        return null;
    }
}
