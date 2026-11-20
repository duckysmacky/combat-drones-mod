package io.github.duckysmacky.combatdrones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.IKeyConflictContext;
import org.lwjgl.input.Keyboard;

/**
 * Drone-specific bindings. Flight axes reuse vanilla movement keys (W/S throttle, A/D yaw) so they follow the
 * player's own rebinds and can never conflict.
 */
public final class DroneKeys {
    private static final String CATEGORY = "key.categories.combat_drones";

    /**
     * Live only while a launcher is in hand. Forge hands a key press to the first active binding on that key, so an
     * always-active Reload would swallow R for other mods' reload keys.
     */
    private static final IKeyConflictContext HOLDING_LAUNCHER = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return Minecraft.getMinecraft().currentScreen == null && !LauncherController.held().isEmpty();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyBinding TOGGLE_FPV = new KeyBinding("key.combat_drones.toggle_fpv", Keyboard.KEY_G, CATEGORY);
    /** Cycles Acro, Angle, Hover. */
    public static final KeyBinding FLIGHT_MODE = new KeyBinding("key.combat_drones.flight_mode", Keyboard.KEY_H, CATEGORY);
    public static final KeyBinding MOTORS = new KeyBinding("key.combat_drones.motors", Keyboard.KEY_N, CATEGORY);

    // Keyboard alternative to the mouse for pitch/roll; both can be used at once
    public static final KeyBinding PITCH_FORWARD = new KeyBinding("key.combat_drones.pitch_forward", Keyboard.KEY_UP, CATEGORY);
    public static final KeyBinding PITCH_BACK = new KeyBinding("key.combat_drones.pitch_back", Keyboard.KEY_DOWN, CATEGORY);
    public static final KeyBinding ROLL_LEFT = new KeyBinding("key.combat_drones.roll_left", Keyboard.KEY_LEFT, CATEGORY);
    public static final KeyBinding ROLL_RIGHT = new KeyBinding("key.combat_drones.roll_right", Keyboard.KEY_RIGHT, CATEGORY);
    /** Gunship gunner seat: cannon / rocket rack / howitzer. */
    public static final KeyBinding SWITCH_WEAPON = new KeyBinding("key.combat_drones.switch_weapon", Keyboard.KEY_J, CATEGORY);

    /** Stealth Bomber: flight view / bombsight. */
    public static final KeyBinding BOMB_SIGHT = new KeyBinding("key.combat_drones.bomb_sight", Keyboard.KEY_B, CATEGORY);

    /** Stinger and EMP Gun: load a round. Read by its key state, so it works even when another mod's binding takes the press. */
    public static final KeyBinding RELOAD = new KeyBinding("key.combat_drones.reload", HOLDING_LAUNCHER, Keyboard.KEY_R, CATEGORY);

    static final KeyBinding[] ALL = {TOGGLE_FPV, FLIGHT_MODE, MOTORS, PITCH_FORWARD, PITCH_BACK, ROLL_LEFT, ROLL_RIGHT, SWITCH_WEAPON, BOMB_SIGHT, RELOAD};

    private DroneKeys() {
    }
}
