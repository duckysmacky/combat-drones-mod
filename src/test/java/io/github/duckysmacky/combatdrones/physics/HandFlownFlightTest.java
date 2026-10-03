package io.github.duckysmacky.combatdrones.physics;

import io.github.duckysmacky.combatdrones.physics.HandFlownFlight.Input;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight.Mode;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight.Params;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight.State;
import org.junit.jupiter.api.Test;

import javax.vecmath.Vector3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandFlownFlightTest {
    private static final Params RACER = new Params(1.3, 0.12);
    private static final double HOVER_THROTTLE = HandFlownFlight.GRAVITY / (HandFlownFlight.GRAVITY + 0.12);

    private static Input input(double throttle, double pitch, double roll, double yaw, Mode mode) {
        return new Input(throttle, 0, pitch, roll, yaw, mode, true);
    }

    private static State run(State state, Input input, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            HandFlownFlight.step(state, input, RACER);
        }
        return state;
    }

    @Test
    void hoverThrottleHoldsAltitude() {
        State state = run(new State(), input(HOVER_THROTTLE, 0, 0, 0, Mode.ANGLE), 100);
        assertEquals(0, state.position.y, 1e-6);
    }

    @Test
    void fullThrottleNeverExceedsMaxSpeed() {
        State state = new State();
        for (int tick = 0; tick < 200; tick++) {
            HandFlownFlight.step(state, input(1, 1, 0, 0, Mode.ACRO), RACER);
            assertTrue(state.velocity.length() <= 1.3 + 1e-9);
        }
    }

    @Test
    void acroHoldsAttitudeWithCentredSticks() {
        State state = run(new State(), input(HOVER_THROTTLE, 1, 0, 0, Mode.ACRO), 3);
        Vector3d tilted = HandFlownFlight.forwardOf(state.orientation);
        run(state, input(HOVER_THROTTLE, 0, 0, 0, Mode.ACRO), 20);
        Vector3d after = HandFlownFlight.forwardOf(state.orientation);
        assertTrue(tilted.y < -0.1, "pitch forward should drop the nose");
        assertEquals(tilted.y, after.y, 1e-9);
    }

    @Test
    void angleModeSelfLevels() {
        State state = run(new State(), input(HOVER_THROTTLE, 1, 1, 0, Mode.ANGLE), 20);
        assertTrue(HandFlownFlight.upOf(state.orientation).y < 0.9, "full stick should tilt the drone");
        run(state, input(HOVER_THROTTLE, 0, 0, 0, Mode.ANGLE), 40);
        assertEquals(1, HandFlownFlight.upOf(state.orientation).y, 1e-4);
    }

    @Test
    void stickDirectionsMatchConventions() {
        State pitched = run(new State(), input(HOVER_THROTTLE, 1, 0, 0, Mode.ANGLE), 10);
        assertTrue(pitched.velocity.z > 0, "nose down should accelerate forward (+Z)");
        State rolled = run(new State(), input(HOVER_THROTTLE, 0, 1, 0, Mode.ANGLE), 10);
        assertTrue(rolled.velocity.x < 0, "roll right should accelerate right (-X)");
        State yawed = run(new State(), input(HOVER_THROTTLE, 0, 0, 1, Mode.ANGLE), 10);
        assertTrue(HandFlownFlight.forwardOf(yawed.orientation).x < 0, "yaw right should turn the nose right (-X)");
    }

    @Test
    void yawKeepsDroneLevel() {
        State state = run(new State(), input(HOVER_THROTTLE, 0, 0, 1, Mode.ANGLE), 50);
        assertEquals(1, HandFlownFlight.upOf(state.orientation).y, 1e-6);
        assertEquals(0, state.position.y, 1e-6);
    }

    @Test
    void hoverModeHoldsAltitudeAndClimbsOnCommand() {
        State state = new State();
        state.velocity.set(0.5, -0.3, 0.5);
        run(state, new Input(0, 0, 0, 0, 0, Mode.HOVER, true), 100);
        assertEquals(0, state.velocity.length(), 0.01);

        double altitude = state.position.y;
        run(state, new Input(0, 1, 0, 0, 0, Mode.HOVER, true), 40);
        assertTrue(state.position.y > altitude + 5, "full vertical input should climb");
    }

    @Test
    void eulerRoundTripsAndMatchesMinecraftYaw() {
        double[] euler = HandFlownFlight.toEuler(HandFlownFlight.fromEuler(120, -30, 25));
        assertEquals(120, euler[0], 1e-6);
        assertEquals(-30, euler[1], 1e-6);
        assertEquals(25, euler[2], 1e-6);
        // Vanilla: yaw 90 faces -X
        Vector3d forward = HandFlownFlight.forwardOf(HandFlownFlight.fromEuler(90, 0, 0));
        assertEquals(-1, forward.x, 1e-9);
    }

    @Test
    void motorsOffFalls() {
        State state = run(new State(), new Input(1, 0, 0, 0, 0, Mode.ANGLE, false), 20);
        assertTrue(state.position.y < -2);
        assertTrue(state.velocity.y < 0);
    }
}
