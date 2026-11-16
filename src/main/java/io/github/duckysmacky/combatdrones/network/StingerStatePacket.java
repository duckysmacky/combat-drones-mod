package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to whoever aims a Stinger: the aircraft its seeker is on (entity ID, -1 = none) and how far along the lock
 * is (0 = nothing, 1 = locked).
 */
public class StingerStatePacket implements IMessage {
    private int targetId;
    private float progress;

    public StingerStatePacket() {
    }

    public StingerStatePacket(int targetId, float progress) {
        this.targetId = targetId;
        this.progress = progress;
    }

    public int getTargetId() {
        return targetId;
    }

    public float getProgress() {
        return progress;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetId = buf.readInt();
        progress = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetId);
        buf.writeFloat(progress);
    }

    public static class Handler implements IMessageHandler<StingerStatePacket, IMessage> {
        @Override
        public IMessage onMessage(StingerStatePacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onStingerState(message));
            return null;
        }
    }
}
