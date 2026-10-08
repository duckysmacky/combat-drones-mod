package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.client.DroneKeys;
import io.github.duckysmacky.combatdrones.client.Tooltips;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.type.BombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import io.github.duckysmacky.combatdrones.type.Loadout;
import io.github.duckysmacky.combatdrones.type.RocketType;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/** Placeable drone item; one instance per drone type, registered under the type ID. */
public class ItemDrone extends Item {
    private final String typeId;

    public ItemDrone(String typeId) {
        this.typeId = typeId;
        setMaxStackSize(4);
    }

    private static final String TAG_FUEL = "Fuel";
    private static final String TAG_AMMO = "Ammo";
    private static final String TAG_AMMO_SECONDARY = "Ammo2";
    private static final String TAG_ROCKET_KIND = "RocketKind";

    /** Fuel stored in a ramp-launched unit's item (kept when it's taken off a ramp or lands). */
    public static double getFuel(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? Math.max(0, tag.getDouble(TAG_FUEL)) : 0;
    }

    public static ItemStack withFuel(ItemStack stack, double fuel) {
        if (fuel > 0) {
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            tag.setDouble(TAG_FUEL, fuel);
            stack.setTagCompound(tag);
        }
        return stack;
    }

    /** Rockets and rounds loaded in an armed plane's item (kept when it's taken off a ramp or lands). */
    public static Loadout getLoadout(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? readLoadout(tag) : new Loadout();
    }

    public static ItemStack withLoadout(ItemStack stack, Loadout loadout) {
        if (!loadout.isEmpty()) {
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            writeLoadout(tag, loadout);
            stack.setTagCompound(tag);
        }
        return stack;
    }

    /** The loadout under the shared keys an item, a ramp and a flying unit all save it with. */
    public static Loadout readLoadout(NBTTagCompound tag) {
        Loadout loadout = new Loadout();
        loadout.setAmmo(0, tag.getInteger(TAG_AMMO));
        loadout.setAmmo(1, tag.getInteger(TAG_AMMO_SECONDARY));
        // Missing (older saves, from before rocket sizes) reads as 0: light rockets
        loadout.setRocketKind(RocketType.byIndex(tag.getInteger(TAG_ROCKET_KIND)));
        return loadout;
    }

    public static void writeLoadout(NBTTagCompound tag, Loadout loadout) {
        tag.setInteger(TAG_AMMO, loadout.getAmmo(0));
        tag.setInteger(TAG_AMMO_SECONDARY, loadout.getAmmo(1));
        tag.setInteger(TAG_ROCKET_KIND, loadout.getRocketKind().ordinal());
    }

    /** Translation key naming the ammo {@code station} takes, by weapon (and rocket size for a pod). */
    public static String ammoKey(DroneType type, int station, Loadout loadout) {
        DroneType.WeaponType weapon = type.getWeapon(station);
        String key = weapon.name().toLowerCase(Locale.ROOT);
        return weapon == DroneType.WeaponType.ROCKET_POD ? key + "." + loadout.getRocketKind().key() : key;
    }

