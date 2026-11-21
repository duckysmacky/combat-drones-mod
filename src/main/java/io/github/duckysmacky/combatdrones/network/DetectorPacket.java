package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server to a player: pilots a Pilot Detector scan found (their stand-in bodies), each to wear a tracking dot for
 * {@code ticks}. Added to whatever dots from earlier scans are still up. With a ping interval, sent again at each ping
 * with fresh positions.
 */
public class DetectorPacket implements IMessage {
    private static final int MAX_ENTRIES = 64;

    private List<IntelPacket.Entry> found = Collections.emptyList();
    private int ticks;
    /** This player ran the scan (rather than a teammate's scan being shared, or a later ping of it). */
    private boolean own;
    /** Dots follow the bodies on the client; otherwise they stay at these positions until the next ping. */
    private boolean live;

    public DetectorPacket() {
    }

    public DetectorPacket(List<IntelPacket.Entry> found, int ticks, boolean own, boolean live) {
        this.found = found;
        this.ticks = ticks;
        this.own = own;
        this.live = live;
    }

    public List<IntelPacket.Entry> getFound() {
        return found;
    }

    public int getTicks() {
        return ticks;
    }

    public boolean isOwn() {
        return own;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ticks = buf.readInt();
        own = buf.readBoolean();
        live = buf.readBoolean();
        int count = Math.min(buf.readUnsignedByte(), MAX_ENTRIES);
        found = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            found.add(new IntelPacket.Entry(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), "", false, true, live, ticks));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ticks);
        buf.writeBoolean(own);
        buf.writeBoolean(live);
        int count = Math.min(found.size(), MAX_ENTRIES);
        buf.writeByte(count);
        for (IntelPacket.Entry entry : found.subList(0, count)) {
            buf.writeDouble(entry.x);
            buf.writeDouble(entry.y);
            buf.writeDouble(entry.z);
            buf.writeInt(entry.entityId);
        }
    }

    public static class Handler implements IMessageHandler<DetectorPacket, IMessage> {
        @Override
        public IMessage onMessage(DetectorPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onDetector(message));
            return null;
        }
    }
}
