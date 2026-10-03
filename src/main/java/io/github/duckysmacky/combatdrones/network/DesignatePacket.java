package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: strike what the unit's camera is pointing at (click-designate scheme). Carries only the look
 * direction; the server raytraces from the unit itself, so a client can't place a target anywhere.
 */
public class DesignatePacket implements IMessage {
    private int unitId;
    private Vec3d direction;

    public DesignatePacket() {
    }

    public DesignatePacket(int unitId, Vec3d direction) {
        this.unitId = unitId;
        this.direction = direction;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        unitId = buf.readInt();
        direction = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(unitId);
        buf.writeFloat((float) direction.x);
        buf.writeFloat((float) direction.y);
        buf.writeFloat((float) direction.z);
    }

    public static class Handler implements IMessageHandler<DesignatePacket, IMessage> {
        @Override
        public IMessage onMessage(DesignatePacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                Entity entity = player.world.getEntityByID(message.unitId);
                if (entity instanceof EntityRampUnit) {
                    ((EntityRampUnit) entity).designate(player, message.direction);
                }
            });
            return null;
        }
    }
}
