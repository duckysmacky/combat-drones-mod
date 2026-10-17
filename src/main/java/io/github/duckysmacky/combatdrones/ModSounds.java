package io.github.duckysmacky.combatdrones;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Sound events played by name from code. Engine bands and explosion layers are played straight from sounds.json by
 * the client (see {@code BandSound}) and aren't registered here.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class ModSounds {
    /** High prop whine: what the pilot hears from their own drone (quietly). Others hear its engine bands. */
    public static final SoundEvent DRONE_MOTOR = create("drone.motor");
    public static final SoundEvent DRONE_LINK = create("drone.link");
    public static final SoundEvent DRONE_BEEP = create("drone.beep");
    public static final SoundEvent DRONE_BATTERY = create("drone.battery");
    public static final SoundEvent DRONE_ATTACH = create("drone.attach");
    public static final SoundEvent DRONE_CONNECT = create("drone.connect");
    public static final SoundEvent DRONE_HIT = create("drone.hit");
    /** Radio static and telemetry chirps given off by a pilot's stand-in body, so others can find them. */
    public static final SoundEvent DRONE_PILOT_STATIC = create("drone.pilot_static");
    public static final SoundEvent UNIT_LAUNCH = create("unit.launch");
    public static final SoundEvent GUNSHIP_ROCKET = create("gunship.rocket");
    public static final SoundEvent GUNSHIP_GUN = create("gunship.gun");
    /** Booster lighting up on a munition. */
    public static final SoundEvent UNIT_BOOST = create("unit.boost");
    public static final SoundEvent BOMB_RELEASE = create("bomb.release");
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

    private ModSounds() {
    }

    private static SoundEvent create(String name) {
        ResourceLocation id = new ResourceLocation(CombatDrones.MOD_ID, name);
        return new SoundEvent(id).setRegistryName(id);
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().registerAll(DRONE_MOTOR, DRONE_LINK, DRONE_BEEP, DRONE_BATTERY, DRONE_ATTACH, DRONE_CONNECT, DRONE_HIT, DRONE_PILOT_STATIC, UNIT_LAUNCH,
                GUNSHIP_ROCKET, GUNSHIP_GUN, UNIT_BOOST, BOMB_RELEASE, BULLET_IMPACT, BULLET_FLYBY, MISSILE_IGNITION, MISSILE_LIFTOFF, MISSILE_SCREAM, MISSILE_ALARM);
    }
}
