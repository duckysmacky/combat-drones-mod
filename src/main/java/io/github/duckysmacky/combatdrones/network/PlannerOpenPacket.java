package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.type.Loadout;
import io.github.duckysmacky.combatdrones.type.AerialBombType;
import io.github.duckysmacky.combatdrones.type.RocketType;
import io.github.duckysmacky.combatdrones.client.ClientPacketHandlers;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.netty.buffer.ByteBuf;
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
 * Server to client: what the Mission Planner should show: the player's units on linked ramps and runways (plan +
 * launch) and in the air (live positions, in-flight route edits), and their linked runways (to land on). Also carries
 * the server's route limits for the UI.
 */
public class PlannerOpenPacket implements IMessage {
    /** One plannable unit: on a linked ramp ({@code rampId} set, {@code entityId} = -1) or in the air. */
    public static final class UnitView {
        public int entityId = -1;
        public int rampId = -1;
        public String typeId = "";
        public double x;
        public double y;
        public double z;
        public float heading;
        public float fuelFraction;
        public Loadout loadout = new Loadout();
        public int waypointIndex;
        /** Altitudes in the route are relative to this. */
        public double originY;
        public Route route = Route.empty(Route.TerminalAction.LOITER);
        public Set<Route.TerminalAction> allowedTerminals = EnumSet.noneOf(Route.TerminalAction.class);
        /** Where Return lands without a runway picked: the one it took off from (or is parked on). Null if none. */
        public Route.Runway homeRunway;
        /** Flying back to land ("Return to runway"), its route set aside. */
        public boolean recalling;

        void write(ByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeInt(rampId);
            ByteBufUtils.writeUTF8String(buf, typeId);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(heading);
            buf.writeFloat(fuelFraction);
            for (int station = 0; station < Loadout.STATIONS; station++) {
                buf.writeShort(loadout.getAmmo(station));
            }
            buf.writeByte(loadout.getRocketKind().ordinal());
            buf.writeByte(loadout.getBombKind().ordinal());
            buf.writeInt(waypointIndex);
            buf.writeDouble(originY);
            RouteCodec.write(buf, route);
            int mask = 0;
            for (Route.TerminalAction action : allowedTerminals) {
                mask |= 1 << action.ordinal();
            }
            buf.writeByte(mask);
            RouteCodec.writeRunway(buf, homeRunway);
            buf.writeBoolean(recalling);
        }

        public boolean isGrounded() {
            return rampId >= 0;
        }

        /** Same unit as {@code other} (refreshes keep the selection by this). */
        public boolean sameUnit(UnitView other) {
            return other != null && entityId == other.entityId && rampId == other.rampId;
        }

        static UnitView read(ByteBuf buf) {
            UnitView view = new UnitView();
            view.entityId = buf.readInt();
            view.rampId = buf.readInt();
            view.typeId = ByteBufUtils.readUTF8String(buf);
            view.x = buf.readDouble();
            view.y = buf.readDouble();
            view.z = buf.readDouble();
            view.heading = buf.readFloat();
            view.fuelFraction = buf.readFloat();
            for (int station = 0; station < Loadout.STATIONS; station++) {
                view.loadout.setAmmo(station, buf.readUnsignedShort());
            }
            view.loadout.setRocketKind(RocketType.byIndex(buf.readUnsignedByte()));
            view.loadout.setBombKind(AerialBombType.byIndex(buf.readUnsignedByte()));
            view.waypointIndex = buf.readInt();
            view.originY = buf.readDouble();
            view.route = RouteCodec.read(buf);
            int mask = buf.readUnsignedByte();
            for (Route.TerminalAction action : Route.TerminalAction.values()) {
                if ((mask & 1 << action.ordinal()) != 0) {
                    view.allowedTerminals.add(action);
                }
            }
            view.homeRunway = RouteCodec.readRunway(buf);
            view.recalling = buf.readBoolean();
            return view;
        }
    }

    /** One of the player's linked runways: a map target to land on. */
    public static final class RunwayView {
        public int entityId;
        public Route.Runway runway;
        /** {@code EntityLaunchRamp.Kind} ordinal: which planes it takes. */
        public int kind;
        /** No plane parked on it. */
        public boolean free;

        void write(ByteBuf buf) {
            buf.writeInt(entityId);
            RouteCodec.writeRunway(buf, runway);
            buf.writeByte(kind);
            buf.writeBoolean(free);
        }

        static RunwayView read(ByteBuf buf) {
            RunwayView view = new RunwayView();
            view.entityId = buf.readInt();
            view.runway = RouteCodec.readRunway(buf);
            view.kind = buf.readUnsignedByte();
            view.free = buf.readBoolean();
            return view;
        }
    }

    /** Entity ID (a unit in flight, or the ramp of a grounded one) the screen should select, or -1 to keep its selection. */
    public int selectId = -1;
    public final List<UnitView> units = new ArrayList<>();
    public final List<RunwayView> runways = new ArrayList<>();
    public int maxWaypoints = ModConfig.maxWaypointsPerRoute;
    public double minAltitude = ModConfig.minRouteAltitude;
    public double maxAltitude = ModConfig.maxRouteAltitude;
    public double minLoiterRadius = ModConfig.minLoiterRadius;
    public double maxLoiterRadius = ModConfig.maxLoiterRadius;
    public double maxLength = ModConfig.maxRouteLength;
    public double minLaunchFuel = ModConfig.minLaunchFuel;
    public double maxCarpetLength = ModConfig.maxCarpetLength;

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(selectId);
        buf.writeInt(maxWaypoints);
        buf.writeDouble(minAltitude);
        buf.writeDouble(maxAltitude);
        buf.writeDouble(minLoiterRadius);
        buf.writeDouble(maxLoiterRadius);
        buf.writeDouble(maxLength);
        buf.writeDouble(minLaunchFuel);
        buf.writeDouble(maxCarpetLength);
        buf.writeShort(units.size());
        for (UnitView unit : units) {
            unit.write(buf);
        }
        buf.writeShort(runways.size());
        for (RunwayView runway : runways) {
            runway.write(buf);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        selectId = buf.readInt();
        maxWaypoints = buf.readInt();
        minAltitude = buf.readDouble();
        maxAltitude = buf.readDouble();
        minLoiterRadius = buf.readDouble();
        maxLoiterRadius = buf.readDouble();
        maxLength = buf.readDouble();
        minLaunchFuel = buf.readDouble();
        maxCarpetLength = buf.readDouble();
        int count = buf.readUnsignedShort();
        for (int index = 0; index < count; index++) {
            units.add(UnitView.read(buf));
        }
        int runwayCount = buf.readUnsignedShort();
        for (int index = 0; index < runwayCount; index++) {
            RunwayView runway = RunwayView.read(buf);
            if (runway.runway != null) {
                runways.add(runway);
            }
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
