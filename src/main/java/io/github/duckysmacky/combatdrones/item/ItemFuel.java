package io.github.duckysmacky.combatdrones.item;

import io.github.duckysmacky.combatdrones.type.FuelType;

/** A fuel item: fills the tank of the units that burn {@link #getFuelType()}. */
public class ItemFuel extends ItemWithTooltip {
    private final FuelType fuelType;

    public ItemFuel(FuelType fuelType) {
        this.fuelType = fuelType;
        setMaxStackSize(16);
    }

    public FuelType getFuelType() {
        return fuelType;
    }
}
