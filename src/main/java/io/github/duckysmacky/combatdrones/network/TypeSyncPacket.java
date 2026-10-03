package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
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

/** Server to client on login: every drone type as JSON, keyed by ID. */
public class TypeSyncPacket implements IMessage {
    private final Map<String, String> typesJson = new LinkedHashMap<>();

    public TypeSyncPacket() {
    }

    public TypeSyncPacket(Collection<DroneType> types) {
        for (DroneType type : types) {
            typesJson.put(type.getId(), type.toJson());
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
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(typesJson.size());
        for (Map.Entry<String, String> entry : typesJson.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, entry.getKey());
            ByteBufUtils.writeUTF8String(buf, entry.getValue());
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
