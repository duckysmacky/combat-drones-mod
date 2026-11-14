package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to a player a recon UAV is after. {@code locked} false: a lock-on on them is building ({@code value} = progress
 * 0..1, 0 = it stopped). {@code locked} true: they're locked on ({@code value} = seconds left, 0 = it ended). Never
 * says who.
 */
public class LockWarningPacket implements IMessage {
    private boolean locked;
    private float value;

    public LockWarningPacket() {
    }

    public LockWarningPacket(boolean locked, float value) {
        this.locked = locked;
        this.value = value;
    }

    public boolean isLocked() {
        return locked;
    }

    public float getValue() {
        return value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        locked = buf.readBoolean();
        value = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(locked);
        buf.writeFloat(value);
    }

    public static class Handler implements IMessageHandler<LockWarningPacket, IMessage> {
        @Override
        public IMessage onMessage(LockWarningPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onLockWarning(message));
            return null;
        }
    }
}
