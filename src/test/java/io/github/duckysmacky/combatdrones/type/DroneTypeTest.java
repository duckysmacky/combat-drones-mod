package io.github.duckysmacky.combatdrones.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneTypeTest {
    private static final String RACER = "{\n"
            + "  // comments are allowed\n"
            + "  \"category\": \"HAND_FLOWN\",\n"
            + "  \"maxSpeed\": 1.5, \"acceleration\": 0.1,\n"
            + "  \"batteryOrFuelCapacity\": 2400, \"drainRatePerTick\": 1,\n"
            + "  \"blastPower\": 2, \"maxHP\": 4, \"signalRange\": 96,\n"
            + "  \"impactDetonates\": true, \"defaultBattery\": \"LIGHT\", \"controlScheme\": \"FPV\"\n"
            + "}";

    private static DroneType parse(String json) {
        return DroneType.parse("test", new StringReader(json));
    }

    /**
     * Every bundled type parses, and has its art: a geo model and a texture named after it, and engine sound bands
     * defined in sounds.json (rule 5, no Java).
     */
    @Test
    void bundledTypesParseAndHaveModels() throws IOException {
        Path assets = Paths.get("src/main/resources/assets/combat_drones");
        JsonObject sounds = new JsonParser().parse(new String(Files.readAllBytes(assets.resolve("sounds.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        List<Path> files;
        try (Stream<Path> listing = Files.list(assets.resolve("drone_types"))) {
            files = listing.filter(path -> path.toString().endsWith(".json")).collect(Collectors.toList());
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            String id = file.getFileName().toString().replace(".json", "");
            try (Reader reader = Files.newBufferedReader(file)) {
                DroneType type = DroneType.parse(id, reader);
                assertTrue(type.getHitboxWidth() > 0, id);
                assertTrue(type.getSound() != null, id + " has no engine sound");
                for (String event : type.getSound().getEvents()) {
                    assertTrue(sounds.has(event), id + ": no sound event " + event);
                }
                for (String event : new String[]{type.getSound().getDive(), type.getSound().getTerminal()}) {
                    assertTrue(event == null || sounds.has(event), id + ": no sound event " + event);
                }
            }
            assertTrue(Files.exists(assets.resolve("geo/" + id + ".geo.json")), id + " has no geo model");
            assertTrue(Files.exists(assets.resolve("textures/entity/" + id + ".png")), id + " has no texture");
        }
    }

    @Test
    void parsesCommentedJson() {
        DroneType type = parse(RACER);
        assertEquals("test", type.getId());
        assertEquals(DroneType.Category.HAND_FLOWN, type.getCategory());
        assertEquals(DroneType.WeaponType.NONE, type.getWeaponType());
        assertTrue(type.isImpactDetonates());
        assertTrue(type.isArmed());
    }

    @Test
    void rejectsMissingCategory() {
        assertThrows(IllegalArgumentException.class, () -> parse(RACER.replace("\"category\": \"HAND_FLOWN\",", "")));
    }

    @Test
    void rejectsUnknownEnumValue() {
        assertThrows(IllegalArgumentException.class, () -> parse(RACER.replace("HAND_FLOWN", "HAND_FLOWNN")));
    }

    @Test
    void soundBandsMustRiseToTheFullRange() {
        String banded = RACER.replace("{", "{ \"sound\": { \"crossfade\": 0.1, \"bands\": [{\"event\": \"a\", \"until\": 0.4}, BAND] },");
        DroneType type = parse(banded.replace("BAND", "{\"event\": \"b\", \"until\": 1}"));
        assertArrayEquals(new String[]{"a", "b"}, type.getSound().getEvents());
        assertArrayEquals(new double[]{0.4, 1}, type.getSound().getUntils(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> parse(banded.replace("BAND", "{\"event\": \"b\", \"until\": 0.9}")));
        assertThrows(IllegalArgumentException.class, () -> parse(banded.replace("BAND", "{\"event\": \"b\", \"until\": 0.3}")));
        assertThrows(IllegalArgumentException.class, () -> parse(banded.replace("BAND", "{\"until\": 1}")));
        assertThrows(IllegalArgumentException.class, () -> parse(banded.replace("0.1", "0.5").replace("BAND", "{\"event\": \"b\", \"until\": 1}")));
    }

    @Test
    void rejectsNegativeStat() {
        assertThrows(IllegalArgumentException.class, () -> parse(RACER.replace("\"blastPower\": 2", "\"blastPower\": -1")));
    }

    @Test
    void rampTypeNeedsControlScheme() {
        String ramp = RACER.replace("HAND_FLOWN", "RAMP_AUTOPILOT")
                .replace("\"signalRange\": 96", "\"defaultCruiseAltitude\": 40");
        // A hand-flown scheme isn't valid on a ramp-launched type
        assertThrows(IllegalArgumentException.class, () -> parse(ramp));
        assertEquals(DroneType.ControlScheme.STICK, parse(ramp.replace("\"FPV\"", "\"STICK\"")).getControlScheme());
        assertThrows(IllegalArgumentException.class, () -> parse(RACER.replace("\"FPV\"", "\"STICK\"")));
    }

    @Test
    void verticalStrikeTypeNeedsMissileScheme() {
        String missile = RACER.replace("HAND_FLOWN", "VERTICAL_STRIKE");
        assertThrows(IllegalArgumentException.class, () -> parse(missile));
        assertEquals(DroneType.ControlScheme.MISSILE, parse(missile.replace("\"FPV\"", "\"MISSILE\"")).getControlScheme());
        // MISSILE belongs to vertical strike types only, and they always carry a warhead
        assertThrows(IllegalArgumentException.class, () -> parse(RACER.replace("\"FPV\"", "\"MISSILE\"")));
        assertThrows(IllegalArgumentException.class, () -> parse(missile.replace("\"FPV\"", "\"MISSILE\"").replace("\"impactDetonates\": true", "\"impactDetonates\": false")));
    }

    @Test
    void unarmedTypeCantCarryBlast() {
        assertThrows(IllegalArgumentException.class, () -> parse(RACER.replace("{", "{ \"armed\": false,")));
    }

    @Test
    void jsonRoundTripKeepsStats() {
        DroneType original = parse(RACER);
        DroneType copy = DroneType.parse("test", new StringReader(original.toJson()));
        assertEquals(original.getMaxSpeed(), copy.getMaxSpeed());
        assertEquals(original.getSignalRange(), copy.getSignalRange());
        assertEquals(original.isImpactDetonates(), copy.isImpactDetonates());
    }

    @Test
    void statOverridesApplyAndRevalidate() {
        DroneType tuned = parse(RACER).withStats(Collections.singletonMap("maxHP", 12));
        assertEquals(12, tuned.getMaxHP());
        assertEquals(1.5, tuned.getMaxSpeed());
        assertThrows(IllegalArgumentException.class, () -> parse(RACER).withStats(Collections.singletonMap("maxHP", -1)));
        assertThrows(IllegalArgumentException.class, () -> parse(RACER).withStats(Collections.singletonMap("category", 1)));
        assertThrows(IllegalArgumentException.class, () -> parse(RACER).withStats(Collections.singletonMap("noSuchStat", 1)));
    }

    @Test
    void numericStatsSkipOtherCategory() {
        Map<String, Number> stats = parse(RACER).numericStats();
        assertTrue(stats.containsKey("signalRange"));
        assertTrue(stats.containsKey("maxHP"));
        assertFalse(stats.containsKey("defaultCruiseAltitude"));
        assertFalse(stats.containsKey("category"));
    }

    @Test
    void payloadSlotsNeedAPayloadKind() {
        assertThrows(IllegalArgumentException.class, () -> parse(RACER.replace("{", "{ \"payloadCapacity\": 1,")));
        DroneType withBombs = parse(RACER.replace("{", "{ \"payloadCapacity\": 1, \"payload\": \"BOMB\","));
        assertEquals(DroneType.PayloadKind.BOMB, withBombs.getPayload());
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> parse("{ \"category\": "));
    }
}
