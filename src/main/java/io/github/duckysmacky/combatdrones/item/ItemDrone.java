package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.client.DroneKeys;
import io.github.duckysmacky.combatdrones.client.Tooltips;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

/** Placeable drone item; one instance per drone type, registered under the type ID. */
public class ItemDrone extends Item {
    private final String typeId;

    public ItemDrone(String typeId) {
        this.typeId = typeId;
        setMaxStackSize(1);
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
        }
        if (type.isImpactDetonates()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.combat_drones.drone.kamikaze"));
            tooltip.add(TextFormatting.GRAY + (type.getPayloadCapacity() > 0
                    ? I18n.format("tooltip.combat_drones.drone.grenade_slots", type.getPayloadCapacity())
                    : I18n.format("tooltip.combat_drones.drone.no_payload")));
        }
        if (handFlown) {
            Tooltips.addDetails("tooltip.combat_drones.drone.setup", tooltip,
                    Tooltips.keyName(DroneKeys.TOGGLE_FPV), Tooltips.keyName(DroneKeys.FLIGHT_MODE), Tooltips.keyName(DroneKeys.MOTORS),
                    Tooltips.keyName(DroneKeys.CAMERA));
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
