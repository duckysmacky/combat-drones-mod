package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityMissilePad;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import javax.vecmath.AxisAngle4d;

/**
 * Launch pad ({@code geo/missile_pad}) with the loaded missile standing on its deck, nose up. Drawn exactly where (and
 * how) the {@code EntityCruiseMissile} appears at ignition, so the hand-over on launch doesn't jump.
 */
public class RenderMissilePad extends Render<EntityMissilePad> {
    public RenderMissilePad(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 1.2F;
    }

    @Override
    public void doRender(EntityMissilePad pad, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.pushMatrix();
        GlStateManager.rotate(-pad.rotationYaw, 0, 1, 0);
        GeoModels.render("missile_pad", 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        DroneType missile = pad.getMissileType();
        if (missile != null) {
            GlStateManager.translate(0, EntityMissilePad.DECK_HEIGHT + missile.getHitboxHeight() / 2, 0);
            AxisAngle4d rotation = new AxisAngle4d();
            rotation.set(HandFlownFlight.fromEuler(pad.rotationYaw, -90, 0));
            GlStateManager.rotate((float) Math.toDegrees(rotation.angle), (float) rotation.x, (float) rotation.y, (float) rotation.z);
            RenderRampUnit.renderStill(missile);
        }
        GlStateManager.popMatrix();
        super.doRender(pad, x, y, z, entityYaw, partialTicks);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityMissilePad pad) {
        return null; // GeoModels binds the texture
    }
}
