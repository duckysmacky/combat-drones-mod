package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to pilot, every tick: authoritative flight state plus the last input sequence it reflects. Full precision
 * (unlike the vanilla tracker) because the pilot's prediction replays from it.
 */
public class DroneStatePacket implements IMessage {
    private int droneId;
    private int sequence;
    private final HandFlownFlight.State state = new HandFlownFlight.State();
    private float batteryFraction;
    private int payload;

    public DroneStatePacket() {
    }

    public DroneStatePacket(EntityHandFlownDrone drone, int sequence) {
        this.droneId = drone.getEntityId();
        this.sequence = sequence;
        HandFlownFlight.State source = drone.getFlightState();
        state.position.set(source.position);
        state.velocity.set(source.velocity);
        state.orientation.set(source.orientation);
        batteryFraction = (float) drone.getChargeFraction();
        payload = drone.getPayloadCount();
    }

    public float getBatteryFraction() {
        return batteryFraction;
    }

    public int getPayload() {
        return payload;
    }

    public int getDroneId() {
        return droneId;
    }

    public int getSequence() {
        return sequence;
    }

    public HandFlownFlight.State getState() {
        return state;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        droneId = buf.readInt();
        sequence = buf.readInt();
        state.position.set(buf.readDouble(), buf.readDouble(), buf.readDouble());
        state.velocity.set(buf.readDouble(), buf.readDouble(), buf.readDouble());
        state.orientation.set(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
        batteryFraction = buf.readFloat();
        payload = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(droneId);
        buf.writeInt(sequence);
        buf.writeDouble(state.position.x);
        buf.writeDouble(state.position.y);
        buf.writeDouble(state.position.z);
        buf.writeDouble(state.velocity.x);
        buf.writeDouble(state.velocity.y);
        buf.writeDouble(state.velocity.z);
        buf.writeDouble(state.orientation.x);
        buf.writeDouble(state.orientation.y);
        buf.writeDouble(state.orientation.z);
        buf.writeDouble(state.orientation.w);
        buf.writeFloat(batteryFraction);
        buf.writeByte(payload);
    }

    public static class Handler implements IMessageHandler<DroneStatePacket, IMessage> {
        @Override
        public IMessage onMessage(DroneStatePacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onDroneState(message));
            return null;
        }
    }
}
