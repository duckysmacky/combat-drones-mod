package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: the pilot pulled the trigger (LMB). What it does is up to the drone; the server validates. */
public class TriggerPacket implements IMessage {
    private int droneId;

    public TriggerPacket() {
    }

    public TriggerPacket(int droneId) {
        this.droneId = droneId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        droneId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(droneId);
    }

    public static class Handler implements IMessageHandler<TriggerPacket, IMessage> {
        @Override
        public IMessage onMessage(TriggerPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                Entity entity = player.world.getEntityByID(message.droneId);
                if (entity instanceof EntityHandFlownDrone) {
                    ((EntityHandFlownDrone) entity).onTrigger(player);
                }
            });
            return null;
        }
    }
}
