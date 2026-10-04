package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.world.MapTiles;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client to server, from an open Mission Planner: the map regions (8 x 8 chunks) it's showing, nearest first, each
 * with the server tick it was last brought up to date (-1 for never). Answered with {@link MapTilePacket}s.
 */
public class MapTileRequestPacket implements IMessage {
    public static final int MAX_REGIONS = 256;

    public static final class Region {
        public final int x;
        public final int z;
        public final long since;

        public Region(int x, int z, long since) {
            this.x = x;
            this.z = z;
            this.since = since;
        }
    }

    private List<Region> regions = new ArrayList<>();

    public MapTileRequestPacket() {
    }

    public MapTileRequestPacket(List<Region> regions) {
        this.regions = regions;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = Math.min(buf.readUnsignedShort(), MAX_REGIONS);
        regions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            regions.add(new Region(buf.readInt(), buf.readInt(), buf.readLong()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(regions.size(), MAX_REGIONS);
        buf.writeShort(count);
        for (Region region : regions.subList(0, count)) {
            buf.writeInt(region.x);
            buf.writeInt(region.z);
            buf.writeLong(region.since);
        }
    }

    public static class Handler implements IMessageHandler<MapTileRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(MapTileRequestPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> MapTiles.answer(player, message.regions));
            return null;
        }
    }
}
