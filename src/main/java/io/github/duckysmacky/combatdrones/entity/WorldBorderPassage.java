package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * With {@code worldBorderCollision} off, drones and ramp units fly through the world border. Vanilla hands an entity
 * inside the border a solid stone box for every block beyond it; those stand-ins are swapped back for the real
 * blocks there, so terrain past the border still collides. Runs on both sides: piloted drones predict on the client
 * with the server's (synced) setting.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class WorldBorderPassage {
    private WorldBorderPassage() {
    }

    @SubscribeEvent
    public static void onCollisionBoxes(GetCollisionBoxesEvent event) {
        Entity entity = event.getEntity();
        if (ModConfig.worldBorderCollision || !(entity instanceof EntityRampUnit || entity instanceof EntityHandFlownDrone)) {
            return;
        }
        World world = event.getWorld();
        WorldBorder border = world.getWorldBorder();
        AxisAlignedBB area = event.getAabb();
        if (area.minX >= border.minX() && area.maxX <= border.maxX() && area.minZ >= border.minZ() && area.maxZ <= border.maxZ()) {
            return;
        }
        List<AxisAlignedBB> boxes = event.getCollisionBoxesList();
        // Anything wholly past the border is a stone stand-in (or an entity box out there, which drones don't need)
        boxes.removeIf(box -> !border.contains(box));
        // Same block range vanilla scans
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = MathHelper.floor(area.minX) - 1; x <= MathHelper.ceil(area.maxX); x++) {
            for (int z = MathHelper.floor(area.minZ) - 1; z <= MathHelper.ceil(area.maxZ); z++) {
                if (!world.isBlockLoaded(pos.setPos(x, 64, z))) {
                    continue;
                }
                for (int y = MathHelper.floor(area.minY) - 1; y <= MathHelper.ceil(area.maxY); y++) {
                    pos.setPos(x, y, z);
                    if (!border.contains(pos)) {
                        world.getBlockState(pos).addCollisionBoxToList(world, pos, area, boxes, entity, false);
                    }
                }
            }
        }
    }
}
