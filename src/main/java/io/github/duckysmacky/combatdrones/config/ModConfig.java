package io.github.duckysmacky.combatdrones.config;

import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Global server balance knobs ({@code config/combat_drones.cfg}). Per-type stats live in the drone type JSON
 * instead. Knobs are added here as the feature that reads them lands.
 */
@Config(modid = CombatDrones.MOD_ID)
public final class ModConfig {
    @Config.Comment("Whether drone and munition explosions break blocks.")
    public static boolean blockDamageEnabled = true;

    @Config.Comment("Extra blast power per Fragmentation Grenade carried by a racer (vanilla TNT = 4).")
    @Config.RangeDouble(min = 0, max = 10)
    public static double grenadeBlastBonus = 1.0;

    private ModConfig() {
    }

    @Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
    public static final class SyncHandler {
        private SyncHandler() {
        }

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (CombatDrones.MOD_ID.equals(event.getModID())) {
                ConfigManager.sync(CombatDrones.MOD_ID, Config.Type.INSTANCE);
            }
        }
    }
}
