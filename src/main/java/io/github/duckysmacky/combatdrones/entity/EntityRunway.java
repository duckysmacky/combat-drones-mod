package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.physics.RunwayOps;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Light or Heavy Runway: a flat strip planes and the recon UAV take off from and land back on. Faces one of the four
 * compass directions, so its hitbox is the exact rectangle of the strip. Planes take off from the tail toward the far
 * end and land the same way; a plane that lands is put back on it unlaunched, ready to refuel, rearm and go again.
 * Unlike a ramp it belongs to whoever placed it, and stays linked to their planner while its plane is out.
 */
public class EntityRunway extends EntityLaunchRamp {
    /** Client status: a plane just landed on it (it taxis back to the tail). */
    public static final byte DOCKED_STATUS = 40;
    /** Deck top above the ground the runway sits on (1 px). */
    public static final double DECK_HEIGHT = 1.0 / 16.0;
    /** Headroom above the deck the strip needs clear to be placed. */
    private static final int HEADROOM = 3;

    /** Client: {@code ticksExisted} when a plane last landed on it, for the taxi back. */
    public int dockedAt = -1000;

    public EntityRunway(World world) {
        super(world);
    }

    public EntityRunway(World world, Kind kind, double x, double y, double z, EnumFacing facing, EntityPlayer placer) {
        super(world, kind, x, y, z, facing.getHorizontalAngle());
        owner = placer.getUniqueID();
        setPosition(x, y, z);
    }

    /** The strip's rectangle, not the square vanilla makes from the width: long along its heading. */
    @Override
    public void setPosition(double x, double y, double z) {
        if (dataManager == null) { // the Entity constructor places it before its data exists
            super.setPosition(x, y, z);
            return;
        }
        posX = x;
        posY = y;
        posZ = z;
        Kind kind = getKind();
        boolean alongX = Math.abs(Math.sin(Math.toRadians(rotationYaw))) > 0.5;
        double halfLength = kind.getLengthBlocks() / 2.0;
        double halfWidth = kind.getWidthBlocks() / 2.0;
        double halfX = alongX ? halfLength : halfWidth;
        double halfZ = alongX ? halfWidth : halfLength;
        setEntityBoundingBox(new AxisAlignedBB(x - halfX, y, z - halfZ, x + halfX, y + kind.getHitboxHeight(), z + halfZ));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote && ticksExisted % 20 == 1) {
            setPosition(posX, posY, posZ); // the yaw can arrive after the position on a fresh client copy
        }
    }

    /** The strip as the autopilot sees it: threshold at the tail, on the deck. */
    public Route.Runway geometry() {
        Kind kind = getKind();
        double radians = Math.toRadians(rotationYaw);
        double half = kind.getLengthBlocks() / 2.0;
        return new Route.Runway(getUniqueID(), posX + Math.sin(radians) * half, posY + DECK_HEIGHT, posZ - Math.cos(radians) * half,
                rotationYaw, kind.getLengthBlocks(), kind.getWidthBlocks());
    }

    /** Server: the plane rolls out from the tail and takes off; returns it, or null if empty. */
    @Nullable
    @Override
    public EntityRampUnit launch() {
        DroneType type = getUnitType();
        if (type == null) {
            return null;
        }
        Route.Runway runway = geometry();
        double seat = RunwayOps.seat(runway);
        double x = runway.pointX(seat, 0);
        double z = runway.pointZ(seat, 0);
        EntityRampUnit unit = new EntityRampUnit(world, type, owner, getRoute(), getFuel(), getLoadout(), x, runway.y, z, rotationYaw);
        unit.startTakeoff(runway);
        world.spawnEntity(unit);
        clearUnit();
        return unit;
    }

    /** Takes {@code type} from {@code unitOwner}: free, theirs, and the right size. */
    public boolean canTake(DroneType type, @Nullable UUID unitOwner) {
        return !isDead && !isLoaded() && owner != null && owner.equals(unitOwner) && getKind().accepts(type);
    }

    /** Server: a plane that just stopped on it goes back on it, unlaunched, with what it had left. */
    public void dock(DroneType type, EntityRampUnit unit) {
        putUnit(type, unit.getOwner(), unit.fuel, unit.getLoadout(), unit.getRoute());
        world.setEntityState(this, DOCKED_STATUS);
        world.playSound(null, posX, posY, posZ, ModSounds.UNIT_INSTALL, SoundCategory.NEUTRAL, 1.0F, 0.8F);
    }

    @Override
    public void handleStatusUpdate(byte id) {
        if (id == DOCKED_STATUS) {
            dockedAt = ticksExisted;
        } else {
            super.handleStatusUpdate(id);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distanceSq) {
        return distanceSq < 192 * 192;
    }

    /**
     * Deck cells of a runway of {@code kind} starting at {@code start} (the block it sits on top of, deck level) and
     * running {@code facing}: the start block and the ones to its right for a 2-wide strip, centred on it for 3.
     */
    public static List<BlockPos> footprint(Kind kind, BlockPos start, EnumFacing facing) {
        EnumFacing right = facing.rotateY();
        int first = -(kind.getWidthBlocks() - 1) / 2;
        List<BlockPos> cells = new ArrayList<>();
        for (int along = 0; along < kind.getLengthBlocks(); along++) {
            for (int across = first; across < first + kind.getWidthBlocks(); across++) {
                cells.add(start.offset(facing, along).offset(right, across));
            }
        }
        return cells;
    }

    /** Room for the strip: solid ground under every cell, nothing solid or liquid on it or just above, no ramp in the way. */
    public static boolean fits(World world, List<BlockPos> cells) {
        AxisAlignedBB area = null;
        for (BlockPos cell : cells) {
            BlockPos ground = cell.down();
            if (!world.getBlockState(ground).isSideSolid(world, ground, EnumFacing.UP)) {
                return false;
            }
            for (int up = 0; up < HEADROOM; up++) {
                BlockPos above = cell.up(up);
                IBlockState state = world.getBlockState(above);
                AxisAlignedBB solid = state.getCollisionBoundingBox(world, above);
                if (state.getMaterial().isLiquid() || (solid != null && solid != Block.NULL_AABB)) {
                    return false;
                }
            }
            AxisAlignedBB box = new AxisAlignedBB(cell);
            area = area == null ? box : area.union(box);
        }
        return area != null && world.getEntitiesWithinAABB(EntityLaunchRamp.class, area.shrink(0.05)).isEmpty();
    }
}
