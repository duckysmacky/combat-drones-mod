package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.github.duckysmacky.combatdrones.world.MapColors;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: map tiles for the Mission Planner. A reply to one request comes as several of these; the last
 * one lists the regions now fully up to date as of {@link #getServerTick()}.
 */
public class MapTilePacket implements IMessage {
    public static final int MAX_TILES = 64;

    private int dimension;
    private long serverTick;
    private List<Long> tileKeys = new ArrayList<>();
    private List<byte[]> tiles = new ArrayList<>();
    private List<Long> completedRegions = new ArrayList<>();

    public MapTilePacket() {
    }

    public MapTilePacket(int dimension, long serverTick, List<Long> tileKeys, List<byte[]> tiles, List<Long> completedRegions) {
        this.dimension = dimension;
        this.serverTick = serverTick;
        this.tileKeys = tileKeys;
        this.tiles = tiles;
        this.completedRegions = completedRegions;
    }

    public int getDimension() {
        return dimension;
    }

    public long getServerTick() {
        return serverTick;
    }

    public List<Long> getTileKeys() {
        return tileKeys;
    }

    public List<byte[]> getTiles() {
        return tiles;
    }

    public List<Long> getCompletedRegions() {
        return completedRegions;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimension = buf.readInt();
        serverTick = buf.readLong();
        int tileCount = Math.min(buf.readUnsignedShort(), MAX_TILES);
        for (int index = 0; index < tileCount; index++) {
            tileKeys.add(buf.readLong());
            byte[] tile = new byte[MapColors.TILE_SIZE];
            buf.readBytes(tile);
            tiles.add(tile);
        }
        int regionCount = Math.min(buf.readUnsignedShort(), MapTileRequestPacket.MAX_REGIONS);
        for (int index = 0; index < regionCount; index++) {
            completedRegions.add(buf.readLong());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeLong(serverTick);
        buf.writeShort(tiles.size());
        for (int index = 0; index < tiles.size(); index++) {
            buf.writeLong(tileKeys.get(index));
            buf.writeBytes(tiles.get(index));
        }
        buf.writeShort(completedRegions.size());
        for (long region : completedRegions) {
            buf.writeLong(region);
        }
    }

    public static class Handler implements IMessageHandler<MapTilePacket, IMessage> {
        @Override
        public IMessage onMessage(MapTilePacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onMapTiles(message));
            return null;
        }
    }
}
