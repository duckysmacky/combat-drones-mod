package io.github.duckysmacky.combatdrones.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.physics.DownedFall;
import io.github.duckysmacky.combatdrones.physics.RouteValidator;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.AerialBombType;
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
 *   <li>{@code [uav]}: recon UAV tracking dots and lock-ons (server).</li>
 *   <li>{@code [runways]}: planes taking off from a runway (server).</li>
 *   <li>{@code [<id>]}: a hand-flown drone type's own table (shrapnel, grenade extras, lock-on), one for each such type
 *       that detonates on impact or carries a sensor (server).</li>
 *   <li>{@code [pilot_detector]}: the Pilot Detector scanner (server).</li>
 *   <li>{@code [stinger]}: the Stinger anti-air launcher and its missile (server).</li>
 *   <li>{@code [emp_station]}: the EMP Field Station (server).</li>
 *   <li>{@code [emp_blast]}: the EMP Blast Gadget (server).</li>
 *   <li>{@code [emp_gun]}: the EMP Gun and its orb (server).</li>
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
            "drones.gunship_rocket", "drones.gunship_gun", "drones.strike_plane", "drones.attack_plane", "weapons.rocketBlastPower",
            // The racers were renamed and retuned: old tables are dropped, not carried over
            "drones.racer_micro", "drones.racer_standard", "drones.racer_longrange",
            // Recon settings moved to [uav]
            "general.detectionSweepRadius", "general.detectionDecaySeconds", "general.activeLockRange", "general.lockConeDegrees",
            "general.activeLockHoldTicks", "general.activeLockDurationSeconds", "general.reconIntelForAllies", "general.activeLockForAllies",
            "general.alwaysShowActiveLock",
            // Moved back to [general] (value carried over, see MOVED_KEYS)
            "uav.ignoredTeams",
            // Moved to the drones' own tables: shrapnel values carried over; the grenade bonus default changed (2.5 -> 3), so
            // not that one
            "general.grenadeBlastBonus", "general.racerShrapnelDamage", "general.grenadeShrapnelDamage", "general.shrapnelRadius",
            // Short-lived during the drone rework: a shared [drone] table and lock-on stats in [drones.<id>]
            "drone", "drones.recon_uav.lockOnHoldSeconds", "drones.recon_uav.lockOnDurationSeconds",
            "drones.recon_drone.lockOnHoldSeconds", "drones.recon_drone.lockOnDurationSeconds",
            // Renamed (values carried over, see MOVED_KEYS)
            "missiles.missileIgnitionSeconds", "missiles.missileLiftoffSeconds", "missiles.missileSlowClimbSeconds",
            "missiles.missileFastClimbSeconds", "missiles.missileLiftoffSpeed", "missiles.missileSlowClimbSpeed", "missiles.missileFastClimbSpeed",
            "missiles.missileSteerRateDegrees", "missiles.missileBoostMultiplier", "missiles.missileShrapnelDamage",
            "missiles.missileShrapnelRadius", "emp_station.fizzlePower", "emp_blast.fizzlePower"};
    /** Keys moved to another table: {old, new}. An old value is carried over unless the new key is already set. */
    private static final String[][] MOVED_KEYS = {{"uav.ignoredTeams", "general.ignoredTeams"},
            {"general.racerShrapnelDamage", "racer_drone.shrapnelDamage"}, {"general.racerShrapnelDamage", "standard_drone.shrapnelDamage"},
            {"general.racerShrapnelDamage", "recon_drone.shrapnelDamage"}, {"general.shrapnelRadius", "racer_drone.shrapnelRadius"},
            {"general.shrapnelRadius", "standard_drone.shrapnelRadius"}, {"general.shrapnelRadius", "recon_drone.shrapnelRadius"},
            {"general.grenadeShrapnelDamage", "standard_drone.grenadeShrapnelDamage"},
            // Renamed: [missiles] lost its redundant prefix, the EMP blast keys say what they are
            {"missiles.missileIgnitionSeconds", "missiles.ignitionSeconds"},
            {"missiles.missileLiftoffSeconds", "missiles.liftoffSeconds"},
            {"missiles.missileSlowClimbSeconds", "missiles.slowClimbSeconds"},
            {"missiles.missileFastClimbSeconds", "missiles.fastClimbSeconds"},
            {"missiles.missileLiftoffSpeed", "missiles.liftoffSpeed"},
            {"missiles.missileSlowClimbSpeed", "missiles.slowClimbSpeed"},
            {"missiles.missileFastClimbSpeed", "missiles.fastClimbSpeed"},
            {"missiles.missileSteerRateDegrees", "missiles.steerRateDegrees"},
            {"missiles.missileBoostMultiplier", "missiles.boostMultiplier"},
            {"missiles.missileShrapnelDamage", "missiles.shrapnelDamage"},
            {"missiles.missileShrapnelRadius", "missiles.shrapnelRadius"},
            {"emp_station.fizzlePower", "emp_station.destroyBlastPower"}, {"emp_station.fizzlePower", "state.emped.crashBlastPower"},
            {"emp_blast.fizzlePower", "emp_blast.destroyBlastPower"}};

    // [general]
    public static boolean blockDamageEnabled = true;
    public static double droneDamageTakenMultiplier = 1.0;
    public static double launchGraceSeconds = 1.0;
    public static int maxWaypointsPerRoute = 16;
    public static double minRouteAltitude = 5.0;
    public static double maxRouteAltitude = 120.0;
    public static double minLoiterRadius = 12.0;
    public static double maxLoiterRadius = 160.0;
    public static double maxRouteLength = 3000.0;
    public static double maxCarpetLength = 256.0;
    public static double minLaunchFuel = 0.25;
    public static double lowFuelFraction = 0.15;
    public static double runwaySpoolUpSeconds = 3.0;
    public static double runwayLiftoffSpeed = 0.5;
    public static double runwayClimbOutHeight = 8.0;
    public static double maxMarkDistance = 256.0;

    public static double designateRange = 512.0;
    public static double boostSpeedMultiplier = 1.5;
    public static double boostFuelMultiplier = 4.0;
    public static boolean worldBorderCollision = false;
    public static boolean pilotHintEnabled = true;
    public static double pilotHintRange = 48.0;
    public static List<String> ignoredTeams = Collections.singletonList("ffa");

    // [uav]
    public static boolean trackingDotsEnabled = true;
    public static double trackingRange = 192.0;
    public static double trackingIntervalSeconds = 3.0;
    public static boolean trackingNeedsPilot = true;
    public static boolean trackingDotsForAllies = true;
    public static LockOnSettings uavLockOn = new LockOnSettings(5.0, 60.0);

    // [<id>] per hand-flown drone type, by type ID
    private static Map<String, DroneSettings> droneTables = Collections.emptyMap();

    // [pilot_detector]
    public static double detectorRange = 32.0;
    public static int detectorCharges = 1;
    public static double detectorReloadSeconds = 30.0;
    public static double detectorDotSeconds = 15.0;
    public static boolean detectorDotsForAllies = false;
    public static double detectorPingIntervalSeconds = 0.0;

    // [emp_station]
    public static double stationRadius = 24.0;
    public static double stationDischargePerSecond = 4.0;
    public static double stationRechargePerSecond = 2.0;
    public static String stationSpares = "owner";
    public static String stationOnContact = "disable";
    public static double stationDisableSeconds = 5.0;
    public static double stationDestroyBlastPower = 5.0;

    // [emp_blast]
    public static double blastRadius = 18.0;
    public static int blastCharges = 1;
    public static double blastReloadSeconds = 180.0;
    public static boolean blastBuildUp = true;
    public static double blastBuildUpSeconds = 1.0;
    public static double blastExpandSeconds = 2.0;
    public static String blastSpares = "owner";
    public static String blastOnContact = "disable";
    public static double blastDisableSeconds = 5.0;
    public static double blastDestroyBlastPower = 5.0;

    // [state.shot_down]
    public static boolean shotDownPlanes = true;
    public static boolean shotDownDrones = false;
    public static boolean shotDownMunitions = false;
    public static double shotDownFallingHP = 40.0;
    public static double shotDownGraceSeconds = 0.5;
    public static double shotDownDrag = 0.08;
    public static double shotDownFallAcceleration = 4.0;
    public static double shotDownMaxFallSpeed = 25.0;
    public static double shotDownRollDegreesPerSecond = 90.0;
    public static double shotDownTurnDegreesPerSecond = 12.0;

    // [state.emped]
    public static boolean empedPlanes = true;
    public static boolean empedDrones = true;
    public static boolean empedMunitions = true;
    public static boolean empedMissiles = true;
    public static double empedDrag = 0.1;
    public static double empedFallAcceleration = 3.0;
    public static double empedMaxFallSpeed = 12.0;
    public static double empedCrashBlastPower = 5.0;

    // [emp_gun]
    public static double gunRadius = 12.0;
    public static String gunFieldMode = "travelling";
    public static double gunTravellingExpandSeconds = 2.5;
    public static double gunBurstExpandSeconds = 0.5;
    public static double gunChargeSeconds = 3.0;
    public static double gunChargeSlowdown = 0.5;
    public static double gunProjectileSpeed = 1.25;
    public static double gunMaxRange = 128.0;
    public static double gunReloadSeconds = 3.0;
    public static String gunSpares = "team";
    public static String gunOnContact = "disable";
    public static double gunDisableSeconds = 5.0;
    public static double gunDestroyBlastPower = 5.0;

    // [stinger]
    public static double stingerDamage = 100.0;
    public static double stingerBlastPower = 1.5;
    public static double stingerLockRange = 64.0;
    public static double stingerLockSeconds = 6.0;
    public static double stingerLockConeDegrees = 6.0;
    public static double stingerMissileSpeed = 3.0;
    public static double stingerTurnDegreesPerTick = 12.0;
    public static double stingerFuelSeconds = 10.0;
    public static double stingerReloadSeconds = 3.0;
    public static boolean stingerAllowDumbFire = false;
    public static String stingerSpares = "team";
    public static boolean stingerWarnTarget = true;

    // [weapons]
    public static int roundsPerRifleItem = 16;
    public static double rocketSpeed = 3.0;
    public static int rocketCooldownTicks = 15;
    public static double cannonDamage = 17.0;
    public static double cannonBlastPower = 1.0;
    public static double cannonSpeed = 8.0;
    public static double cannonCooldownTicks = 15.0;
    public static double cannonSpread = 0.3;
    public static double howitzerDamage = 30.0;
    public static double howitzerBlastPower = 8.25;
    public static double howitzerSpeed = 6.0;
    public static double howitzerCooldownTicks = 160.0;
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
    public static int maxEngineVoices = 4;
    public static int maxProjectileSounds = 6;

    /**
     * Every knob's compiled default, captured before the first load (static initializers run in source order, so
     * this must stay below the fields). "Default: X" comments must record these, not whatever was loaded last.
     */
    private static final Map<Field, Object> COMPILED_DEFAULTS = currentValues();
    private static final DroneSettings DEFAULT_DRONE_TABLE = new DroneSettings();
    @Nullable
    private static File configDirectory;

    private ModConfig() {
    }

    private static Map<Field, Object> currentValues() {
        Map<Field, Object> values = new LinkedHashMap<>();
        for (Field field : ModConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            Class<?> type = field.getType();
            if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)
                    && (type.isPrimitive() || type == List.class || type == String.class || type == Map.class || type == LockOnSettings.class)) {
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
     * is rethrown for the caller to report. Returns the settings that changed, as "name: old -> new" ([drones.<id>],
     * battery and bomb tables aren't listed).
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
            describeChanges(now.getKey().getName(), previous.get(now.getKey()), now.getValue(), changes);
        }
        return changes;
    }

    /** Adds "name: old -> new" if the value changed; per-drone tables and settings objects are compared key by key. */
    static void describeChanges(String name, @Nullable Object before, Object now, List<String> changes) {
        if (now instanceof Map) {
            Map<?, ?> was = before instanceof Map ? (Map<?, ?>) before : Collections.emptyMap();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) now).entrySet()) {
                describeChanges(String.valueOf(entry.getKey()), was.get(entry.getKey()), entry.getValue(), changes);
            }
        } else if (now instanceof LockOnSettings || now instanceof DroneSettings) {
            for (Field field : now.getClass().getFields()) {
                try {
                    describeChanges(name + "." + field.getName(), before == null ? null : field.get(before), field.get(now), changes);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        } else if (!now.equals(before)) {
            changes.add(name + ": " + before + " -> " + now);
        }
    }

    /** Whether a vehicle of this kind shot to 0 HP goes down burning ({@code [state.shot_down]}) instead of exploding at once. */
    public static boolean isShotDownEnabled(DroneType.Kind kind) {
        switch (kind) {
            case PLANE:
                return shotDownPlanes;
            case DRONE:
                return shotDownDrones;
            case MUNITION:
                return shotDownMunitions;
            default:
                return false; // the cruise missile has no going-down state
        }
    }

    /** Whether a vehicle of this kind is EMP-disabled by a disable-mode field ({@code [state.emped]}) rather than destroyed. */
    public static boolean isEmpedEnabled(DroneType.Kind kind) {
        switch (kind) {
            case PLANE:
                return empedPlanes;
            case DRONE:
                return empedDrones;
            case MUNITION:
                return empedMunitions;
            default:
                return empedMissiles;
        }
    }

    /** How an EMP-disabled vehicle falls: no roll, no spiral. */
    public static DownedFall.Params empedFall() {
        return DownedFall.Params.perSecond(empedDrag, empedFallAcceleration, empedMaxFallSpeed, 0, 0);
    }

    /** The fall settings clients need to predict a hand-flown drone going down, as synced by {@code TypeSyncPacket}. */
    public static double[] syncedFall() {
        return new double[]{shotDownDrag, shotDownFallAcceleration, shotDownMaxFallSpeed, shotDownRollDegreesPerSecond,
                shotDownTurnDegreesPerSecond, empedDrag, empedFallAcceleration, empedMaxFallSpeed};
    }

    /** Client: adopts the server's {@link #syncedFall}, kept inside the config bounds. */
    public static void applySyncedFall(double[] fall) {
        shotDownDrag = clamp(fall[0], 0, 0.99);
        shotDownFallAcceleration = clamp(fall[1], 0.5, 40);
        shotDownMaxFallSpeed = clamp(fall[2], 2, 80);
        shotDownRollDegreesPerSecond = clamp(fall[3], 0, 1440);
        shotDownTurnDegreesPerSecond = clamp(fall[4], 0, 180);
        empedDrag = clamp(fall[5], 0, 0.99);
        empedFallAcceleration = clamp(fall[6], 0.5, 40);
        empedMaxFallSpeed = clamp(fall[7], 2, 80);
    }

    private static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }

    /** How a shot-down vehicle falls. */
    public static DownedFall.Params shotDownFall() {
        return DownedFall.Params.perSecond(shotDownDrag, shotDownFallAcceleration, shotDownMaxFallSpeed, shotDownRollDegreesPerSecond,
                shotDownTurnDegreesPerSecond);
    }

    /** A hand-flown drone type's own table; compiled defaults for a type without one. */
    public static DroneSettings droneSettings(@Nullable DroneType type) {
        DroneSettings settings = type == null ? null : droneTables.get(type.getId());
        return settings != null ? settings : DEFAULT_DRONE_TABLE;
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
        return new RouteValidator.Limits(maxWaypointsPerRoute, minRouteAltitude, maxRouteAltitude, maxRouteLength, minLoiterRadius, maxLoiterRadius);
    }

    /** Must run after {@link DroneTypes#load}, since the drone tables default to the bundled types. */
    public static void load(File configDir) {
        configDirectory = configDir;
        File file = new File(configDir, FILE_NAME);
        try (CommentedFileConfig config = open(file)) {
            config.load();
            Defaults defaults = new Defaults(config);
            for (String[] moved : MOVED_KEYS) {
                if (config.contains(moved[0]) && !config.contains(moved[1])) {
                    config.set(moved[1], config.get(moved[0]));
                    config.setComment(moved[1], config.getComment(moved[0]));
                    defaults.changed = true;
                }
            }
            // First, so retired drone tables aren't reported as unknown types
            for (String retired : RETIRED_KEYS) {
                if (config.contains(retired)) {
                    config.remove(retired);
                    defaults.changed = true;
                }
            }

            blockDamageEnabled = defaults.get("general.blockDamageEnabled", blockDamageEnabled, "Whether drone and munition explosions break blocks.");
            maxWaypointsPerRoute = (int) defaults.number("general.maxWaypointsPerRoute", maxWaypointsPerRoute, 1, 64,
                    "Most waypoints a Mission Planner route may have.");
            maxRouteAltitude = defaults.number("general.maxRouteAltitude", maxRouteAltitude, 10, 250,
                    "Highest waypoint altitude, in blocks above the launch ramp.");
            minRouteAltitude = Math.min(maxRouteAltitude, defaults.number("general.minRouteAltitude", minRouteAltitude, 0, 250,
                    "Lowest waypoint altitude, in blocks above the launch ramp (at most maxRouteAltitude). A unit type's "
                            + "minLoiterAltitude raises its orbit further."));
            minLoiterRadius = defaults.number("general.minLoiterRadius", minLoiterRadius, 1, 512,
                    "Tightest loiter radius, in blocks. A unit type's minLoiterRadius can raise it for that type, never lower it.");
            maxLoiterRadius = Math.max(minLoiterRadius, defaults.number("general.maxLoiterRadius", maxLoiterRadius, 1, 512,
                    "Widest loiter radius, in blocks (at least minLoiterRadius). Keep it above every unit type's minLoiterRadius, "
                            + "or that type's orbiting routes are refused."));
            maxRouteLength = defaults.number("general.maxRouteLength", maxRouteLength, 100, 20000,
                    "Longest total route (from the ramp through every waypoint), in blocks.");
            maxCarpetLength = defaults.number("general.maxCarpetLength", maxCarpetLength, 16, 2000,
                    "Longest carpet run (blocks) a bomber's drop point may be given in the Mission Planner.");
            minLaunchFuel = defaults.number("general.minLaunchFuel", minLaunchFuel, 0, 1,
                    "Share of a full tank a ramp unit needs before it may launch.");
            lowFuelFraction = defaults.number("general.lowFuelFraction", lowFuelFraction, 0, 1,
                    "Fuel share at which a loitering unit stops circling: munitions dive on their last waypoint, planes fly home and land.");
            maxMarkDistance = defaults.number("general.maxMarkDistance", maxMarkDistance, 16, 1024,
                    "How far a target can be marked with the Mission Planner, in blocks.");
            designateRange = defaults.number("general.designateRange", designateRange, 16, 1024,
                    "How far a point can be clicked from a click-designate munition's camera (light munition), in blocks. Long clicks "
                            + "may load the chunks along the line on the server.");
            boostSpeedMultiplier = defaults.number("general.boostSpeedMultiplier", boostSpeedMultiplier, 1, 4,
                    "Top speed multiplier while a remotely controlled loitering munition boosts (Space).");
            boostFuelMultiplier = defaults.number("general.boostFuelMultiplier", boostFuelMultiplier, 1, 20,
                    "Fuel burn multiplier while boosting.");
            worldBorderCollision = defaults.get("general.worldBorderCollision", worldBorderCollision,
                    "Whether drones and ramp units collide with (and crash into) the world border. Off: they fly through it.");
            launchGraceSeconds = defaults.number("general.launchGraceSeconds", launchGraceSeconds, 0, 5,
                    "Seconds after leaving a ramp (or lifting off a runway) during which a ramp unit can't crash or go off on "
                            + "contact, so the ramp, the ground or people around it can't set it off.");
            droneDamageTakenMultiplier = defaults.number("general.droneDamageTakenMultiplier", droneDamageTakenMultiplier, 0, 10,
                    "Multiplies all damage drones take from attacks (weapons, arrows, melee). Lower = harder to shoot down.");
            pilotHintEnabled = defaults.get("general.pilotHintEnabled", pilotHintEnabled,
                    "Pilots' stand-in bodies give off radio static and a blinking red TX light, so players can find someone flying a drone or plane.");
            pilotHintRange = defaults.number("general.pilotHintRange", pilotHintRange, 8, 64,
                    "How far away a pilot's radio static can be heard, in blocks (quieter further out).");
            ignoredTeams = defaults.list("general.ignoredTeams", ignoredTeams,
                    "Scoreboard teams (case-insensitive) whose members aren't teammates: recon UAVs track and lock them, the Pilot "
                            + "Detector finds them, EMPs that spare teammates still hit them, and nothing is shared. For free-for-all games "
                            + "that put everyone on one team.");

            runwaySpoolUpSeconds = defaults.number("runways.spoolUpSeconds", runwaySpoolUpSeconds, 0, 30,
                    "How long a plane runs its engine up on the spot before its takeoff roll, in seconds.");
            runwayLiftoffSpeed = defaults.number("runways.liftoffSpeed", runwayLiftoffSpeed, 0.45, 1,
                    "Speed a plane lifts off at, as a share of its top speed (it stalls under 0.4). The roll is sized to reach it "
                            + "1 block before the runway's far end, so higher means a harder, faster takeoff run.");
            runwayClimbOutHeight = defaults.number("runways.climbOutHeight", runwayClimbOutHeight, 2, 64,
                    "How high over the runway a plane climbs straight ahead before it turns onto its route, in blocks.");

            trackingDotsEnabled = defaults.get("uav.trackingDotsEnabled", trackingDotsEnabled,
                    "Enemies a flying recon UAV can see get a red dot above them, visible through walls to its owner (and teammates, "
                            + "see trackingDotsForAllies). Walking out of the UAV's sight hides the dot at the next update.");
            trackingRange = defaults.number("uav.trackingRange", trackingRange, 8, 512,
                    "How far a recon UAV tracks enemies with dots, in blocks (half against sneaking players).");
            trackingIntervalSeconds = defaults.number("uav.trackingIntervalSeconds", trackingIntervalSeconds, 0, 60,
                    "How often tracking dots update, in seconds. Between updates a dot stays where its target was. 0 = live.");
            trackingNeedsPilot = defaults.get("uav.trackingNeedsPilot", trackingNeedsPilot,
                    "Tracking dots only while the owner watches through the UAV camera, and only for enemies on screen. Off: any "
                            + "enemy in the UAV's sight gets a dot, watched or not.");
            trackingDotsForAllies = defaults.get("uav.trackingDotsForAllies", trackingDotsForAllies,
                    "Owner's vanilla scoreboard teammates also see the tracking dots (and the planner map dots). Off: only the owner.");
            uavLockOn = loadLockOn(defaults, "uav", "UAV", uavLockOn);

            Map<String, DroneSettings> tables = new LinkedHashMap<>();
            for (DroneType type : DroneTypes.all()) {
                if (type.getCategory() == DroneType.Category.HAND_FLOWN && (type.isImpactDetonates() || type.isSensor())) {
                    tables.put(type.getId(), loadDroneTable(defaults, type));
                }
            }
            droneTables = tables;

            detectorRange = defaults.number("pilot_detector.range", detectorRange, 4, 256,
                    "How far a scan reaches, in blocks: pilots' stand-in bodies within it get a dot.");
            detectorCharges = (int) defaults.number("pilot_detector.charges", detectorCharges, 1, 20,
                    "Scans the detector holds. Each scan uses one.");
            detectorReloadSeconds = defaults.number("pilot_detector.reloadSeconds", detectorReloadSeconds, 0, 3600,
                    "Seconds to win back one spent scan (one at a time).");
            detectorDotSeconds = defaults.number("pilot_detector.dotSeconds", detectorDotSeconds, 1, 600,
                    "How long a found pilot keeps the red dot over their body, in seconds. It follows the body and shows through walls.");
            detectorDotsForAllies = defaults.get("pilot_detector.dotsForAllies", detectorDotsForAllies,
                    "The scanning player's vanilla scoreboard teammates (see general.ignoredTeams) also see the dots. Off: only the scanner.");
            detectorPingIntervalSeconds = defaults.number("pilot_detector.pingIntervalSeconds", detectorPingIntervalSeconds, 0, 60,
                    "How often a scan's dots update, in seconds. Between updates a dot stays where its body was. 0 = live.");

            stationRadius = defaults.number("emp_station.radius", stationRadius, 1, 32,
                    "Radius of the station's EMP field, in blocks. Drones, units and missiles flying into it are knocked out as if shot down.");
            stationDischargePerSecond = defaults.number("emp_station.dischargePerSecond", stationDischargePerSecond, 0, 100,
                    "Charge (of 100) the station uses per second while its field is up. It switches itself off when empty.");
            stationRechargePerSecond = defaults.number("emp_station.rechargePerSecond", stationRechargePerSecond, 0, 100,
                    "Charge (of 100) the station wins back per second while switched off.");
            stationSpares = defaults.choice("emp_station.spares", stationSpares, SPARES,
                    "Whose vehicles the field leaves alone: none, owner (whoever placed the station) or team (the owner and their "
                            + "scoreboard teammates, see general.ignoredTeams).");
            stationOnContact = defaults.choice("emp_station.onContact", stationOnContact, ON_CONTACT,
                    "What the field does to a flying vehicle it touches: destroy (it's knocked out with only a destroyBlastPower blast: "
                            + "no warhead, bombs or fuel going off) or disable (for disableSeconds its motors cut out and it glides down out "
                            + "of control, see [state.emped]).");
            stationDisableSeconds = defaults.number("emp_station.disableSeconds", stationDisableSeconds, 0.5, 120,
                    "How long a vehicle stays disabled (onContact = disable), in seconds, counted from when it last touched the field.");
            stationDestroyBlastPower = defaults.number("emp_station.destroyBlastPower", stationDestroyBlastPower, 0, 10,
                    "Blast power of a vehicle the field destroys (onContact = destroy, or a kind [state.emped] leaves out). Vanilla TNT = 4; "
                            + "0 = no blast, just a wreck.");

            blastRadius = defaults.number("emp_blast.radius", blastRadius, 1, 32, "Radius the EMP blast grows to, in blocks.");
            blastCharges = (int) defaults.number("emp_blast.charges", blastCharges, 1, 20, "Blasts the gadget holds. Each use takes one.");
            blastReloadSeconds = defaults.number("emp_blast.reloadSeconds", blastReloadSeconds, 0, 3600,
                    "Seconds to win back one spent blast (one at a time).");
            blastBuildUp = defaults.get("emp_blast.buildUp", blastBuildUp,
                    "The gadget charges up (crackling, a rising whine) for buildUpSeconds before the blast. Off: it goes off at once.");
            blastBuildUpSeconds = defaults.number("emp_blast.buildUpSeconds", blastBuildUpSeconds, 0.5, 3,
                    "How long the build-up takes, in seconds. The field forms where the user stands when it ends.");
            blastExpandSeconds = defaults.number("emp_blast.expandSeconds", blastExpandSeconds, 0.25, 10,
                    "How long the field takes to grow from a block across to its full radius, in seconds. It then fades out.");
            blastSpares = defaults.choice("emp_blast.spares", blastSpares, SPARES,
                    "Whose vehicles the blast leaves alone: none, owner (the user) or team (the user and their scoreboard teammates, "
                            + "see general.ignoredTeams).");
            blastOnContact = defaults.choice("emp_blast.onContact", blastOnContact, ON_CONTACT,
                    "What the blast does to a flying vehicle it touches: destroy (it's knocked out with only a destroyBlastPower blast: "
                            + "no warhead, bombs or fuel going off) or disable (for disableSeconds its motors cut out and it glides down out "
                            + "of control, see [state.emped]).");
            blastDisableSeconds = defaults.number("emp_blast.disableSeconds", blastDisableSeconds, 0.5, 120,
                    "How long a vehicle stays disabled (onContact = disable), in seconds, counted from when it last touched the blast.");
            blastDestroyBlastPower = defaults.number("emp_blast.destroyBlastPower", blastDestroyBlastPower, 0, 10,
                    "Blast power of a vehicle the blast destroys (onContact = destroy, or a kind [state.emped] leaves out). Vanilla TNT = 4; "
                            + "0 = no blast, just a wreck.");

            shotDownPlanes = defaults.get("state.shot_down.planes", shotDownPlanes,
                    "Planes (Striker, Spitfire, Gunship, Stealth Bomber, Recon UAV) shot to 0 HP go down burning instead of "
                            + "exploding at once. Off: they explode on the spot.");
            shotDownDrones = defaults.get("state.shot_down.drones", shotDownDrones,
                    "The same for hand-flown drones (FPV and DJI). Off: they explode (or wreck) on the spot.");
            shotDownMunitions = defaults.get("state.shot_down.munitions", shotDownMunitions,
                    "The same for loitering munitions. Off: their warhead goes off on the spot.");
            shotDownFallingHP = defaults.number("state.shot_down.fallingHP", shotDownFallingHP, 1, 1000,
                    "Health a vehicle going down has left. Shot to 0 again, it blows up in the air before it reaches the ground.");
            shotDownGraceSeconds = defaults.number("state.shot_down.graceSeconds", shotDownGraceSeconds, 0, 5,
                    "Seconds a vehicle that just went down ignores further damage, so the blast or the rest of a salvo that downed it "
                            + "doesn't finish it off in the same moment.");
            shotDownDrag = defaults.number("state.shot_down.drag", shotDownDrag, 0, 0.99,
                    "Share of its forward speed a vehicle going down loses per second. Higher = a shorter, steeper arc.");
            shotDownFallAcceleration = defaults.number("state.shot_down.fallAcceleration", shotDownFallAcceleration, 0.5, 40,
                    "How fast a vehicle going down picks up its fall, in blocks/s^2.");
            shotDownMaxFallSpeed = defaults.number("state.shot_down.maxFallSpeed", shotDownMaxFallSpeed, 2, 80,
                    "Fastest a vehicle going down falls, in blocks/s.");
            shotDownRollDegreesPerSecond = defaults.number("state.shot_down.rollDegreesPerSecond", shotDownRollDegreesPerSecond, 0, 1440,
                    "How fast a plane going down rolls over, in degrees/s (0 = no roll). The pilot's camera rolls with it.");
            shotDownTurnDegreesPerSecond = defaults.number("state.shot_down.turnDegreesPerSecond", shotDownTurnDegreesPerSecond, 0, 180,
                    "How fast its path curls into a spiral, in degrees/s (0 = straight on).");

            empedPlanes = defaults.get("state.emped.planes", empedPlanes,
                    "Planes an EMP field disables (onContact = disable) glide down out of control and recover when it wears off. Off: "
                            + "the field destroys them instead (destroyBlastPower).");
            empedDrones = defaults.get("state.emped.drones", empedDrones, "The same for hand-flown drones.");
            empedMunitions = defaults.get("state.emped.munitions", empedMunitions, "The same for loitering munitions.");
            empedMissiles = defaults.get("state.emped.missiles", empedMissiles, "The same for the cruise missile.");
            empedDrag = defaults.number("state.emped.drag", empedDrag, 0, 0.99,
                    "Share of its forward speed an EMP-disabled vehicle loses per second.");
            empedFallAcceleration = defaults.number("state.emped.fallAcceleration", empedFallAcceleration, 0.5, 40,
                    "How fast an EMP-disabled vehicle picks up its fall, in blocks/s^2.");
            empedMaxFallSpeed = defaults.number("state.emped.maxFallSpeed", empedMaxFallSpeed, 2, 80,
                    "Fastest an EMP-disabled vehicle falls, in blocks/s.");
            empedCrashBlastPower = defaults.number("state.emped.crashBlastPower", empedCrashBlastPower, 0, 10,
                    "Blast of an EMP-disabled vehicle hitting anything (its warhead, bombs and fuel stay dead; a cruise missile's warhead "
                            + "still goes off). Vanilla TNT = 4; 0 = no blast, just a wreck.");

            gunRadius = defaults.number("emp_gun.radius", gunRadius, 1, 32, "Radius of the orb's EMP field, in blocks.");
            gunFieldMode = defaults.choice("emp_gun.fieldMode", gunFieldMode, FIELD_MODES,
                    "travelling: the orb carries its field the whole flight, knocking out vehicles it passes, and fades where it stops. "
                            + "burst: only the orb's core flies; it bursts into the field (growing over burstExpandSeconds) when it hits a block, "
                            + "touches a flying vehicle or reaches maxRange.");
            gunTravellingExpandSeconds = defaults.number("emp_gun.travellingExpandSeconds", gunTravellingExpandSeconds, 0, 10,
                    "fieldMode = travelling: how long the orb's field takes to grow from the core to its full radius after the shot, "
                            + "in seconds (fast at first, easing in). 0 = full size at once.");
            gunBurstExpandSeconds = defaults.number("emp_gun.burstExpandSeconds", gunBurstExpandSeconds, 0.1, 5,
                    "fieldMode = burst: how long the burst takes to grow to its full radius, in seconds. It then fades out.");
            gunChargeSeconds = defaults.number("emp_gun.chargeSeconds", gunChargeSeconds, 0.25, 5,
                    "How long left-click must be held before the gun is charged, in seconds. Releasing it then fires; releasing "
                            + "earlier cancels the shot without using the cell.");
            gunChargeSlowdown = defaults.number("emp_gun.chargeSlowdown", gunChargeSlowdown, 0, 1,
                    "Share of walking speed the shooter loses while charging or holding a charge (0 = none, 1 = can't walk).");
            gunProjectileSpeed = defaults.number("emp_gun.projectileSpeed", gunProjectileSpeed, 0.25, 5,
                    "Orb speed, in blocks/tick. It flies dead straight, so fast aircraft have to be led.");
            gunMaxRange = defaults.number("emp_gun.maxRange", gunMaxRange, 8, 256,
                    "How far the orb flies before it fades out (travelling) or bursts (burst), in blocks.");
            gunReloadSeconds = defaults.number("emp_gun.reloadSeconds", gunReloadSeconds, 0, 60,
                    "How long loading an EMP Cell into the gun takes, in seconds.");
            gunSpares = defaults.choice("emp_gun.spares", gunSpares, SPARES,
                    "Whose vehicles the field leaves alone: none, owner (the shooter) or team (the shooter and their scoreboard "
                            + "teammates, see general.ignoredTeams).");
            gunOnContact = defaults.choice("emp_gun.onContact", gunOnContact, ON_CONTACT,
                    "What the field does to a flying vehicle it touches: destroy (it's knocked out with only a destroyBlastPower blast: "
                            + "no warhead, bombs or fuel going off) or disable (for disableSeconds its motors cut out and it glides down out "
                            + "of control, see [state.emped]). Ramps and runways it touches can't launch for disableSeconds either way.");
            gunDisableSeconds = defaults.number("emp_gun.disableSeconds", gunDisableSeconds, 0.5, 120,
                    "How long a vehicle stays disabled (onContact = disable), in seconds, counted from when it last touched the field.");
            gunDestroyBlastPower = defaults.number("emp_gun.destroyBlastPower", gunDestroyBlastPower, 0, 10,
                    "Blast power of a vehicle the field destroys (onContact = destroy, or a kind [state.emped] leaves out). Vanilla TNT = 4; "
                            + "0 = no blast, just a wreck.");

            stingerDamage = defaults.number("stinger.damage", stingerDamage, 0, 1000,
                    "Damage a Stinger missile does to the aircraft it hits, on top of its blast (compare the maxHP of [drones.*]; "
                            + "general.droneDamageTakenMultiplier applies). Only aircraft take it.");
            stingerBlastPower = defaults.number("stinger.blastPower", stingerBlastPower, 0, 10, "Stinger missile blast (vanilla TNT = 4).");
            stingerLockRange = defaults.number("stinger.lockRange", stingerLockRange, 8, 256, "How far the Stinger can lock on, in blocks.");
            stingerLockSeconds = defaults.number("stinger.lockSeconds", stingerLockSeconds, 0.5, 30,
                    "How long an aircraft has to stay inside the seeker circle of the scope to be locked, in seconds.");
            stingerLockConeDegrees = defaults.number("stinger.lockConeDegrees", stingerLockConeDegrees, 1, 20,
                    "Size of the seeker circle: how far from the middle of the scope the aircraft may be, in degrees.");
            stingerMissileSpeed = defaults.number("stinger.missileSpeed", stingerMissileSpeed, 0.5, 10,
                    "Stinger missile speed, in blocks/tick. Slower than an aircraft's maxSpeed and it can be outrun.");
            stingerTurnDegreesPerTick = defaults.number("stinger.turnDegreesPerTick", stingerTurnDegreesPerTick, 1, 90,
                    "How hard the missile can turn after its target, in degrees per tick.");
            stingerFuelSeconds = defaults.number("stinger.fuelSeconds", stingerFuelSeconds, 1, 60,
                    "How long the missile flies, in seconds, before it blows up in the air.");
            stingerReloadSeconds = defaults.number("stinger.reloadSeconds", stingerReloadSeconds, 0, 60,
                    "How long loading a Stinger Missile into the launcher takes, in seconds.");
            stingerAllowDumbFire = defaults.get("stinger.allowDumbFire", stingerAllowDumbFire,
                    "The launcher also fires without a lock: the missile then flies straight ahead, unguided.");
            stingerSpares = defaults.choice("stinger.spares", stingerSpares, SPARES,
                    "Whose aircraft the launcher refuses to lock on to: none, owner (the shooter's own) or team (the shooter's and "
                            + "their scoreboard teammates', see general.ignoredTeams).");
            stingerWarnTarget = defaults.get("stinger.warnTarget", stingerWarnTarget,
                    "Whoever flies, watches or owns an aircraft being locked gets a warning while the lock builds and holds.");

            roundsPerRifleItem = (int) defaults.number("weapons.roundsPerRifleItem", roundsPerRifleItem, 1, 256,
                    "Gun rounds one Rifle Rounds item loads into a Spitfire's belt.");
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

            missileIgnitionSeconds = defaults.number("missiles.ignitionSeconds", missileIgnitionSeconds, 0, 60,
                    "Cruise missile launch: seconds the engine burns on the pad before it lifts off.");
            missileLiftoffSeconds = defaults.number("missiles.liftoffSeconds", missileLiftoffSeconds, 0.5, 60,
                    "Seconds of the slow rise off the pad.");
            missileSlowClimbSeconds = defaults.number("missiles.slowClimbSeconds", missileSlowClimbSeconds, 0.5, 60,
                    "Seconds of the slow climb (still easy to hit).");
            missileFastClimbSeconds = defaults.number("missiles.fastClimbSeconds", missileFastClimbSeconds, 0.5, 60,
                    "Seconds of the fast climb, after which the missile is out of sight and ready to strike.");
            missileLiftoffSpeed = defaults.number("missiles.liftoffSpeed", missileLiftoffSpeed, 0.01, 2,
                    "Climb speed reached at the end of liftoff, in blocks/tick.");
            missileSlowClimbSpeed = defaults.number("missiles.slowClimbSpeed", missileSlowClimbSpeed, 0.01, 4,
                    "Climb speed reached at the end of the slow climb, in blocks/tick.");
            missileFastClimbSpeed = defaults.number("missiles.fastClimbSpeed", missileFastClimbSpeed, 0.01, 8,
                    "Climb speed reached at the end of the fast climb, in blocks/tick.");
            strikeRange = defaults.number("missiles.strikeRange", strikeRange, 16, 20000,
                    "How far from its pad a cruise missile can strike, in blocks.");
            strikeHoldSeconds = defaults.number("missiles.strikeHoldSeconds", strikeHoldSeconds, 0, 30,
                    "Seconds the missile hangs over the strike point, armed and aimable, before it dives (the terrain loads meanwhile).");
            strikeAltitude = defaults.number("missiles.strikeAltitude", strikeAltitude, 16, 400,
                    "Blocks above the ground at the strike point where the dive starts.");
            steerConeDegrees = defaults.number("missiles.steerConeDegrees", steerConeDegrees, 0, 60,
                    "How far from straight down the pilot can steer the dive, in degrees.");
            missileSteerRateDegrees = defaults.number("missiles.steerRateDegrees", missileSteerRateDegrees, 0.1, 10,
                    "Fastest the dive can be turned, in degrees per tick.");
            missileBoostMultiplier = defaults.number("missiles.boostMultiplier", missileBoostMultiplier, 1, 5,
                    "Dive speed multiplier while the pilot holds boost (Space).");
            fuelBlastPower = defaults.number("missiles.fuelBlastPower", fuelBlastPower, 0, 20,
                    "Blast when a cruise missile is shot down or crashes before its dive (its fuel; the warhead only goes off on impact "
                            + "in the dive). Vanilla TNT = 4.");
            missileShrapnelDamage = defaults.number("missiles.shrapnelDamage", missileShrapnelDamage, 0, 60,
                    "Shrapnel damage (half-hearts) at point blank from a warhead impact, falling off to 0 at shrapnelRadius.");
            missileShrapnelRadius = defaults.number("missiles.shrapnelRadius", missileShrapnelRadius, 0, 64,
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

            for (AerialBombType bomb : AerialBombType.values()) {
                String table = "aerial_bombs." + bomb.key();
                bomb.set((int) defaults.number(table + ".bayUnits", bomb.getDefaultBayUnits(), 1, 16, null),
                        defaults.number(table + ".blastPower", bomb.getDefaultBlastPower(), 0, 20, null),
                        defaults.number(table + ".cooldownTicks", bomb.getDefaultCooldownTicks(), 1, 1200, null),
                        defaults.number(table + ".carpetIntervalTicks", bomb.getDefaultCarpetIntervalTicks(), 1, 1200, null));
            }

            for (DroneType type : DroneTypes.all()) {
                applyDroneTable(defaults, type);
            }
            warnUnknownDrones(config);

            // Table comments last: a comment can only attach to a table that exists
            defaults.comment("general", "Global balance settings. On a server these apply to everyone.");
            defaults.comment("uav", "Recon UAV: tracking dots over the enemies it sees, and lock-ons from its camera.");
            defaults.comment("pilot_detector", "Pilot Detector: a hand-held scanner whose pulse marks the stand-in bodies of players "
                    + "flying a drone, unit or missile nearby. Self and teammates are never marked.");
            defaults.comment("emp_station", "EMP Field Station: a placed block that keeps an EMP field up around itself while switched on.");
            defaults.comment("emp_blast", "EMP Blast Gadget: a hand-held charge that sets off a growing EMP field around its user.");
            defaults.comment("state", "What happens to vehicles that are shot down or hit by an EMP.");
            defaults.comment("state.shot_down", "Shot down (0 HP): the vehicle loses all control and goes down in a burning, spinning arc; "
                    + "whoever flies or watches it rides it out. It explodes on impact with its own blastPower ([drones.<id>]), as if flown "
                    + "into the ground.");
            defaults.comment("state.emped", "EMP-disabled (an EMP field with onContact = disable): motors off, the vehicle glides down out of "
                    + "control for the field's disableSeconds; whoever flies or watches it stays on and gets control back when it wears off.");
            defaults.comment("emp_gun", "EMP Gun: a shoulder-fired launcher that charges up, then fires a straight-flying orb "
                    + "carrying an EMP field. No lock-on.");
            defaults.comment("stinger", "Stinger: a shoulder-fired launcher whose missile homes in on a locked aircraft (drones, munitions, "
                    + "planes, missiles in flight). Never locks on to players.");
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
            defaults.comment("aerial_bombs", "Bombs for the Stealth Bomber, one size loaded at a time. bayUnits: space taken in the bay (the "
                    + "plane's payloadCapacity); blastPower: vanilla scale (TNT = 4); cooldownTicks: ticks between drops made by hand; "
                    + "carpetIntervalTicks: ticks between bombs in a carpet run flown by the autopilot.");
            defaults.comment("drones", "Per-drone-type stats. Units: speed in blocks/tick (1 = 72 km/h), acceleration in blocks/tick², "
                    + "capacity (with a Standard pack) and drain in charge units (full-throttle flight time = capacity / drain ticks), blastPower on the vanilla "
                    + "scale (TNT = 4), maxHP like mob health, signalRange and motorSoundRange in blocks. Hand-flown types' shrapnel, grenade "
                    + "and lock-on settings are in their own [<id>] table.");

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
                "Strength of the analog-goggle static while flying (0 = off). It grows as the signal weakens. Also scales the gunner's CRT lines.");
        maxEngineVoices = (int) defaults.number("client.maxEngineVoices", (Integer) compiledDefault("maxEngineVoices"), 1, 32,
                "How many drone/plane engines you hear at once: the nearest ones in earshot. Each takes up to 3 sound channels.");
        maxProjectileSounds = (int) defaults.number("client.maxProjectileSounds", (Integer) compiledDefault("maxProjectileSounds"), 1, 32,
                "How many rockets, shells and bombs in flight you hear at once: the nearest ones in earshot.");
    }

    /** The lock-on keys of {@code table}; {@code unit} names the vehicle in their comments. */
    private static LockOnSettings loadLockOn(Defaults defaults, String table, String unit, LockOnSettings fallback) {
        String key = table + ".";
        LockOnSettings settings = new LockOnSettings(fallback.lockOnHoldSeconds, fallback.lockOnDurationSeconds);
        settings.lockOnEnabled = defaults.get(key + "lockOnEnabled", fallback.lockOnEnabled,
                "Lock-on from the " + unit + " camera: hold LMB with the crosshair on an enemy for lockOnHoldSeconds to box them through "
                        + "walls for lockOnDurationSeconds.");
        settings.lockOnRange = defaults.number(key + "lockOnRange", fallback.lockOnRange, 8, 512,
                "How far the " + unit + " camera can lock on, in blocks.");
        settings.lockOnConeDegrees = defaults.number(key + "lockOnConeDegrees", fallback.lockOnConeDegrees, 0.5, 20,
                "How close to the crosshair a target counts as aimed at, in degrees.");
        settings.lockOnAimMargin = defaults.number(key + "lockOnAimMargin", fallback.lockOnAimMargin, 0, 5,
                "Blocks added around a target's hitbox when checking whether the crosshair is on it (aim help with a moving camera).");
        settings.lockOnHoldSeconds = defaults.number(key + "lockOnHoldSeconds", fallback.lockOnHoldSeconds, 0.5, 30,
                "How long the crosshair must stay on the target, LMB held, to lock on, in seconds.");
        settings.lockOnDurationSeconds = defaults.number(key + "lockOnDurationSeconds", fallback.lockOnDurationSeconds, 1, 600,
                "How long a lock-on lasts, in seconds. The target stays boxed even out of the " + unit + "'s sight.");
        settings.lockOnCharges = (int) defaults.number(key + "lockOnCharges", fallback.lockOnCharges, 1, 20,
                "Lock-on charges a " + unit + " carries. Every attempt uses one, failed or not.");
        settings.lockOnReloadSeconds = defaults.number(key + "lockOnReloadSeconds", fallback.lockOnReloadSeconds, 1, 600,
                "Seconds to win back one spent charge (one at a time).");
        settings.maxActiveLockOns = (int) defaults.number(key + "maxActiveLockOns", fallback.maxActiveLockOns, 0, 20,
                "Most lock-ons one " + unit + " may have running at once. 0 = no limit.");
        settings.reloadDuringLockOn = defaults.get(key + "reloadDuringLockOn", fallback.reloadDuringLockOn,
                "Charges reload while the " + unit + " has a lock-on running. Off: the reload waits until it ends.");
        settings.lockOnForAllies = defaults.get(key + "lockOnForAllies", fallback.lockOnForAllies,
                "Owner's vanilla scoreboard teammates also see locked-on targets boxed. Off: only the owner.");
        settings.lockOnWarning = defaults.get(key + "lockOnWarning", fallback.lockOnWarning,
                "A player being locked on gets a blinking warning and beeps while the lock-on builds.");
        settings.lockedWarning = defaults.get(key + "lockedWarning", fallback.lockedWarning,
                "A locked-on player gets a banner at the top of the screen for as long as the lock-on lasts.");
        settings.trackNonPlayers = defaults.get(key + "trackNonPlayers", fallback.trackNonPlayers,
                "Mobs and other living non-players can be " + ("uav".equals(table) ? "tracked and " : "") + "locked on too, shown in "
                        + "yellow. Mostly for testing.");
        return settings;
    }

    /** A hand-flown type's own table: only the keys that apply to it are read and written. */
    private static DroneSettings loadDroneTable(Defaults defaults, DroneType type) {
        String table = type.getId();
        DroneSettings fallback = new DroneSettings();
        DroneSettings settings = new DroneSettings();
        List<String> parts = new ArrayList<>();
        if (type.isImpactDetonates()) {
            parts.add("impact shrapnel");
            settings.shrapnelDamage = defaults.number(table + ".shrapnelDamage", fallback.shrapnelDamage, 0, 40,
                    "Shrapnel damage (half-hearts) at point blank when it detonates; falls off to 0 at shrapnelRadius, needs line of sight.");
            settings.shrapnelRadius = defaults.number(table + ".shrapnelRadius", fallback.shrapnelRadius, 0, 20,
                    "Reach of its shrapnel, in blocks.");
        }
        if (type.getPayload() == DroneType.PayloadKind.GRENADE) {
            parts.add("Fragmentation Grenade extras");
            settings.grenadeBlastBonus = defaults.number(table + ".grenadeBlastBonus", fallback.grenadeBlastBonus, 0, 20,
                    "Extra blast power per Fragmentation Grenade attached (vanilla TNT = 4).");
            settings.grenadeShrapnelDamage = defaults.number(table + ".grenadeShrapnelDamage", fallback.grenadeShrapnelDamage, 0, 40,
                    "Extra shrapnel damage per Fragmentation Grenade attached.");
        }
        if (type.isSensor()) {
            parts.add("lock-on from its camera (its own settings, apart from the UAV's)");
            settings.lockOn = loadLockOn(defaults, table, "drone", fallback.lockOn);
        }
        defaults.comment(table, "Hand-flown drone " + table + ": " + String.join(", ", parts) + ". Its flight stats are in [drones."
                + table + "].");
        return settings;
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

    /** Values of the EMP {@code spares} settings. */
    private static final String[] SPARES = {"none", "owner", "team"};
    /** Values of the EMP {@code onContact} settings. */
    private static final String[] ON_CONTACT = {"destroy", "disable"};
    /** Values of {@code emp_gun.fieldMode}. */
    private static final String[] FIELD_MODES = {"travelling", "burst"};
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

        /** One of {@code allowed} (case-insensitive, returned lower case); anything else falls back. */
        String choice(String path, String fallback, String[] allowed, String description) {
            String value = (String) track(path, fallback, description, String.class, false);
            for (String option : allowed) {
                if (option.equalsIgnoreCase(value)) {
                    return option;
                }
            }
            CombatDrones.logger.error("{} = {} in {} must be one of {}, using {}", path, value, FILE_NAME, String.join(", ", allowed), fallback);
            return fallback;
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
