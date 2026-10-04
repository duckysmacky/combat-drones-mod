package io.github.duckysmacky.combatdrones.world;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;

/**
 * Top-down map colours shared by the server's map snapshot and the planner's live view. A column is encoded like
 * vanilla maps: {@code colorIndex * 4 + shade} in one byte, 0 for nothing. Shading follows vanilla too: brighter
 * where the ground rises toward the south, darker where it falls.
 */
public final class MapColors {
    public static final int TILE_SIZE = 16 * 16;

    private MapColors() {
    }

    /** All 256 columns of a chunk, row by row ({@code z * 16 + x}). */
    public static byte[] tile(World world, Chunk chunk) {
        byte[] tile = new byte[TILE_SIZE];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Chunk north = world.getChunkProvider().getLoadedChunk(chunk.x, chunk.z - 1);
        int baseX = chunk.x << 4;
        int baseZ = chunk.z << 4;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                tile[localZ * 16 + localX] = encode(world, chunk, localZ == 0 ? north : chunk, baseX + localX, baseZ + localZ, cursor);
            }
        }
        return tile;
    }

    /**
     * One column's encoded colour. {@code northChunk} holds the column north of it (the same chunk unless it's on
     * the chunk's north edge); without it the column is drawn unshaded.
     */
    public static byte encode(World world, Chunk chunk, @Nullable Chunk northChunk, int x, int z, BlockPos.MutableBlockPos cursor) {
        if (chunk.isEmpty()) {
            return 0;
        }
        int height = surfaceHeight(chunk, x, z, cursor);
        if (height <= 0) {
            return 0;
        }
        cursor.setPos(x, height - 1, z);
        IBlockState state = chunk.getBlockState(cursor);
        MapColor color = state.getMapColor(world, cursor);
        if (color == MapColor.AIR) {
            return 0;
        }
        int northHeight = northChunk == null || northChunk.isEmpty() ? height : surfaceHeight(northChunk, x, z - 1, cursor);
        int shade = height > northHeight ? 2 : height < northHeight ? 0 : 1;
        return (byte) (color.colorIndex * 4 + shade);
    }

    /** ARGB for an encoded colour (fully transparent for 0). */
    public static int toArgb(byte encoded) {
        int value = encoded & 0xFF;
        int index = value >> 2;
        if (index == 0 || index >= MapColor.COLORS.length || MapColor.COLORS[index] == null) {
            return 0;
        }
        return MapColor.COLORS[index].getMapColor(value & 3);
    }

    /** One above the topmost block that shows on a map (skips air and other colourless blocks under the heightmap). */
    private static int surfaceHeight(Chunk chunk, int x, int z, BlockPos.MutableBlockPos cursor) {
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
}
