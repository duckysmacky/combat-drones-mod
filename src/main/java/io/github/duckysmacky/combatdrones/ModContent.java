package io.github.duckysmacky.combatdrones;

import io.github.duckysmacky.combatdrones.entity.EntityBomb;
import io.github.duckysmacky.combatdrones.entity.EntityCruiseMissile;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import io.github.duckysmacky.combatdrones.entity.EntityMissilePad;
import io.github.duckysmacky.combatdrones.entity.EntityPilotBody;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.entity.EntityRocket;
import io.github.duckysmacky.combatdrones.item.ItemBattery;
import io.github.duckysmacky.combatdrones.item.ItemBlockWithTooltip;
import io.github.duckysmacky.combatdrones.item.ItemBomb;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.item.ItemGoggles;
import io.github.duckysmacky.combatdrones.item.ItemLaunchRamp;
import io.github.duckysmacky.combatdrones.item.ItemMissilePad;
import io.github.duckysmacky.combatdrones.item.ItemMissionPlanner;
import io.github.duckysmacky.combatdrones.item.ItemRemote;
import io.github.duckysmacky.combatdrones.item.ItemMissileTablet;
import io.github.duckysmacky.combatdrones.item.ItemWithTooltip;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.BombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import io.github.duckysmacky.combatdrones.type.RocketType;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Registers every item and block. Placeholder plain items until each one gets behaviour. */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID)
public final class ModContent {
    public static final CreativeTabs TAB = new CreativeTabs(CombatDrones.MOD_ID) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(fpvRemote);
        }

        /** Grouped by role (see {@link #tabRank}) instead of registry ID order, which existing worlds would keep. */
        @Override
        @SideOnly(Side.CLIENT)
        public void displayAllRelevantItems(NonNullList<ItemStack> items) {
            super.displayAllRelevantItems(items);
            items.sort(Comparator.comparingInt(ModContent::tabRank));
        }
    };

    /** Hand-flown, then ramp-launched drones in this order; types not listed go last in their group. */
    private static final List<String> DRONE_TAB_ORDER = Arrays.asList("racer_micro", "racer_standard", "racer_longrange", "dji_drone",
            "loitering_light", "loitering_heavy", "strike_plane", "attack_plane", "gunship", "recon_uav", "cruise_missile");

    /**
     * Creative tab position: FPV gear (0+), hand-flown drones (100+), batteries and payloads (200+), ramp gear (400+),
     * ramp units (500+), their ammo (600+), anything else (800), the not yet functional Rearm Station last.
     */
    private static int tabRank(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemDrone) {
            DroneType type = ((ItemDrone) item).getDroneType();
            int index = type == null ? -1 : DRONE_TAB_ORDER.indexOf(type.getId());
            boolean handFlown = type != null && type.getCategory() == DroneType.Category.HAND_FLOWN;
            return (handFlown ? 100 : 500) + (index < 0 ? 99 : index);
        }
        int[] bases = {0, 200, 400, 600};
        List<List<Item>> groups = Arrays.asList(
                Arrays.asList(fpvRemote, fpvGoggles),
                Arrays.asList(lightBattery, standardBattery, heavyBattery, fragGrenade, bombLight, bomb, bombHeavy),
                Arrays.asList(launchRamp, planeRamp, missionPlanner, fuelCan, missilePad, missileTablet),
                Arrays.asList(rocket, rocketHeavy, rifleRounds, cannonShell));
        for (int group = 0; group < groups.size(); group++) {
            int index = groups.get(group).indexOf(item);
            if (index >= 0) {
                return bases[group] + index;
            }
        }
        return item == Item.getItemFromBlock(rearmStation) ? 900 : 800;
    }

    /** Cosmetic helmet: no protection, never wears out. Wearing it is what lets a player enter FPV. */
    private static final ItemArmor.ArmorMaterial GOGGLES_MATERIAL = EnumHelper.addArmorMaterial(
            CombatDrones.MOD_ID + ":goggles", CombatDrones.MOD_ID + ":goggles", 0, new int[] {0, 0, 0, 0}, 0, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.0F);

    /** Every registered item, in creative-tab order. The client registers one model per entry. */
    public static final List<Item> ITEMS = new ArrayList<>();

    public static Item launchRamp;
    public static Item planeRamp;
    public static Item missilePad;
    public static Item missileTablet;
    public static Block rearmStation;

    public static Item lightBattery;
    public static Item standardBattery;
    public static Item heavyBattery;
    public static Item fuelCan;
    public static Item fpvRemote;
    public static Item fpvGoggles;
    public static Item missionPlanner;
    public static Item fragGrenade;
    public static Item bombLight;
    public static Item bomb;
    public static Item bombHeavy;
    public static Item rocket;
    public static Item rocketHeavy;
    public static Item rifleRounds;
    public static Item cannonShell;

    private ModContent() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        rearmStation = simpleBlock("rearm_station");
        event.getRegistry().registerAll(rearmStation);
    }

    // Forge fires the block registry event before the item one, so the blocks exist here
    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        launchRamp = register(event, named(new ItemLaunchRamp(EntityLaunchRamp.Kind.MUNITION), EntityLaunchRamp.Kind.MUNITION.getName()));
        planeRamp = register(event, named(new ItemLaunchRamp(EntityLaunchRamp.Kind.PLANE), EntityLaunchRamp.Kind.PLANE.getName()));
        register(event, new ItemBlockWithTooltip(rearmStation));
        fpvRemote = register(event, named(new ItemRemote(), "fpv_remote"));
        fpvGoggles = register(event, named(new ItemGoggles(GOGGLES_MATERIAL), "fpv_goggles"));
        missionPlanner = register(event, named(new ItemMissionPlanner(), "mission_planner"));
        missilePad = register(event, named(new ItemMissilePad(), "missile_pad"));
        missileTablet = register(event, named(new ItemMissileTablet(), "missile_control_tablet"));
        lightBattery = register(event, named(new ItemBattery(BatteryPack.LIGHT), BatteryPack.LIGHT.itemName()));
        standardBattery = register(event, named(new ItemBattery(BatteryPack.STANDARD), BatteryPack.STANDARD.itemName()));
        heavyBattery = register(event, named(new ItemBattery(BatteryPack.HEAVY), BatteryPack.HEAVY.itemName()));
        fuelCan = register(event, simpleItem("fuel_can").setMaxStackSize(16));
        fragGrenade = register(event, simpleItem("frag_grenade").setMaxStackSize(16));
        bombLight = register(event, named(new ItemBomb(BombType.LIGHT, 64), BombType.LIGHT.itemName()));
        bomb = register(event, named(new ItemBomb(BombType.MEDIUM, 32), BombType.MEDIUM.itemName()));
        bombHeavy = register(event, named(new ItemBomb(BombType.HEAVY, 16), BombType.HEAVY.itemName()));
        rocket = register(event, simpleItem(RocketType.LIGHT.itemName()));
        rocketHeavy = register(event, simpleItem(RocketType.HEAVY.itemName()).setMaxStackSize(16));
        rifleRounds = register(event, simpleItem("rifle_rounds"));
        cannonShell = register(event, simpleItem("cannon_shell"));
        // One placeable item per data-defined type; the registry name is the type ID
        for (DroneType type : DroneTypes.all()) {
            register(event, named(new ItemDrone(type.getId()), type.getId()));
        }
    }

    /** The Launch Ramp used to be a block; worlds from before it became an entity just drop the old blocks. */
    @SubscribeEvent
    public static void dropRetiredBlocks(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getAllMappings()) {
            if (mapping.key.equals(new ResourceLocation(CombatDrones.MOD_ID, "launch_ramp"))) {
                mapping.ignore();
            }
        }
    }

    /**
     * Renamed items carry over: the Missile Control Tablet was briefly the Strike Laptop (link and all), and the
     * Strike and Attack Planes were the two Gunship Planes (fuel and ammo kept).
     */
    @SubscribeEvent
    public static void remapRenamedItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            if (mapping.key.equals(new ResourceLocation(CombatDrones.MOD_ID, "strike_laptop"))) {
                mapping.remap(missileTablet);
                continue;
            }
            String renamed = DroneTypes.migrateId(mapping.key.getPath());
            if (mapping.key.getNamespace().equals(CombatDrones.MOD_ID) && !renamed.equals(mapping.key.getPath())) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(CombatDrones.MOD_ID, renamed));
                if (item != null) {
                    mapping.remap(item);
                }
            }
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
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityRampUnit.class)
                .id(new ResourceLocation(CombatDrones.MOD_ID, "ramp_unit"), 2)
                .name(CombatDrones.MOD_ID + ".ramp_unit")
                .tracker(160, 1, false)
                .build());
        // Static: the yaw it was placed with arrives in the spawn packet, nothing to stream afterwards
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityLaunchRamp.class)
                .id(new ResourceLocation(CombatDrones.MOD_ID, "launch_ramp"), 3)
                .name(CombatDrones.MOD_ID + ".launch_ramp")
                .tracker(128, 20, false)
                .build());
        // Stands still; equipment changes go out with the regular living-entity updates
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityPilotBody.class)
                .id(new ResourceLocation(CombatDrones.MOD_ID, "pilot_body"), 4)
                .name(CombatDrones.MOD_ID + ".pilot_body")
                .tracker(128, 3, false)
                .build());
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityRocket.class)
                .id(new ResourceLocation(CombatDrones.MOD_ID, "rocket"), 5)
                .name(CombatDrones.MOD_ID + ".rocket")
                .tracker(160, 1, true)
                .build());
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityMissilePad.class)
                .id(new ResourceLocation(CombatDrones.MOD_ID, "missile_pad"), 6)
                .name(CombatDrones.MOD_ID + ".missile_pad")
                .tracker(160, 20, false)
                .build());
        // Seen from far off while it climbs; every tick for the pilot's camera in the dive
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityCruiseMissile.class)
                .id(new ResourceLocation(CombatDrones.MOD_ID, "cruise_missile"), 7)
                .name(CombatDrones.MOD_ID + ".cruise_missile")
                .tracker(256, 1, false)
                .build());
    }

    public static Item bombItem(BombType type) {
        switch (type) {
            case LIGHT:
                return bombLight;
            case HEAVY:
                return bombHeavy;
            default:
                return bomb;
        }
    }

    public static Item rampItem(EntityLaunchRamp.Kind kind) {
        return kind == EntityLaunchRamp.Kind.PLANE ? planeRamp : launchRamp;
    }

    public static Item rocketItem(RocketType kind) {
        return kind == RocketType.HEAVY ? rocketHeavy : rocket;
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
        return named(new Block(Material.IRON).setHardness(3.0F).setResistance(10.0F), name);
    }

    private static Block named(Block block, String name) {
        return block.setRegistryName(CombatDrones.MOD_ID, name).setTranslationKey(CombatDrones.MOD_ID + "." + name).setCreativeTab(TAB);
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
