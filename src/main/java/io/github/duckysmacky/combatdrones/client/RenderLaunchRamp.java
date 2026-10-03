package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.block.BlockLaunchRamp;
import io.github.duckysmacky.combatdrones.block.TileLaunchRamp;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/** Draws the unit waiting on a Launch Ramp, nose up the rail along the ramp's facing. */
public class RenderLaunchRamp extends TileEntitySpecialRenderer<TileLaunchRamp> {
    private static final float PIXEL = 1.0F / 16.0F;
    private static final float RAIL_ANGLE = 22.5F;

    private final RenderRampUnit.PlaneModel model = new RenderRampUnit.PlaneModel();

    @Override
    public void render(TileLaunchRamp ramp, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (!ramp.isLoaded()) {
            return;
        }
        IBlockState state = ramp.getWorld().getBlockState(ramp.getPos());
        if (!(state.getBlock() instanceof BlockLaunchRamp)) {
            return;
        }
        EnumFacing facing = state.getValue(BlockLaunchRamp.FACING);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.75, z + 0.5);
        // Same orientation maths as the flying unit: vanilla yaw for the facing, nose pitched up the rail
        GlStateManager.rotate(-facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.rotate(-RAIL_ANGLE, 1, 0, 0);
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        bindTexture(RenderRampUnit.TEXTURE);
        model.render(null, 0, 0, 0, 0, 0, PIXEL);
        GlStateManager.popMatrix();
    }
}
