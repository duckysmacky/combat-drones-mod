package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.physics.Route;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/** Route (de)serialization for NBT (ramps, flying units) and packets (Mission Planner). */
public final class RouteCodec {
    /** Hard cap when reading untrusted data, independent of the config limit the validator checks. */
    private static final int MAX_READ_WAYPOINTS = 64;

    private RouteCodec() {
    }

    public static NBTTagCompound toNbt(Route route) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList points = new NBTTagList();
        for (Route.Waypoint waypoint : route.getWaypoints()) {
            NBTTagCompound point = new NBTTagCompound();
            point.setDouble("X", waypoint.x);
            point.setDouble("Z", waypoint.z);
            point.setDouble("Alt", waypoint.altitude);
            point.setString("Speed", waypoint.speed.name());
            points.appendTag(point);
        }
        tag.setTag("Waypoints", points);
        tag.setString("Terminal", route.getTerminal().name());
        tag.setDouble("LoiterRadius", route.getLoiterRadius());
        return tag;
    }

    public static Route fromNbt(NBTTagCompound tag, Route.TerminalAction fallbackTerminal) {
        List<Route.Waypoint> waypoints = new ArrayList<>();
        NBTTagList points = tag.getTagList("Waypoints", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < Math.min(points.tagCount(), MAX_READ_WAYPOINTS); index++) {
            NBTTagCompound point = points.getCompoundTagAt(index);
            waypoints.add(new Route.Waypoint(point.getDouble("X"), point.getDouble("Z"), point.getDouble("Alt"), speedOf(point.getString("Speed"))));
        }
        Route.TerminalAction terminal = fallbackTerminal;
        for (Route.TerminalAction action : Route.TerminalAction.values()) {
            if (action.name().equals(tag.getString("Terminal"))) {
                terminal = action;
            }
        }
        double radius = tag.hasKey("LoiterRadius") ? tag.getDouble("LoiterRadius") : Route.DEFAULT_LOITER_RADIUS;
        return new Route(waypoints, terminal, radius);
    }

    private static Route.Speed speedOf(String name) {
        for (Route.Speed speed : Route.Speed.values()) {
            if (speed.name().equals(name)) {
                return speed;
            }
        }
        return Route.Speed.FAST; // routes saved before speeds existed flew at today's FAST
    }

    public static void write(ByteBuf buf, Route route) {
        buf.writeByte(route.getTerminal().ordinal());
        buf.writeDouble(route.getLoiterRadius());
        buf.writeByte(route.size());
        for (Route.Waypoint waypoint : route.getWaypoints()) {
            buf.writeDouble(waypoint.x);
            buf.writeDouble(waypoint.z);
            buf.writeDouble(waypoint.altitude);
            buf.writeByte(waypoint.speed.ordinal());
        }
    }

    public static Route read(ByteBuf buf) {
        Route.TerminalAction[] actions = Route.TerminalAction.values();
        int terminalIndex = buf.readUnsignedByte();
        Route.TerminalAction terminal = terminalIndex < actions.length ? actions[terminalIndex] : Route.TerminalAction.LOITER;
        double loiterRadius = buf.readDouble();
        int count = Math.min(buf.readUnsignedByte(), MAX_READ_WAYPOINTS);
        List<Route.Waypoint> waypoints = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double x = buf.readDouble();
            double z = buf.readDouble();
            double altitude = buf.readDouble();
            int speedIndex = buf.readUnsignedByte();
            Route.Speed[] speeds = Route.Speed.values();
            waypoints.add(new Route.Waypoint(x, z, altitude, speedIndex < speeds.length ? speeds[speedIndex] : Route.Speed.FAST));
        }
        return new Route(waypoints, terminal, loiterRadius);
    }
}
