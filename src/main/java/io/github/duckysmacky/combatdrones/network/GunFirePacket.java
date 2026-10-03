package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server to nearby clients: one gun-mount round, from the muzzle to where it hit (or ran out), for the tracer. */
public class GunFirePacket implements IMessage {
    private Vec3d from;
    private Vec3d to;

    public GunFirePacket() {
    }

    public GunFirePacket(Vec3d from, Vec3d to) {
        this.from = from;
        this.to = to;
    }

    public Vec3d getFrom() {
        return from;
    }

    public Vec3d getTo() {
        return to;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        from = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        to = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(from.x);
        buf.writeDouble(from.y);
        buf.writeDouble(from.z);
        buf.writeDouble(to.x);
        buf.writeDouble(to.y);
        buf.writeDouble(to.z);
    }

    public static class Handler implements IMessageHandler<GunFirePacket, IMessage> {
        @Override
        public IMessage onMessage(GunFirePacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onGunFire(message));
            return null;
        }
    }
}
