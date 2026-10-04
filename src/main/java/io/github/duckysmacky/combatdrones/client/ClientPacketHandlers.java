package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.network.DroneStatePacket;
import io.github.duckysmacky.combatdrones.network.GunFirePacket;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import io.github.duckysmacky.combatdrones.network.LockProgressPacket;
import io.github.duckysmacky.combatdrones.network.PlannerOpenPacket;
import io.github.duckysmacky.combatdrones.network.RemoteSessionPacket;
import io.github.duckysmacky.combatdrones.network.TypeSyncPacket;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Client-side bodies of client-bound packet handlers; only ever invoked on the client thread. */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void onTypeSync(TypeSyncPacket packet) {
        DroneTypes.applyServerTypes(packet.getTypesJson());
        packet.applyEquipment();
    }

    public static void onPlannerOpen(PlannerOpenPacket packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen instanceof GuiMissionPlanner && ((GuiMissionPlanner) minecraft.currentScreen).accepts(packet)) {
            ((GuiMissionPlanner) minecraft.currentScreen).update(packet);
        } else {
            minecraft.displayGuiScreen(new GuiMissionPlanner(packet));
        }
    }

    public static void onRemoteSession(RemoteSessionPacket packet) {
        RemoteController.onSession(packet.getUnitId(), packet.isControl());
    }

    /** Gun tracer: sparks strung along the round's path and a puff where it ended. */
    public static void onGunFire(GunFirePacket packet) {
        World world = Minecraft.getMinecraft().world;
        if (world == null) {
            return;
        }
        Vec3d from = packet.getFrom();
        Vec3d to = packet.getTo();
        double length = from.distanceTo(to);
        Vec3d step = to.subtract(from).normalize().scale(2);
        for (int index = 1; index * 2 < length; index++) {
            world.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, from.x + step.x * index, from.y + step.y * index, from.z + step.z * index, 0, 0, 0);
        }
        world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, to.x, to.y, to.z, 0, 0.05, 0);
    }

    public static void onIntel(IntelPacket packet) {
        ClientIntel.set(packet.getEntries());
    }

    public static void onLockProgress(LockProgressPacket packet) {
        ClientIntel.setLockProgress(packet.getProgress());
    }

    public static void onDroneState(DroneStatePacket packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) {
            return;
        }
        Entity entity = minecraft.world.getEntityByID(packet.getDroneId());
        if (entity instanceof EntityHandFlownDrone && ((EntityHandFlownDrone) entity).isLocallyPiloted()) {
            ((EntityHandFlownDrone) entity).reconcile(packet.getSequence(), packet.getState(), packet.getBatteryFraction());
        }
    }
}
