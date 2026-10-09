package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.physics.Route;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: a route for a ramp's unit (optionally launching it, or launching and flying it) or for an airborne unit. Validated server-side. */
public class PlannerSubmitPacket implements IMessage {
    int rampId = -1;
    int unitId = -1;
    Route route;
    boolean launch;
    /** With launch: take manual control the moment it leaves the ramp. */
    boolean fly;

    public PlannerSubmitPacket() {
    }

    public static PlannerSubmitPacket forRamp(int rampId, Route route, boolean launch, boolean fly) {
        PlannerSubmitPacket packet = new PlannerSubmitPacket();
        packet.rampId = rampId;
        packet.route = route;
        packet.launch = launch;
        packet.fly = fly;
        return packet;
    }

    public static PlannerSubmitPacket forUnit(int unitId, Route route) {
        PlannerSubmitPacket packet = new PlannerSubmitPacket();
        packet.unitId = unitId;
        packet.route = route;
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(rampId);
        buf.writeInt(unitId);
        buf.writeBoolean(launch);
        buf.writeBoolean(fly);
        RouteCodec.write(buf, route);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        rampId = buf.readInt();
        unitId = buf.readInt();
        launch = buf.readBoolean();
        fly = buf.readBoolean();
        route = RouteCodec.read(buf);
    }

    public static class Handler implements IMessageHandler<PlannerSubmitPacket, IMessage> {
        @Override
        public IMessage onMessage(PlannerSubmitPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> PlannerServer.submit(player, message));
            return null;
        }
    }
}
