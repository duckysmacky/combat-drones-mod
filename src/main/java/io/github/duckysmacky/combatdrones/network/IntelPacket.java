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

/** Server to a unit owner: everything their recon currently has spotted (replaces the previous list). */
public class IntelPacket implements IMessage {
    private static final int MAX_ENTRIES = 64;

    public static final class Entry {
        public final double x;
        public final double y;
        public final double z;
        public final String name;
        public final boolean locked;
        /** 1 = fresh (or locked), falling to 0 as a passive sighting goes stale. */
        public final float freshness;

        public Entry(double x, double y, double z, String name, boolean locked, float freshness) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            this.locked = locked;
            this.freshness = freshness;
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
            entries.add(new Entry(buf.readDouble(), buf.readDouble(), buf.readDouble(), ByteBufUtils.readUTF8String(buf), buf.readBoolean(), buf.readFloat()));
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
            ByteBufUtils.writeUTF8String(buf, entry.name);
            buf.writeBoolean(entry.locked);
            buf.writeFloat(entry.freshness);
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
