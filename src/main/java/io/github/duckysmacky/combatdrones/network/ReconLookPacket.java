package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.RemoteSessions;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server, every couple of ticks while watching a sensor unit's camera: where the camera points. The server
 * decides what (if anything) is under the crosshair and runs the lock; the client never names a target.
 */
public class ReconLookPacket implements IMessage {
    private int unitId;
    private Vec3d direction;

    public ReconLookPacket() {
    }

    public ReconLookPacket(int unitId, Vec3d direction) {
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

    public static class Handler implements IMessageHandler<ReconLookPacket, IMessage> {
        @Override
        public IMessage onMessage(ReconLookPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(
                    () -> RemoteSessions.onReconLook(player, message.unitId, message.direction));
            return null;
        }
    }
}
