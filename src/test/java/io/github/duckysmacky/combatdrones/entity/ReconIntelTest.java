package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconIntelTest {
    private static final long NOW = 1000;

    private boolean reconForAllies;
    private boolean lockForAllies;
    private boolean alwaysShow;

    @BeforeEach
    void saveConfig() {
        reconForAllies = ModConfig.reconIntelForAllies;
        lockForAllies = ModConfig.activeLockForAllies;
        alwaysShow = ModConfig.alwaysShowActiveLock;
        ModConfig.reconIntelForAllies = true;
        ModConfig.activeLockForAllies = true;
        ModConfig.alwaysShowActiveLock = false;
    }

    @AfterEach
    void restoreConfig() {
        ModConfig.reconIntelForAllies = reconForAllies;
        ModConfig.activeLockForAllies = lockForAllies;
        ModConfig.alwaysShowActiveLock = alwaysShow;
    }

    /** Last seen at (10, 64, 10), now locked and followed live to (50, 64, 50). */
    private static ReconIntel.Mark lockedMark() {
        ReconIntel.Mark mark = new ReconIntel.Mark(UUID.randomUUID());
        mark.name = "Enemy";
        mark.seenX = 10;
        mark.seenY = 64;
        mark.seenZ = 10;
        mark.x = 50;
        mark.y = 64;
        mark.z = 50;
        mark.expiresAt = NOW + 100;
        mark.lockedUntil = NOW + 600;
        return mark;
    }

    @Test
    void ownerSeesTheLiveLock() {
        IntelPacket.Entry entry = ReconIntel.viewOf(lockedMark(), NOW, true, true);
        assertTrue(entry.locked);
        assertEquals(50, entry.x);
    }

    @Test
    void teammateGetsTheLockWhenLocksAreShared() {
        IntelPacket.Entry entry = ReconIntel.viewOf(lockedMark(), NOW, false, false);
        assertTrue(entry.locked);
        assertEquals(50, entry.x);
    }

    @Test
    void teammateWithoutSharedLocksOnlyGetsTheLastSighting() {
        ModConfig.activeLockForAllies = false;
        IntelPacket.Entry entry = ReconIntel.viewOf(lockedMark(), NOW, false, true);
        assertFalse(entry.locked);
        assertFalse(entry.inWorld);
        assertEquals(10, entry.x);
    }

    @Test
    void teammateGetsNothingWhenNothingIsShared() {
        ModConfig.activeLockForAllies = false;
        ModConfig.reconIntelForAllies = false;
        assertNull(ReconIntel.viewOf(lockedMark(), NOW, false, true));
    }

    @Test
    void stalePassiveSightingIsDropped() {
        ReconIntel.Mark mark = lockedMark();
        mark.lockedUntil = 0;
        mark.expiresAt = NOW - 1;
        assertNull(ReconIntel.viewOf(mark, NOW, true, false));
    }

    @Test
    void lockShowsInTheWorldOnlyWhileTheOwnerWatchesUnlessAlwaysShown() {
        assertTrue(ReconIntel.viewOf(lockedMark(), NOW, false, true).inWorld);
        assertFalse(ReconIntel.viewOf(lockedMark(), NOW, false, false).inWorld);
        ModConfig.alwaysShowActiveLock = true;
        assertTrue(ReconIntel.viewOf(lockedMark(), NOW, false, false).inWorld);
    }
}
