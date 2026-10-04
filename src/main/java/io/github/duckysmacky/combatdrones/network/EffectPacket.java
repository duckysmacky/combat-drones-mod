package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to nearby clients: a one-off visual + sound effect the client builds itself, so it can scale particles and
 * pick near or distant sounds (with a speed-of-sound delay) per listener, which vanilla sound packets can't.
 */
public class EffectPacket implements IMessage {
    public enum Kind {
        /** Scale = blast power. Heard out to {@link #EXPLOSION_RANGE}. */
        EXPLOSION,
        /** A unit leaving the catapult; direction = launch heading. */
        LAUNCH
    }

    public static final double EXPLOSION_RANGE = 400;
    private static final double LAUNCH_RANGE = 96;

    private Kind kind;
    private Vec3d position;
    private Vec3d direction;
    private float scale;

    public EffectPacket() {
    }

    private EffectPacket(Kind kind, Vec3d position, Vec3d direction, float scale) {
        this.kind = kind;
        this.position = position;
        this.direction = direction;
        this.scale = scale;
    }

    public static void explosion(World world, double x, double y, double z, float power) {
        send(world, new EffectPacket(Kind.EXPLOSION, new Vec3d(x, y, z), Vec3d.ZERO, power), EXPLOSION_RANGE);
    }

    public static void launch(World world, Vec3d position, Vec3d direction) {
        send(world, new EffectPacket(Kind.LAUNCH, position, direction, 1), LAUNCH_RANGE);
    }

    private static void send(World world, EffectPacket packet, double range) {
        ModNetwork.CHANNEL.sendToAllAround(packet, new NetworkRegistry.TargetPoint(world.provider.getDimension(),
                packet.position.x, packet.position.y, packet.position.z, range));
    }

    public Kind getKind() {
        return kind;
    }

    public Vec3d getPosition() {
        return position;
    }

    public Vec3d getDirection() {
        return direction;
    }

    public float getScale() {
        return scale;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        kind = ordinal >= 0 && ordinal < Kind.values().length ? Kind.values()[ordinal] : Kind.EXPLOSION;
        position = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        direction = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
        scale = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind.ordinal());
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
        buf.writeFloat((float) direction.x);
        buf.writeFloat((float) direction.y);
        buf.writeFloat((float) direction.z);
        buf.writeFloat(scale);
    }

    public static class Handler implements IMessageHandler<EffectPacket, IMessage> {
        @Override
        public IMessage onMessage(EffectPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onEffect(message));
            return null;
        }
    }
}
