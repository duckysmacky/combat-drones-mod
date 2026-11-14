package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.RemoteSessions;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server, every couple of ticks while watching a sensor unit's camera: where the camera points, how much of
 * the world fits on screen (half-angles, for on-screen tracking) and whether LMB is held. The server decides what (if
 * anything) is under the crosshair and runs the lock-on; the client never names a target.
 */
public class ReconLookPacket implements IMessage {
    private int unitId;
    private float yaw;
    private float pitch;
    private float halfWidth;
    private float halfHeight;
    private boolean trigger;

    public ReconLookPacket() {
    }

    public ReconLookPacket(int unitId, float yaw, float pitch, float halfWidth, float halfHeight, boolean trigger) {
        this.unitId = unitId;
        this.yaw = yaw;
        this.pitch = pitch;
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.trigger = trigger;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        unitId = buf.readInt();
        yaw = buf.readFloat();
        pitch = buf.readFloat();
        halfWidth = buf.readFloat();
        halfHeight = buf.readFloat();
        trigger = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(unitId);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeFloat(halfWidth);
        buf.writeFloat(halfHeight);
        buf.writeBoolean(trigger);
    }

    public static class Handler implements IMessageHandler<ReconLookPacket, IMessage> {
        @Override
        public IMessage onMessage(ReconLookPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(
                    () -> RemoteSessions.onReconLook(player, message.unitId, message.yaw, message.pitch, message.halfWidth,
                            message.halfHeight, message.trigger));
            return null;
        }
    }
}
