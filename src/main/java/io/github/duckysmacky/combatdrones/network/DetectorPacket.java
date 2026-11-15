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
 * {@code ticks}. Added to whatever dots from earlier scans are still up.
 */
public class DetectorPacket implements IMessage {
    private static final int MAX_ENTRIES = 64;

    private List<IntelPacket.Entry> found = Collections.emptyList();
    private int ticks;
    /** This player ran the scan (rather than a teammate's scan being shared). */
    private boolean own;

    public DetectorPacket() {
    }

    public DetectorPacket(List<IntelPacket.Entry> found, int ticks, boolean own) {
        this.found = found;
        this.ticks = ticks;
        this.own = own;
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
        int count = Math.min(buf.readUnsignedByte(), MAX_ENTRIES);
        found = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            found.add(new IntelPacket.Entry(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), "", false, true, true, ticks));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ticks);
        buf.writeBoolean(own);
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
