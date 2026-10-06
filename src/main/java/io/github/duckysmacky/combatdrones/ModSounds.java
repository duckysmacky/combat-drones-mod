package io.github.duckysmacky.combatdrones;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;

/**
 * Sound events. Recordings come from the reference mods and CC0 sources (see docs/ART_PASS.md for each clip's origin);
 * the UI beeps and chimes are still synthesized by {@code tools/gen_sounds.py}.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class ModSounds {
    /** High prop whine: what the pilot hears from their own drone (quietly). */
    public static final SoundEvent DRONE_MOTOR = create("drone.motor");
    /** Softer, lower hum: what everyone else hears. */
    public static final SoundEvent DRONE_MOTOR_FAR = create("drone.motor_far");
    public static final SoundEvent DRONE_LINK = create("drone.link");
    public static final SoundEvent DRONE_BEEP = create("drone.beep");
    public static final SoundEvent DRONE_BATTERY = create("drone.battery");
    public static final SoundEvent DRONE_ATTACH = create("drone.attach");
    public static final SoundEvent DRONE_CONNECT = create("drone.connect");
    public static final SoundEvent DRONE_HIT = create("drone.hit");
    /** Radio static and telemetry chirps given off by a pilot's stand-in body, so others can find them. */
    public static final SoundEvent DRONE_PILOT_STATIC = create("drone.pilot_static");
    /** Propeller engine of a flying ramp-launched unit. */
    public static final SoundEvent UNIT_ENGINE = create("unit.engine");
    public static final SoundEvent UNIT_LAUNCH = create("unit.launch");
    public static final SoundEvent GUNSHIP_ROCKET = create("gunship.rocket");
    public static final SoundEvent GUNSHIP_GUN = create("gunship.gun");
    /** Booster lighting up on a munition. */
    public static final SoundEvent UNIT_BOOST = create("unit.boost");
    public static final SoundEvent BOMB_RELEASE = create("bomb.release");
    /** Close blast; layered over vanilla's explosion sound. */
    public static final SoundEvent EXPLOSION_NEAR = create("explosion.near");
    /** Rumble heard from far away, delayed by distance. */
    public static final SoundEvent EXPLOSION_FAR = create("explosion.far");
    public static final SoundEvent BULLET_IMPACT = create("bullet.impact");
    /** Supersonic crack of a round passing close by. */
    public static final SoundEvent BULLET_FLYBY = create("bullet.flyby");
    /** Cruise missile engine lighting up on its pad. */
    public static final SoundEvent MISSILE_IGNITION = create("missile.ignition");
    /** Cruise missile leaving the pad: a blast heard far away. */
    public static final SoundEvent MISSILE_LIFTOFF = create("missile.liftoff");
    /** Rising whistle of a diving cruise missile (loop). */
    public static final SoundEvent MISSILE_SCREAM = create("missile.scream");
    /** INBOUND alarm for everyone near a strike point. */
    public static final SoundEvent MISSILE_ALARM = create("missile.alarm");
    /** Per-type engine loops, picked by a drone type's {@code engineSound}. */
    private static final SoundEvent[] ENGINES = {create("unit.engine_electric"), create("unit.engine_twostroke"),
            create("unit.engine_piston"), create("drone.motor_dji"), create("missile.engine")};

    private ModSounds() {
    }

    private static SoundEvent create(String name) {
        ResourceLocation id = new ResourceLocation(CombatDrones.MOD_ID, name);
        return new SoundEvent(id).setRegistryName(id);
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().registerAll(DRONE_MOTOR, DRONE_MOTOR_FAR, DRONE_LINK, DRONE_BEEP, DRONE_BATTERY, DRONE_ATTACH, DRONE_CONNECT, DRONE_HIT, DRONE_PILOT_STATIC, UNIT_ENGINE, UNIT_LAUNCH,
                GUNSHIP_ROCKET, GUNSHIP_GUN, UNIT_BOOST, BOMB_RELEASE, EXPLOSION_NEAR, EXPLOSION_FAR, BULLET_IMPACT, BULLET_FLYBY, MISSILE_IGNITION, MISSILE_LIFTOFF, MISSILE_SCREAM, MISSILE_ALARM);
        event.getRegistry().registerAll(ENGINES);
    }

    /** A type's engine loop by name (e.g. "unit.engine_twostroke"), or {@code fallback} if unset or unknown. */
    public static SoundEvent engine(@Nullable String name, SoundEvent fallback) {
        if (name != null) {
            for (SoundEvent engine : ENGINES) {
                if (engine.getSoundName().getPath().equals(name)) {
                    return engine;
                }
            }
        }
        return fallback;
    }
}
