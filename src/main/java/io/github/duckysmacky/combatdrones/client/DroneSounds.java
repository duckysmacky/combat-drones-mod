package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;

/** Starts a motor loop for every drone this client can see with its motors running, and an engine loop for fuelled ramp units. */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class DroneSounds {
    private static final Map<Integer, ITickableSound> MOTOR_SOUNDS = new HashMap<>();

    private DroneSounds() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Effects.tick(minecraft.world != null);
        if (minecraft.world == null) {
            MOTOR_SOUNDS.clear();
            return;
        }
        MOTOR_SOUNDS.values().removeIf(ITickableSound::isDonePlaying);
        for (Entity entity : minecraft.world.loadedEntityList) {
            if (entity instanceof EntityHandFlownDrone && ((EntityHandFlownDrone) entity).areMotorsOn()
                    && !MOTOR_SOUNDS.containsKey(entity.getEntityId())) {
                DroneMotorSound sound = new DroneMotorSound((EntityHandFlownDrone) entity);
                MOTOR_SOUNDS.put(entity.getEntityId(), sound);
                minecraft.getSoundHandler().playSound(sound);
            } else if (entity instanceof EntityRampUnit && UnitEngineSound.isRunning((EntityRampUnit) entity)
                    && !MOTOR_SOUNDS.containsKey(entity.getEntityId())) {
                UnitEngineSound sound = new UnitEngineSound((EntityRampUnit) entity);
                MOTOR_SOUNDS.put(entity.getEntityId(), sound);
                minecraft.getSoundHandler().playSound(sound);
            }
        }
    }
}
