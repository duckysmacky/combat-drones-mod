package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityPilotBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

/** Draws a stand-in body as its player: their skin (slim or wide arms), armour and held items. */
public class RenderPilotBody extends RenderLivingBase<EntityPilotBody> {
    private final ModelPlayer wideModel;
    private final ModelPlayer slimModel = new PilotModel(true);

    public RenderPilotBody(RenderManager renderManager) {
        super(renderManager, new PilotModel(false), 0.5F);
        wideModel = (ModelPlayer) mainModel;
        addLayer(new LayerBipedArmor(this) {
            @Override
            protected void initArmor() {
                modelLeggings = new PilotArmor(0.5F);
                modelArmor = new PilotArmor(1.0F);
            }
        });
        addLayer(new LayerHeldItem(this));
    }

    @Override
    public void doRender(EntityPilotBody body, double x, double y, double z, float entityYaw, float partialTicks) {
        NetworkPlayerInfo info = playerInfo(body);
        UUID owner = body.getOwner();
        String skinType = info != null ? info.getSkinType() : owner != null ? DefaultPlayerSkin.getSkinType(owner) : "default";
        mainModel = "slim".equals(skinType) ? slimModel : wideModel;
        super.doRender(body, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityPilotBody body) {
        NetworkPlayerInfo info = playerInfo(body);
        if (info != null) {
            return info.getLocationSkin();
        }
        UUID owner = body.getOwner();
        return owner != null ? DefaultPlayerSkin.getDefaultSkin(owner) : DefaultPlayerSkin.getDefaultSkinLegacy();
    }

    /** The pilot's stance: both hands up on the controller, head tipped down into the goggles. */
    private static void pose(ModelBiped model) {
        model.bipedRightArm.rotateAngleX = -0.95F;
        model.bipedRightArm.rotateAngleY = -0.38F;
        model.bipedRightArm.rotateAngleZ = 0;
        model.bipedLeftArm.rotateAngleX = -0.95F;
        model.bipedLeftArm.rotateAngleY = 0.38F;
        model.bipedLeftArm.rotateAngleZ = 0;
        model.bipedHead.rotateAngleX += 0.14F;
        ModelBase.copyModelAngles(model.bipedHead, model.bipedHeadwear);
    }

    private static final class PilotModel extends ModelPlayer {
        PilotModel(boolean slim) {
            super(0.0F, slim);
        }

        @Override
        public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                                      float scaleFactor, Entity entity) {
            super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entity);
            pose(this);
            copyModelAngles(bipedRightArm, bipedRightArmwear);
            copyModelAngles(bipedLeftArm, bipedLeftArmwear);
        }
    }

    /** Armour that follows the pilot's stance instead of vanilla's arms-down one. */
    private static final class PilotArmor extends ModelBiped {
        PilotArmor(float size) {
            super(size);
        }

        @Override
        public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                                      float scaleFactor, Entity entity) {
            super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entity);
            pose(this);
        }
    }

    /** The owner's tab-list entry, which holds their downloaded skin (null if they're offline). */
    @Nullable
    private static NetworkPlayerInfo playerInfo(EntityPilotBody body) {
        NetHandlerPlayClient connection = Minecraft.getMinecraft().getConnection();
        UUID owner = body.getOwner();
        return connection == null || owner == null ? null : connection.getPlayerInfo(owner);
    }

    @Override
    protected void preRenderCallback(EntityPilotBody body, float partialTicks) {
        // Same scale vanilla uses for players
        GlStateManager.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
