package io.github.duckysmacky.combatdrones.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FuelTypeTest {
    private static FuelType fuelOf(String id) throws IOException {
        try (Reader reader = Files.newBufferedReader(Paths.get("src/main/resources/assets/combat_drones/drone_types/" + id + ".json"))) {
            return FuelType.forUnit(DroneType.parse(id, reader));
        }
    }

    @Test
    void bundledTypesBurnTheirClassFuel() throws IOException {
        assertEquals(FuelType.AVGAS, fuelOf("loitering_light"));
        assertEquals(FuelType.AVGAS, fuelOf("loitering_heavy"));
        assertEquals(FuelType.JET_A1, fuelOf("bullet_plane"));
        assertEquals(FuelType.JET_A1, fuelOf("rocket_plane"));
        assertEquals(FuelType.JET_A1, fuelOf("recon_uav"));
        assertEquals(FuelType.JP8, fuelOf("gunship"));
        assertEquals(FuelType.JP8, fuelOf("stealth_bomber"));
        assertEquals(FuelType.JP10, fuelOf("cruise_missile"));
    }

    @Test
    void itemNames() {
        assertEquals("jet_a1_fuel", FuelType.JET_A1.itemName());
        assertEquals("jp10_fuel", FuelType.JP10.itemName());
    }
}
