package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityEmpOrb;
import io.github.duckysmacky.combatdrones.entity.EntityEmpStation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.Entity;
import net.minecraft.util.SoundCategory;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The hum and crackle of an EMP field, heard a little beyond it: a switched-on EMP Field Station, or an EMP Gun orb
 * in flight (following it, a little higher).
 */
public class EmpHumSound extends MovingSound {
    private static final Map<Entity, EmpHumSound> PLAYING = new WeakHashMap<>();

    private final Entity source;

    private EmpHumSound(Entity source) {
        super(ModSounds.EMP_HUM, SoundCategory.BLOCKS);
        this.source = source;
        repeat = true;
        repeatDelay = 0;
        update();
    }

    /** Client, every tick a station is on or an orb flies: starts its hum unless it's already playing. */
    static void ensure(Entity source) {
        EmpHumSound sound = PLAYING.get(source);
        if (sound == null || sound.isDonePlaying()) {
            sound = new EmpHumSound(source);
            PLAYING.put(source, sound);
            Minecraft.getMinecraft().getSoundHandler().playSound(sound);
        }
    }

    @Override
    public void update() {
        EntityEmpStation station = source instanceof EntityEmpStation ? (EntityEmpStation) source : null;
        EntityEmpOrb orb = source instanceof EntityEmpOrb ? (EntityEmpOrb) source : null;
        if (source.isDead || (station != null && !station.isActive()) || (orb != null && orb.getStopped() >= 0)) {
            donePlaying = true;
            return;
        }
        xPosF = (float) source.posX;
        yPosF = (float) (source.posY + source.height / 2);
        zPosF = (float) source.posZ;
        // Heard as far as the field reaches and a bit beyond (16 x volume blocks); the file sets the loudness
        float radius = station != null ? station.getRadius() : orb.getRadius();
        volume = Math.max(1, (radius + 16) / 16);
        // A station sags a little as its charge runs down
        pitch = station != null ? 0.94F + 0.06F * station.getCharge() / EntityEmpStation.MAX_CHARGE : 1.2F;
    }
}
