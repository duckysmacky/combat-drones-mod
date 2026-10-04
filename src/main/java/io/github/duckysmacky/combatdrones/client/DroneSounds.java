package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.network.EngineSoundPacket;
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

    /** Engines of planes too far away to be tracked, by entity ID (see {@link FarEngineSound}). */
    private static final Map<Integer, FarEngineSound> FAR_ENGINES = new HashMap<>();

    /** From the server: running planes within earshot that this client doesn't track. */
    static void onFarEngines(EngineSoundPacket packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        for (EngineSoundPacket.Entry entry : packet.getEntries()) {
            FarEngineSound known = FAR_ENGINES.get(entry.entityId);
            if (known != null && !known.isDonePlaying()) {
                known.refresh(entry);
                continue;
            }
            FarEngineSound sound = FarEngineSound.of(entry);
            if (sound != null) {
                FAR_ENGINES.put(entry.entityId, sound);
                minecraft.getSoundHandler().playSound(sound);
            }
        }
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
            FAR_ENGINES.clear();
            return;
        }
        MOTOR_SOUNDS.values().removeIf(ITickableSound::isDonePlaying);
        FAR_ENGINES.values().removeIf(ITickableSound::isDonePlaying);
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
