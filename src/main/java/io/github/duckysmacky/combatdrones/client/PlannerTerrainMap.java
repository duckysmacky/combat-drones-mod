package io.github.duckysmacky.combatdrones.client;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

/**
 * Top-down terrain under the Mission Planner map, shaded like a vanilla map, at one block per texel. Built from the
 * chunks the client has loaded, so anything past render distance stays blank. A window of {@link #SIZE} blocks follows
 * the map centre and is rescanned a few rows per frame, so newly loaded chunks fill in while the planner is open.
 */
final class PlannerTerrainMap {
    private static final int SIZE = 512;
    private static final int ROWS_PER_FRAME = 16;

    private final DynamicTexture texture = new DynamicTexture(SIZE, SIZE);
    private final int[] pixels = texture.getTextureData();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int originX = Integer.MIN_VALUE;
    private int originZ;
    private int nextRow;

    /** Moves the window when the view centre strays from it, then scans the next few rows. */
    void update(WorldClient world, double centerX, double centerZ) {
        if (originX == Integer.MIN_VALUE || Math.abs(centerX - (originX + SIZE / 2)) > SIZE / 4
                || Math.abs(centerZ - (originZ + SIZE / 2)) > SIZE / 4) {
            originX = (int) Math.floor(centerX) - SIZE / 2;
            originZ = (int) Math.floor(centerZ) - SIZE / 2;
            Arrays.fill(pixels, 0);
            nextRow = 0;
        }
        for (int row = 0; row < ROWS_PER_FRAME; row++) {
            int worldZ = originZ + nextRow;
            for (int column = 0; column < SIZE; column++) {
                pixels[nextRow * SIZE + column] = colorAt(world, originX + column, worldZ);
            }
            nextRow = (nextRow + 1) % SIZE;
        }
        texture.updateDynamicTexture();
    }

    /** Vanilla map shading: brighter where the ground rises toward the south, darker where it falls. */
    private int colorAt(WorldClient world, int x, int z) {
        Chunk chunk = world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
        if (chunk == null || chunk.isEmpty()) {
            return 0;
        }
        int height = surfaceHeight(chunk, x, z);
        if (height <= 0) {
            return 0;
        }
        cursor.setPos(x, height - 1, z);
        IBlockState state = chunk.getBlockState(cursor);
        MapColor color = state.getMapColor(world, cursor);
        Chunk northChunk = world.getChunkProvider().getLoadedChunk(x >> 4, (z - 1) >> 4);
        int northHeight = northChunk == null || northChunk.isEmpty() ? height : surfaceHeight(northChunk, x, z - 1);
        int shade = height > northHeight ? 2 : height < northHeight ? 0 : 1;
        return color.getMapColor(shade);
    }

    /** One above the topmost block that shows on a map (skips air and other colourless blocks under the heightmap). */
    private int surfaceHeight(Chunk chunk, int x, int z) {
        int height = chunk.getHeightValue(x & 15, z & 15);
        while (height > 0) {
            cursor.setPos(x, height - 1, z);
            if (chunk.getBlockState(cursor).getMaterial().getMaterialMapColor() != MapColor.AIR) {
                break;
            }
            height--;
        }
        return height;
    }

    /** Draws the scanned window into the given screen rectangle, with the same world-to-screen mapping as the map. */
    void draw(int left, int top, int right, int bottom, double centerX, double centerZ, double scale) {
        double midX = (left + right) / 2.0;
        double midY = (top + bottom) / 2.0;
        double screenLeft = Math.max(left, midX + (originX - centerX) * scale);
        double screenTop = Math.max(top, midY + (originZ - centerZ) * scale);
        double screenRight = Math.min(right, midX + (originX + SIZE - centerX) * scale);
        double screenBottom = Math.min(bottom, midY + (originZ + SIZE - centerZ) * scale);
        if (screenLeft >= screenRight || screenTop >= screenBottom) {
            return;
        }
        double u0 = (centerX + (screenLeft - midX) / scale - originX) / SIZE;
        double v0 = (centerZ + (screenTop - midY) / scale - originZ) / SIZE;
        double u1 = (centerX + (screenRight - midX) / scale - originX) / SIZE;
        double v1 = (centerZ + (screenBottom - midY) / scale - originZ) / SIZE;

        GlStateManager.enableBlend();
        GlStateManager.color(0.8F, 0.8F, 0.8F, 1.0F);
        GlStateManager.bindTexture(texture.getGlTextureId());
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(screenLeft, screenBottom, 0).tex(u0, v1).endVertex();
        buffer.pos(screenRight, screenBottom, 0).tex(u1, v1).endVertex();
        buffer.pos(screenRight, screenTop, 0).tex(u1, v0).endVertex();
        buffer.pos(screenLeft, screenTop, 0).tex(u0, v0).endVertex();
        tessellator.draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    void delete() {
        texture.deleteGlTexture();
    }
}
