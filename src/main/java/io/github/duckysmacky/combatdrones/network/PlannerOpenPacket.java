package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Server to client: what the Mission Planner should show. Either one Launch Ramp (plan + launch) or the player's
 * airborne units (live positions, in-flight route edits). Also carries the server's route limits for the UI.
 */
public class PlannerOpenPacket implements IMessage {
    /** One plannable unit: on a ramp ({@code entityId} = -1) or in the air. */
    public static final class UnitView {
        public int entityId = -1;
        public String typeId = "";
        public double x;
        public double y;
        public double z;
        public float heading;
        public float fuelFraction;
        public int waypointIndex;
        /** Altitudes in the route are relative to this. */
        public double originY;
        public Route route = Route.empty(Route.TerminalAction.LOITER);
        public Set<Route.TerminalAction> allowedTerminals = EnumSet.noneOf(Route.TerminalAction.class);

        void write(ByteBuf buf) {
            buf.writeInt(entityId);
            ByteBufUtils.writeUTF8String(buf, typeId);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(heading);
            buf.writeFloat(fuelFraction);
            buf.writeInt(waypointIndex);
            buf.writeDouble(originY);
            RouteCodec.write(buf, route);
            int mask = 0;
            for (Route.TerminalAction action : allowedTerminals) {
                mask |= 1 << action.ordinal();
            }
            buf.writeByte(mask);
        }

        static UnitView read(ByteBuf buf) {
            UnitView view = new UnitView();
            view.entityId = buf.readInt();
            view.typeId = ByteBufUtils.readUTF8String(buf);
            view.x = buf.readDouble();
            view.y = buf.readDouble();
            view.z = buf.readDouble();
            view.heading = buf.readFloat();
            view.fuelFraction = buf.readFloat();
            view.waypointIndex = buf.readInt();
            view.originY = buf.readDouble();
            view.route = RouteCodec.read(buf);
            int mask = buf.readUnsignedByte();
            for (Route.TerminalAction action : Route.TerminalAction.values()) {
                if ((mask & 1 << action.ordinal()) != 0) {
                    view.allowedTerminals.add(action);
                }
            }
            return view;
        }
    }

    /** Ramp position in ramp mode, or null in airborne mode. */
    public BlockPos rampPos;
    public final List<UnitView> units = new ArrayList<>();
    public int maxWaypoints = ModConfig.maxWaypointsPerRoute;
    public double maxAltitude = ModConfig.maxRouteAltitude;
    public double maxLength = ModConfig.maxRouteLength;
    public double minLaunchFuel = ModConfig.minLaunchFuel;

    public boolean isRampMode() {
        return rampPos != null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(rampPos != null);
        if (rampPos != null) {
            buf.writeLong(rampPos.toLong());
        }
        buf.writeInt(maxWaypoints);
        buf.writeDouble(maxAltitude);
        buf.writeDouble(maxLength);
        buf.writeDouble(minLaunchFuel);
        buf.writeShort(units.size());
        for (UnitView unit : units) {
            unit.write(buf);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        rampPos = buf.readBoolean() ? BlockPos.fromLong(buf.readLong()) : null;
        maxWaypoints = buf.readInt();
        maxAltitude = buf.readDouble();
        maxLength = buf.readDouble();
        minLaunchFuel = buf.readDouble();
        int count = buf.readUnsignedShort();
        for (int index = 0; index < count; index++) {
            units.add(UnitView.read(buf));
        }
    }

    public static class Handler implements IMessageHandler<PlannerOpenPacket, IMessage> {
        @Override
        public IMessage onMessage(PlannerOpenPacket message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> ClientPacketHandlers.onPlannerOpen(message));
            return null;
        }
    }
}
