package io.github.duckysmacky.combatdrones;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.duckysmacky.combatdrones.client.hud.Feed;
import io.github.duckysmacky.combatdrones.type.DroneType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled assets hang together: nothing names a file that isn't there. Catches a renamed or forgotten asset. */
class AssetsTest {
    private static final Path ASSETS = Paths.get("src/main/resources/assets/combat_drones");
    private static final String NAMESPACE = CombatDrones.MOD_ID + ":";

    private static JsonObject json(Path file) throws IOException {
        return new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<Path> list(String folder, String extension) throws IOException {
        try (Stream<Path> listing = Files.list(ASSETS.resolve(folder))) {
            return listing.filter(path -> path.toString().endsWith(extension)).collect(Collectors.toList());
        }
    }

    @Test
    void everySoundEventHasItsFiles() throws IOException {
        JsonObject sounds = json(ASSETS.resolve("sounds.json"));
        assertFalse(sounds.entrySet().isEmpty());
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            for (JsonElement sound : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
                assertTrue(name.startsWith(NAMESPACE), event.getKey() + ": " + name + " is outside the mod's namespace");
                Path file = ASSETS.resolve("sounds/" + name.substring(NAMESPACE.length()) + ".ogg");
                assertTrue(Files.exists(file), event.getKey() + ": missing " + file);
            }
        }
    }

    @Test
    void everyGeoModelHasATexture() throws IOException {
        List<Path> models = list("geo", ".geo.json");
        assertFalse(models.isEmpty());
        for (Path model : models) {
            String name = model.getFileName().toString().replace(".geo.json", "");
            assertTrue(Files.exists(ASSETS.resolve("textures/entity/" + name + ".png")), name + " has no texture");
        }
    }

    /** Every bundled type gets a camera feed, and every feed's picture grade is there to load. */
    @Test
    void everyTypeHasAFeed() throws IOException {
        for (Path file : list("drone_types", ".json")) {
            String id = file.getFileName().toString().replace(".json", "");
            try (Reader reader = Files.newBufferedReader(file)) {
                assertNotNull(Feed.of(DroneType.parse(id, reader)), id);
            }
        }
        for (Feed feed : Feed.values()) {
            assertTrue(feed.shader == null || Files.exists(ASSETS.resolve("shaders/post/" + feed.shader + ".json")), feed + ": no shader");
        }
    }

    @Test
    void everyModelTextureExists() throws IOException {
        for (String folder : new String[]{"models/item"}) {
            for (Path model : list(folder, ".json")) {
                JsonObject textures = json(model).getAsJsonObject("textures");
                if (textures == null) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> texture : textures.entrySet()) {
                    String name = texture.getValue().getAsString();
                    if (name.startsWith(NAMESPACE)) {
                        Path file = ASSETS.resolve("textures/" + name.substring(NAMESPACE.length()) + ".png");
                        assertTrue(Files.exists(file), model.getFileName() + ": missing " + file);
                    }
                }
            }
        }
    }
}
