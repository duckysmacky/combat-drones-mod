package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server to a player: every tracking dot and lock-on from their own (or their teammates') recon UAVs that they may
 * see. Replaces the previous list.
 */
public class IntelPacket implements IMessage {
    private static final int MAX_ENTRIES = 64;

    public static final class Entry {
        /** Where the target is (feet), as the server last knew it. */
        public final double x;
        public final double y;
        public final double z;
        /** The target's entity id, so a client that tracks it can draw it where it really is. */
        public final int entityId;
        public final String name;
        /** A lock-on (ESP box) rather than a tracking dot. */
        public final boolean locked;
        /** A player (or a player's stand-in body): red; anything else yellow. */
        public final boolean player;
        /** Follow the entity live on the client; otherwise stay at the server's snapshot until the next update. */
        public final boolean live;
        /** A lock-on's ticks left (0 for a dot). */
        public final int ticksLeft;

        public Entry(double x, double y, double z, int entityId, String name, boolean locked, boolean player, boolean live, int ticksLeft) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.entityId = entityId;
            this.name = name;
            this.locked = locked;
            this.player = player;
            this.live = live;
            this.ticksLeft = ticksLeft;
        }
    }

    private List<Entry> entries = Collections.emptyList();

    public IntelPacket() {
    }

    public IntelPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = Math.min(buf.readUnsignedByte(), MAX_ENTRIES);
        entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), ByteBufUtils.readUTF8String(buf),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readInt()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeByte(count);
        for (Entry entry : entries.subList(0, count)) {
            buf.writeDouble(entry.x);
            buf.writeDouble(entry.y);
            buf.writeDouble(entry.z);
            buf.writeInt(entry.entityId);
            ByteBufUtils.writeUTF8String(buf, entry.name);
            buf.writeBoolean(entry.locked);
            buf.writeBoolean(entry.player);
            buf.writeBoolean(entry.live);
            buf.writeInt(entry.ticksLeft);
        }
    }

    public static class Handler implements IMessageHandler<IntelPacket, IMessage> {
        @Override
        public IMessage onMessage(IntelPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onIntel(message));
            return null;
        }
    }
}
