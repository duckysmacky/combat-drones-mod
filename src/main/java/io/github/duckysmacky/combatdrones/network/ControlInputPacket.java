package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server, once per tick while flying: stick positions and switches. Pure intent, never state. */
public class ControlInputPacket implements IMessage {
    private int droneId;
    private int sequence;
    private HandFlownFlight.Input input;

    public ControlInputPacket() {
    }

    public ControlInputPacket(int droneId, int sequence, HandFlownFlight.Input input) {
        this.droneId = droneId;
        this.sequence = sequence;
        this.input = input;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        droneId = buf.readInt();
        sequence = buf.readInt();
        float throttle = buf.readFloat();
        float vertical = buf.readFloat();
        float pitch = buf.readFloat();
        float roll = buf.readFloat();
        float yaw = buf.readFloat();
        int modeIndex = buf.readByte();
        boolean motorsOn = buf.readBoolean();
        // Never trust the wire: clamp every axis and fall back on unknown modes
        HandFlownFlight.Mode[] modes = HandFlownFlight.Mode.values();
        HandFlownFlight.Mode mode = modeIndex >= 0 && modeIndex < modes.length ? modes[modeIndex] : HandFlownFlight.Mode.ANGLE;
        input = new HandFlownFlight.Input(clamp(throttle, 0), clamp(vertical, -1), clamp(pitch, -1), clamp(roll, -1), clamp(yaw, -1), mode, motorsOn);
    }

    private static double clamp(float value, double min) {
        if (Float.isNaN(value)) {
            return 0;
        }
        return Math.max(min, Math.min(1, value));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(droneId);
        buf.writeInt(sequence);
        buf.writeFloat((float) input.throttle);
        buf.writeFloat((float) input.vertical);
        buf.writeFloat((float) input.pitch);
        buf.writeFloat((float) input.roll);
        buf.writeFloat((float) input.yaw);
        buf.writeByte(input.mode.ordinal());
        buf.writeBoolean(input.motorsOn);
    }

    public static class Handler implements IMessageHandler<ControlInputPacket, IMessage> {
        @Override
        public IMessage onMessage(ControlInputPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                Entity entity = player.world.getEntityByID(message.droneId);
                if (entity instanceof EntityHandFlownDrone) {
                    // The drone itself rejects senders that aren't its pilot
                    ((EntityHandFlownDrone) entity).queueInput(player, message.sequence, message.input);
                }
            });
            return null;
        }
    }
}
