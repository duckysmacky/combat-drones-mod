package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client, twice a second: running ramp units within earshot that this client doesn't track (beyond the
 * server's entity view distance), so their engines can still be heard. Position and velocity let the client
 * dead-reckon between updates.
 */
public class EngineSoundPacket implements IMessage {
    public static final int MAX_ENTRIES = 32;

    public static final class Entry {
        public final int entityId;
        public final String typeId;
        public final float x;
        public final float y;
        public final float z;
        public final float motionX;
        public final float motionY;
        public final float motionZ;
        public final boolean boosting;

        public Entry(int entityId, String typeId, float x, float y, float z, float motionX, float motionY, float motionZ, boolean boosting) {
            this.entityId = entityId;
            this.typeId = typeId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.boosting = boosting;
        }
    }

    private List<Entry> entries = new ArrayList<>();

    public EngineSoundPacket() {
    }

    public EngineSoundPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = Math.min(buf.readUnsignedByte(), MAX_ENTRIES);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buf.readInt(), ByteBufUtils.readUTF8String(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeByte(count);
        for (Entry entry : entries.subList(0, count)) {
            buf.writeInt(entry.entityId);
            ByteBufUtils.writeUTF8String(buf, entry.typeId);
            buf.writeFloat(entry.x);
            buf.writeFloat(entry.y);
            buf.writeFloat(entry.z);
            buf.writeFloat(entry.motionX);
            buf.writeFloat(entry.motionY);
            buf.writeFloat(entry.motionZ);
            buf.writeBoolean(entry.boosting);
        }
    }

    public static class Handler implements IMessageHandler<EngineSoundPacket, IMessage> {
        @Override
        public IMessage onMessage(EngineSoundPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onEngineSounds(message));
            return null;
        }
    }
}
