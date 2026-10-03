package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server to client on login: every drone type as JSON keyed by ID, plus battery pack multipliers. */
public class TypeSyncPacket implements IMessage {
    private final Map<String, String> typesJson = new LinkedHashMap<>();
    private final double[] packCapacity = new double[BatteryPack.values().length];
    private final double[] packPerformance = new double[BatteryPack.values().length];

    public TypeSyncPacket() {
    }

    public TypeSyncPacket(Collection<DroneType> types) {
        for (DroneType type : types) {
            typesJson.put(type.getId(), type.toJson());
        }
        for (BatteryPack pack : BatteryPack.values()) {
            packCapacity[pack.ordinal()] = pack.getCapacityMultiplier();
            packPerformance[pack.ordinal()] = pack.getPerformanceMultiplier();
        }
    }

    /** Client side: adopt the server's battery pack multipliers. */
    public void applyBatteryPacks() {
        for (BatteryPack pack : BatteryPack.values()) {
            pack.setMultipliers(packCapacity[pack.ordinal()], packPerformance[pack.ordinal()]);
        }
    }

    public Map<String, String> getTypesJson() {
        return typesJson;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        for (int index = 0; index < count; index++) {
            typesJson.put(ByteBufUtils.readUTF8String(buf), ByteBufUtils.readUTF8String(buf));
        }
        for (int index = 0; index < packCapacity.length; index++) {
            // Clamp like the config does, so a hostile server can't send absurd values
            packCapacity[index] = Math.max(0.1, Math.min(10, buf.readDouble()));
            packPerformance[index] = Math.max(0.1, Math.min(2, buf.readDouble()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(typesJson.size());
        for (Map.Entry<String, String> entry : typesJson.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, entry.getKey());
            ByteBufUtils.writeUTF8String(buf, entry.getValue());
        }
        for (int index = 0; index < packCapacity.length; index++) {
            buf.writeDouble(packCapacity[index]);
            buf.writeDouble(packPerformance[index]);
        }
    }

    public static class Handler implements IMessageHandler<TypeSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(TypeSyncPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onTypeSync(message));
            return null;
        }
    }
}
