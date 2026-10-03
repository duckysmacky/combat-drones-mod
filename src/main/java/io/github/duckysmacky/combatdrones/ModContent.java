package io.github.duckysmacky.combatdrones;

import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Registers every item and block. Placeholder plain items until each one gets behaviour. */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class ModContent {
    public static final CreativeTabs TAB = new CreativeTabs(CombatDrones.MOD_ID) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(fpvRemote);
        }
    };

    /** Every registered item, in creative-tab order. The client registers one model per entry. */
    public static final List<Item> ITEMS = new ArrayList<>();

    public static Block launchRamp;
    public static Block rearmStation;

    public static Item lightBattery;
    public static Item standardBattery;
    public static Item heavyBattery;
    public static Item fuelCan;
    public static Item fpvRemote;
    public static Item fpvGoggles;
    public static Item missionPlanner;
    public static Item fragGrenade;
    public static Item bomb;
    public static Item rocket;
    public static Item rifleRounds;

    private ModContent() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        launchRamp = simpleBlock("launch_ramp");
        rearmStation = simpleBlock("rearm_station");
        event.getRegistry().registerAll(launchRamp, rearmStation);
    }

    // Forge fires the block registry event before the item one, so the blocks exist here
    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        register(event, new ItemBlock(launchRamp).setRegistryName(launchRamp.getRegistryName()));
        register(event, new ItemBlock(rearmStation).setRegistryName(rearmStation.getRegistryName()));
        fpvRemote = register(event, simpleItem("fpv_remote").setMaxStackSize(1));
        fpvGoggles = register(event, simpleItem("fpv_goggles").setMaxStackSize(1));
        missionPlanner = register(event, simpleItem("mission_planner").setMaxStackSize(1));
        lightBattery = register(event, simpleItem("light_battery").setMaxStackSize(1));
        standardBattery = register(event, simpleItem("standard_battery").setMaxStackSize(1));
        heavyBattery = register(event, simpleItem("heavy_battery").setMaxStackSize(1));
        fuelCan = register(event, simpleItem("fuel_can").setMaxStackSize(1));
        fragGrenade = register(event, simpleItem("frag_grenade").setMaxStackSize(16));
        bomb = register(event, simpleItem("bomb").setMaxStackSize(4));
        rocket = register(event, simpleItem("rocket").setMaxStackSize(16));
        rifleRounds = register(event, simpleItem("rifle_rounds"));
        // One placeable item per data-defined type; the registry name is the type ID
        for (DroneType type : DroneTypes.all()) {
            register(event, simpleItem(type.getId()).setMaxStackSize(1));
        }
    }

    private static Block simpleBlock(String name) {
        return new Block(Material.IRON).setHardness(3.0F).setResistance(10.0F)
                .setRegistryName(CombatDrones.MOD_ID, name).setTranslationKey(CombatDrones.MOD_ID + "." + name).setCreativeTab(TAB);
    }

    private static Item simpleItem(String name) {
        return new Item().setRegistryName(CombatDrones.MOD_ID, name).setTranslationKey(CombatDrones.MOD_ID + "." + name).setCreativeTab(TAB);
    }

    private static Item register(RegistryEvent.Register<Item> event, Item item) {
        event.getRegistry().register(item);
        ITEMS.add(item);
        return item;
    }
}
