package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.BombType;
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

/**
 * Server to client on login and config reload: every drone type as JSON keyed by ID, battery pack and bomb values, and
 * the server settings client-side prediction depends on.
 */
public class TypeSyncPacket implements IMessage {
    private final Map<String, String> typesJson = new LinkedHashMap<>();
    private final double[] packCapacity = new double[BatteryPack.values().length];
    private final double[] packPerformance = new double[BatteryPack.values().length];
    private final int[] bombBayUnits = new int[BombType.values().length];
    private final double[] bombBlast = new double[BombType.values().length];
    private final double[] bombWeight = new double[BombType.values().length];
    private boolean worldBorderCollision;
    private double steerConeDegrees;

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
        for (BombType bomb : BombType.values()) {
            bombBayUnits[bomb.ordinal()] = bomb.getBayUnits();
            bombBlast[bomb.ordinal()] = bomb.getBlastPower();
            bombWeight[bomb.ordinal()] = bomb.getWeight();
        }
        worldBorderCollision = ModConfig.worldBorderCollision;
        steerConeDegrees = ModConfig.steerConeDegrees;
    }

    /** Client side: adopt the server's battery pack and bomb values and synced settings. */
    public void applyEquipment() {
        for (BatteryPack pack : BatteryPack.values()) {
            pack.setMultipliers(packCapacity[pack.ordinal()], packPerformance[pack.ordinal()]);
        }
        for (BombType bomb : BombType.values()) {
            bomb.set(bombBayUnits[bomb.ordinal()], bombBlast[bomb.ordinal()], bombWeight[bomb.ordinal()]);
        }
        ModConfig.worldBorderCollision = worldBorderCollision;
        ModConfig.steerConeDegrees = steerConeDegrees;
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
        for (int index = 0; index < bombBayUnits.length; index++) {
            bombBayUnits[index] = Math.max(1, Math.min(16, buf.readInt()));
            bombBlast[index] = Math.max(0, Math.min(20, buf.readDouble()));
            bombWeight[index] = Math.max(0, Math.min(0.5, buf.readDouble()));
        }
        worldBorderCollision = buf.readBoolean();
        steerConeDegrees = Math.max(0, Math.min(60, buf.readDouble()));
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
        for (int index = 0; index < bombBayUnits.length; index++) {
            buf.writeInt(bombBayUnits[index]);
            buf.writeDouble(bombBlast[index]);
            buf.writeDouble(bombWeight[index]);
        }
        buf.writeBoolean(worldBorderCollision);
        buf.writeDouble(steerConeDegrees);
    }

    public static class Handler implements IMessageHandler<TypeSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(TypeSyncPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onTypeSync(message));
            return null;
        }
    }
}
