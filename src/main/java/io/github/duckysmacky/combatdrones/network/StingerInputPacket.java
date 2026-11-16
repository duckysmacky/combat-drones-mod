package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.StingerServer;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the Stinger's trigger or reload key ({@code StingerServer.ACTION_*}). Aiming needs no packet (it
 * is vanilla item use) and neither does the aim (the server has the player's look); the server checks everything.
 */
public class StingerInputPacket implements IMessage {
    private int action;

    public StingerInputPacket() {
    }

    public StingerInputPacket(int action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
    }

    public static class Handler implements IMessageHandler<StingerInputPacket, IMessage> {
        @Override
        public IMessage onMessage(StingerInputPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> StingerServer.onInput(player, message.action));
            return null;
        }
    }
}
