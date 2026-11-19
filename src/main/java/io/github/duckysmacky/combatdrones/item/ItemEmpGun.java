package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import net.minecraft.item.Item;
import net.minecraft.util.SoundEvent;

/**
 * EMP Gun: a launcher holding one EMP Cell. Holding left-click charges it; releasing once charged fires an orb
 * carrying an EMP field ({@code entity.EntityEmpOrb}). Charging and firing run in {@code entity.EmpGunServer}.
 */
public class ItemEmpGun extends ItemLauncher {
    @Override
    public Item ammo() {
        return ModContent.empCell;
    }

    @Override
    public int reloadTicks() {
        return (int) Math.round(ModConfig.gunReloadSeconds * 20);
    }

    @Override
    public SoundEvent reloadSound() {
        return ModSounds.EMP_GUN_RELOAD;
    }

    @Override
    public float zoom() {
        return 2.0F;
    }

    /** Ticks left-click must be held before releasing fires. */
    public static int chargeTicks() {
        return Math.max(1, (int) Math.round(ModConfig.gunChargeSeconds * 20));
    }
}
