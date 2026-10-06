package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: what the Missile Control Tablet shows. Sent when it's opened ({@code open}: show the screen) and in reply to
 * its status polls. Everything is display-only; LAUNCH and TAKE CONTROL are re-validated server-side.
 */
public class TabletStatusPacket implements IMessage {
    public enum State {
        NO_PAD, NO_MISSILE, NO_FUEL, SKY_BLOCKED, READY, LAUNCHING, STANDBY, STRIKING, IMPACT, DESTROYED, LOST;

        public boolean canLaunch() {
            return this == READY;
        }
    }

    public boolean open;
    public State state = State.NO_PAD;
    /** Launch stage ordinal ({@code CruiseMissileFlight.Stage}) while LAUNCHING, else -1. */
    public int stage = -1;
    /** Ticks left: in the launch stage, until ready, of standby fuel, or until arrival (by state). */
    public int ticksLeft;
    public float fuelFraction;
    public float healthFraction = 1;
    public int padX;
    public int padY;
    public int padZ;
    public float strikeRange;
    /** How far from the strike point the dive can be steered (blocks), for the map. */
    public float steerReach;

    @Override
    public void fromBytes(ByteBuf buf) {
        open = buf.readBoolean();
        int ordinal = buf.readByte();
        state = ordinal >= 0 && ordinal < State.values().length ? State.values()[ordinal] : State.NO_PAD;
        stage = buf.readByte();
        ticksLeft = buf.readInt();
        fuelFraction = buf.readFloat();
        healthFraction = buf.readFloat();
        padX = buf.readInt();
        padY = buf.readInt();
        padZ = buf.readInt();
        strikeRange = buf.readFloat();
        steerReach = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeByte(state.ordinal());
        buf.writeByte(stage);
        buf.writeInt(ticksLeft);
        buf.writeFloat(fuelFraction);
        buf.writeFloat(healthFraction);
        buf.writeInt(padX);
        buf.writeInt(padY);
        buf.writeInt(padZ);
        buf.writeFloat(strikeRange);
        buf.writeFloat(steerReach);
    }

    public static class Handler implements IMessageHandler<TabletStatusPacket, IMessage> {
        @Override
        public IMessage onMessage(TabletStatusPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onTabletStatus(message));
            return null;
        }
    }
}
