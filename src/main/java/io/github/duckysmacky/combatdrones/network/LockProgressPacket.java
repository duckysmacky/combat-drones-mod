package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server to a recon camera viewer: how far along a lock is (0 = nothing under the crosshair, 1 = locked). */
public class LockProgressPacket implements IMessage {
    private float progress;

    public LockProgressPacket() {
    }

    public LockProgressPacket(float progress) {
        this.progress = progress;
    }

    public float getProgress() {
        return progress;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        progress = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(progress);
    }

    public static class Handler implements IMessageHandler<LockProgressPacket, IMessage> {
        @Override
        public IMessage onMessage(LockProgressPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onLockProgress(message));
            return null;
        }
    }
}
