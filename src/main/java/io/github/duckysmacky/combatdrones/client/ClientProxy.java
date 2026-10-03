package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CommonProxy;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityHandFlownDrone.class, RenderHandFlownDrone::new);
    }

    @Override
    public void init() {
        for (KeyBinding key : DroneKeys.ALL) {
            ClientRegistry.registerKeyBinding(key);
        }
    }
}
