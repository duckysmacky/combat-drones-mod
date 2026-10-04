package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.world.MapColors;
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
 * Top-down terrain under the Mission Planner map, shaded like a vanilla map. Chunks this client has loaded are drawn
 * live; everything else comes from the server's map snapshot ({@link ClientMapTiles}), so places seen before (by any
 * player or unit) stay on the map. A window of {@link #SIZE} texels follows the map centre, at one block per texel or
 * coarser when zoomed out, and is rescanned a few rows per frame so arriving tiles fill in while the planner is open.
 */
final class PlannerTerrainMap {
    private static final int SIZE = 512;
    private static final int ROWS_PER_FRAME = 16;

    private final DynamicTexture texture = new DynamicTexture(SIZE, SIZE);
    private final int[] pixels = texture.getTextureData();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int originX = Integer.MIN_VALUE;
    private int originZ;
    private int blocksPerTexel = 1;
    private int nextRow;

    /**
     * Moves the window when the view centre strays from it (or the zoom needs a different resolution), asks the
     * server for the map around it, then scans the next few rows.
     *
     * @param visibleBlocks width of the area the map currently shows, in blocks
     */
    void update(WorldClient world, double centerX, double centerZ, double visibleBlocks) {
        int wantedResolution = 1;
        while (SIZE * wantedResolution < visibleBlocks * 1.5 && wantedResolution < 16) {
            wantedResolution *= 2;
        }
        int extent = SIZE * blocksPerTexel;
        if (originX == Integer.MIN_VALUE || wantedResolution != blocksPerTexel || Math.abs(centerX - (originX + extent / 2)) > extent / 4
                || Math.abs(centerZ - (originZ + extent / 2)) > extent / 4) {
            blocksPerTexel = wantedResolution;
            extent = SIZE * blocksPerTexel;
            originX = (int) Math.floor(centerX) - extent / 2;
            originZ = (int) Math.floor(centerZ) - extent / 2;
            Arrays.fill(pixels, 0);
            nextRow = 0;
        }
        ClientMapTiles.request(originX, originZ, originX + extent, originZ + extent);
        for (int row = 0; row < ROWS_PER_FRAME; row++) {
            int worldZ = originZ + nextRow * blocksPerTexel;
            for (int column = 0; column < SIZE; column++) {
                pixels[nextRow * SIZE + column] = colorAt(world, originX + column * blocksPerTexel, worldZ);
            }
            nextRow = (nextRow + 1) % SIZE;
        }
        texture.updateDynamicTexture();
    }

    /** Live from a loaded chunk, else the server's snapshot, else blank. */
    private int colorAt(WorldClient world, int x, int z) {
        Chunk chunk = world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
        if (chunk != null && !chunk.isEmpty()) {
            Chunk north = (z & 15) == 0 ? world.getChunkProvider().getLoadedChunk(x >> 4, (z - 1) >> 4) : chunk;
            return MapColors.toArgb(MapColors.encode(world, chunk, north, x, z, cursor));
        }
        byte[] tile = ClientMapTiles.get(x >> 4, z >> 4);
        return tile == null ? 0 : MapColors.toArgb(tile[(z & 15) * 16 + (x & 15)]);
    }

    /** Draws the scanned window into the given screen rectangle, with the same world-to-screen mapping as the map. */
    void draw(int left, int top, int right, int bottom, double centerX, double centerZ, double scale) {
        int extent = SIZE * blocksPerTexel;
        double midX = (left + right) / 2.0;
        double midY = (top + bottom) / 2.0;
        double screenLeft = Math.max(left, midX + (originX - centerX) * scale);
        double screenTop = Math.max(top, midY + (originZ - centerZ) * scale);
        double screenRight = Math.min(right, midX + (originX + extent - centerX) * scale);
        double screenBottom = Math.min(bottom, midY + (originZ + extent - centerZ) * scale);
        if (screenLeft >= screenRight || screenTop >= screenBottom) {
            return;
        }
        double u0 = (centerX + (screenLeft - midX) / scale - originX) / extent;
        double v0 = (centerZ + (screenTop - midY) / scale - originZ) / extent;
        double u1 = (centerX + (screenRight - midX) / scale - originX) / extent;
        double v1 = (centerZ + (screenBottom - midY) / scale - originZ) / extent;

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
