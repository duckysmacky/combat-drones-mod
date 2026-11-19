package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.Fx;
import io.github.duckysmacky.combatdrones.client.fx.FxParticle;
import net.minecraft.world.World;
import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.CommonProxy;
import io.github.duckysmacky.combatdrones.entity.EntityBomb;
import io.github.duckysmacky.combatdrones.entity.EntityCruiseMissile;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import io.github.duckysmacky.combatdrones.entity.EntityEmpBlast;
import io.github.duckysmacky.combatdrones.entity.EntityEmpOrb;
import io.github.duckysmacky.combatdrones.entity.EntityEmpStation;
import io.github.duckysmacky.combatdrones.entity.EntityMissilePad;
import io.github.duckysmacky.combatdrones.entity.EntityPilotBody;
import io.github.duckysmacky.combatdrones.entity.EntityRocket;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.entity.EntityRunway;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityHandFlownDrone.class, RenderHandFlownDrone::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityBomb.class, RenderProjectile::bomb);
        RenderingRegistry.registerEntityRenderingHandler(EntityRampUnit.class, RenderRampUnit::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityLaunchRamp.class, RenderLaunchRamp::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityRunway.class, RenderLaunchRamp::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityMissilePad.class, RenderMissilePad::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityEmpStation.class, RenderEmpStation::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityEmpBlast.class, RenderEmpBlast::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityEmpOrb.class, RenderEmpOrb::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityCruiseMissile.class, RenderRampUnit::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityPilotBody.class, RenderPilotBody::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityRocket.class, RenderProjectile::rocket);
        GeoModels.init();
    }

    @Override
    public void fx(World world, Fx kind, double x, double y, double z, double velocityX, double velocityY, double velocityZ, float size) {
        if (world.isRemote) {
            FxParticle.spawn(world, kind, x, y, z, velocityX, velocityY, velocityZ, size);
        }
    }

    @Override
    public void empHum(Entity source) {
        EmpHumSound.ensure(source);
    }

    @Override
    public void init() {
        for (KeyBinding key : DroneKeys.ALL) {
            ClientRegistry.registerKeyBinding(key);
        }
    }

    /**
     * The client only ticks an entity with 32 blocks of loaded chunks all round it, so a unit flying near the edge of
     * the render distance stayed drawn but stopped moving (its client motion runs in onUpdate, and position packets
     * only feed that). Flying units always tick here; on the server their forced chunks already keep them going.
     */
    @SubscribeEvent
    public static void onCanUpdate(EntityEvent.CanUpdate event) {
        Entity entity = event.getEntity();
        if (entity.world.isRemote && (entity instanceof EntityRampUnit || entity instanceof EntityHandFlownDrone)) {
            event.setCanUpdate(true);
        }
    }
}
