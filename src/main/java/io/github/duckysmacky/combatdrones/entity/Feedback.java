package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.Fx;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketCustomSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;

/**
 * What a player gets back for handling a drone, a ramp or the pad: each action that goes through has its own sound
 * and makes the thing settle with a few sparks and dust (entity status {@link #HANDLED_STATUS}, so every client near
 * sees it); a refused one ticks at the player who tried, on top of its message.
 */
public final class Feedback {
    /** Entity status byte: something was just put on, taken off or poured into this entity. */
    public static final byte HANDLED_STATUS = 3;
    /** How long the settle lasts on the client, ticks. */
    public static final float SETTLE_TICKS = 7;

    private Feedback() {
    }

    /** Server: the action went through. */
    public static void done(Entity entity, SoundEvent sound, float pitch) {
        entity.world.playSound(null, entity.posX, entity.posY, entity.posZ, sound, SoundCategory.PLAYERS, 1.0F, pitch);
        entity.world.setEntityState(entity, HANDLED_STATUS);
    }

    /** Server: the action was refused. Heard by the one who tried only. */
    public static void deny(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).connection.sendPacket(new SPacketCustomSound(CombatDrones.MOD_ID + ":ui.deny", SoundCategory.MASTER,
                    player.posX, player.posY, player.posZ, 0.7F, 1.0F));
        }
    }

    /** Client, on {@link #HANDLED_STATUS}: a few sparks and a puff of dust round the entity. */
    static void handled(Entity entity) {
        for (int index = 0; index < 5; index++) {
            double x = entity.posX + (entity.world.rand.nextDouble() - 0.5) * entity.width;
            double z = entity.posZ + (entity.world.rand.nextDouble() - 0.5) * entity.width;
            CombatDrones.proxy.fx(entity.world, index < 3 ? Fx.SPARK : Fx.DUST, x, entity.posY + entity.height * 0.4, z,
                    (entity.world.rand.nextDouble() - 0.5) * 0.12, 0.08 + entity.world.rand.nextDouble() * 0.1, (entity.world.rand.nextDouble() - 0.5) * 0.12, 0.5F);
        }
    }

    /**
     * How far through its settle an entity is, as a squash amount: 0 at rest, peaking just after it was handled.
     * {@code handledAt} is the entity's {@code ticksExisted} when the status arrived.
     */
    public static float settle(Entity entity, int handledAt, float partialTicks) {
        float age = (entity.ticksExisted + partialTicks - handledAt) / SETTLE_TICKS;
        return age < 0 || age >= 1 ? 0 : (float) Math.sin(age * Math.PI) * (1 - age);
    }
}
