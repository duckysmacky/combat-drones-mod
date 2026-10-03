package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CommonProxy;
import io.github.duckysmacky.combatdrones.block.TileLaunchRamp;
import io.github.duckysmacky.combatdrones.entity.EntityBomb;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityHandFlownDrone.class, RenderHandFlownDrone::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityBomb.class, RenderBomb::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityRampUnit.class, RenderRampUnit::new);
        ClientRegistry.bindTileEntitySpecialRenderer(TileLaunchRamp.class, new RenderLaunchRamp());
    }

    @Override
    public void init() {
        for (KeyBinding key : DroneKeys.ALL) {
            ClientRegistry.registerKeyBinding(key);
        }
    }
}
