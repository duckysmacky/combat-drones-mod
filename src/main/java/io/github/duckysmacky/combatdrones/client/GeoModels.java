package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.GeckoLib;
import software.bernie.geckolib3.geo.render.built.GeoBone;
import software.bernie.geckolib3.geo.render.built.GeoCube;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.geo.render.built.GeoQuad;
import software.bernie.geckolib3.geo.render.built.GeoVertex;
import software.bernie.geckolib3.model.provider.GeoModelProvider;
import software.bernie.geckolib3.renderers.geo.IGeoRenderer;
import software.bernie.geckolib3.resource.GeckoLibCache;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Client-only bridge to GeckoLib, used purely as a Bedrock {@code geo.json} loader and quad renderer: nothing in
 * common code implements {@code IAnimatable}, so the dedicated server never needs GeckoLib.
 *
 * <p>Models live at {@code geo/<name>.geo.json} with the texture at {@code textures/entity/<name>.png}, where the
 * name is the drone type ID (or "launch_ramp", "bomb_light", ...). They're authored in Blockbench convention: pixels,
 * Y up, nose toward north (-Z), centred on the hitbox centre (ground at y = 0 for the ramp). Rendering mirrors Z so
 * the nose points along +Z, the forward axis of the flight model, and the result is in blocks.
 *
 * <p>Bone naming drives the moving parts: {@code rotor*} bones spin about Y (multirotor props), {@code prop*} bones
 * about Z (plane propellers). A sibling bone named {@code <bone>_blur} replaces the blades while spinning fast.
 */
public final class GeoModels implements IGeoRenderer<Object> {
    private static final GeoModels RENDERER = new GeoModels();
    /** Radians per tick above which a prop shows its blur disc instead of its blades. */
    private static final float BLUR_SPEED = 1.2F;
    private static final Map<GeoModel, float[]> BOUNDS = new HashMap<>();
    private static final Map<Entity, float[]> PROP_ANGLES = new WeakHashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private GeoModels() {
    }

    /** Registers GeckoLib's resource reload listener, which loads every {@code geo/} file. Client pre-init. */
    public static void init() {
        GeckoLib.initialize();
    }

    @Nullable
    public static GeoModel get(String name) {
        return GeckoLibCache.getInstance().getGeoModels().get(new ResourceLocation(CombatDrones.MOD_ID, "geo/" + name + ".geo.json"));
    }

    public static ResourceLocation texture(String name) {
        return new ResourceLocation(CombatDrones.MOD_ID, "textures/entity/" + name + ".png");
    }

    /**
     * The model name for a drone type: its own ID, or the family default when a data-only variant has no art yet,
     * so adding a type never needs a model to show up.
     */
    public static String nameFor(DroneType type) {
        if (get(type.getId()) != null) {
            return type.getId();
        }
        return type.getCategory() == DroneType.Category.HAND_FLOWN ? "racer_standard" : "loitering_light";
    }

