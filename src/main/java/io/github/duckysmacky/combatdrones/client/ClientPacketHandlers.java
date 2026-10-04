package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.network.DroneStatePacket;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.network.GunFirePacket;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import io.github.duckysmacky.combatdrones.network.LockProgressPacket;
import io.github.duckysmacky.combatdrones.network.MapTilePacket;
import io.github.duckysmacky.combatdrones.network.PlannerOpenPacket;
import io.github.duckysmacky.combatdrones.network.RemoteSessionPacket;
import io.github.duckysmacky.combatdrones.network.TypeSyncPacket;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

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

    public static void onGunFire(GunFirePacket packet) {
        Effects.gunFire(packet);
    }

    public static void onEffect(EffectPacket packet) {
        Effects.onEffect(packet);
    }

    public static void onIntel(IntelPacket packet) {
        ClientIntel.set(packet.getEntries());
    }

    public static void onMapTiles(MapTilePacket packet) {
        ClientMapTiles.accept(packet);
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
