package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CommonProxy;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.entity.EntityBomb;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityHandFlownDrone.class, RenderHandFlownDrone::new);
        // Factories run once the render manager exists, after item registration, so ModContent.bomb is set by then
        RenderingRegistry.registerEntityRenderingHandler(EntityBomb.class,
                manager -> new RenderSnowball<>(manager, ModContent.bomb, Minecraft.getMinecraft().getRenderItem()));
    }

    @Override
    public void init() {
        for (KeyBinding key : DroneKeys.ALL) {
            ClientRegistry.registerKeyBinding(key);
        }
    }
}
