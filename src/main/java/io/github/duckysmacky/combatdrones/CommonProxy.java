package io.github.duckysmacky.combatdrones;

import io.github.duckysmacky.combatdrones.entity.EntityEmpStation;
import net.minecraft.world.World;

/** Server-safe lifecycle hooks; {@code client.ClientProxy} adds the client-only setup. */
public class CommonProxy {
    public void preInit() {
    }

    public void init() {
    }

    /**
     * Client: spawns one of the mod's particles at a point with a starting velocity; {@code size} scales it (1 = about
     * a quarter block). Nothing on a dedicated server, so common code can call it freely.
     */
    public void fx(World world, Fx kind, double x, double y, double z, double velocityX, double velocityY, double velocityZ, float size) {
    }

    /** Client: keeps a switched-on EMP station's hum playing. Nothing on a dedicated server. */
    public void empHum(EntityEmpStation station) {
    }
}
