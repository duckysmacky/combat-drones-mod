package io.github.duckysmacky.combatdrones.type;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneTypeTest {
    private static final String RACER = "{\n"
            + "  // comments are allowed\n"
            + "  \"category\": \"HAND_FLOWN\",\n"
            + "  \"maxSpeed\": 1.5, \"acceleration\": 0.1,\n"
            + "  \"batteryOrFuelCapacity\": 2400, \"drainRatePerTick\": 1,\n"
            + "  \"blastPower\": 2, \"maxHP\": 4, \"signalRange\": 96,\n"
            + "  \"impactDetonates\": true\n"
            + "}";

    private static DroneType parse(String json) {
        return DroneType.parse("test", new StringReader(json));
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
    void rejectsNegativeStat() {
        assertThrows(IllegalArgumentException.class, () -> parse(RACER.replace("\"blastPower\": 2", "\"blastPower\": -1")));
    }

    @Test
    void rampTypeNeedsControlScheme() {
        String ramp = RACER.replace("HAND_FLOWN", "RAMP_AUTOPILOT")
                .replace("\"signalRange\": 96", "\"defaultCruiseAltitude\": 40");
        assertThrows(IllegalArgumentException.class, () -> parse(ramp));
        assertEquals(DroneType.ControlScheme.STICK, parse(ramp.replace("{", "{ \"controlScheme\": \"STICK\",")).getControlScheme());
    }

    @Test
    void unarmedTypeCantCarryBlast() {
        assertThrows(IllegalArgumentException.class, () -> parse(RACER.replace("{", "{ \"armed\": false,")));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> parse("{ \"category\": "));
    }
}
