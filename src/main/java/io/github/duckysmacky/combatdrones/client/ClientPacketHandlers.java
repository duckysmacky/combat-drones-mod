package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.network.DroneStatePacket;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.network.EngineSoundPacket;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/** Client-side bodies of client-bound packet handlers; only ever invoked on the client thread. */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void onTypeSync(TypeSyncPacket packet) {
        DroneTypes.applyServerTypes(packet.getTypesJson());
        packet.applyEquipment();
    }

    public static void onClientConfigReload() {
        Minecraft minecraft = Minecraft.getMinecraft();
        String message;
        try {
            ModConfig.reloadClient();
            message = TextFormatting.GREEN + "[client] options of " + ModConfig.FILE_NAME + " reloaded";
        } catch (RuntimeException e) {
            CombatDrones.logger.error("Client config reload failed, keeping the previous values", e);
            message = TextFormatting.RED + "Couldn't reload " + ModConfig.FILE_NAME + ", keeping the previous values: " + e.getMessage();
        }
        if (minecraft.player != null) {
            minecraft.player.sendMessage(new TextComponentString(message));
        }
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
        RemoteController.onSession(packet.getUnitId(), packet.isControl(), packet.isDrone(),
                new Vec3d(packet.getHomeX(), packet.getHomeY(), packet.getHomeZ()));
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

    public static void onEngineSounds(EngineSoundPacket packet) {
        DroneSounds.onFarEngines(packet);
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
