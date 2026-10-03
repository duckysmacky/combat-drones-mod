package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.physics.RouteValidator;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Set;

/** Server side of the Mission Planner: what a player may see and change, all validated here. */
public final class PlannerServer {
    /** How close a player must stand to a ramp to plan or launch from it. */
    private static final double RAMP_REACH = 8;

    private PlannerServer() {
    }

    public static Set<Route.TerminalAction> allowedTerminals(DroneType type) {
        return RouteValidator.allowedTerminals(type.isArmed(), type.getWeaponType() != DroneType.WeaponType.NONE);
    }

    /** Opens the planner on a ramp (owner only). */
    public static void openRamp(EntityPlayer player, EntityLaunchRamp ramp) {
        DroneType type = ramp.getUnitType();
        if (type == null) {
            status(player, "message.combat_drones.ramp.empty");
            return;
        }
        if (!ramp.isOwnedBy(player)) {
            status(player, "message.combat_drones.ramp.not_owner");
            return;
        }
        PlannerOpenPacket packet = new PlannerOpenPacket();
        packet.rampId = ramp.getEntityId();
        PlannerOpenPacket.UnitView view = new PlannerOpenPacket.UnitView();
        view.typeId = type.getId();
        view.x = ramp.posX;
        view.y = ramp.posY + 1;
        view.z = ramp.posZ;
        view.heading = ramp.rotationYaw;
        view.fuelFraction = ramp.getFuelFraction();
        view.originY = view.y;
        view.route = ramp.getRoute();
        view.allowedTerminals = allowedTerminals(type);
        packet.units.add(view);
        ModNetwork.CHANNEL.sendTo(packet, (EntityPlayerMP) player);
    }

    /** Opens (or refreshes) the airborne view: every unit this player owns that is flying in their world. */
    public static void openAirborne(EntityPlayerMP player) {
        PlannerOpenPacket packet = new PlannerOpenPacket();
        for (Entity entity : player.world.loadedEntityList) {
            if (!(entity instanceof EntityRampUnit) || entity.isDead || !player.getUniqueID().equals(((EntityRampUnit) entity).getOwner())) {
                continue;
            }
            EntityRampUnit unit = (EntityRampUnit) entity;
            DroneType type = unit.getDroneType();
            if (type == null) {
                continue;
            }
            PlannerOpenPacket.UnitView view = new PlannerOpenPacket.UnitView();
            view.entityId = unit.getEntityId();
            view.typeId = type.getId();
            view.x = unit.posX;
            view.y = unit.posY;
            view.z = unit.posZ;
            view.heading = unit.rotationYaw;
            view.fuelFraction = unit.getFuelFraction();
            view.waypointIndex = unit.getWaypointIndex();
            view.originY = unit.getLaunchY();
            view.route = unit.getRoute();
            view.allowedTerminals = allowedTerminals(type);
            packet.units.add(view);
        }
        ModNetwork.CHANNEL.sendTo(packet, player);
    }

    public static void submit(EntityPlayerMP player, PlannerSubmitPacket packet) {
        if (packet.rampId >= 0) {
            submitToRamp(player, packet);
        } else {
            submitToUnit(player, packet);
        }
    }

    private static void submitToRamp(EntityPlayerMP player, PlannerSubmitPacket packet) {
        Entity entity = player.world.getEntityByID(packet.rampId);
        if (!(entity instanceof EntityLaunchRamp) || entity.isDead) {
            return;
        }
        EntityLaunchRamp ramp = (EntityLaunchRamp) entity;
        if (player.getDistanceSq(ramp) > RAMP_REACH * RAMP_REACH) {
            status(player, "message.combat_drones.planner.too_far");
            return;
        }
        DroneType type = ramp.getUnitType();
        if (type == null || !ramp.isOwnedBy(player)) {
            status(player, "message.combat_drones.ramp.not_owner");
            return;
        }
        String problem = RouteValidator.validate(packet.route, ramp.posX, ramp.posZ, ModConfig.routeLimits(), allowedTerminals(type));
        if (problem != null) {
            status(player, problem);
            return;
        }
        ramp.setRoute(packet.route);
        if (!packet.launch) {
            status(player, "message.combat_drones.planner.route_saved");
            openRamp(player, ramp);
            return;
        }
        if (ramp.getFuelFraction() < ModConfig.minLaunchFuel) {
            status(player, "message.combat_drones.planner.low_fuel");
            openRamp(player, ramp);
            return;
        }
        ramp.launch();
        status(player, "message.combat_drones.planner.launched");
    }

    private static void submitToUnit(EntityPlayerMP player, PlannerSubmitPacket packet) {
        Entity entity = player.world.getEntityByID(packet.unitId);
        if (!(entity instanceof EntityRampUnit) || entity.isDead || !player.getUniqueID().equals(((EntityRampUnit) entity).getOwner())) {
            status(player, "message.combat_drones.planner.unit_gone");
            return;
        }
        EntityRampUnit unit = (EntityRampUnit) entity;
        DroneType type = unit.getDroneType();
        if (type == null) {
            return;
        }
        String problem = RouteValidator.validate(packet.route, unit.posX, unit.posZ, ModConfig.routeLimits(), allowedTerminals(type));
        if (problem != null) {
            status(player, problem);
            return;
        }
        unit.setRoute(packet.route);
        status(player, "message.combat_drones.planner.route_applied");
        openAirborne(player);
    }

    private static void status(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }
}
