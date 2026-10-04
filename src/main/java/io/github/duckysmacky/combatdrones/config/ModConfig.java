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

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single mod config, {@code config/combat_drones.toml}. Missing entries are written back with their defaults,
 * so the file always lists every knob; values the admin changed are kept. Read at startup and again on
 * {@code /combat_drones config_reload}.
 *
 * <ul>
 *   <li>{@code [general]}: global server balance knobs.</li>
 *   <li>{@code [client]}: options for your own client only; a server ignores them.</li>
 *   <li>{@code [batteries.<size>]}: battery pack capacity and weight multipliers, synced to clients.</li>
 *   <li>{@code [bombs.<size>]}: bomb bay size, blast power and weight, synced to clients.</li>
 *   <li>{@code [drones.<id>]}: per-type balance stats, defaulting to the bundled drone type data. The server's
 *       values are synced to clients on login.</li>
 * </ul>
 */
public final class ModConfig {
    public static final String FILE_NAME = CombatDrones.MOD_ID + ".toml";
    /** Keys from older versions, removed from existing files so they don't look like live settings. */
    private static final String[] RETIRED_KEYS = {"general.bombBlastPower"};

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
    public static double detectionSweepRadius = 64.0;
    public static double detectionDecaySeconds = 15.0;
    public static double activeLockRange = 192.0;
    public static double lockConeDegrees = 6.0;
    public static int activeLockHoldTicks = 30;
    public static double activeLockDurationSeconds = 60.0;
    public static boolean reconIntelForAllies = false;
    public static double boostSpeedMultiplier = 1.5;
    public static double boostFuelMultiplier = 4.0;

    // [weapons]
    public static int roundsPerRifleItem = 16;
    public static double rocketBlastPower = 2.5;
    public static double rocketSpeed = 3.0;
    public static int rocketCooldownTicks = 20;
    public static double gunDamagePerRound = 3.0;
    public static int gunCooldownTicks = 2;
    public static double gunSpread = 1.5;
    public static double gunRange = 96.0;
    public static double gunHitMargin = 0.5;

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
            if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && field.getType().isPrimitive()) {
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
     * is rethrown for the caller to report.
     */
    public static void reload() {
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
    }

    public static RouteValidator.Limits routeLimits() {
        return new RouteValidator.Limits(maxWaypointsPerRoute, maxRouteAltitude, maxRouteLength);
    }

    /** Must run after {@link DroneTypes#load}, since the drone tables default to the bundled types. */
    public static void load(File configDir) {
        configDirectory = configDir;
        File file = new File(configDir, FILE_NAME);
        try (CommentedFileConfig config = CommentedFileConfig.builder(file, TomlFormat.instance()).preserveInsertionOrder().sync().build()) {
            config.load();
            Defaults defaults = new Defaults(config);

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
                    "How far a target can be marked with the Mission Planner, or designated from a munition's camera, in blocks.");
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
                    "Share recon UAV intel (spotted players) with the owner's vanilla scoreboard teammates, who are then never "
                            + "spotted themselves. Off: only the owner sees it (use this for free-for-all games that put everyone on one team).");
            boostSpeedMultiplier = defaults.number("general.boostSpeedMultiplier", boostSpeedMultiplier, 1, 4,
                    "Top speed multiplier while a remotely controlled loitering munition boosts (Space).");
            boostFuelMultiplier = defaults.number("general.boostFuelMultiplier", boostFuelMultiplier, 1, 20,
                    "Fuel burn multiplier while boosting.");
            droneDamageTakenMultiplier = defaults.number("general.droneDamageTakenMultiplier", droneDamageTakenMultiplier, 0, 10,
                    "Multiplies all damage drones take from attacks (weapons, arrows, melee). Lower = harder to shoot down.");

            roundsPerRifleItem = (int) defaults.number("weapons.roundsPerRifleItem", roundsPerRifleItem, 1, 256,
                    "Gun rounds one Rifle Rounds item loads into a Gunship Plane's belt.");
            rocketBlastPower = defaults.number("weapons.rocketBlastPower", rocketBlastPower, 0, 20, "Rocket explosion power (vanilla TNT = 4).");
            rocketSpeed = defaults.number("weapons.rocketSpeed", rocketSpeed, 0.5, 10, "Rocket speed on top of the plane's own, in blocks/tick.");
            rocketCooldownTicks = (int) defaults.number("weapons.rocketCooldownTicks", rocketCooldownTicks, 1, 200, "Ticks between rockets while the trigger is held.");
            gunDamagePerRound = defaults.number("weapons.gunDamagePerRound", gunDamagePerRound, 0, 40, "Damage per gun round (half-hearts).");
            gunCooldownTicks = (int) defaults.number("weapons.gunCooldownTicks", gunCooldownTicks, 1, 40, "Ticks between gun rounds while the trigger is held.");
            gunSpread = defaults.number("weapons.gunSpread", gunSpread, 0, 10, "Gun spread: random aim error per round, in degrees.");
            gunRange = defaults.number("weapons.gunRange", gunRange, 8, 256, "Gun range in blocks.");
            gunHitMargin = defaults.number("weapons.gunHitMargin", gunHitMargin, 0, 2,
                    "Blocks added around targets' hitboxes for gun rounds, so fast passes still connect.");

            invertMousePitch = defaults.get("client.invertMousePitch", invertMousePitch, "Invert vertical mouse movement when flying a drone in FPV.");
            planeMouseSensitivity = defaults.number("client.planeMouseSensitivity", planeMouseSensitivity, 0.1, 5,
                    "Mouse sensitivity when flying a ramp unit by hand (1 = default).");
            staticIntensity = defaults.number("client.staticIntensity", staticIntensity, 0, 1,
                    "Strength of the analog-goggle static while flying (0 = off). It grows as the signal weakens.");

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

            for (DroneType type : DroneTypes.all()) {
                applyDroneTable(defaults, type);
            }
            warnUnknownDrones(config);

            // Table comments last: a comment can only attach to a table that exists
            defaults.comment("general", "Global balance settings. On a server these apply to everyone.");
            defaults.comment("weapons", "Gunship Plane weapons, fired only under manual control.");
            defaults.comment("client", "Options for your own client only.");
            defaults.comment("batteries", "Battery pack sizes for hand-flown drones. capacityMultiplier scales a drone's base capacity "
                    + "(defined for a Light pack); performanceMultiplier scales its top speed and acceleration (pack weight).");
            defaults.comment("bombs", "Bombs for DJI-style drones. bayUnits: space taken in a drone's bomb bay (its payloadCapacity); "
                    + "blastPower: vanilla scale (TNT = 4); weightPerBomb: share of top speed and acceleration lost per bomb carried.");
            defaults.comment("drones", "Per-drone-type stats. Units: speed in blocks/tick (1 = 72 km/h), acceleration in blocks/tick², "
                    + "capacity (with a Light pack) and drain in charge units (full-throttle flight time = capacity / drain ticks), blastPower on the vanilla "
                    + "scale (TNT = 4), maxHP like mob health, signalRange and motorSoundRange in blocks.");

            for (String retired : RETIRED_KEYS) {
                if (config.contains(retired)) {
                    config.remove(retired);
                    defaults.changed = true;
                }
            }
            if (defaults.changed) {
                config.save();
            }
        }
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
