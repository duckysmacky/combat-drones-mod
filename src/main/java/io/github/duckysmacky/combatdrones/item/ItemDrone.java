package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.client.DroneKeys;
import io.github.duckysmacky.combatdrones.client.Tooltips;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.type.BombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
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
        boolean handFlown = type.getCategory() == DroneType.Category.HAND_FLOWN;
        tooltip.add(TextFormatting.GRAY + I18n.format(handFlown ? "tooltip.combat_drones.drone.hand_flown" : "tooltip.combat_drones.drone.ramp_launched"));
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
        if (handFlown) {
            tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.drone.range", Math.round(type.getSignalRange())));
            tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.combat_drones.drone.default_battery",
                    I18n.format("item.combat_drones." + type.getDefaultBattery().itemName() + ".name")));
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
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.combat_drones.not_functional"));
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