    /** Draws a model at the current GL origin, nose along +Z. Returns false if the model isn't loaded. */
    public static boolean render(String name, float red, float green, float blue) {
        GeoModel model = get(name);
        if (model == null) {
            if (MISSING.add(name)) {
                CombatDrones.logger.warn("No geo model loaded for '{}' (expected assets/combat_drones/geo/{}.geo.json)", name, name);
            }
            return false;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture(name));
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0F, 1.0F, -1.0F);
        RENDERER.render(model, null, 0, red, green, blue, 1.0F);
        GlStateManager.popMatrix();
        return true;
    }

    /**
     * Advances and returns an entity's prop angle for this frame. Integrated per entity so changing RPM doesn't make
     * the props jump; kept client-side only, keyed weakly so unloaded entities drop out.
     */
    public static float propAngle(Entity entity, float radiansPerTick, float partialTicks) {
        float time = entity.ticksExisted + partialTicks;
        float[] state = PROP_ANGLES.computeIfAbsent(entity, key -> new float[] {time, 0});
        state[1] = (state[1] + radiansPerTick * Math.max(0, time - state[0])) % (float) (Math.PI * 2);
        state[0] = time;
        return state[1];
    }

    /** Sets every {@code rotor*} / {@code prop*} bone to {@code angle}, swapping in blur discs above blur speed. */
    public static void spinProps(String name, float angle, float radiansPerTick) {
        GeoModel model = get(name);
        if (model != null) {
            boolean blurred = Math.abs(radiansPerTick) > BLUR_SPEED;
            for (GeoBone bone : model.topLevelBones) {
                spinProps(bone, angle, blurred);
            }
        }
    }

    private static void spinProps(GeoBone bone, float angle, boolean blurred) {
        String name = bone.getName();
        boolean rotor = name.startsWith("rotor");
        if (rotor || name.startsWith("prop")) {
            boolean blur = name.endsWith("_blur");
            boolean hasBlur = !blur && bone.parent != null && bone.parent.childBones.stream().anyMatch(other -> other.getName().equals(name + "_blur"));
            bone.setHidden(blur ? !blurred : hasBlur && blurred);
            if (rotor) {
                bone.setRotationY(angle);
            } else {
                bone.setRotationZ(angle);
            }
        }
        for (GeoBone child : bone.childBones) {
            spinProps(child, angle, blurred);
        }
    }

    /** Hides or shows a named bone (bomb racks, grenade clips...). No-op if the model doesn't have it. */
    public static void setVisible(String name, String bone, boolean visible) {
        GeoModel model = get(name);
        if (model != null) {
            model.getBone(bone).ifPresent(found -> found.setHidden(!visible));
        }
    }

    /**
     * Unrotated model bounds in blocks after the Z mirror: {minX, minY, minZ, maxX, maxY, maxZ}. Used to fit item
     * icons and map icons; ignores bone rotations, which only affect small parts.
     */
    public static float[] bounds(String name) {
        GeoModel model = get(name);
        if (model == null) {
            return new float[] {-0.5F, -0.5F, -0.5F, 0.5F, 0.5F, 0.5F};
        }
        return BOUNDS.computeIfAbsent(model, key -> {
            float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (GeoBone bone : key.topLevelBones) {
                grow(box, bone);
            }
            return box;
        });
    }

    private static void grow(float[] box, GeoBone bone) {
        for (GeoCube cube : bone.childCubes) {
            for (GeoQuad quad : cube.quads) {
                if (quad == null) {
                    continue;
                }
                for (GeoVertex vertex : quad.vertices) {
                    float[] point = {vertex.position.getX(), vertex.position.getY(), -vertex.position.getZ()};
                    for (int axis = 0; axis < 3; axis++) {
                        box[axis] = Math.min(box[axis], point[axis]);
                        box[axis + 3] = Math.max(box[axis + 3], point[axis]);
                    }
                }
            }
        }
        for (GeoBone child : bone.childBones) {
            grow(box, child);
        }
    }

    /**
     * Map icon: the model seen from straight above, nose along {@code heading} (Minecraft yaw), longest horizontal
     * side {@code sizePx} GUI pixels, centred on the given screen point. Unlit, so the paint reads as it is.
     */
    public static void renderTopDown(String name, int screenX, int screenY, float heading, float sizePx) {
        float[] box = bounds(name);
        float scale = sizePx / Math.max(box[3] - box[0], box[5] - box[2]);
        GlStateManager.pushMatrix();
        GlStateManager.translate(screenX, screenY, 200);
        // World up toward the viewer, south down the screen: a mirror, as GUI space is left-handed
        GlStateManager.scale(scale, -scale, scale);
        GlStateManager.rotate(90, 1, 0, 0);
        GlStateManager.rotate(-heading, 0, 1, 0);
        GlStateManager.translate(-(box[0] + box[3]) / 2, 0, -(box[2] + box[5]) / 2);
        GlStateManager.disableLighting();
        GlStateManager.enableDepth();
        spinProps(name, 0, 0); // blades, not blur discs, whatever the last world frame left
        render(name, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    /**
     * 3D inventory icon for vehicles and the launch ramp: the item's own geo model, fitted into the unit cube the
     * {@code builtin/entity} item model renders in (display transforms come from {@code item/vehicle_base.json}).
     */
    public static final class ItemRenderer extends TileEntityItemStackRenderer {
        public static final ItemRenderer INSTANCE = new ItemRenderer();

        @Override
        public void renderByItem(ItemStack stack) {
            ResourceLocation id = stack.getItem().getRegistryName();
            if (id == null) {
                return;
            }
            DroneType type = DroneTypes.get(id.getPath());
            String name = type != null ? nameFor(type) : id.getPath();
            float[] box = bounds(name);
            float scale = 1.0F / Math.max(box[3] - box[0], Math.max(box[4] - box[1], box[5] - box[2]));
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.5F, 0.5F, 0.5F);
            GlStateManager.scale(scale, scale, scale);
            GlStateManager.translate(-(box[0] + box[3]) / 2, -(box[1] + box[4]) / 2, -(box[2] + box[5]) / 2);
            spinProps(name, 0, 0);
            render(name, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    /**
     * GeckoLib's default, minus its crash on missing faces: a geo.json face without UVs (zero-area faces, or faces
     * Blockbench leaves untextured) builds a null quad, and the stock loop dereferences it.
     */
    @Override
    public void renderCube(BufferBuilder builder, GeoCube cube, float red, float green, float blue, float alpha) {
        MATRIX_STACK.moveToPivot(cube);
        MATRIX_STACK.rotate(cube);
        MATRIX_STACK.moveBackFromPivot(cube);
        for (GeoQuad quad : cube.quads) {
            if (quad == null) {
                continue;
            }
            Vector3f normal = new Vector3f(quad.normal.getX(), quad.normal.getY(), quad.normal.getZ());
            MATRIX_STACK.getNormalMatrix().transform(normal);
            // Flat cubes: keep the normal facing outward on the side that has one (as GeckoLib does)
            if ((cube.size.y == 0 || cube.size.z == 0) && normal.getX() < 0) {
                normal.x *= -1;
            }
            if ((cube.size.x == 0 || cube.size.z == 0) && normal.getY() < 0) {
                normal.y *= -1;
            }
            if ((cube.size.x == 0 || cube.size.y == 0) && normal.getZ() < 0) {
                normal.z *= -1;
            }
            for (GeoVertex vertex : quad.vertices) {
                Vector4f position = new Vector4f(vertex.position.getX(), vertex.position.getY(), vertex.position.getZ(), 1);
                MATRIX_STACK.getModelMatrix().transform(position);
                builder.pos(position.getX(), position.getY(), position.getZ()).tex(vertex.textureU, vertex.textureV)
                        .color(red, green, blue, alpha).normal(normal.getX(), normal.getY(), normal.getZ()).endVertex();
            }
        }
    }

    @Override
    public GeoModelProvider getGeoModelProvider() {
        return null; // only used by GeckoLib's own animated renderers
    }

    @Override
    public ResourceLocation getTextureLocation(Object animatable) {
        return null; // the caller binds the texture
    }
}
