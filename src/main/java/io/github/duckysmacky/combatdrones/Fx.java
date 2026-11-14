package io.github.duckysmacky.combatdrones;

/**
 * The mod's own particles, by what they are. Common code asks for one through {@link CommonProxy#fx} (a no-op on a
 * dedicated server); the client draws it with its own sprite and behaviour ({@code client.fx.FxParticle}).
 */
public enum Fx {
    /** A grey puff that swells and thins as it rises: exhaust, launch air, gun smoke. */
    SMOKE,
    /** Sooty smoke that hangs for a long time: blast columns, the missile's climb, burning wrecks. */
    DARK_SMOKE,
    /** A ball of flame, lit by itself, gone in half a second. */
    FIRE,
    /** A hot fleck thrown out and pulled down by gravity. */
    SPARK,
    /** A tumbling chunk that falls and stays where it lands for a moment. */
    DEBRIS,
    /** Pale dust kicked up from the ground. */
    DUST,
    /** A shockwave: a bright ring growing fast and fading. */
    RING,
    /** A muzzle or blast flash: one bright frame or two. */
    FLASH
}
