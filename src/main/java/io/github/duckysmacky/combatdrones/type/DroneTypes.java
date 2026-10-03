package io.github.duckysmacky.combatdrones.type;

import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Registry of drone types. Bundled definitions live in {@code assets/combat_drones/drone_types/}; a file
 * with the same name in {@code config/combat_drones/drone_types/} replaces the bundled one (stat tuning).
 * Overrides can't add new IDs: each type registers an item, and an ID only one side knows would make
 * Forge reject the connection on registry sync.
 */
public final class DroneTypes {
    private static final String BUNDLED_DIR = "assets/" + CombatDrones.MOD_ID + "/drone_types";
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]+");

    // Sorted so item registration and creative-tab order don't depend on filesystem iteration order
    private static final Map<String, DroneType> TYPES = new TreeMap<>();

    private DroneTypes() {
    }

    public static Collection<DroneType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    public static DroneType get(String id) {
        return TYPES.get(id);
    }

    /** Must run in preInit: registry events fire right after it and register one item per type. */
    public static void load(ModContainer mod, File configDir) {
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

        File[] overrideFiles = new File(configDir, CombatDrones.MOD_ID + "/drone_types").listFiles();
        if (overrideFiles != null) {
            for (File file : overrideFiles) {
                applyOverride(file.toPath());
            }
        }
        CombatDrones.logger.info("Loaded {} drone types: {}", TYPES.size(), TYPES.keySet());
    }

    private static void applyOverride(Path file) {
        try {
            String id = idOf(file);
            if (id == null) {
                return;
            }
            if (!TYPES.containsKey(id)) {
                CombatDrones.logger.warn("Ignoring drone type override '{}': no bundled type with that ID", id);
                return;
            }
            TYPES.put(id, read(id, file));
            CombatDrones.logger.info("Drone type '{}' overridden from {}", id, file);
        } catch (IllegalArgumentException e) {
            CombatDrones.logger.error("Invalid drone type override {}, keeping bundled values: {}", file, e.getMessage());
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
