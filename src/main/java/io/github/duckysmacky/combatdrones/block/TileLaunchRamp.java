package io.github.duckysmacky.combatdrones.block;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.network.RouteCodec;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A Launch Ramp's contents: at most one ramp-launched unit (type, owner, fuel) and the route planned for it. The unit
 * only becomes an entity when it launches.
 */
public class TileLaunchRamp extends TileEntity {
    private String unitType = "";
    @Nullable
    private UUID owner;
    private double fuel;
    private Route route = Route.empty(Route.TerminalAction.LOITER);

    public boolean isLoaded() {
        return getUnitType() != null;
    }

    @Nullable
    public DroneType getUnitType() {
        return unitType.isEmpty() ? null : DroneTypes.get(unitType);
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public boolean isOwnedBy(EntityPlayer player) {
        return player.getUniqueID().equals(owner);
    }

    public double getFuel() {
        return fuel;
    }

    public double getFuelFraction() {
        DroneType type = getUnitType();
        return type == null ? 0 : Math.min(1, fuel / type.getBatteryOrFuelCapacity());
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
        changed();
    }

    /** Loads a unit from its item; the installer owns it. */
    public void install(DroneType type, double fuel, EntityPlayer installer) {
        unitType = type.getId();
        owner = installer.getUniqueID();
        this.fuel = Math.min(fuel, type.getBatteryOrFuelCapacity());
        route = Route.empty(defaultTerminal(type));
        changed();
    }

    /** One Fuel Can fills the tank completely. */
    public void refuel() {
        DroneType type = getUnitType();
        if (type != null) {
            fuel = type.getBatteryOrFuelCapacity();
            changed();
        }
    }

    /** Takes the unit off as its item (fuel kept in the item), emptying the ramp. */
    public ItemStack takeUnit() {
        DroneType type = getUnitType();
        if (type == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(CombatDrones.MOD_ID, type.getId()));
        ItemStack stack = item == null ? ItemStack.EMPTY : ItemDrone.withFuel(new ItemStack(item), fuel);
        clear();
        return stack;
    }

    /** Spawns the unit at the top of the rail, flying the planned route along the ramp's facing. */
    public void launch(EnumFacing facing) {
        DroneType type = getUnitType();
        if (type == null || world == null || world.isRemote) {
            return;
        }
        world.spawnEntity(new EntityRampUnit(world, type, owner, route, fuel,
                pos.getX() + 0.5 + facing.getXOffset() * 0.5, pos.getY() + 1.2, pos.getZ() + 0.5 + facing.getZOffset() * 0.5,
                facing.getHorizontalAngle()));
        clear();
    }

    private void clear() {
        unitType = "";
        owner = null;
        fuel = 0;
        route = Route.empty(Route.TerminalAction.LOITER);
        changed();
    }

    private static Route.TerminalAction defaultTerminal(DroneType type) {
        return type.isArmed() ? Route.TerminalAction.LOITER : Route.TerminalAction.LOOP;
    }

    private void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setString("Unit", unitType);
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setDouble("Fuel", fuel);
        compound.setTag("Route", RouteCodec.toNbt(route));
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        unitType = compound.getString("Unit");
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        fuel = compound.getDouble("Fuel");
        route = RouteCodec.fromNbt(compound.getCompoundTag("Route"), Route.TerminalAction.LOITER);
    }

    // Client sync, so the loaded unit can be drawn on the rail
    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
}