    /** Looked up on use rather than cached, so server-synced type stats are always the ones applied. */
    public DroneType getDroneType() {
        return DroneTypes.get(typeId);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        DroneType type = getDroneType();
        if (type == null) {
            return;
        }
        // Order for every item: what it is, its current state, then stats, behaviour and how-to behind Shift
        boolean handFlown = type.getCategory() == DroneType.Category.HAND_FLOWN;
        boolean missile = type.getCategory() == DroneType.Category.VERTICAL_STRIKE;
        tooltip.add(TextFormatting.GRAY + I18n.format(handFlown ? "tooltip.combat_drones.drone.hand_flown"
                : missile ? "tooltip.combat_drones.drone.vertical_strike" : "tooltip.combat_drones.drone.ramp_launched"));
        if (!handFlown) {
            double fuel = getFuel(stack);
            tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.drone.fuel",
                    Math.round(Math.min(1, fuel / type.getBatteryOrFuelCapacity()) * 100)));
            Loadout loadout = getLoadout(stack);
            for (int station = 0; station < Loadout.STATIONS; station++) {
                if (type.getWeapon(station) != DroneType.WeaponType.NONE) {
                    tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.drone.ammo." + ammoKey(type, station, loadout),
                            loadout.getAmmo(station), loadout.capacity(type, station)));
                }
            }
        }
        if (!GuiScreen.isShiftKeyDown()) {
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.combat_drones.hold_shift"));
            return;
        }
        // Live stats: on a server these are the server's (config-tuned) values
        tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.drone.speed", Math.round(type.getMaxSpeed() * 72)));
        tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.drone.health", formatNumber(type.getMaxHP())));
        if (type.getBlastPower() > 0) {
            tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.drone.blast", formatNumber(type.getBlastPower())));
        }
        if (missile) {
            tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.drone.standby",
                    Math.round(type.getBatteryOrFuelCapacity() / Math.max(1e-6, type.getDrainRatePerTick()) / 20)));
            Tooltips.addDetails("tooltip.combat_drones.drone.setup_missile", tooltip, Tooltips.keyName(DroneKeys.TOGGLE_FPV));
            return;
        }
        if (handFlown) {
            tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.drone.range", Math.round(type.getSignalRange())));
            tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.drone.default_battery",
                    I18n.format("item.combat_drones." + type.getDefaultBattery().itemName() + ".name")));
        }
        if (!type.isImpactDetonates() && type.getBlastPower() > 0) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.combat_drones.drone.crash_blast"));
        }
        if (type.isImpactDetonates()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.combat_drones.drone.kamikaze"));
            if (type.getPayload() == DroneType.PayloadKind.GRENADE && type.getPayloadCapacity() > 0) {
                tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.combat_drones.drone.kamikaze_trigger"));
            }
        }
        if (type.getPayload() == DroneType.PayloadKind.BOMB && type.getPayloadCapacity() > 0) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.combat_drones.drone.bomb_bay",
                    BombType.LIGHT.capacityIn(type.getPayloadCapacity()), BombType.MEDIUM.capacityIn(type.getPayloadCapacity()),
                    BombType.HEAVY.capacityIn(type.getPayloadCapacity())));
        } else if (type.getPayload() != null && type.getPayloadCapacity() > 0) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.combat_drones.drone.payload_slots", type.getPayloadCapacity(),
                    I18n.format("tooltip.combat_drones.payload." + type.getPayload().name().toLowerCase(Locale.ROOT))));
        } else if (handFlown && type.isImpactDetonates()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.combat_drones.drone.no_payload"));
        }
        if (type.getControlScheme() == DroneType.ControlScheme.DJI) {
            Tooltips.addDetails("tooltip.combat_drones.drone.setup_dji", tooltip,
                    Tooltips.keyName(DroneKeys.TOGGLE_FPV), Tooltips.keyName(DroneKeys.MOTORS));
        } else if (handFlown) {
            Tooltips.addDetails("tooltip.combat_drones.drone.setup", tooltip,
                    Tooltips.keyName(DroneKeys.TOGGLE_FPV), Tooltips.keyName(DroneKeys.FLIGHT_MODE), Tooltips.keyName(DroneKeys.MOTORS));
        } else {
            Tooltips.addDetails("tooltip.combat_drones.drone.setup_ramp", tooltip);
            // How "Take control" / View works for this unit
            String control = type.isSensor() ? "sensor"
                    : type.getWeaponType() != DroneType.WeaponType.NONE ? "armed_plane"
                    : type.getControlScheme() == null ? null : type.getControlScheme().name().toLowerCase(Locale.ROOT);
            if (control != null) {
                Tooltips.addDetails("tooltip.combat_drones.drone.control." + control, tooltip,
                        Tooltips.keyName(DroneKeys.FLIGHT_MODE), Tooltips.keyName(DroneKeys.TOGGLE_FPV));
            }
        }
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        DroneType type = getDroneType();
        // Ramp-launched types are installed on a Launch Ramp instead (M5)
        if (type == null || type.getCategory() != DroneType.Category.HAND_FLOWN) {
            return EnumActionResult.PASS;
        }
        if (!world.isRemote) {
            BlockPos spawnPos = pos.offset(facing);
            world.spawnEntity(new EntityHandFlownDrone(world, type, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.rotationYaw));
            ItemStack stack = player.getHeldItem(hand);
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
        }
        return EnumActionResult.SUCCESS;
    }
}
