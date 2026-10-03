package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server, once per tick under "Take control": stick, throttle and trigger for a ramp unit. Intent only. */
public class PlaneInputPacket implements IMessage {
    private int unitId;
    private double pitch;
    private double roll;
    private double throttle;
    private boolean trigger;

    public PlaneInputPacket() {
    }

    public PlaneInputPacket(int unitId, double pitch, double roll, double throttle, boolean trigger) {
        this.unitId = unitId;
        this.pitch = pitch;
        this.roll = roll;
        this.throttle = throttle;
        this.trigger = trigger;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        unitId = buf.readInt();
        // Never trust the wire: clamp every axis, NaN becomes neutral
        pitch = clamp(buf.readFloat(), -1);
        roll = clamp(buf.readFloat(), -1);
        throttle = clamp(buf.readFloat(), 0);
        trigger = buf.readBoolean();
    }

    private static double clamp(float value, double min) {
        return Float.isNaN(value) ? 0 : Math.max(min, Math.min(1, value));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(unitId);
        buf.writeFloat((float) pitch);
        buf.writeFloat((float) roll);
        buf.writeFloat((float) throttle);
        buf.writeBoolean(trigger);
    }

    public static class Handler implements IMessageHandler<PlaneInputPacket, IMessage> {
        @Override
        public IMessage onMessage(PlaneInputPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                Entity entity = player.world.getEntityByID(message.unitId);
                if (entity instanceof EntityRampUnit) {
                    // The unit itself rejects anyone who isn't its controller
                    ((EntityRampUnit) entity).acceptManualInput(player, message.pitch, message.roll, message.throttle, message.trigger);
                }
            });
            return null;
        }
    }
}
