package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.network.DroneStatePacket;
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
    }

    public static void onDroneState(DroneStatePacket packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) {
            return;
        }
        Entity entity = minecraft.world.getEntityByID(packet.getDroneId());
        if (entity instanceof EntityHandFlownDrone && ((EntityHandFlownDrone) entity).isLocallyPiloted()) {
            ((EntityHandFlownDrone) entity).reconcile(packet.getSequence(), packet.getState());
        }
    }
}
