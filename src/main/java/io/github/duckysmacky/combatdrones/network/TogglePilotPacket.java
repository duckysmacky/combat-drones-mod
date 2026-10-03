package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: enter or leave FPV with the drone linked to the held remote. No payload; the server decides. */
public class TogglePilotPacket implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<TogglePilotPacket, IMessage> {
        @Override
        public IMessage onMessage(TogglePilotPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> EntityHandFlownDrone.togglePilot(player));
            return null;
        }
    }
}
