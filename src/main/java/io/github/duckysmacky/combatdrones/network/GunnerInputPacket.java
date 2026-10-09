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

import javax.annotation.Nullable;

/**
 * Client to server, every tick from the gunship's gunner seat: where the camera aims (and, while firing, the point the
 * crosshair is on), whether the trigger is held and which weapon station is selected. The server fires real projectiles
 * from the unit itself, so a client can only pick where to shoot, never where a round lands.
 */
public class GunnerInputPacket implements IMessage {
    private int unitId;
    private Vec3d aim;
    @Nullable
    private Vec3d target;
    private boolean trigger;
    private int station;

    public GunnerInputPacket() {
    }

    public GunnerInputPacket(int unitId, Vec3d aim, @Nullable Vec3d target, boolean trigger, int station) {
        this.unitId = unitId;
        this.aim = aim;
        this.target = target;
        this.trigger = trigger;
        this.station = station;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        unitId = buf.readInt();
        aim = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
        target = buf.readBoolean() ? new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
        trigger = buf.readBoolean();
        station = buf.readUnsignedByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(unitId);
        buf.writeFloat((float) aim.x);
        buf.writeFloat((float) aim.y);
        buf.writeFloat((float) aim.z);
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeDouble(target.x);
            buf.writeDouble(target.y);
            buf.writeDouble(target.z);
        }
        buf.writeBoolean(trigger);
        buf.writeByte(station);
    }

    public static class Handler implements IMessageHandler<GunnerInputPacket, IMessage> {
        @Override
        public IMessage onMessage(GunnerInputPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                Entity entity = player.world.getEntityByID(message.unitId);
                if (entity instanceof EntityRampUnit) {
                    ((EntityRampUnit) entity).acceptGunnerInput(player, message.aim, message.target, message.trigger, message.station);
                }
            });
            return null;
        }
    }
}
