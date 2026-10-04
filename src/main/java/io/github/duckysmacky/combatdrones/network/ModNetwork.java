package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The mod's packet channel. Client-bound handlers only schedule work and call into {@code client.ClientPacketHandlers},
 * so a dedicated server never has to load a client class while registering them.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class ModNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CombatDrones.MOD_ID);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(TypeSyncPacket.Handler.class, TypeSyncPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(TogglePilotPacket.Handler.class, TogglePilotPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(ControlInputPacket.Handler.class, ControlInputPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(DroneStatePacket.Handler.class, DroneStatePacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(TriggerPacket.Handler.class, TriggerPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PlannerOpenPacket.Handler.class, PlannerOpenPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PlannerRequestPacket.Handler.class, PlannerRequestPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PlannerSubmitPacket.Handler.class, PlannerSubmitPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(RemoteRequestPacket.Handler.class, RemoteRequestPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(RemoteSessionPacket.Handler.class, RemoteSessionPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PlaneInputPacket.Handler.class, PlaneInputPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(DesignatePacket.Handler.class, DesignatePacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(GunFirePacket.Handler.class, GunFirePacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PlannerMarkPacket.Handler.class, PlannerMarkPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(IntelPacket.Handler.class, IntelPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(ReconLookPacket.Handler.class, ReconLookPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(LockProgressPacket.Handler.class, LockProgressPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MapTileRequestPacket.Handler.class, MapTileRequestPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MapTilePacket.Handler.class, MapTilePacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(EngineSoundPacket.Handler.class, EngineSoundPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(EffectPacket.Handler.class, EffectPacket.class, id++, Side.CLIENT);
    }

    /** After a config reload: everyone gets the new stats right away. */
    public static void syncTypesToAll() {
        CHANNEL.sendToAll(new TypeSyncPacket(DroneTypes.all()));
    }

    /** Clients predict flight locally, so they need the server's (possibly overridden) stats, not their own files. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            CHANNEL.sendTo(new TypeSyncPacket(DroneTypes.all()), (EntityPlayerMP) event.player);
        }
    }
}
