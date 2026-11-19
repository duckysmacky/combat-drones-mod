package io.github.duckysmacky.combatdrones.network;

import io.github.duckysmacky.combatdrones.entity.EmpGunServer;
import io.github.duckysmacky.combatdrones.entity.StingerServer;
import io.github.duckysmacky.combatdrones.item.ItemEmpGun;
import io.github.duckysmacky.combatdrones.item.ItemStinger;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: a hand-held launcher's trigger pressed or released, or its reload key. Handed to the server
 * logic of whichever launcher the sender holds. Raising it needs no packet (it is vanilla item use) and neither does
 * the aim (the server has the player's look); the server checks everything.
 */
public class LauncherInputPacket implements IMessage {
    public static final int ACTION_PRESS = 0;
    public static final int ACTION_RELOAD = 1;
    public static final int ACTION_RELEASE = 2;
    /** The trigger let go of without meaning to fire: a screen opened or the launcher put away. */
    public static final int ACTION_CANCEL = 3;

    private int action;

    public LauncherInputPacket() {
    }

    public LauncherInputPacket(int action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
    }

    public static class Handler implements IMessageHandler<LauncherInputPacket, IMessage> {
        @Override
        public IMessage onMessage(LauncherInputPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                Item held = player.getHeldItemMainhand().getItem();
                if (held instanceof ItemStinger) {
                    StingerServer.onInput(player, message.action);
                } else if (held instanceof ItemEmpGun) {
                    EmpGunServer.onInput(player, message.action);
                }
            });
            return null;
        }
    }
}
