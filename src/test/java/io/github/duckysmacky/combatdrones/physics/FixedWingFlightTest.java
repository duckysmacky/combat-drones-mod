package io.github.duckysmacky.combatdrones.physics;

import io.github.duckysmacky.combatdrones.physics.FixedWingFlight.Controls;
import io.github.duckysmacky.combatdrones.physics.FixedWingFlight.Params;
import io.github.duckysmacky.combatdrones.physics.FixedWingFlight.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWingFlightTest {
    private static final Params PLANE = new Params(1.0, 0.1);

    private static State cruising() {
        State state = new State();
        state.speed = 0.8;
        return state;
    }

    private static void run(State state, Controls controls, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            FixedWingFlight.step(state, controls, PLANE);
        }
    }

    @Test
    void fullThrottleLevelFlightSettlesAtTopSpeed() {
        State state = cruising();
        run(state, new Controls(0, 0, 1, true), 200);
        assertEquals(1.0, state.speed, 1e-3);
        assertEquals(0, state.position.y, 1e-6);
        assertTrue(state.position.z > 100, "heading 0 flies toward +Z");
    }

    @Test
    void bankingRightTurnsRight() {
        State state = cruising();
        run(state, new Controls(30, 0, 0.8, true), 20);
        assertTrue(state.heading > 5, "positive bank should increase heading (turn right)");
        assertTrue(state.position.x < 0, "turning right from +Z drifts toward -X");
    }

    @Test
    void noseUpClimbsAndSlows() {
        State state = cruising();
        run(state, new Controls(0, -20, 0.8, true), 40);
        assertTrue(state.position.y > 5);
        assertTrue(state.speed < 0.8);
    }

    @Test
    void stalledPlaneRecovers() {
        State state = new State();
        state.speed = 0.05;
        run(state, new Controls(0, 0, 1, true), 100);
        assertTrue(state.speed > PLANE.stallSpeed(), "full throttle should recover from a stall");
    }

    @Test
    void engineOffGlidesDown() {
        State state = cruising();
        run(state, new Controls(0, FixedWingFlight.GLIDE_PITCH_DEG, 0, false), 200);
        assertTrue(state.position.y < -10);
        assertTrue(state.speed > PLANE.stallSpeed(), "a glide keeps flying speed");
        assertTrue(state.position.z > -state.position.y * 2, "a glide covers more ground than it loses height");
    }

    @Test
    void fullForwardStickDivesSteeply() {
        State state = cruising();
        run(state, FixedWingFlight.fromStick(1, 0, 1, true), 40);
        assertEquals(FixedWingFlight.MAX_DIVE_PITCH_DEG, state.pitch, 1e-6);
        assertTrue(state.position.y < -20);
    }

    @Test
    void releasedStickLevelsOut() {
        State state = cruising();
        run(state, FixedWingFlight.fromStick(0.5, 1, 0.8, true), 20);
        assertTrue(state.bank > 30 && state.pitch > 10);
        run(state, FixedWingFlight.fromStick(0, 0, 0.8, true), 40);
        assertEquals(0, state.bank, 1e-6);
        assertEquals(0, state.pitch, 1e-6);
    }

    @Test
    void stickWithoutFuelCutsTheEngine() {
        Controls controls = FixedWingFlight.fromStick(0, 0, 1, false);
        assertEquals(0, controls.throttle, 1e-9);
        assertTrue(!controls.engineOn);
    }
}
