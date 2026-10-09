package io.github.duckysmacky.combatdrones.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.physics.RouteValidator;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.BombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import io.github.duckysmacky.combatdrones.type.RocketType;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single mod config, {@code config/combat_drones.toml}. Missing entries are written back with their defaults,
 * so the file always lists every knob; values the admin changed are kept. Read at startup and again on
 * {@code /combat_drones config_reload}.
 *
 * <ul>
 *   <li>{@code [general]}: global server balance knobs.</li>
 *   <li>{@code [client]}: options for your own client only; a server ignores them. Re-read alone by
 *       {@link #reloadClient}.</li>
 *   <li>{@code [batteries.<size>]}: battery pack capacity and weight multipliers, synced to clients.</li>
 *   <li>{@code [bombs.<size>]}: bomb bay size, blast power and weight, synced to clients.</li>
 *   <li>{@code [rockets.<size>]}: rocket pod bay size and blast power, synced to clients.</li>
 *   <li>{@code [drones.<id>]}: per-type balance stats, defaulting to the bundled drone type data. The server's
 *       values are synced to clients on login.</li>
 * </ul>
 */
public final class ModConfig {
    public static final String FILE_NAME = CombatDrones.MOD_ID + ".toml";
    /** Keys from older versions, removed from existing files so they don't look like live settings. */
    private static final String[] RETIRED_KEYS = {"general.bombBlastPower", "missiles.missilePreparingSeconds", "missiles.inboundDelaySeconds",
            "drones.gunship_rocket", "drones.gunship_gun", "weapons.rocketBlastPower"};

    // [general]
    public static boolean blockDamageEnabled = true;
    public static double grenadeBlastBonus = 2.5;
    public static double droneDamageTakenMultiplier = 1.0;
    public static double racerShrapnelDamage = 6.0;
    public static double grenadeShrapnelDamage = 5.0;
    public static double shrapnelRadius = 7.0;
    public static int maxWaypointsPerRoute = 16;
    public static double maxRouteAltitude = 120.0;
    public static double maxRouteLength = 3000.0;
    public static double minLaunchFuel = 0.25;
    public static double lowFuelFraction = 0.15;
    public static double maxMarkDistance = 256.0;
    public static double designateRange = 512.0;
    public static double detectionSweepRadius = 64.0;
    public static double detectionDecaySeconds = 15.0;
    public static double activeLockRange = 192.0;
    public static double lockConeDegrees = 6.0;
    public static int activeLockHoldTicks = 30;
    public static double activeLockDurationSeconds = 60.0;
    public static boolean reconIntelForAllies = true;
    public static boolean activeLockForAllies = true;
    public static boolean alwaysShowActiveLock = false;
    public static List<String> ignoredTeams = Collections.singletonList("ffa");
    public static double boostSpeedMultiplier = 1.5;
    public static double boostFuelMultiplier = 4.0;
    public static boolean worldBorderCollision = false;
    public static boolean pilotHintEnabled = true;
    public static double pilotHintRange = 48.0;

    // [weapons]
    public static int roundsPerRifleItem = 16;
    public static double rocketSpeed = 3.0;
    public static int rocketCooldownTicks = 15;
    public static double cannonDamage = 17.0;
    public static double cannonBlastPower = 1.0;
    public static double cannonSpeed = 8.0;
    public static double cannonCooldownTicks = 10.0;
    public static double cannonSpread = 0.3;
    public static double howitzerDamage = 30.0;
    public static double howitzerBlastPower = 8.25;
    public static double howitzerSpeed = 6.0;
    public static double howitzerCooldownTicks = 80.0;
    public static double howitzerSpread = 0.2;
    public static double gunDamagePerRound = 8.0;
    public static double gunCooldownTicks = 1.0;
    public static double gunSpread = 1.5;
    public static double gunRange = 96.0;
    public static double gunHitMargin = 0.5;

    // [missiles]
    public static double missileIgnitionSeconds = 5.0;
    public static double missileLiftoffSeconds = 4.0;
    public static double missileSlowClimbSeconds = 5.0;
    public static double missileFastClimbSeconds = 20.0;
    public static double missileLiftoffSpeed = 0.1;
    public static double missileSlowClimbSpeed = 0.4;
    public static double missileFastClimbSpeed = 1.6;
    public static double strikeRange = 1000.0;
    public static double strikeHoldSeconds = 3.0;
    public static double strikeAltitude = 250.0;
    public static double steerConeDegrees = 30.0;
    public static double missileSteerRateDegrees = 1.5;
    public static double missileBoostMultiplier = 2.0;
    public static double fuelBlastPower = 6.0;
    public static double missileShrapnelDamage = 25.0;
    public static double missileShrapnelRadius = 24.0;
    public static double strikeAlertRadius = 128.0;

    // [client]
    public static boolean invertMousePitch = false;
    public static double staticIntensity = 1.0;
    public static double planeMouseSensitivity = 1.0;

    /**
     * Every knob's compiled default, captured before the first load (static initializers run in source order, so
     * this must stay below the fields). "Default: X" comments must record these, not whatever was loaded last.
     */
    private static final Map<Field, Object> COMPILED_DEFAULTS = currentValues();
    @Nullable
    private static File configDirectory;

    private ModConfig() {
    }

    private static Map<Field, Object> currentValues() {
        Map<Field, Object> values = new LinkedHashMap<>();
        for (Field field : ModConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && (field.getType().isPrimitive() || field.getType() == List.class)) {
                try {
                    values.put(field, field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return values;
    }

    private static void restore(Map<Field, Object> values) {
        for (Map.Entry<Field, Object> entry : values.entrySet()) {
            try {
                entry.getKey().set(null, entry.getValue());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Re-reads the file in game: back to compiled defaults and bundled drone stats, then load as at startup. If the
     * file can't be read (a TOML syntax error, say), everything goes back to the values in use before and the error
     * is rethrown for the caller to report. Returns the settings that changed, as "name: old -> new" (drone, battery
     * and bomb tables aren't listed).
     */
    public static List<String> reload() {
        if (configDirectory == null) {
            throw new IllegalStateException("Config was never loaded");
        }
        Map<Field, Object> previous = currentValues();
        Map<String, DroneType> previousTypes = DroneTypes.snapshot();
        try {
            restore(COMPILED_DEFAULTS);
            DroneTypes.resetToBundled();
            load(configDirectory);
        } catch (RuntimeException e) {
            restore(previous);
            DroneTypes.restore(previousTypes);
            throw e;
        }
        List<String> changes = new ArrayList<>();
        for (Map.Entry<Field, Object> now : currentValues().entrySet()) {
            Object before = previous.get(now.getKey());
            if (!now.getValue().equals(before)) {
                changes.add(now.getKey().getName() + ": " + before + " -> " + now.getValue());
            }
        }
        return changes;
    }

    /**
     * Client: re-reads only the {@code [client]} table of this client's own file. Everything else on a client comes
     * from the server it's connected to (or, in single player, from the full reload the integrated server just did).
     */
    public static void reloadClient() {
        if (configDirectory == null) {
            throw new IllegalStateException("Config was never loaded");
        }
        try (CommentedFileConfig config = open(new File(configDirectory, FILE_NAME))) {
            config.load();
            Defaults defaults = new Defaults(config);
            loadClient(defaults);
            defaults.comment("client", CLIENT_COMMENT);
            if (defaults.changed) {
                config.save();
            }
        }
    }

    public static RouteValidator.Limits routeLimits() {
        return new RouteValidator.Limits(maxWaypointsPerRoute, maxRouteAltitude, maxRouteLength);
    }

    /** Must run after {@link DroneTypes#load}, since the drone tables default to the bundled types. */
    public static void load(File configDir) {
        configDirectory = configDir;
        File file = new File(configDir, FILE_NAME);
        try (CommentedFileConfig config = open(file)) {
            config.load();
            Defaults defaults = new Defaults(config);
            // First, so retired drone tables aren't reported as unknown types
            for (String retired : RETIRED_KEYS) {
                if (config.contains(retired)) {
                    config.remove(retired);
                    defaults.changed = true;
                }
            }

            blockDamageEnabled = defaults.get("general.blockDamageEnabled", blockDamageEnabled, "Whether drone and munition explosions break blocks.");
            grenadeBlastBonus = defaults.number("general.grenadeBlastBonus", grenadeBlastBonus, 0, 20,
                    "Extra blast power per Fragmentation Grenade attached to a racer (vanilla TNT = 4).");
            racerShrapnelDamage = defaults.number("general.racerShrapnelDamage", racerShrapnelDamage, 0, 40,
                    "Shrapnel damage (half-hearts) at point blank when a racer detonates; falls off to 0 at shrapnelRadius, needs line of sight.");
            grenadeShrapnelDamage = defaults.number("general.grenadeShrapnelDamage", grenadeShrapnelDamage, 0, 40,
                    "Extra shrapnel damage per Fragmentation Grenade the racer carries.");
            shrapnelRadius = defaults.number("general.shrapnelRadius", shrapnelRadius, 0, 20, "Reach of racer shrapnel, in blocks.");
            maxWaypointsPerRoute = (int) defaults.number("general.maxWaypointsPerRoute", maxWaypointsPerRoute, 1, 64,
                    "Most waypoints a Mission Planner route may have.");
            maxRouteAltitude = defaults.number("general.maxRouteAltitude", maxRouteAltitude, 10, 250,
                    "Highest waypoint altitude, in blocks above the launch ramp.");
            maxRouteLength = defaults.number("general.maxRouteLength", maxRouteLength, 100, 20000,
                    "Longest total route (from the ramp through every waypoint), in blocks.");
            minLaunchFuel = defaults.number("general.minLaunchFuel", minLaunchFuel, 0, 1,
                    "Share of a full tank a ramp unit needs before it may launch.");
            lowFuelFraction = defaults.number("general.lowFuelFraction", lowFuelFraction, 0, 1,
                    "Fuel share at which a loitering unit stops circling: munitions dive on their last waypoint, planes fly home and land.");
            maxMarkDistance = defaults.number("general.maxMarkDistance", maxMarkDistance, 16, 1024,
                    "How far a target can be marked with the Mission Planner, in blocks.");
            designateRange = defaults.number("general.designateRange", designateRange, 16, 1024,
                    "How far a point can be clicked from a click-designate munition's camera (light munition), in blocks. Long clicks "
                            + "may load the chunks along the line on the server.");
            detectionSweepRadius = defaults.number("general.detectionSweepRadius", detectionSweepRadius, 8, 256,
                    "How far a recon UAV spots players in its line of sight, in blocks (half against sneaking players).");
            detectionDecaySeconds = defaults.number("general.detectionDecaySeconds", detectionDecaySeconds, 1, 600,
                    "How long a spotted player stays marked at their last seen position, in seconds.");
            activeLockRange = defaults.number("general.activeLockRange", activeLockRange, 8, 512,
                    "How far the recon camera can lock a player, in blocks.");
            lockConeDegrees = defaults.number("general.lockConeDegrees", lockConeDegrees, 0.5, 20,
                    "How close to the camera crosshair a player must be to start a lock, in degrees.");
            activeLockHoldTicks = (int) defaults.number("general.activeLockHoldTicks", activeLockHoldTicks, 1, 400,
                    "Ticks the crosshair must stay on a player to lock them (20 ticks = 1 s).");
            activeLockDurationSeconds = defaults.number("general.activeLockDurationSeconds", activeLockDurationSeconds, 1, 600,
                    "How long a locked player is tracked live on the owner's map and cameras, in seconds.");
            reconIntelForAllies = defaults.get("general.reconIntelForAllies", reconIntelForAllies,
                    "Share recon UAV sightings (spotted players, on the planner map and in their own cameras) with the owner's vanilla "
                            + "scoreboard teammates. Off: only the owner sees them.");
            activeLockForAllies = defaults.get("general.activeLockForAllies", activeLockForAllies,
                    "Share active locks (players locked from the recon camera, tracked live) with the owner's teammates. They see the "
                            + "locked player boxed in the world while the owner is in the UAV camera.");
            alwaysShowActiveLock = defaults.get("general.alwaysShowActiveLock", alwaysShowActiveLock,
                    "Keep locked players boxed in the world for the whole lock, even after the owner leaves the UAV camera.");
            ignoredTeams = defaults.list("general.ignoredTeams", ignoredTeams,
                    "Scoreboard teams (case-insensitive) whose members aren't teammates for recon: they spot and lock each other and "
                            + "share nothing. For free-for-all games that put everyone on one team.");
            boostSpeedMultiplier = defaults.number("general.boostSpeedMultiplier", boostSpeedMultiplier, 1, 4,
                    "Top speed multiplier while a remotely controlled loitering munition boosts (Space).");
            boostFuelMultiplier = defaults.number("general.boostFuelMultiplier", boostFuelMultiplier, 1, 20,
                    "Fuel burn multiplier while boosting.");
            worldBorderCollision = defaults.get("general.worldBorderCollision", worldBorderCollision,
                    "Whether drones and ramp units collide with (and crash into) the world border. Off: they fly through it.");
            droneDamageTakenMultiplier = defaults.number("general.droneDamageTakenMultiplier", droneDamageTakenMultiplier, 0, 10,
                    "Multiplies all damage drones take from attacks (weapons, arrows, melee). Lower = harder to shoot down.");
            pilotHintEnabled = defaults.get("general.pilotHintEnabled", pilotHintEnabled,
                    "Pilots' stand-in bodies give off radio static and a blinking red TX light, so players can find someone flying a drone or plane.");
            pilotHintRange = defaults.number("general.pilotHintRange", pilotHintRange, 8, 64,
                    "How far away a pilot's radio static can be heard, in blocks (quieter further out).");

            roundsPerRifleItem = (int) defaults.number("weapons.roundsPerRifleItem", roundsPerRifleItem, 1, 256,
                    "Gun rounds one Rifle Rounds item loads into an Attack Plane's belt.");
            rocketSpeed = defaults.number("weapons.rocketSpeed", rocketSpeed, 0.5, 10, "Rocket speed on top of the plane's own, in blocks/tick.");
            rocketCooldownTicks = (int) defaults.number("weapons.rocketCooldownTicks", rocketCooldownTicks, 1, 200, "Ticks between rockets while the trigger is held.");
            cannonDamage = defaults.number("weapons.cannonDamage", cannonDamage, 0, 40,
                    "Gunship cannon: damage (half-hearts) to whatever a shell hits directly, on top of its blast.");
            cannonBlastPower = defaults.number("weapons.cannonBlastPower", cannonBlastPower, 0, 10, "Gunship cannon shell blast (vanilla TNT = 4).");
            cannonSpeed = defaults.number("weapons.cannonSpeed", cannonSpeed, 0.5, 20, "Gunship cannon shell speed on top of the plane's own, in blocks/tick.");
            cannonCooldownTicks = defaults.number("weapons.cannonCooldownTicks", cannonCooldownTicks, 1, 200,
                    "Ticks between cannon shells while the trigger is held; fractions work.");
            cannonSpread = defaults.number("weapons.cannonSpread", cannonSpread, 0, 10, "Gunship cannon spread: random aim error per shell, in degrees.");
            howitzerDamage = defaults.number("weapons.howitzerDamage", howitzerDamage, 0, 100,
                    "Gunship 105 mm howitzer: damage (half-hearts) to whatever a shell hits directly, on top of its blast.");
            howitzerBlastPower = defaults.number("weapons.howitzerBlastPower", howitzerBlastPower, 0, 20, "Howitzer shell blast (vanilla TNT = 4).");
            howitzerSpeed = defaults.number("weapons.howitzerSpeed", howitzerSpeed, 0.5, 20, "Howitzer shell speed, in blocks/tick.");
            howitzerCooldownTicks = defaults.number("weapons.howitzerCooldownTicks", howitzerCooldownTicks, 1, 1200,
                    "Ticks between howitzer shells while the trigger is held.");
            howitzerSpread = defaults.number("weapons.howitzerSpread", howitzerSpread, 0, 10, "Howitzer spread: random aim error per shell, in degrees.");
            gunDamagePerRound = defaults.number("weapons.gunDamagePerRound", gunDamagePerRound, 0, 40, "Damage per gun round (half-hearts).");
            gunCooldownTicks = defaults.number("weapons.gunCooldownTicks", gunCooldownTicks, 1, 40,
                    "Ticks between gun rounds while the trigger is held; fractions work (1 = 20 rounds/s, 1.25 = 16 rounds/s).");
            gunSpread = defaults.number("weapons.gunSpread", gunSpread, 0, 10, "Gun spread: random aim error per round, in degrees.");
            gunRange = defaults.number("weapons.gunRange", gunRange, 8, 256, "Gun range in blocks.");
            gunHitMargin = defaults.number("weapons.gunHitMargin", gunHitMargin, 0, 2,
                    "Blocks added around targets' hitboxes for gun rounds, so fast passes still connect.");

            missileIgnitionSeconds = defaults.number("missiles.missileIgnitionSeconds", missileIgnitionSeconds, 0, 60,
                    "Cruise missile launch: seconds the engine burns on the pad before it lifts off.");
            missileLiftoffSeconds = defaults.number("missiles.missileLiftoffSeconds", missileLiftoffSeconds, 0.5, 60,
                    "Seconds of the slow rise off the pad.");
            missileSlowClimbSeconds = defaults.number("missiles.missileSlowClimbSeconds", missileSlowClimbSeconds, 0.5, 60,
                    "Seconds of the slow climb (still easy to hit).");
            missileFastClimbSeconds = defaults.number("missiles.missileFastClimbSeconds", missileFastClimbSeconds, 0.5, 60,
                    "Seconds of the fast climb, after which the missile is out of sight and ready to strike.");
            missileLiftoffSpeed = defaults.number("missiles.missileLiftoffSpeed", missileLiftoffSpeed, 0.01, 2,
                    "Climb speed reached at the end of liftoff, in blocks/tick.");
            missileSlowClimbSpeed = defaults.number("missiles.missileSlowClimbSpeed", missileSlowClimbSpeed, 0.01, 4,
                    "Climb speed reached at the end of the slow climb, in blocks/tick.");
            missileFastClimbSpeed = defaults.number("missiles.missileFastClimbSpeed", missileFastClimbSpeed, 0.01, 8,
                    "Climb speed reached at the end of the fast climb, in blocks/tick.");
            strikeRange = defaults.number("missiles.strikeRange", strikeRange, 16, 20000,
                    "How far from its pad a cruise missile can strike, in blocks.");
            strikeHoldSeconds = defaults.number("missiles.strikeHoldSeconds", strikeHoldSeconds, 0, 30,
                    "Seconds the missile hangs over the strike point, armed and aimable, before it dives (the terrain loads meanwhile).");
            strikeAltitude = defaults.number("missiles.strikeAltitude", strikeAltitude, 16, 400,
                    "Blocks above the ground at the strike point where the dive starts.");
            steerConeDegrees = defaults.number("missiles.steerConeDegrees", steerConeDegrees, 0, 60,
                    "How far from straight down the pilot can steer the dive, in degrees.");
            missileSteerRateDegrees = defaults.number("missiles.missileSteerRateDegrees", missileSteerRateDegrees, 0.1, 10,
                    "Fastest the dive can be turned, in degrees per tick.");
            missileBoostMultiplier = defaults.number("missiles.missileBoostMultiplier", missileBoostMultiplier, 1, 5,
                    "Dive speed multiplier while the pilot holds boost (Space).");
            fuelBlastPower = defaults.number("missiles.fuelBlastPower", fuelBlastPower, 0, 20,
                    "Blast when a cruise missile is shot down or crashes before its dive (its fuel; the warhead only goes off on impact "
                            + "in the dive). Vanilla TNT = 4.");
            missileShrapnelDamage = defaults.number("missiles.missileShrapnelDamage", missileShrapnelDamage, 0, 60,
                    "Shrapnel damage (half-hearts) at point blank from a warhead impact, falling off to 0 at missileShrapnelRadius.");
            missileShrapnelRadius = defaults.number("missiles.missileShrapnelRadius", missileShrapnelRadius, 0, 64,
                    "Reach of warhead shrapnel, in blocks.");
            strikeAlertRadius = defaults.number("missiles.strikeAlertRadius", strikeAlertRadius, 0, 1024,
                    "Players within this many blocks of the strike point get the INBOUND alarm when the dive starts (0 = nobody).");

            loadClient(defaults);

            for (BatteryPack pack : BatteryPack.values()) {
                String table = "batteries." + pack.key();
                pack.setMultipliers(
                        defaults.number(table + ".capacityMultiplier", pack.getDefaultCapacityMultiplier(), 0.1, 10, null),
                        defaults.number(table + ".performanceMultiplier", pack.getDefaultPerformanceMultiplier(), 0.1, 2, null));
            }

            for (BombType bomb : BombType.values()) {
                String table = "bombs." + bomb.key();
                bomb.set((int) defaults.number(table + ".bayUnits", bomb.getDefaultBayUnits(), 1, 16, null),
                        defaults.number(table + ".blastPower", bomb.getDefaultBlastPower(), 0, 20, null),
                        defaults.number(table + ".weightPerBomb", bomb.getDefaultWeight(), 0, 0.5, null));
            }

            for (RocketType rocket : RocketType.values()) {
                String table = "rockets." + rocket.key();
                rocket.set((int) defaults.number(table + ".bayUnits", rocket.getDefaultBayUnits(), 1, 16, null),
                        defaults.number(table + ".blastPower", rocket.getDefaultBlastPower(), 0, 20, null));
            }

            for (DroneType type : DroneTypes.all()) {
                applyDroneTable(defaults, type);
            }
            warnUnknownDrones(config);

            // Table comments last: a comment can only attach to a table that exists
            defaults.comment("general", "Global balance settings. On a server these apply to everyone.");
            defaults.comment("weapons", "Armed plane weapons, fired only under manual control.");
            defaults.comment("missiles", "Cruise Missile launch, standby and strike. Its speed, warhead, health and standby fuel are in "
                    + "[drones.cruise_missile].");
            defaults.comment("client", CLIENT_COMMENT);
            defaults.comment("batteries", "Battery pack sizes for hand-flown drones. capacityMultiplier scales a drone's base capacity "
                    + "(defined for a Standard pack); performanceMultiplier scales its top speed and acceleration (pack weight).");
            defaults.comment("bombs", "Bombs for DJI-style drones. bayUnits: space taken in a drone's bomb bay (its payloadCapacity); "
                    + "blastPower: vanilla scale (TNT = 4); weightPerBomb: share of top speed and acceleration lost per bomb carried.");
            defaults.comment("rockets", "Rockets for rocket pods, one size loaded at a time. bayUnits: space taken in the pod (the plane's "
                    + "payloadCapacity); blastPower: vanilla scale (TNT = 4).");
            defaults.comment("drones", "Per-drone-type stats. Units: speed in blocks/tick (1 = 72 km/h), acceleration in blocks/tick², "
                    + "capacity (with a Standard pack) and drain in charge units (full-throttle flight time = capacity / drain ticks), blastPower on the vanilla "
                    + "scale (TNT = 4), maxHP like mob health, signalRange and motorSoundRange in blocks.");

            if (defaults.changed) {
                config.save();
            }
        }
    }

    private static Object compiledDefault(String fieldName) {
        for (Map.Entry<Field, Object> entry : COMPILED_DEFAULTS.entrySet()) {
            if (entry.getKey().getName().equals(fieldName)) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("No config field " + fieldName);
    }

    private static CommentedFileConfig open(File file) {
        return CommentedFileConfig.builder(file, TomlFormat.instance()).preserveInsertionOrder().sync().build();
    }

    /**
     * Falls back on compiled defaults rather than current values: {@link #reloadClient} can't reset the shared statics
     * first, since in single player the integrated server reads them at the same time.
     */
    private static void loadClient(Defaults defaults) {
        invertMousePitch = defaults.get("client.invertMousePitch", (Boolean) compiledDefault("invertMousePitch"), "Invert vertical mouse movement when flying a drone in FPV.");
        planeMouseSensitivity = defaults.number("client.planeMouseSensitivity", (Double) compiledDefault("planeMouseSensitivity"), 0.1, 5,
                "Mouse sensitivity when flying a ramp unit by hand (1 = default).");
        staticIntensity = defaults.number("client.staticIntensity", (Double) compiledDefault("staticIntensity"), 0, 1,
                "Strength of the analog-goggle static while flying (0 = off). It grows as the signal weakens.");
    }

    private static void applyDroneTable(Defaults defaults, DroneType type) {
        String table = "drones." + type.getId();
        Map<String, Number> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, Number> stat : type.numericStats().entrySet()) {
            overrides.put(stat.getKey(), defaults.stat(table + "." + stat.getKey(), stat.getValue()));
        }
        try {
            DroneTypes.override(type.getId(), overrides);
        } catch (IllegalArgumentException e) {
            CombatDrones.logger.error("Invalid stats in [{}] of {}, using bundled values: {}", table, FILE_NAME, e.getMessage());
        }
    }

    private static void warnUnknownDrones(CommentedFileConfig config) {
        Config drones = config.get("drones");
        if (drones == null) {
            return;
        }
        for (String id : drones.valueMap().keySet()) {
            if (DroneTypes.get(id) == null) {
                CombatDrones.logger.warn("Ignoring [drones.{}] in {}: no drone type with that ID", id, FILE_NAME);
            }
        }
    }

    private static final String DEFAULT_MARK = "Default: ";
    private static final String CLIENT_COMMENT = "Options for your own client only. Apply edits with /combat_drones reload_client_config "
            + "(an op's config_reload also reloads everyone's).";

    /**
     * Whether a stored value should follow the (possibly new) default: yes if it still equals the default recorded
     * when it was written, i.e. nobody edited it. Files written before defaults were recorded have no record; for
     * those, {@code legacyFollows} decides (generated stat tables yes, hand-set options no).
     */
    static boolean followsDefault(Object stored, @Nullable String recordedDefault, boolean legacyFollows) {
        if (recordedDefault == null) {
            return legacyFollows;
        }
        return sameValue(stored, recordedDefault);
    }

    static boolean sameValue(Object stored, String text) {
        if (stored instanceof Number) {
            try {
                return Math.abs(((Number) stored).doubleValue() - Double.parseDouble(text)) < 1e-9;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return String.valueOf(stored).equals(text);
    }

    /** The default recorded at the end of an entry's comment ("... Default: 0.7"), or null. */
    @Nullable
    static String recordedDefault(@Nullable String comment) {
        if (comment == null) {
            return null;
        }
        int index = comment.lastIndexOf(DEFAULT_MARK);
        return index < 0 ? null : comment.substring(index + DEFAULT_MARK.length()).trim();
    }

    /**
     * Reads values, writing defaults (with comments) for anything missing or of the wrong type. Every entry's comment
     * records its default, so changed defaults in a mod update reach values the admin never touched.
     */
    private static final class Defaults {
        private final CommentedFileConfig config;
        boolean changed;

        Defaults(CommentedFileConfig config) {
            this.config = config;
        }

        void comment(String path, String comment) {
            if (config.getComment(path) == null) {
                config.setComment(path, comment);
                changed = true;
            }
        }

        boolean get(String path, boolean fallback, String description) {
            return (Boolean) track(path, fallback, description, Boolean.class, false);
        }

        /** A list of strings, kept unmodifiable; anything else in the list resets it to the default. */
        @SuppressWarnings("unchecked")
        List<String> list(String path, List<String> fallback, String description) {
            List<Object> value = (List<Object>) track(path, fallback, description, List.class, false);
            for (Object element : value) {
                if (!(element instanceof String)) {
                    CombatDrones.logger.error("{} in {} must list only strings, using {}", path, FILE_NAME, fallback);
                    return fallback;
                }
            }
            return Collections.unmodifiableList(new ArrayList<>((List<String>) (List<?>) value));
        }

        double number(String path, double fallback, double min, double max, @Nullable String description) {
            // Option knobs carry a description; tables of generated numbers (batteries, bombs) don't
            double number = ((Number) track(path, fallback, description, Number.class, description == null)).doubleValue();
            if (number >= min && number <= max) {
                return number;
            }
            CombatDrones.logger.error("{} = {} in {} is outside {}..{}, using {}", path, number, FILE_NAME, min, max, fallback);
            return fallback;
        }

        Number stat(String path, Number fallback) {
            // Float fields would otherwise be written as e.g. 1.600000023841858
            Number clean = fallback instanceof Float ? Double.valueOf(fallback.toString()) : fallback;
            return (Number) track(path, clean, null, Number.class, true);
        }

        private Object track(String path, Object fallback, @Nullable String description, Class<?> type, boolean legacyFollows) {
            Object value = config.get(path);
            boolean valid = type.isInstance(value);
            String recorded = recordedDefault(config.getComment(path));
            String comment = (description == null ? "" : description + " ") + DEFAULT_MARK + fallback;
            if (valid && !followsDefault(value, recorded, legacyFollows)) {
                setComment(path, comment);
                return value;
            }
            if (value != null && !valid) {
                CombatDrones.logger.error("{} in {} has the wrong type, resetting it to {}", path, FILE_NAME, fallback);
            }
            if (!valid || !sameValue(value, String.valueOf(fallback))) {
                config.set(path, fallback);
                changed = true;
            }
            setComment(path, comment);
            return fallback;
        }

        private void setComment(String path, String comment) {
            if (!comment.equals(config.getComment(path))) {
                config.setComment(path, comment);
                changed = true;
            }
        }
    }
}
