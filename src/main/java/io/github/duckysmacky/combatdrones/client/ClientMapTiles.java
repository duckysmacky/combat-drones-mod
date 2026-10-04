package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.network.MapTilePacket;
import io.github.duckysmacky.combatdrones.network.MapTileRequestPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.world.MapTiles;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The server's map snapshot as far as this client has fetched it, for the Mission Planner. Kept for the session and
 * one dimension at a time. Each 8 x 8-chunk region remembers when it was last brought up to date, so re-requests only
 * fetch what changed.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class ClientMapTiles {
    /** Ticks between requests while the planner is open (the server also rate-limits). */
    private static final int REQUEST_INTERVAL_TICKS = 20;

    private static final Map<Long, byte[]> TILES = new HashMap<>();
    private static final Map<Long, Long> REGION_SINCE = new HashMap<>();
    private static int dimension = Integer.MIN_VALUE;
    private static long lastRequestTime;

    private ClientMapTiles() {
    }

    @Nullable
    static byte[] get(int chunkX, int chunkZ) {
        return TILES.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** Asks for the regions covering a block area (nearest to its centre first), at most once a second. */
    static void request(double minX, double minZ, double maxX, double maxZ) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.world.getTotalWorldTime() - lastRequestTime < REQUEST_INTERVAL_TICKS) {
            return;
        }
        lastRequestTime = minecraft.world.getTotalWorldTime();
        if (minecraft.player.dimension != dimension) {
            clear();
            dimension = minecraft.player.dimension;
        }
        int shift = 4 + MapTiles.REGION_SHIFT;
        int fromX = (int) Math.floor(minX) >> shift;
        int fromZ = (int) Math.floor(minZ) >> shift;
        int toX = (int) Math.floor(maxX) >> shift;
        int toZ = (int) Math.floor(maxZ) >> shift;
        double centerX = (fromX + toX) / 2.0;
        double centerZ = (fromZ + toZ) / 2.0;
        List<MapTileRequestPacket.Region> regions = new ArrayList<>();
        for (int regionZ = fromZ; regionZ <= toZ; regionZ++) {
            for (int regionX = fromX; regionX <= toX; regionX++) {
                Long since = REGION_SINCE.get(ChunkPos.asLong(regionX, regionZ));
                regions.add(new MapTileRequestPacket.Region(regionX, regionZ, since == null ? -1 : since));
            }
        }
        regions.sort(Comparator.comparingDouble(region -> Math.hypot(region.x - centerX, region.z - centerZ)));
        if (regions.size() > MapTileRequestPacket.MAX_REGIONS) {
            regions = new ArrayList<>(regions.subList(0, MapTileRequestPacket.MAX_REGIONS));
        }
        ModNetwork.CHANNEL.sendToServer(new MapTileRequestPacket(regions));
    }

    static void accept(MapTilePacket packet) {
        if (packet.getDimension() != dimension) {
            return;
        }
        for (int index = 0; index < packet.getTiles().size(); index++) {
            TILES.put(packet.getTileKeys().get(index), packet.getTiles().get(index));
        }
        for (long region : packet.getCompletedRegions()) {
            REGION_SINCE.put(region, packet.getServerTick());
        }
    }

    private static void clear() {
        TILES.clear();
        REGION_SINCE.clear();
    }

    /** Fired on the network thread: hand the reset to the client thread, which owns these maps. */
    @SubscribeEvent
    public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            clear();
            dimension = Integer.MIN_VALUE;
            lastRequestTime = 0;
        });
    }
}
