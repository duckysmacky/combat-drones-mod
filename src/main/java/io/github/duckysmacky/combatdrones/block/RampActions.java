package io.github.duckysmacky.combatdrones.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentTranslation;

/** Server-side Launch Ramp actions that need the Mission Planner. The planner itself arrives with its packets. */
final class RampActions {
    private RampActions() {
    }

    static boolean openPlanner(TileLaunchRamp ramp, EntityPlayer player) {
        player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.ramp.planner_soon"), true);
        return true;
    }
}
