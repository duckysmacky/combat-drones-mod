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
    // Handling: one sound per job
    public static final SoundEvent DRONE_PLACE = create("drone.place");
    public static final SoundEvent DRONE_PICKUP = create("drone.pickup");
    public static final SoundEvent UNIT_INSTALL = create("unit.install");
    public static final SoundEvent UNIT_REMOVE = create("unit.remove");
    public static final SoundEvent UNIT_AMMO = create("unit.ammo");
    public static final SoundEvent PAD_LOAD = create("pad.load");
    public static final SoundEvent RAMP_PLACE = create("ramp.place");
    /** Radio static and telemetry chirps given off by a pilot's stand-in body, so others can find them. */
    public static final SoundEvent DRONE_PILOT_STATIC = create("drone.pilot_static");
    public static final SoundEvent UNIT_LAUNCH = create("unit.launch");
    public static final SoundEvent GUNSHIP_ROCKET = create("gunship.rocket");
    public static final SoundEvent GUNSHIP_GUN = create("gunship.gun");
    /** Booster lighting up on a munition. */
    public static final SoundEvent UNIT_BOOST = create("unit.boost");
    public static final SoundEvent BOMB_RELEASE = create("bomb.release");
    public static final SoundEvent BOMBER_RELEASE = create("bomber.release");
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

    /** Pilot Detector pulse going out, heard by everyone near. */
    public static final SoundEvent DETECTOR_PING = create("detector.ping");
    /** Pilot Detector: someone found (the scanner only). */
    public static final SoundEvent DETECTOR_CONTACT = create("detector.contact");

    /** EMP Field Station: switched on / off, and its field's hum (loop). */
    public static final SoundEvent EMP_STATION_ON = create("emp.station_on");
    public static final SoundEvent EMP_STATION_OFF = create("emp.station_off");
    public static final SoundEvent EMP_HUM = create("emp.hum");
    /** A vehicle knocked out by an EMP. */
    public static final SoundEvent EMP_ZAP = create("emp.zap");

    /** EMP Blast Gadget: the build-up whine, and the blast. */
    public static final SoundEvent EMP_CHARGE = create("emp.charge");
    public static final SoundEvent EMP_BLAST = create("emp.blast");

    private ModSounds() {
    }

    private static SoundEvent create(String name) {
        ResourceLocation id = new ResourceLocation(CombatDrones.MOD_ID, name);
        return new SoundEvent(id).setRegistryName(id);
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().registerAll(DRONE_MOTOR, DRONE_LINK, DRONE_BEEP, DRONE_BATTERY, DRONE_ATTACH, DRONE_CONNECT, DRONE_HIT, DRONE_PILOT_STATIC, UNIT_LAUNCH,
                GUNSHIP_ROCKET, GUNSHIP_GUN, UNIT_BOOST, BOMB_RELEASE, BOMBER_RELEASE, BULLET_IMPACT, BULLET_FLYBY, MISSILE_IGNITION, MISSILE_LIFTOFF, MISSILE_SCREAM, MISSILE_ALARM,
                DRONE_PLACE, DRONE_PICKUP, UNIT_INSTALL, UNIT_REMOVE, UNIT_AMMO, PAD_LOAD, RAMP_PLACE,
                DETECTOR_PING, DETECTOR_CONTACT, EMP_STATION_ON, EMP_STATION_OFF, EMP_HUM, EMP_ZAP, EMP_CHARGE, EMP_BLAST);
    }
}
