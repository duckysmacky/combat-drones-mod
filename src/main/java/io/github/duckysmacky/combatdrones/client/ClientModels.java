package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Client-only: Forge never loads this class on a dedicated server. */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class ClientModels {
    private static final ResourceLocation STINGER_ICON = new ResourceLocation(CombatDrones.MOD_ID, "stinger_icon");

    private ClientModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        for (Item item : ModContent.ITEMS) {
            if (item instanceof ItemDrone || item == ModContent.launchRamp || item == ModContent.lightRunway || item == ModContent.heavyRunway || item == ModContent.missilePad
                    || item == ModContent.empStation || item == ModContent.stinger) {
                item.setTileEntityItemStackRenderer(GeoModels.ItemRenderer.INSTANCE);
            }
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
        // The Stinger's inventory sprite is a second model, swapped in per view by StingerModel
        ModelBakery.registerItemVariants(ModContent.stinger, ModContent.stinger.getRegistryName(), STINGER_ICON);
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        ModelResourceLocation held = new ModelResourceLocation(ModContent.stinger.getRegistryName(), "inventory");
        IBakedModel heldModel = event.getModelRegistry().getObject(held);
        IBakedModel icon = event.getModelRegistry().getObject(new ModelResourceLocation(STINGER_ICON, "inventory"));
        if (heldModel != null && icon != null) {
            event.getModelRegistry().putObject(held, new StingerModel(heldModel, icon));
        }
    }
}
