package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityCruiseMissile;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.physics.CruiseMissileFlight;
import io.github.duckysmacky.combatdrones.type.Loadout;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import javax.vecmath.AxisAngle4d;

/** Fixed-wing unit drawn with its type's geo model, rotated by heading, pitch and synced bank. */
public class RenderRampUnit extends Render<EntityRampUnit> {
    /** Prop speed with the engine running, radians per tick (blurred). */
    private static final float PROP_SPEED = 2.6F;
    /** Control surfaces: degrees of deflection per degree-per-tick of roll or pitch rate, and the most they move. */
    private static final float SURFACE_GAIN = 9.0F;
    private static final float SURFACE_LIMIT = 22.0F;
    /** A gun barrel kicks back this many model pixels and returns over this many ticks. */
    private static final float RECOIL_PIXELS = 2.5F;
    private static final float RECOIL_TICKS = 8.0F;
    /** Per unit: when (entity ticks) each `recoil_*` bone last fired. */
    private static final Map<Entity, Map<String, Float>> RECOIL = new WeakHashMap<>();

    public RenderRampUnit(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.5F;
    }

    @Override
    public void doRender(EntityRampUnit unit, double x, double y, double z, float entityYaw, float partialTicks) {
        DroneType type = unit.getDroneType();
        if (type == null) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + unit.height / 2, z);
        AxisAngle4d rotation = new AxisAngle4d();
        rotation.set(unit.renderOrientation(partialTicks));
        GlStateManager.rotate((float) Math.toDegrees(rotation.angle), (float) rotation.x, (float) rotation.y, (float) rotation.z);
        String model = GeoModels.nameFor(type);
        boolean running = UnitEngineSound.isRunning(unit);
        GeoModels.spinProps(model, GeoModels.propAngle(unit, running ? PROP_SPEED : 0, partialTicks), GeoModels.propSpeed(unit));
        pose(unit, type, model, running, partialTicks);
        float tint = unit.hitFlashTicks > 0 ? 0.35F : 1.0F; // hit flash, like mobs turning red
        GeoModels.render(model, 1.0F, tint, tint);
        GlStateManager.popMatrix();
        super.doRender(unit, x, y, z, entityYaw, partialTicks);
    }

    /** A weapon on this unit just fired: its barrel bone ({@code recoil_gun}, {@code recoil_cannon}...) kicks back. */
    static void kick(Entity unit, String bone) {
        RECOIL.computeIfAbsent(unit, key -> new HashMap<>()).put(bone, (float) unit.ticksExisted);
    }

    /**
     * Everything that moves besides the props, from state every client already has: lamps, boost flame, control
     * surfaces following roll and pitch rate, the sensor ball, rockets left in the pods, barrel recoil, and the
     * missile's nozzle glow and fins. Bones a model doesn't have are skipped.
     */
    private static void pose(EntityRampUnit unit, DroneType type, String model, boolean running, float partialTicks) {
        float time = unit.ticksExisted + partialTicks;
        GeoModels.lights(model, running, time);
        GeoModels.show(model, "glow_boost", unit.isBoosting());
        GeoModels.show(model, "glow_exhaust", running); // jet exhausts

        float roll = MathHelper.clamp(MathHelper.wrapDegrees(unit.getBank() - unit.prevBank) * SURFACE_GAIN, -SURFACE_LIMIT, SURFACE_LIMIT);
        float pitch = MathHelper.clamp((unit.rotationPitch - unit.prevRotationPitch) * SURFACE_GAIN, -SURFACE_LIMIT, SURFACE_LIMIT);
        GeoModels.rotate(model, "aileron_r", -roll, 0, 0); // rolling right: right aileron up, left down
        GeoModels.rotate(model, "aileron_l", roll, 0, 0);
        GeoModels.rotate(model, "elevator", pitch, 0, 0); // pitching up (negative): trailing edge up

        // Sensor ball: follows the camera for whoever is looking through it, sweeps slowly for everyone else
        if (unit == RemoteController.getUnit() && !RemoteController.isStickControl()) {
            float yaw = unit.prevRotationYaw + MathHelper.wrapDegrees(unit.rotationYaw - unit.prevRotationYaw) * partialTicks;
            GeoModels.rotate(model, "turret", MathHelper.clamp(RemoteController.getCameraPitch(), -10, 90),
                    -MathHelper.wrapDegrees(RemoteController.getCameraYaw() - yaw), 0);
        } else {
            GeoModels.rotate(model, "turret", 35, (float) Math.sin(time * 0.03) * 50, 0);
        }

        int tips = GeoModels.show(model, "ammo_", true);
        if (tips > 0) {
            Loadout loadout = unit.getLoadout();
            for (int station = 0; station < Loadout.STATIONS; station++) {
                if (type.getWeapon(station) == DroneType.WeaponType.ROCKET_POD) {
                    int capacity = loadout.capacity(type, station);
                    int left = capacity <= 0 ? 0 : MathHelper.ceil(tips * loadout.getAmmo(station) / (float) capacity);
                    for (int tip = left; tip < tips; tip++) {
                        GeoModels.show(model, "ammo_" + tip, false);
                    }
                }
            }
        }

        Map<String, Float> fired = RECOIL.get(unit);
        if (fired != null) {
            for (Map.Entry<String, Float> shot : fired.entrySet()) {
                float age = (time - shot.getValue()) / RECOIL_TICKS;
                if (age >= 0 && age < 1) {
                    float back = RECOIL_PIXELS * (1 - age) * (1 - age);
                    // The chin gun points at the nose (-Z); the gunship's guns point out to starboard (+X)
                    boolean forward = shot.getKey().equals("recoil_gun");
                    GeoModels.offset(model, shot.getKey(), forward ? 0 : -back, 0, forward ? back : 0);
                }
            }
        }

        if (unit instanceof EntityCruiseMissile) {
            EntityCruiseMissile missile = (EntityCruiseMissile) unit;
            CruiseMissileFlight.Stage stage = missile.getStage();
            GeoModels.show(model, "glow_nozzle", unit.isBoosting() || stage == CruiseMissileFlight.Stage.IGNITION
                    || stage == CruiseMissileFlight.Stage.LIFTOFF || stage == CruiseMissileFlight.Stage.SLOW_CLIMB
                    || stage == CruiseMissileFlight.Stage.FAST_CLIMB);
            GeoModels.rotate(model, "fin_h", MathHelper.clamp(missile.getTiltForward(), -SURFACE_LIMIT, SURFACE_LIMIT), 0, 0);
            GeoModels.rotate(model, "fin_v", 0, MathHelper.clamp(missile.getTiltRight(), -SURFACE_LIMIT, SURFACE_LIMIT), 0);
        }
    }

    /** Draws a ramp unit's model unrotated at the GL origin (used on the launch rail). */
    static void renderStill(DroneType type) {
        String model = GeoModels.nameFor(type);
        GeoModels.spinProps(model, 0, 0);
        GeoModels.render(model, 1.0F, 1.0F, 1.0F);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityRampUnit unit) {
        return null; // GeoModels binds the per-type texture
    }
}
