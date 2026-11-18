package io.github.duckysmacky.combatdrones.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigTest {
    @Test
    void parsesRecordedDefault() {
        assertEquals("0.7", ModConfig.recordedDefault("Top speed. Default: 0.7"));
        assertEquals("true", ModConfig.recordedDefault("Default: true"));
        assertNull(ModConfig.recordedDefault("Whether explosions break blocks."));
        assertNull(ModConfig.recordedDefault(null));
    }

    @Test
    void untouchedValuesFollowNewDefaults() {
        // Stored 0.9 was written when the default was 0.9: follow the new default
        assertTrue(ModConfig.followsDefault(0.9, "0.9", false));
        // Integer vs double formatting doesn't matter
        assertTrue(ModConfig.followsDefault(4, "4.0", false));
    }

    @Test
    void editedValuesAreKept() {
        assertFalse(ModConfig.followsDefault(1.2, "0.9", true));
        assertFalse(ModConfig.followsDefault(true, "false", true));
    }

    @Test
    void legacyEntriesDependOnTheirKind() {
        assertTrue(ModConfig.followsDefault(1, null, true));
        assertFalse(ModConfig.followsDefault(1, null, false));
    }

    @Test
    void reloadListsChangedKeysInsideDroneTables() {
        DroneSettings before = new DroneSettings();
        DroneSettings now = new DroneSettings();
        now.lockOn = new LockOnSettings(3.0, 30.0);
        now.lockOn.trackNonPlayers = true;
        List<String> changes = new ArrayList<>();
        ModConfig.describeChanges("droneTables", Collections.singletonMap("recon_drone", before),
                Collections.singletonMap("recon_drone", now), changes);
        assertEquals(Collections.singletonList("recon_drone.lockOn.trackNonPlayers: false -> true"), changes);
    }
}
