package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundBandsTest {
    private static final double[] UNTILS = {0.3, 0.65, 1.0};

    @Test
    void eachBandOwnsItsStretchOutsideTheCrossfades() {
        assertArrayEquals(new double[]{1, 0, 0}, SoundBands.bandGains(0.1, UNTILS, 0.2), 1e-9);
        assertArrayEquals(new double[]{0, 1, 0}, SoundBands.bandGains(0.5, UNTILS, 0.2), 1e-9);
        assertArrayEquals(new double[]{0, 0, 1}, SoundBands.bandGains(0.9, UNTILS, 0.2), 1e-9);
    }

    @Test
    void crossfadeKeepsTheEnergyAndNeverSoundsThreeBands() {
        for (double fraction = 0; fraction < 1; fraction += 0.01) {
            double[] gains = SoundBands.bandGains(fraction, UNTILS, 0.2);
            double energy = 0;
            int sounding = 0;
            for (double gain : gains) {
                energy += gain * gain;
                sounding += gain > 1e-9 ? 1 : 0;
            }
            assertEquals(1, energy, 1e-9, "energy at " + fraction);
            assertTrue(sounding <= 2, "bands sounding at " + fraction);
        }
        double[] boundary = SoundBands.bandGains(0.3, UNTILS, 0.2);
        assertEquals(boundary[0], boundary[1], 1e-9, "halfway across the crossfade the two bands are level");
    }

    @Test
    void noCrossfadeSwitchesHard() {
        assertArrayEquals(new double[]{1, 0, 0}, SoundBands.bandGains(0.299, UNTILS, 0), 1e-9);
        assertArrayEquals(new double[]{0, 1, 0}, SoundBands.bandGains(0.3, UNTILS, 0), 1e-9);
    }

    @Test
    void silentAtAndBeyondRange() {
        assertArrayEquals(new double[3], SoundBands.bandGains(1, UNTILS, 0.2), 1e-9);
        assertArrayEquals(new double[3], SoundBands.bandGains(1.5, UNTILS, 0.2), 1e-9);
        assertEquals(0, SoundBands.distanceGain(1), 1e-9);
        assertEquals(1, SoundBands.distanceGain(0), 1e-9);
        assertTrue(SoundBands.distanceGain(0.5) > SoundBands.distanceGain(0.8));
    }

    @Test
    void dopplerRisesClosingAndDropsReceding() {
        assertEquals(1, SoundBands.doppler(0), 1e-9);
        assertTrue(SoundBands.doppler(-2) > 1);
        assertTrue(SoundBands.doppler(2) < 1);
        assertEquals(1.35, SoundBands.doppler(-100), 1e-9);
    }

    @Test
    void diveSwellsWithSteepnessAndSpeed() {
        assertEquals(0, SoundBands.diveAmount(5, 1), 1e-9, "level flight");
        assertEquals(0, SoundBands.diveAmount(-60, 1), 1e-9, "climbing");
        assertEquals(1, SoundBands.diveAmount(70, 1.4), 1e-9, "steep and boosting");
        assertEquals(0.5, SoundBands.diveAmount(70, 0.5), 1e-9);
    }

    @Test
    void smoothstepEnds() {
        assertEquals(0, SoundBands.smoothstep(10, 20, 60), 1e-9);
        assertEquals(0.5, SoundBands.smoothstep(40, 20, 60), 1e-9);
        assertEquals(1, SoundBands.smoothstep(70, 20, 60), 1e-9);
    }
}
