package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.physics.Route;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
            if (waypoint.isDrop()) {
                point.setDouble("Drop", waypoint.dropLength);
            }
            points.appendTag(point);
        }
        tag.setTag("Waypoints", points);
        tag.setString("Terminal", route.getTerminal().name());
        tag.setDouble("LoiterRadius", route.getLoiterRadius());
        Route.Target target = route.getTarget();
        if (target != null) {
            NBTTagCompound targetTag = new NBTTagCompound();
            targetTag.setDouble("X", target.x);
            targetTag.setDouble("Y", target.y);
            targetTag.setDouble("Z", target.z);
            tag.setTag("Target", targetTag);
        }
        Route.Runway runway = route.getRunway();
        if (runway != null) {
            NBTTagCompound runwayTag = new NBTTagCompound();
            runwayTag.setUniqueId("Id", runway.id);
            runwayTag.setDouble("X", runway.x);
            runwayTag.setDouble("Y", runway.y);
            runwayTag.setDouble("Z", runway.z);
            runwayTag.setDouble("Heading", runway.heading);
            runwayTag.setDouble("Length", runway.length);
            runwayTag.setDouble("Width", runway.width);
            tag.setTag("Runway", runwayTag);
        }
        return tag;
    }

    public static Route fromNbt(NBTTagCompound tag, Route.TerminalAction fallbackTerminal) {
        List<Route.Waypoint> waypoints = new ArrayList<>();
        NBTTagList points = tag.getTagList("Waypoints", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < Math.min(points.tagCount(), MAX_READ_WAYPOINTS); index++) {
            NBTTagCompound point = points.getCompoundTagAt(index);
            waypoints.add(new Route.Waypoint(point.getDouble("X"), point.getDouble("Z"), point.getDouble("Alt"), speedOf(point.getString("Speed")),
                    point.hasKey("Drop") ? point.getDouble("Drop") : Route.NO_DROP));
        }
        Route.TerminalAction terminal = fallbackTerminal;
        for (Route.TerminalAction action : Route.TerminalAction.values()) {
            if (action.name().equals(tag.getString("Terminal"))) {
                terminal = action;
            }
        }
        double radius = tag.hasKey("LoiterRadius") ? tag.getDouble("LoiterRadius") : Route.DEFAULT_LOITER_RADIUS;
        Route.Target target = null;
        if (tag.hasKey("Target", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound targetTag = tag.getCompoundTag("Target");
            target = new Route.Target(targetTag.getDouble("X"), targetTag.getDouble("Y"), targetTag.getDouble("Z"));
        }
        return new Route(waypoints, terminal, radius, target, runwayFromNbt(tag.getCompoundTag("Runway")));
    }

    @Nullable
    public static Route.Runway runwayFromNbt(NBTTagCompound tag) {
        if (!tag.hasUniqueId("Id")) {
            return null;
        }
        return new Route.Runway(tag.getUniqueId("Id"), tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                tag.getDouble("Heading"), tag.getDouble("Length"), tag.getDouble("Width"));
    }

    public static NBTTagCompound runwayToNbt(@Nullable Route.Runway runway) {
        return toNbt(Route.empty(Route.TerminalAction.RETURN).withRunway(runway)).getCompoundTag("Runway");
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
            buf.writeFloat((float) waypoint.dropLength);
        }
        Route.Target target = route.getTarget();
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeDouble(target.x);
            buf.writeDouble(target.y);
            buf.writeDouble(target.z);
        }
        writeRunway(buf, route.getRunway());
    }

    public static void writeRunway(ByteBuf buf, @Nullable Route.Runway runway) {
        buf.writeBoolean(runway != null);
        if (runway != null) {
            buf.writeLong(runway.id.getMostSignificantBits());
            buf.writeLong(runway.id.getLeastSignificantBits());
            buf.writeDouble(runway.x);
            buf.writeDouble(runway.y);
            buf.writeDouble(runway.z);
            buf.writeDouble(runway.heading);
            buf.writeDouble(runway.length);
            buf.writeDouble(runway.width);
        }
    }

    @Nullable
    public static Route.Runway readRunway(ByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        UUID id = new UUID(buf.readLong(), buf.readLong());
        return new Route.Runway(id, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
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
            waypoints.add(new Route.Waypoint(x, z, altitude, speedIndex < speeds.length ? speeds[speedIndex] : Route.Speed.FAST, buf.readFloat()));
        }
        Route.Target target = buf.readBoolean() ? new Route.Target(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
        return new Route(waypoints, terminal, loiterRadius, target, readRunway(buf));
    }
}
