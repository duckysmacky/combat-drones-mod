package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.ForgeChunkManager;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps a flying vehicle's chunk, and the one it's heading into, force-loaded with a Forge ENTITY ticket, so it keeps
 * ticking far from any player. Server side only. After a restart Forge hands the ticket back through the mod's
 * loading callback ({@link #adopt}); it restores the ticket but not its chunks, so adopting forces the current one.
 */
public final class ChunkAnchor {
    /** Implemented by entities that hold an anchor, so the loading callback can hand their ticket back. */
    public interface Holder {
        void adoptChunkTicket(ForgeChunkManager.Ticket ticket);
    }

    private final Entity entity;
    @Nullable
    private ForgeChunkManager.Ticket ticket;

    public ChunkAnchor(Entity entity) {
        this.entity = entity;
    }

    /** Forces the entity's chunk and the chunk at {@code (aheadX, aheadZ)}; releases chunks it has left behind. */
    public void hold(double aheadX, double aheadZ) {
        if (ticket == null) {
            ticket = ForgeChunkManager.requestTicket(CombatDrones.instance, entity.world, ForgeChunkManager.Type.ENTITY);
            if (ticket == null) {
                return; // out of tickets (forgeChunkLoading.cfg): fly on, but only while players keep the area loaded
            }
            ticket.bindEntity(entity);
        }
        Set<ChunkPos> wanted = new HashSet<>();
        wanted.add(new ChunkPos(MathHelper.floor(entity.posX) >> 4, MathHelper.floor(entity.posZ) >> 4));
        wanted.add(new ChunkPos(MathHelper.floor(aheadX) >> 4, MathHelper.floor(aheadZ) >> 4));
        for (ChunkPos forced : ticket.getChunkList()) {
            if (!wanted.contains(forced)) {
                ForgeChunkManager.unforceChunk(ticket, forced);
            }
        }
        for (ChunkPos chunk : wanted) {
            if (!ticket.getChunkList().contains(chunk)) {
                ForgeChunkManager.forceChunk(ticket, chunk);
            }
        }
    }

    public void release() {
        if (ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
            ticket = null;
        }
    }

    public void adopt(ForgeChunkManager.Ticket restored) {
        ticket = restored;
        ForgeChunkManager.forceChunk(restored, new ChunkPos(MathHelper.floor(entity.posX) >> 4, MathHelper.floor(entity.posZ) >> 4));
    }
}
