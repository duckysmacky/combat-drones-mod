package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: a remote session started (the vehicle ridden, whether it's under manual control, whether it's a
 * hand-flown drone, and where the player's body was left) or ended (-1).
 */
public class RemoteSessionPacket implements IMessage {
    private int unitId;
    private boolean control;
    private boolean drone;
    private double homeX;
    private double homeY;
    private double homeZ;

    public RemoteSessionPacket() {
    }

    public RemoteSessionPacket(int unitId, boolean control, boolean drone, double homeX, double homeY, double homeZ) {
        this.unitId = unitId;
        this.control = control;
        this.drone = drone;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
    }

    public int getUnitId() {
        return unitId;
    }

    public boolean isControl() {
        return control;
    }

    public boolean isDrone() {
        return drone;
    }

    public double getHomeX() {
        return homeX;
    }

    public double getHomeY() {
        return homeY;
    }

    public double getHomeZ() {
        return homeZ;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        unitId = buf.readInt();
        control = buf.readBoolean();
        drone = buf.readBoolean();
        homeX = buf.readDouble();
        homeY = buf.readDouble();
        homeZ = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(unitId);
        buf.writeBoolean(control);
        buf.writeBoolean(drone);
        buf.writeDouble(homeX);
        buf.writeDouble(homeY);
        buf.writeDouble(homeZ);
    }

    public static class Handler implements IMessageHandler<RemoteSessionPacket, IMessage> {
        @Override
        public IMessage onMessage(RemoteSessionPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onRemoteSession(message));
            return null;
        }
    }
}
