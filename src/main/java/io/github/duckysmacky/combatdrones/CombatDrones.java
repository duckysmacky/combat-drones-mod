package io.github.duckysmacky.combatdrones;

import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = CombatDrones.MOD_ID, name = CombatDrones.NAME, useMetadata = true, acceptedMinecraftVersions = "[1.12.2]")
public class CombatDrones {
    public static final String MOD_ID = "combat_drones";
    public static final String NAME = "Combat Drones";

    public static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        DroneTypes.load(Loader.instance().activeModContainer(), event.getModConfigurationDirectory());
    }
}
