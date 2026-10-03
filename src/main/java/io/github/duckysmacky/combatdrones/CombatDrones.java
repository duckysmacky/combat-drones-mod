package io.github.duckysmacky.combatdrones;

import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = CombatDrones.MOD_ID, name = CombatDrones.NAME, useMetadata = true, acceptedMinecraftVersions = "[1.12.2]")
public class CombatDrones {
    public static final String MOD_ID = "combat_drones";
    public static final String NAME = "Combat Drones";

    public static Logger logger;

    @SidedProxy(clientSide = "io.github.duckysmacky.combatdrones.client.ClientProxy", serverSide = "io.github.duckysmacky.combatdrones.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        DroneTypes.load(Loader.instance().activeModContainer());
        ModConfig.load(event.getModConfigurationDirectory());
        ModNetwork.register();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
