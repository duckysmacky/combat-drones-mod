package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.RemoteSessions;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the FPV key (or Esc during a remote session). Leaves a remote session if the player is in one,
 * otherwise enters or leaves FPV with the drone linked to the held remote. No payload; the server decides.
 */
public class TogglePilotPacket implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<TogglePilotPacket, IMessage> {
        @Override
        public IMessage onMessage(TogglePilotPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                // A drone's FPV session ends through the drone itself (clean exit, it hovers); a ramp unit's directly
                if (RemoteSessions.isRemote(player) && !RemoteSessions.isDroneSession(player)) {
                    RemoteSessions.end(player, null);
                } else {
                    EntityHandFlownDrone.togglePilot(player);
                }
            });
            return null;
        }
    }
}
