package io.github.duckysmacky.combatdrones.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.List;

/**
 * A launcher's item model: its flat sprite in the inventory and in item frames, its 3D geo model (drawn by
 * {@link GeoModels.ItemRenderer}) everywhere else. Remembers which view was asked for last, so the renderer knows
 * when it is drawing the first-person view.
 */
final class LauncherModel implements IBakedModel {
    private static ItemCameraTransforms.TransformType lastView = ItemCameraTransforms.TransformType.NONE;
    private final IBakedModel held;
    private final IBakedModel icon;

    LauncherModel(IBakedModel held, IBakedModel icon) {
        this.held = held;
        this.icon = icon;
    }

    static boolean isFirstPerson() {
        return lastView == ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND || lastView == ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND;
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType view) {
        lastView = view;
        boolean flat = view == ItemCameraTransforms.TransformType.GUI || view == ItemCameraTransforms.TransformType.FIXED;
        return (flat ? icon : held).handlePerspective(view);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        return held.getQuads(state, side, rand);
    }

    @Override
    public boolean isAmbientOcclusion() {
        return held.isAmbientOcclusion();
    }

    /** As the sprite: this is what vanilla lights the inventory slot by. */
    @Override
    public boolean isGui3d() {
        return icon.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return held.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return icon.getParticleTexture();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return held.getOverrides();
    }
}
