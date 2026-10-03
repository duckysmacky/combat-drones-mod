package io.github.duckysmacky.combatdrones;

import io.github.duckysmacky.combatdrones.entity.EntityBomb;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.item.ItemBattery;
import io.github.duckysmacky.combatdrones.item.ItemBlockWithTooltip;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.item.ItemGoggles;
import io.github.duckysmacky.combatdrones.item.ItemRemote;
import io.github.duckysmacky.combatdrones.item.ItemWithTooltip;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

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

    /** Cosmetic helmet: no protection, never wears out. Wearing it is what lets a player enter FPV. */
    private static final ItemArmor.ArmorMaterial GOGGLES_MATERIAL = EnumHelper.addArmorMaterial(
            CombatDrones.MOD_ID + ":goggles", CombatDrones.MOD_ID + ":goggles", 0, new int[] {0, 0, 0, 0}, 0, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.0F);

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
        register(event, new ItemBlockWithTooltip(launchRamp));
        register(event, new ItemBlockWithTooltip(rearmStation));
        fpvRemote = register(event, named(new ItemRemote(), "fpv_remote"));
        fpvGoggles = register(event, named(new ItemGoggles(GOGGLES_MATERIAL), "fpv_goggles"));
        missionPlanner = register(event, simpleItem("mission_planner").setMaxStackSize(1));
        lightBattery = register(event, named(new ItemBattery(BatteryPack.LIGHT), BatteryPack.LIGHT.itemName()));
        standardBattery = register(event, named(new ItemBattery(BatteryPack.STANDARD), BatteryPack.STANDARD.itemName()));
        heavyBattery = register(event, named(new ItemBattery(BatteryPack.HEAVY), BatteryPack.HEAVY.itemName()));
        fuelCan = register(event, simpleItem("fuel_can").setMaxStackSize(1));
        fragGrenade = register(event, simpleItem("frag_grenade").setMaxStackSize(16));
        bomb = register(event, simpleItem("bomb").setMaxStackSize(4));
        rocket = register(event, simpleItem("rocket").setMaxStackSize(16));
        rifleRounds = register(event, simpleItem("rifle_rounds"));
        // One placeable item per data-defined type; the registry name is the type ID
        for (DroneType type : DroneTypes.all()) {
            register(event, named(new ItemDrone(type.getId()), type.getId()));
        }
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        // Update every tick: FPV pilots and spectators need smooth motion, not the default 3-tick cadence
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityHandFlownDrone.class)
                .id(new ResourceLocation(CombatDrones.MOD_ID, "hand_flown_drone"), 0)
                .name(CombatDrones.MOD_ID + ".hand_flown_drone")
                .tracker(160, 1, false)
                .build());
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityBomb.class)
                .id(new ResourceLocation(CombatDrones.MOD_ID, "bomb"), 1)
                .name(CombatDrones.MOD_ID + ".bomb")
                .tracker(160, 1, true)
                .build());
    }

    public static Item batteryItem(BatteryPack pack) {
        switch (pack) {
            case STANDARD:
                return standardBattery;
            case HEAVY:
                return heavyBattery;
            default:
                return lightBattery;
        }
    }

    private static Block simpleBlock(String name) {
        return new Block(Material.IRON).setHardness(3.0F).setResistance(10.0F)
                .setRegistryName(CombatDrones.MOD_ID, name).setTranslationKey(CombatDrones.MOD_ID + "." + name).setCreativeTab(TAB);
    }

    private static Item simpleItem(String name) {
        return named(new ItemWithTooltip(), name);
    }

    private static Item named(Item item, String name) {
        return item.setRegistryName(CombatDrones.MOD_ID, name).setTranslationKey(CombatDrones.MOD_ID + "." + name).setCreativeTab(TAB);
    }

    private static Item register(RegistryEvent.Register<Item> event, Item item) {
        event.getRegistry().register(item);
        ITEMS.add(item);
        return item;
    }
}
