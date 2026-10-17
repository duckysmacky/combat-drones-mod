package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;

/**
 * One loop (a distance band or the dive layer) or one-shot (the terminal scream) of an {@link EngineVoice}, which
 * sets its position, volume and pitch every tick. No vanilla falloff ({@code NONE} still pans by position): the voice
 * applies its own distance gain. Plays by sound event name straight from sounds.json, so needs no registration.
 */
final class BandSound extends PositionedSound implements ITickableSound {
    private boolean done;

    BandSound(String event) {
        this(event, true);
    }

    BandSound(String event, boolean loop) {
        super(new ResourceLocation(CombatDrones.MOD_ID, event), SoundCategory.NEUTRAL);
        repeat = loop;
        repeatDelay = 0;
        attenuationType = AttenuationType.NONE;
    }

    void set(double x, double y, double z, double gain, double pitch) {
        xPosF = (float) x;
        yPosF = (float) y;
        zPosF = (float) z;
        volume = (float) gain;
        this.pitch = (float) pitch;
    }

    void stop() {
        done = true;
    }

    @Override
    public boolean isDonePlaying() {
        return done;
    }

    @Override
    public void update() {
        // Driven by the voice
    }
}
