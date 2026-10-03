package io.github.duckysmacky.combatdrones.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single mod config, {@code config/combat_drones.toml}. Missing entries are written back with their defaults,
 * so the file always lists every knob; values the admin changed are kept. Read once at startup.
 *
 * <ul>
 *   <li>{@code [general]}: global server balance knobs.</li>
 *   <li>{@code [client]}: options for your own client only; a server ignores them.</li>
 *   <li>{@code [batteries.<size>]}: battery pack capacity and weight multipliers, synced to clients.</li>
 *   <li>{@code [drones.<id>]}: per-type balance stats, defaulting to the bundled drone type data. The server's
 *       values are synced to clients on login.</li>
 * </ul>
 */
public final class ModConfig {
    public static final String FILE_NAME = CombatDrones.MOD_ID + ".toml";

    // [general]
    public static boolean blockDamageEnabled = true;
    public static double grenadeBlastBonus = 2.0;
    public static double droneDamageTakenMultiplier = 1.0;

    // [client]
    public static boolean invertMousePitch = false;
    public static double staticIntensity = 1.0;

    private ModConfig() {
    }

    /** Must run after {@link DroneTypes#load}, since the drone tables default to the bundled types. */
    public static void load(File configDir) {
        File file = new File(configDir, FILE_NAME);
        try (CommentedFileConfig config = CommentedFileConfig.builder(file, TomlFormat.instance()).preserveInsertionOrder().sync().build()) {
            config.load();
            Defaults defaults = new Defaults(config);

            blockDamageEnabled = defaults.get("general.blockDamageEnabled", blockDamageEnabled, "Whether drone and munition explosions break blocks.");
            grenadeBlastBonus = defaults.number("general.grenadeBlastBonus", grenadeBlastBonus, 0, 20,
                    "Extra blast power per Fragmentation Grenade attached to a racer (vanilla TNT = 4).");
            droneDamageTakenMultiplier = defaults.number("general.droneDamageTakenMultiplier", droneDamageTakenMultiplier, 0, 10,
                    "Multiplies all damage drones take from attacks (weapons, arrows, melee). Lower = harder to shoot down.");

            invertMousePitch = defaults.get("client.invertMousePitch", invertMousePitch, "Invert vertical mouse movement when flying a drone in FPV.");
            staticIntensity = defaults.number("client.staticIntensity", staticIntensity, 0, 1,
                    "Strength of the analog-goggle static while flying (0 = off). It grows as the signal weakens.");

            for (BatteryPack pack : BatteryPack.values()) {
                String table = "batteries." + pack.key();
                pack.setMultipliers(
                        defaults.number(table + ".capacityMultiplier", pack.getDefaultCapacityMultiplier(), 0.1, 10, null),
                        defaults.number(table + ".performanceMultiplier", pack.getDefaultPerformanceMultiplier(), 0.1, 2, null));
            }

            for (DroneType type : DroneTypes.all()) {
                applyDroneTable(defaults, type);
            }
            warnUnknownDrones(config);

            // Table comments last: a comment can only attach to a table that exists
            defaults.comment("general", "Global balance settings. On a server these apply to everyone.");
            defaults.comment("client", "Options for your own client only.");
            defaults.comment("batteries", "Battery pack sizes for hand-flown drones. capacityMultiplier scales a drone's base capacity "
                    + "(defined for a Light pack); performanceMultiplier scales its top speed and acceleration (pack weight).");
            defaults.comment("drones", "Per-drone-type stats. Units: speed in blocks/tick (1 = 72 km/h), acceleration in blocks/tick², "
                    + "capacity (with a Light pack) and drain in charge units (full-throttle flight time = capacity / drain ticks), blastPower on the vanilla "
                    + "scale (TNT = 4), maxHP like mob health, signalRange and motorSoundRange in blocks.");

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

    /** Reads values, writing defaults (with comments) for anything missing or of the wrong type. */
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

        boolean get(String path, boolean fallback, String comment) {
            Object value = config.get(path);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            reset(path, fallback, comment);
            return fallback;
        }

        double number(String path, double fallback, double min, double max, String comment) {
            Object value = config.get(path);
            if (value instanceof Number) {
                double number = ((Number) value).doubleValue();
                if (number >= min && number <= max) {
                    return number;
                }
                CombatDrones.logger.error("{} = {} in {} is outside {}..{}, using {}", path, number, FILE_NAME, min, max, fallback);
                return fallback;
            }
            reset(path, fallback, comment);
            return fallback;
        }

        Number stat(String path, Number fallback) {
            Object value = config.get(path);
            if (value instanceof Number) {
                return (Number) value;
            }
            // Float fields would otherwise be written as e.g. 1.600000023841858
            Number clean = fallback instanceof Float ? Double.valueOf(fallback.toString()) : fallback;
            reset(path, clean, null);
            return clean;
        }

        private void reset(String path, Object value, String comment) {
            if (config.contains(path)) {
                CombatDrones.logger.error("{} in {} has the wrong type, resetting it to {}", path, FILE_NAME, value);
            }
            config.set(path, value);
            if (comment != null) {
                config.setComment(path, comment);
            }
            changed = true;
        }
    }
}
