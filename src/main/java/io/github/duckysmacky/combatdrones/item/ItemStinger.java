package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import net.minecraft.item.Item;
import net.minecraft.util.SoundEvent;

/**
 * Stinger: an anti-air launcher holding one missile. Seeking, locking and firing run in {@code entity.StingerServer}.
 */
public class ItemStinger extends ItemLauncher {
    @Override
    public Item ammo() {
        return ModContent.stingerMissile;
    }

    @Override
    public int reloadTicks() {
        return (int) Math.round(ModConfig.stingerReloadSeconds * 20);
    }

    @Override
    public SoundEvent reloadSound() {
        return ModSounds.STINGER_RELOAD;
    }

    @Override
    public float zoom() {
        return 2.5F;
    }
}
