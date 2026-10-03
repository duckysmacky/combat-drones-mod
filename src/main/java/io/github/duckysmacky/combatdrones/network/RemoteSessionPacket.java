package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server to client: a remote session started (the unit to watch, and whether it's under manual control) or ended (-1). */
public class RemoteSessionPacket implements IMessage {
    private int unitId;
    private boolean control;

    public RemoteSessionPacket() {
    }

    public RemoteSessionPacket(int unitId, boolean control) {
        this.unitId = unitId;
        this.control = control;
    }

    public int getUnitId() {
        return unitId;
    }

    public boolean isControl() {
        return control;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        unitId = buf.readInt();
        control = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(unitId);
        buf.writeBoolean(control);
    }

    public static class Handler implements IMessageHandler<RemoteSessionPacket, IMessage> {
        @Override
        public IMessage onMessage(RemoteSessionPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onRemoteSession(message));
            return null;
        }
    }
}
