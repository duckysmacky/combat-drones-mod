package io.github.duckysmacky.combatdrones.world;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.network.MapTilePacket;
import io.github.duckysmacky.combatdrones.network.MapTileRequestPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side snapshot of the top-down map, so the Mission Planner can draw places nobody has loaded lately. Every
 * chunk the server loads (players nearby, or a flying unit's chunk ticket) is captured on load and again on unload
 * (after decoration), kept per dimension, and saved with the world in {@code data/combat_drones_map_<dim>.dat}.
 * Each tile remembers the server tick it last changed, so clients only fetch what's new to them.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class MapTiles {
    /** A region is 8 x 8 chunks: the unit clients ask for. */
    public static final int REGION_SHIFT = 3;

    private static final class Dimension {
        final Map<Long, byte[]> tiles = new HashMap<>();
        final Map<Long, Long> changedAt = new HashMap<>();
        boolean dirty;
    }

    private static final Map<Integer, Dimension> DIMENSIONS = new HashMap<>();

    private MapTiles() {
    }

    /** Tile data for a chunk, or null if never seen. */
    @Nullable
    public static byte[] get(int dimension, int chunkX, int chunkZ) {
        Dimension tiles = DIMENSIONS.get(dimension);
        return tiles == null ? null : tiles.tiles.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** Server tick a chunk's tile last changed (0 for tiles loaded from disk), or -1 if never seen. */
    public static long changedAt(int dimension, int chunkX, int chunkZ) {
        Dimension tiles = DIMENSIONS.get(dimension);
        Long tick = tiles == null ? null : tiles.changedAt.get(ChunkPos.asLong(chunkX, chunkZ));
        return tick == null ? -1 : tick;
    }

    public static long now() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? 0 : server.getTickCounter();
    }

    /** Most tiles sent for one request; the rest of the regions wait for the next request. */
    private static final int TILE_BUDGET = 1024;
    /** One request per player per this many ticks. */
    private static final int REQUEST_INTERVAL_TICKS = 15;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    /**
     * A planner's request: whole regions in the given order (nearest first), each sending only tiles changed since
     * the client last had it, until the tile budget runs out. The last packet lists the regions sent in full.
     */
    public static void answer(EntityPlayerMP player, List<MapTileRequestPacket.Region> regions) {
        long now = now();
        Long last = LAST_REQUEST.get(player.getUniqueID());
        if (last != null && now - last < REQUEST_INTERVAL_TICKS) {
            return;
        }
        LAST_REQUEST.put(player.getUniqueID(), now);
        int dimension = player.dimension;
        Dimension tiles = DIMENSIONS.get(dimension);
        List<Long> keys = new ArrayList<>();
        List<byte[]> data = new ArrayList<>();
        List<Long> completed = new ArrayList<>();
        int sent = 0;
        for (MapTileRequestPacket.Region region : regions) {
            List<Long> regionKeys = new ArrayList<>();
            if (tiles != null) {
                for (int offsetZ = 0; offsetZ < 1 << REGION_SHIFT; offsetZ++) {
                    for (int offsetX = 0; offsetX < 1 << REGION_SHIFT; offsetX++) {
                        long key = ChunkPos.asLong((region.x << REGION_SHIFT) + offsetX, (region.z << REGION_SHIFT) + offsetZ);
                        Long changed = tiles.changedAt.get(key);
                        if (changed != null && changed > region.since) {
                            regionKeys.add(key);
                        }
                    }
                }
            }
            if (sent > 0 && sent + regionKeys.size() > TILE_BUDGET) {
                break;
            }
            for (long key : regionKeys) {
                keys.add(key);
                data.add(tiles.tiles.get(key));
                if (keys.size() == MapTilePacket.MAX_TILES) {
                    ModNetwork.CHANNEL.sendTo(new MapTilePacket(dimension, now, keys, data, new ArrayList<>()), player);
                    keys = new ArrayList<>();
                    data = new ArrayList<>();
                }
            }
            sent += regionKeys.size();
            completed.add(ChunkPos.asLong(region.x, region.z));
        }
        ModNetwork.CHANNEL.sendTo(new MapTilePacket(dimension, now, keys, data, completed), player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_REQUEST.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        capture(event.getWorld(), event.getChunk());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        capture(event.getWorld(), event.getChunk());
    }

    private static void capture(World world, Chunk chunk) {
        if (world.isRemote || chunk.isEmpty()) {
            return;
        }
        byte[] tile = MapColors.tile(world, chunk);
        Dimension tiles = DIMENSIONS.computeIfAbsent(world.provider.getDimension(), key -> new Dimension());
        long key = ChunkPos.asLong(chunk.x, chunk.z);
        if (!Arrays.equals(tiles.tiles.get(key), tile)) {
            tiles.tiles.put(key, tile);
            tiles.changedAt.put(key, now());
            tiles.dirty = true;
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        File file = fileOf(world);
        if (!file.isFile()) {
            return;
        }
        try (InputStream in = new FileInputStream(file)) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(in);
            Dimension tiles = DIMENSIONS.computeIfAbsent(world.provider.getDimension(), key -> new Dimension());
            NBTTagList list = root.getTagList("Tiles", Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < list.tagCount(); index++) {
                NBTTagCompound entry = list.getCompoundTagAt(index);
                byte[] data = entry.getByteArray("Data");
                long key = entry.getLong("Pos");
                // Chunks captured live before this load are newer than the file
                if (data.length == MapColors.TILE_SIZE && !tiles.tiles.containsKey(key)) {
                    tiles.tiles.put(key, data);
                    tiles.changedAt.put(key, 0L);
                }
            }
        } catch (IOException e) {
            CombatDrones.logger.error("Couldn't read the map snapshot {}", file, e);
        }
    }

    @SubscribeEvent
    public static void onWorldSave(WorldEvent.Save event) {
        save(event.getWorld());
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (!world.isRemote) {
            save(world);
            DIMENSIONS.remove(world.provider.getDimension());
        }
    }

    private static void save(World world) {
        Dimension tiles = world.isRemote ? null : DIMENSIONS.get(world.provider.getDimension());
        if (tiles == null || !tiles.dirty) {
            return;
        }
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Long, byte[]> tile : tiles.tiles.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("Pos", tile.getKey());
            entry.setByteArray("Data", tile.getValue());
            list.appendTag(entry);
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Tiles", list);
        File file = fileOf(world);
        File temporary = new File(file.getPath() + ".tmp");
        try {
            file.getParentFile().mkdirs();
            // Write aside and swap in, so a crash mid-save can't leave a half-written file
            try (OutputStream out = new FileOutputStream(temporary)) {
                CompressedStreamTools.writeCompressed(root, out);
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            tiles.dirty = false;
        } catch (IOException e) {
            CombatDrones.logger.error("Couldn't save the map snapshot {}", file, e);
        }
    }

    private static File fileOf(World world) {
        return new File(world.getSaveHandler().getWorldDirectory(), "data/" + CombatDrones.MOD_ID + "_map_" + world.provider.getDimension() + ".dat");
    }
}
