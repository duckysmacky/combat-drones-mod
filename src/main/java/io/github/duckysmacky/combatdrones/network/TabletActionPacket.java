package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.MissileServer;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server, from the Missile Control Tablet screen: poll the status, launch, or order the strike on a point. Intent
 * only: the server finds the tablet in the sender's hands and checks everything itself.
 */
public class TabletActionPacket implements IMessage {
    public enum Action { STATUS, LAUNCH, STRIKE }

    private Action action = Action.STATUS;
    private double targetX;
    private double targetZ;

    public TabletActionPacket() {
    }

    public TabletActionPacket(Action action, double targetX, double targetZ) {
        this.action = action;
        this.targetX = targetX;
        this.targetZ = targetZ;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        action = ordinal >= 0 && ordinal < Action.values().length ? Action.values()[ordinal] : Action.STATUS;
        targetX = buf.readDouble();
        targetZ = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeDouble(targetX);
        buf.writeDouble(targetZ);
    }

    public static class Handler implements IMessageHandler<TabletActionPacket, IMessage> {
        @Override
        public IMessage onMessage(TabletActionPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(
                    () -> MissileServer.onTabletAction(player, message.action, message.targetX, message.targetZ));
            return null;
        }
    }
}
