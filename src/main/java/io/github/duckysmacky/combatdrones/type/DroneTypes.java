package io.github.duckysmacky.combatdrones.type;

import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.ModContainer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Registry of drone types, defined by the bundled {@code assets/combat_drones/drone_types/*.json}. Their numeric stats
 * can then be tuned in the mod config. New IDs can only come from bundled files: each type registers an item, and an
 * ID only one side knows would make Forge reject the connection on registry sync.
 */
public final class DroneTypes {
    private static final String BUNDLED_DIR = "assets/" + CombatDrones.MOD_ID + "/drone_types";
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]+");

    // Sorted so item registration and creative-tab order don't depend on filesystem iteration order
    private static final Map<String, DroneType> TYPES = new TreeMap<>();
    /** The types exactly as bundled, before any config overrides (config reloads start from these). */
    private static final Map<String, DroneType> BUNDLED = new TreeMap<>();

    private DroneTypes() {
    }

    public static Collection<DroneType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    public static DroneType get(String id) {
        return TYPES.get(id);
    }

    /** Must run in preInit: registry events fire right after it and register one item per type. */
    public static void load(ModContainer mod) {
        TYPES.clear();
        CraftingHelper.findFiles(mod, BUNDLED_DIR, null, (root, file) -> {
            String id = idOf(file);
            if (id != null) {
                // A broken bundled file is a packaging bug, so fail loudly instead of silently dropping a type
                TYPES.put(id, read(id, file));
            }
            return true;
        }, false, true);
        if (TYPES.isEmpty()) {
            throw new IllegalStateException("No drone types found under " + BUNDLED_DIR);
        }

        BUNDLED.clear();
        BUNDLED.putAll(TYPES);
        CombatDrones.logger.info("Loaded {} drone types: {}", TYPES.size(), TYPES.keySet());
    }

    /** Drops config overrides: every type back to its bundled stats. */
    public static void resetToBundled() {
        restore(BUNDLED);
    }

    public static Map<String, DroneType> snapshot() {
        return new TreeMap<>(TYPES);
    }

    public static void restore(Map<String, DroneType> types) {
        TYPES.clear();
        TYPES.putAll(types);
    }

    /**
     * Client side: replace local stats with the server's. Only known IDs are taken; the item registry already
     * guarantees both sides have the same set. On an integrated server this rewrites the same shared map, harmlessly.
     */
    public static void applyServerTypes(Map<String, String> typesJson) {
        for (Map.Entry<String, String> entry : typesJson.entrySet()) {
            String id = entry.getKey();
            if (!TYPES.containsKey(id)) {
                continue;
            }
            try {
                TYPES.put(id, DroneType.parse(id, new StringReader(entry.getValue())));
            } catch (IllegalArgumentException e) {
                CombatDrones.logger.error("Server sent an invalid drone type, keeping local values: {}", e.getMessage());
            }
        }
    }

    /** Replaces a type's numeric stats (config tuning). Throws if the result is invalid; the old type stays. */
    public static void override(String id, Map<String, Number> stats) {
        DroneType type = TYPES.get(id);
        if (type != null && !stats.isEmpty()) {
            TYPES.put(id, type.withStats(stats));
        }
    }

    /** Returns the type ID for a {@code <id>.json} file, or null for anything else. */
    private static String idOf(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".json") || !Files.isRegularFile(file)) {
            return null;
        }
        String id = name.substring(0, name.length() - ".json".length());
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Drone type file name '" + name + "' must be lowercase [a-z0-9_]");
        }
        return id;
    }

    private static DroneType read(String id, Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return DroneType.parse(id, reader);
        } catch (IOException e) {
            throw new IllegalArgumentException("Drone type '" + id + "': can't read " + file, e);
        }
    }
}
