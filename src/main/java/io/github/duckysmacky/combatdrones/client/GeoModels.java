package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.item.ItemLauncher;
import io.github.duckysmacky.combatdrones.item.ItemStinger;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
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
import org.lwjgl.opengl.GL11;

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
 * {@code glow*} bones are lamps and flames: hidden unless a renderer shows them, and drawn full-bright. Renderers
 * pose any other bone by name ({@link #rotate}, {@link #offset}, {@link #show}); a model without that bone ignores it.
 *
 * <p>A model's bones are shared by everything drawn with it, so every draw starts from {@link #reset} (which
 * {@link #spinProps} does) and sets what it needs.
 *
 * <p>Drawn in up to three passes: solid cubes, then blur discs (translucent, blended), then lit glow bones.
 */
public final class GeoModels implements IGeoRenderer<Object> {
    private static final GeoModels RENDERER = new GeoModels();
    /** Radians per tick above which a prop shows its blur disc instead of its blades. */
    private static final float BLUR_SPEED = 1.2F;
    /** How fast a prop spools toward its target speed, per tick. */
    private static final float SPOOL_RATE = 0.12F;
    private static final int SOLID = 0;
    private static final int BLUR = 1;
    private static final int GLOW = 2;
    private static final Map<GeoModel, float[]> BOUNDS = new HashMap<>();
    private static final Map<Entity, float[]> PROP_ANGLES = new WeakHashMap<>();
    /** Per model: every bone by name, with the rotation it was modelled with (radians, GeckoLib's signs). */
    private static final Map<GeoModel, Map<String, Posed>> BONES = new WeakHashMap<>();
    /** Which pass is being drawn, and which pass the bone subtree being walked belongs to. */
    private static int pass;
    private static int boneKind;
    private static boolean blurShown;
    private static boolean glowShown;

    private static final class Posed {
        final GeoBone bone;
        final float x;
        final float y;
        final float z;

        Posed(GeoBone bone) {
            this.bone = bone;
            x = bone.getRotationX();
            y = bone.getRotationY();
            z = bone.getRotationZ();
        }
    }
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
        return type.getCategory() == DroneType.Category.HAND_FLOWN ? "standard_drone" : "loitering_light";
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
        blurShown = false;
        glowShown = false;
        for (Posed posed : bones(model).values()) {
            if (!posed.bone.isHidden()) {
                blurShown |= posed.bone.getName().endsWith("_blur");
                glowShown |= posed.bone.getName().startsWith("glow");
            }
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0F, 1.0F, -1.0F);
        pass = SOLID;
        RENDERER.render(model, null, 0, red, green, blue, 1.0F);
        if (blurShown) {
            // Prop discs: translucent, and never hiding what's behind them
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.004F);
            GlStateManager.depthMask(false);
            pass = BLUR;
            RENDERER.render(model, null, 0, red, green, blue, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.disableBlend();
        }
        if (glowShown) {
            // Lamps and flames: lit by themselves, whatever the light where they are
            float skyLight = OpenGlHelper.lastBrightnessY;
            float blockLight = OpenGlHelper.lastBrightnessX;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            pass = GLOW;
            RENDERER.render(model, null, 0, 1.0F, 1.0F, 1.0F, 1.0F);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, blockLight, skyLight);
        }
        pass = SOLID;
        GlStateManager.popMatrix();
        return true;
    }

    /**
     * Advances and returns an entity's prop angle for this frame. Integrated per entity so changing RPM doesn't make
     * the props jump, and spooled toward {@code radiansPerTick} so engines wind up and down; kept client-side only,
     * keyed weakly so unloaded entities drop out. {@link #propSpeed} gives the spooled speed it turned at.
     */
    public static float propAngle(Entity entity, float radiansPerTick, float partialTicks) {
        float time = entity.ticksExisted + partialTicks;
        float[] state = PROP_ANGLES.computeIfAbsent(entity, key -> new float[] {time, 0, radiansPerTick});
        float elapsed = Math.max(0, time - state[0]);
        state[2] += (radiansPerTick - state[2]) * Math.min(1, elapsed * SPOOL_RATE);
        state[1] = (state[1] + state[2] * elapsed) % (float) (Math.PI * 2);
        state[0] = time;
        return state[1];
    }

    /** The speed (radians per tick) an entity's props are turning at right now, after spooling. */
    public static float propSpeed(Entity entity) {
        float[] state = PROP_ANGLES.get(entity);
        return state == null ? 0 : state[2];
    }

    private static Map<String, Posed> bones(GeoModel model) {
        return BONES.computeIfAbsent(model, key -> {
            Map<String, Posed> all = new HashMap<>();
            for (GeoBone bone : key.topLevelBones) {
                collect(all, bone);
            }
            return all;
        });
    }

    private static void collect(Map<String, Posed> all, GeoBone bone) {
        all.put(bone.getName(), new Posed(bone));
        for (GeoBone child : bone.childBones) {
            collect(all, child);
        }
    }

    /** Back to the modelled pose: every bone unrotated and unmoved, blades instead of blur discs, lamps off. */
    public static void reset(String name) {
        GeoModel model = get(name);
        if (model == null) {
            return;
        }
        for (Posed posed : bones(model).values()) {
            GeoBone bone = posed.bone;
            bone.setRotationX(posed.x);
            bone.setRotationY(posed.y);
            bone.setRotationZ(posed.z);
            bone.setPositionX(0);
            bone.setPositionY(0);
            bone.setPositionZ(0);
            bone.setHidden(bone.getName().endsWith("_blur") || bone.getName().startsWith("glow"));
        }
    }

    /**
     * Resets the model, then sets every {@code rotor*} / {@code prop*} bone to {@code angle}, swapping in blur discs
     * above blur speed.
     */
    public static void spinProps(String name, float angle, float radiansPerTick) {
        GeoModel model = get(name);
        if (model == null) {
            return;
        }
        reset(name);
        boolean blurred = Math.abs(radiansPerTick) > BLUR_SPEED;
        Map<String, Posed> all = bones(model);
        for (Posed posed : all.values()) {
            GeoBone bone = posed.bone;
            String boneName = bone.getName();
            boolean rotor = boneName.startsWith("rotor");
            if (!rotor && !boneName.startsWith("prop")) {
                continue;
            }
            boolean blur = boneName.endsWith("_blur");
            bone.setHidden(blur ? !blurred : blurred && all.containsKey(boneName + "_blur"));
            if (rotor) {
                bone.setRotationY(angle);
            } else {
                bone.setRotationZ(angle);
            }
        }
    }

    /**
     * Turns a bone from its modelled pose, in Blockbench degrees about the model's own axes (X: positive tips the
     * nose side down, Y: positive swings the nose toward the model's left). No-op if the model doesn't have it.
     */
    public static void rotate(String name, String bone, float x, float y, float z) {
        GeoModel model = get(name);
        Posed posed = model == null ? null : bones(model).get(bone);
        if (posed != null) {
            posed.bone.setRotationX(posed.x - (float) Math.toRadians(x));
            posed.bone.setRotationY(posed.y - (float) Math.toRadians(y));
            posed.bone.setRotationZ(posed.z + (float) Math.toRadians(z));
        }
    }

    /** Slides a bone from where it was modelled, in model pixels along its parent's axes (nose toward -Z). */
    public static void offset(String name, String bone, float x, float y, float z) {
        GeoModel model = get(name);
        Posed posed = model == null ? null : bones(model).get(bone);
        if (posed != null) {
            posed.bone.setPositionX(x);
            posed.bone.setPositionY(y);
            posed.bone.setPositionZ(z);
        }
    }

    /** Shows or hides every bone whose name starts with {@code prefix} (lamps, flames, racks). Returns how many. */
    public static int show(String name, String prefix, boolean visible) {
        GeoModel model = get(name);
        int count = 0;
        if (model != null) {
            for (Posed posed : bones(model).values()) {
                if (posed.bone.getName().startsWith(prefix)) {
                    posed.bone.setHidden(!visible);
                    count++;
                }
            }
        }
        return count;
    }

    /** Lamps a running aircraft shows: steady navigation lights and a strobe that flashes about once a second. */
    public static void lights(String name, boolean on, float time) {
        show(name, "glow_nav", on);
        show(name, "glow_strobe", on && time % 24 < 2);
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
            spinProps(name, 0, 0);
            if (stack.getItem() instanceof ItemLauncher) {
                poseLauncher(name, stack, stack.getItem() instanceof ItemStinger ? "missile" : "cell");
            }
            GlStateManager.scale(scale, scale, scale);
            GlStateManager.translate(-(box[0] + box[3]) / 2, -(box[1] + box[4]) / 2, -(box[2] + box[5]) / 2);
            render(name, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }

        /**
         * A launcher's round ({@code bone}) shows only while one is loaded. Reloading, in first person: the tube tips
         * nose-down, the new round slides in from the breech, the tube comes back up.
         */
        private static void poseLauncher(String name, ItemStack stack, String bone) {
            boolean loaded = ItemLauncher.isLoaded(stack);
            float reload = loaded || !LauncherModel.isFirstPerson() ? 0 : LauncherController.reloadProgress(Minecraft.getMinecraft().getRenderPartialTicks());
            show(name, bone, loaded || reload >= RELOAD_TIP);
            if (reload <= 0) {
                return;
            }
            float tip = ease(Math.min(reload, 1 - reload) / RELOAD_TIP);
            GlStateManager.translate(0, -0.12F * tip, 0);
            GlStateManager.rotate(50 * tip, 1, 0, 0);
            float seated = ease((reload - RELOAD_TIP) / (1 - 2 * RELOAD_TIP - 0.1F));
            offset(name, bone, 0, 0, 24 * (1 - seated));
        }

        private static float ease(float amount) {
            float clamped = Math.max(0, Math.min(1, amount));
            return clamped * clamped * (3 - 2 * clamped);
        }

        /** Share of the reload spent tipping the tube down, and again bringing it back up. */
        private static final float RELOAD_TIP = 0.25F;
    }

    /**
     * GeckoLib's default, minus its crash on missing faces: a geo.json face without UVs (zero-area faces, or faces
     * Blockbench leaves untextured) builds a null quad, and the stock loop dereferences it.
     */
    @Override
    public void renderCube(BufferBuilder builder, GeoCube cube, float red, float green, float blue, float alpha) {
        if (boneKind != pass) {
            return;
        }
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

    /** Tags the bone subtree being walked with its pass: blur discs and glow bones are drawn after the solid cubes. */
    @Override
    public void renderRecursively(BufferBuilder builder, GeoBone bone, float red, float green, float blue, float alpha) {
        int outer = boneKind;
        if (bone.getName().startsWith("glow")) {
            boneKind = GLOW;
        } else if (bone.getName().endsWith("_blur")) {
            boneKind = BLUR;
        }
        IGeoRenderer.super.renderRecursively(builder, bone, red, green, blue, alpha);
        boneKind = outer;
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
