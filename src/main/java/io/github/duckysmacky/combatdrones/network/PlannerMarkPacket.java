package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.physics.Route;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: "Mark target" in the Mission Planner. Arms a mark for a ramp (or a unit in flight) along with the
 * player's unsent route draft; the next right-click with the planner picks the Destroy target in the world.
 */
public class PlannerMarkPacket implements IMessage {
    int rampId = -1;
    int unitId = -1;
    Route draft;

    public PlannerMarkPacket() {
    }

    public PlannerMarkPacket(int rampId, int unitId, Route draft) {
        this.rampId = rampId;
        this.unitId = unitId;
        this.draft = draft;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        rampId = buf.readInt();
        unitId = buf.readInt();
        draft = RouteCodec.read(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(rampId);
        buf.writeInt(unitId);
        RouteCodec.write(buf, draft);
    }

    public static class Handler implements IMessageHandler<PlannerMarkPacket, IMessage> {
        @Override
        public IMessage onMessage(PlannerMarkPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> PlannerServer.armMark(player, message));
            return null;
        }
    }
}
