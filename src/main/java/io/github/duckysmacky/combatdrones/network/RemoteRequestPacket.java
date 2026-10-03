package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.entity.RemoteSessions;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: view a unit in flight, or take manual control of it (Mission Planner buttons). */
public class RemoteRequestPacket implements IMessage {
    private int unitId;
    private boolean control;

    public RemoteRequestPacket() {
    }

    public RemoteRequestPacket(int unitId, boolean control) {
        this.unitId = unitId;
        this.control = control;
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

    public static class Handler implements IMessageHandler<RemoteRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(RemoteRequestPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                Entity entity = player.world.getEntityByID(message.unitId);
                String problem = entity instanceof EntityRampUnit
                        ? RemoteSessions.start(player, (EntityRampUnit) entity, message.control)
                        : "message.combat_drones.planner.unit_gone";
                if (problem != null) {
                    player.sendStatusMessage(new TextComponentTranslation(problem), true);
                }
            });
            return null;
        }
    }
}
