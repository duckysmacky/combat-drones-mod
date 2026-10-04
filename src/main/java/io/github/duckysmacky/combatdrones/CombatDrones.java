package io.github.duckysmacky.combatdrones;

import io.github.duckysmacky.combatdrones.command.CommandCombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.entity.ChunkAnchor;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = CombatDrones.MOD_ID, name = CombatDrones.NAME, useMetadata = true, acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-client:geckolib3;after:geckolib3")
public class CombatDrones {
    public static final String MOD_ID = "combat_drones";
    public static final String NAME = "Combat Drones";

    public static Logger logger;

    @Mod.Instance
    public static CombatDrones instance;

    @SidedProxy(clientSide = "io.github.duckysmacky.combatdrones.client.ClientProxy", serverSide = "io.github.duckysmacky.combatdrones.CommonProxy")
    public static CommonProxy proxy;

    public CombatDrones() {
        disableGeckoLibExamples();
    }

    /**
     * In a dev (deobfuscated) environment GeckoLib registers its example blocks, items, creative tab and a creeper
     * renderer unless {@code GeckoLibMod.DISABLE_IN_DEV} is set before its constructor runs; "before:geckolib3" makes
     * ours run first. Reflection, so a server without GeckoLib never links the class. Production never has them.
     */
    private static void disableGeckoLibExamples() {
        try {
            Class.forName("software.bernie.example.GeckoLibMod").getField("DISABLE_IN_DEV").setBoolean(null, true);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // GeckoLib absent (dedicated server) or reorganised: nothing to switch off
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        DroneTypes.load(Loader.instance().activeModContainer());
        ModConfig.load(event.getModConfigurationDirectory());
        ModNetwork.register();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandCombatDrones());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
        // Flying ramp units and running drones keep their own chunks loaded; after a restart Forge returns their tickets here
        ForgeChunkManager.setForcedChunkLoadingCallback(this, (tickets, world) -> {
            for (ForgeChunkManager.Ticket ticket : tickets) {
                if (ticket.getEntity() instanceof ChunkAnchor.Holder) {
                    ((ChunkAnchor.Holder) ticket.getEntity()).adoptChunkTicket(ticket);
                } else {
                    ForgeChunkManager.releaseTicket(ticket);
                }
            }
        });
    }
}
