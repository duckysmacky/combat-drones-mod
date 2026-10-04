package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.entity.Raytrace;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.physics.RouteValidator;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server side of the Mission Planner: what a player may see and change, all validated here. */
public final class PlannerServer {
    /** How close a player must stand to a ramp to plan or launch from it. */
    private static final double RAMP_REACH = 8;
    /** How long an armed "Mark target" waits for the right-click, in ticks. */
    private static final int MARK_TIMEOUT_TICKS = 30 * 20;

    /** An armed "Mark target": which ramp or unit it's for, the route draft to put the target on, and its deadline. */
    private static final class PendingMark {
        final int rampId;
        final int unitId;
        final Route draft;
        final long expiresAt;

        PendingMark(int rampId, int unitId, Route draft, long expiresAt) {
            this.rampId = rampId;
            this.unitId = unitId;
            this.draft = draft;
            this.expiresAt = expiresAt;
        }
    }

    private static final Map<UUID, PendingMark> PENDING_MARKS = new HashMap<>();

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
        view.ammo = ramp.getAmmo();
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
            view.ammo = unit.getAmmo();
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
        Route route = resolveTarget(player.world, packet.route);
        String problem = RouteValidator.validate(route, ramp.posX, ramp.posZ, ModConfig.routeLimits(), allowedTerminals(type));
        if (problem != null) {
            status(player, problem);
            return;
        }
        ramp.setRoute(route);
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
        Route route = resolveTarget(player.world, packet.route);
        String problem = RouteValidator.validate(route, unit.posX, unit.posZ, ModConfig.routeLimits(), allowedTerminals(type));
        if (problem != null) {
            status(player, problem);
            return;
        }
        unit.setRoute(route);
        status(player, "message.combat_drones.planner.route_applied");
        openAirborne(player);
    }

    /** A target picked on the map has no height yet: put it on the ground there. */
    private static Route resolveTarget(World world, Route route) {
        Route.Target target = route.getTarget();
        if (target == null || !Double.isNaN(target.y) || !Double.isFinite(target.x) || !Double.isFinite(target.z)) {
            return route;
        }
        int groundY = world.getHeight(new BlockPos(target.x, 0, target.z)).getY();
        return route.withTarget(new Route.Target(target.x, groundY, target.z));
    }

    /** "Mark target" pressed: remember what it's for (ownership checked now and again on marking). */
    public static void armMark(EntityPlayerMP player, PlannerMarkPacket packet) {
        if (packet.rampId >= 0) {
            Entity entity = player.world.getEntityByID(packet.rampId);
            if (!(entity instanceof EntityLaunchRamp) || !((EntityLaunchRamp) entity).isOwnedBy(player)) {
                status(player, "message.combat_drones.ramp.not_owner");
                return;
            }
        } else if (findOwnedUnit(player, packet.unitId) == null) {
            status(player, "message.combat_drones.planner.unit_gone");
            return;
        }
        PENDING_MARKS.put(player.getUniqueID(), new PendingMark(packet.rampId, packet.unitId, packet.draft,
                player.world.getTotalWorldTime() + MARK_TIMEOUT_TICKS));
        status(player, "message.combat_drones.planner.mark_armed");
    }

    /**
     * Right-click with the planner while a mark is armed: raytrace from the player's eyes (blocks and entities, up to
     * maxMarkDistance) and make the hit the Destroy target. An entity is marked where it stands right now; the strike
     * never follows it. Returns false if no mark was armed, so the click does its usual thing.
     */
    public static boolean tryMark(EntityPlayerMP player) {
        PendingMark mark = PENDING_MARKS.get(player.getUniqueID());
        if (mark == null) {
            return false;
        }
        if (player.world.getTotalWorldTime() > mark.expiresAt) {
            PENDING_MARKS.remove(player.getUniqueID());
            return false;
        }
        Vec3d eyes = player.getPositionEyes(1.0F);
        Vec3d end = eyes.add(player.getLook(1.0F).scale(ModConfig.maxMarkDistance));
        RayTraceResult hit = Raytrace.firstResult(player.world, eyes, end, player);
        if (hit == null) {
            status(player, "message.combat_drones.planner.mark_missed");
            return true;
        }
        PENDING_MARKS.remove(player.getUniqueID());
        Vec3d point = hit.entityHit != null ? new Vec3d(hit.entityHit.posX, hit.entityHit.posY, hit.entityHit.posZ) : hit.hitVec;
        Route draft = mark.draft;
        Route route = new Route(draft.getWaypoints(), Route.TerminalAction.DESTROY, draft.getLoiterRadius(), new Route.Target(point.x, point.y, point.z));
        player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.planner.target_marked", Math.round(point.distanceTo(eyes))), true);
        if (mark.rampId >= 0) {
            Entity entity = player.world.getEntityByID(mark.rampId);
            if (entity instanceof EntityLaunchRamp && ((EntityLaunchRamp) entity).isOwnedBy(player)) {
                // Saved unvalidated, like an unsent draft: Send and Launch validate it
                ((EntityLaunchRamp) entity).setRoute(route);
                openRamp(player, (EntityLaunchRamp) entity);
            }
            return true;
        }
        EntityRampUnit unit = findOwnedUnit(player, mark.unitId);
        DroneType type = unit == null ? null : unit.getDroneType();
        if (type != null) {
            // A unit in flight only ever gets a valid route
            String problem = RouteValidator.validate(route, unit.posX, unit.posZ, ModConfig.routeLimits(), allowedTerminals(type));
            if (problem != null) {
                status(player, problem);
            } else {
                unit.setRoute(route);
            }
        }
        openAirborne(player);
        return true;
    }

    private static EntityRampUnit findOwnedUnit(EntityPlayer player, int unitId) {
        Entity entity = player.world.getEntityByID(unitId);
        return entity instanceof EntityRampUnit && !entity.isDead && player.getUniqueID().equals(((EntityRampUnit) entity).getOwner())
                ? (EntityRampUnit) entity : null;
    }

    private static void status(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }
}
