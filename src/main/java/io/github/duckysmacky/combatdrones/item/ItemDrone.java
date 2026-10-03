package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
