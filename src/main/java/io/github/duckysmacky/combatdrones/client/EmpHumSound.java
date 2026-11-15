package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityEmpStation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;

import java.util.Map;
import java.util.WeakHashMap;

/** The hum and crackle of a switched-on EMP Field Station, heard out to about 24 blocks. */
public class EmpHumSound extends MovingSound {
    private static final Map<EntityEmpStation, EmpHumSound> PLAYING = new WeakHashMap<>();

    private final EntityEmpStation station;

    private EmpHumSound(EntityEmpStation station) {
        super(ModSounds.EMP_HUM, SoundCategory.BLOCKS);
        this.station = station;
        repeat = true;
        repeatDelay = 0;
        volume = 1.5F; // range only (16 x volume blocks); the file sets the loudness
        update();
    }

    /** Client, every tick a station is on: starts its hum unless it's already playing. */
    static void ensure(EntityEmpStation station) {
        EmpHumSound sound = PLAYING.get(station);
        if (sound == null || sound.isDonePlaying()) {
            sound = new EmpHumSound(station);
            PLAYING.put(station, sound);
            Minecraft.getMinecraft().getSoundHandler().playSound(sound);
        }
    }

    @Override
    public void update() {
        if (station.isDead || !station.isActive()) {
            donePlaying = true;
            return;
        }
        xPosF = (float) station.posX;
        yPosF = (float) (station.posY + station.height / 2);
        zPosF = (float) station.posZ;
        // A little sag as the charge runs down
        pitch = 0.94F + 0.06F * station.getCharge() / EntityEmpStation.MAX_CHARGE;
    }
}
