package io.github.duckysmacky.combatdrones.client.hud;

import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;

/**
 * The gadgets' own voice: short tones for key presses, picks, accepted and refused actions and the lock. Played
 * by name straight from sounds.json on this client only, like the engine bands, so they aren't registered events.
 */
public final class UiSounds {
    public static final String CLICK = "ui.click";
    public static final String SELECT = "ui.select";
    public static final String CONFIRM = "ui.confirm";
    public static final String DENY = "ui.deny";
    public static final String LOCK_TICK = "ui.lock_tick";
    public static final String LOCK_LOST = "ui.lock_lost";
    /** A feed going down: the same falling tone as a lost lock. */
    public static final String LINK_DOWN = "ui.link_down";
    /** The Stinger's seeker: a beep while it works on a target, a steady tone (a loop) once it has it. */
    public static final String STINGER_SEEK = "stinger.seek";
    public static final String STINGER_LOCK = "stinger.lock";
    private static final float VOLUME = 0.7F;

    private UiSounds() {
    }

    public static void play(String event) {
        play(event, 1.0F);
    }

    public static void play(String event, float pitch) {
        Minecraft.getMinecraft().getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(CombatDrones.MOD_ID, event),
                SoundCategory.MASTER, VOLUME, pitch, false, 0, ISound.AttenuationType.NONE, 0, 0, 0));
    }
}
