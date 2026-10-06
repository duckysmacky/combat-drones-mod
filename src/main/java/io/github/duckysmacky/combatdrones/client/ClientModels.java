package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Client-only: Forge never loads this class on a dedicated server. */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class ClientModels {
    private ClientModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        for (Item item : ModContent.ITEMS) {
            if (item instanceof ItemDrone || item == ModContent.launchRamp || item == ModContent.missilePad) {
                item.setTileEntityItemStackRenderer(GeoModels.ItemRenderer.INSTANCE);
            }
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
