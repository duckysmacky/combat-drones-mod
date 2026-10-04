package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CommonProxy;
import io.github.duckysmacky.combatdrones.entity.EntityBomb;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import io.github.duckysmacky.combatdrones.entity.EntityPilotBody;
import io.github.duckysmacky.combatdrones.entity.EntityRocket;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityHandFlownDrone.class, RenderHandFlownDrone::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityBomb.class, RenderProjectile::bomb);
        RenderingRegistry.registerEntityRenderingHandler(EntityRampUnit.class, RenderRampUnit::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityLaunchRamp.class, RenderLaunchRamp::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityPilotBody.class, RenderPilotBody::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityRocket.class, RenderProjectile::rocket);
        GeoModels.init();
    }

    @Override
    public void init() {
        for (KeyBinding key : DroneKeys.ALL) {
            ClientRegistry.registerKeyBinding(key);
        }
    }
}
