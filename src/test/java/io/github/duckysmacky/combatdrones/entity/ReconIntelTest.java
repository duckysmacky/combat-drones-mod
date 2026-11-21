package io.github.duckysmacky.combatdrones.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconIntelTest {
    private static final long NOW = 1000;

    private static ReconIntel.LockOn lockOn(long until, UUID source) {
        UUID target = UUID.randomUUID();
        return new ReconIntel.LockOn(new ReconIntel.Mark(target, 1, "Enemy", true, target, 0, 0, 64, 0), until, source, true, true, 0);
    }

    private static Map<UUID, ReconIntel.LockOn> owned(ReconIntel.LockOn... lockOns) {
        Map<UUID, ReconIntel.LockOn> map = new HashMap<>();
        for (ReconIntel.LockOn lockOn : lockOns) {
            map.put(lockOn.mark.targetId, lockOn);
        }
        return map;
    }

    @Test
    void countsOnlyRunningLockOnsFromThatUav() {
        UUID uav = UUID.randomUUID();
        UUID otherUav = UUID.randomUUID();
        Map<UUID, ReconIntel.LockOn> owner = owned(lockOn(NOW + 100, uav), lockOn(NOW - 1, uav), lockOn(NOW + 100, otherUav));
        Map<UUID, ReconIntel.LockOn> teammate = owned(lockOn(NOW, uav));
        assertEquals(2, ReconIntel.countLockOns(Arrays.asList(owner, teammate), uav, NOW));
        assertEquals(1, ReconIntel.countLockOns(Arrays.asList(owner, teammate), otherUav, NOW));
        assertEquals(0, ReconIntel.countLockOns(Arrays.asList(owner, teammate), uav, NOW + 101));
    }
}
