package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.entity.EntityBomb;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.entity.EntityRocket;
import io.github.duckysmacky.combatdrones.network.EngineSoundPacket;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Starts an engine voice for every drone this client can see with its motors running and every fuelled ramp unit
 * (plus far planes it only hears about), a voice for every rocket, shell and bomb in flight, and the quiet motor loop
 * for the drone the player is flying.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class DroneSounds {
    /** Engine voices by entity ID. */
    private static final Map<Integer, EngineVoice> VOICES = new HashMap<>();
    /** Engines of planes too far away to be tracked, by entity ID (see {@link FarEngineSound}). */
    private static final Map<Integer, FarEngineSound> FAR_ENGINES = new HashMap<>();
    /** Rockets, shells and bombs in flight, by entity ID (see {@link ProjectileVoice}). */
    private static final Map<Integer, ProjectileVoice> PROJECTILES = new HashMap<>();
    /** The pilot's own motor loop, by drone entity ID. */
    private static final Map<Integer, ITickableSound> PILOT_SOUNDS = new HashMap<>();

    private DroneSounds() {
    }

    /** From the server: running planes within earshot that this client doesn't track. */
    static void onFarEngines(EngineSoundPacket packet) {
        for (EngineSoundPacket.Entry entry : packet.getEntries()) {
            FarEngineSound known = FAR_ENGINES.get(entry.entityId);
            if (known != null && !known.isDone()) {
                known.refresh(entry);
                continue;
            }
            FarEngineSound sound = FarEngineSound.of(entry);
            if (sound != null) {
                FAR_ENGINES.put(entry.entityId, sound);
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
            VOICES.values().forEach(EngineVoice::stop);
            FAR_ENGINES.values().forEach(EngineVoice::stop);
            PROJECTILES.values().forEach(EngineVoice::stop);
            VOICES.clear();
            FAR_ENGINES.clear();
            PROJECTILES.clear();
            PILOT_SOUNDS.clear();
            return;
        }
        PILOT_SOUNDS.values().removeIf(ITickableSound::isDonePlaying);
        for (Entity entity : minecraft.world.loadedEntityList) {
            int id = entity.getEntityId();
            if (entity instanceof EntityHandFlownDrone) {
                EntityHandFlownDrone drone = (EntityHandFlownDrone) entity;
                DroneType type = drone.getDroneType();
                if (!drone.areMotorsOn() || type == null) {
                    continue;
                }
                if (drone.isLocallyPiloted() && !PILOT_SOUNDS.containsKey(id)) {
                    DroneMotorSound sound = new DroneMotorSound(drone);
                    PILOT_SOUNDS.put(id, sound);
                    minecraft.getSoundHandler().playSound(sound);
                } else if (!drone.isLocallyPiloted() && !VOICES.containsKey(id)) {
                    VOICES.put(id, new DroneEngineSound(drone, type));
                }
            } else if (entity instanceof EntityRampUnit && UnitEngineSound.isRunning((EntityRampUnit) entity) && !VOICES.containsKey(id)) {
                DroneType type = ((EntityRampUnit) entity).getDroneType();
                if (type != null) {
                    VOICES.put(id, new UnitEngineSound((EntityRampUnit) entity, type));
                }
            } else if ((entity instanceof EntityRocket || entity instanceof EntityBomb) && !entity.isDead && !PROJECTILES.containsKey(id)) {
                ProjectileVoice voice = ProjectileVoice.of(entity);
                if (voice != null) {
                    PROJECTILES.put(id, voice);
                }
            }
        }
        Entity view = minecraft.getRenderViewEntity() != null ? minecraft.getRenderViewEntity() : minecraft.player;
        if (view == null) {
            return;
        }
        Vec3d listener = view.getPositionEyes(1);
        boolean indoors = isIndoors(listener);
        // Only the nearest few in earshot sound (a ridden one counts as nearest): keeps clear of 1.12's channel limit
        List<EngineVoice> engines = new ArrayList<>(VOICES.values());
        engines.addAll(FAR_ENGINES.values());
        playNearest(engines, ModConfig.maxEngineVoices, listener, indoors);
        playNearest(new ArrayList<>(PROJECTILES.values()), ModConfig.maxProjectileSounds, listener, indoors);
        VOICES.values().removeIf(EngineVoice::isDone);
        FAR_ENGINES.values().removeIf(EngineVoice::isDone);
        PROJECTILES.values().removeIf(EngineVoice::isDone);
    }

    /** Ticks every voice, letting only the {@code max} nearest in earshot sound. */
    private static void playNearest(List<EngineVoice> voices, int max, Vec3d listener, boolean indoors) {
        List<EngineVoice> inEarshot = new ArrayList<>();
        for (EngineVoice voice : voices) {
            if (voice.prepare(listener) < 1) {
                inEarshot.add(voice);
            }
        }
        inEarshot.sort(Comparator.comparingDouble(EngineVoice::getDistance));
        Set<EngineVoice> audible = new HashSet<>(inEarshot.subList(0, Math.min(inEarshot.size(), max)));
        for (EngineVoice voice : voices) {
            voice.play(audible.contains(voice), indoors);
        }
    }

    /** Under a roof (or underground): no sky straight above the listener's head. */
    static boolean isIndoors(Vec3d listener) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.world != null && !minecraft.world.canSeeSky(new BlockPos(listener));
    }
}
