package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.type.RocketType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Rocket or cannon shell fired by a plane: flies straight (no drop) and explodes on the first block or entity in its
 * path, credited to the plane's owner. Checked with a raytrace along each tick's movement, so it can't tunnel through
 * thin targets at speed. Blast power and direct-hit damage are fixed when it's fired, so a config reload mid-flight
 * doesn't change it.
 */
public class EntityRocket extends Entity {
    private static final int LIFETIME_TICKS = 100;

    /** What was fired: picks the model and trail. Synced so clients draw the right one. */
    public enum Kind {
        ROCKET("rocket", true),
        HEAVY_ROCKET("rocket_heavy", true),
        CANNON_SHELL("cannon_shell", false),
        HOWITZER_SHELL("howitzer_shell", false);

        private final String modelName;
        private final boolean rocketMotor;

        Kind(String modelName, boolean rocketMotor) {
            this.modelName = modelName;
            this.rocketMotor = rocketMotor;
        }

        public String modelName() {
            return modelName;
        }

        /** Rockets trail smoke and a flame; shells only a faint tracer. */
        public boolean hasRocketMotor() {
            return rocketMotor;
        }

        static Kind byIndex(int index) {
            Kind[] kinds = values();
            return index >= 0 && index < kinds.length ? kinds[index] : ROCKET;
        }
    }

    private static final DataParameter<Integer> KIND = EntityDataManager.createKey(EntityRocket.class, DataSerializers.VARINT);

    /** The plane that fired it (never hit by its own rocket); only known until a reload. */
    @Nullable
    private Entity shooter;
    @Nullable
    private UUID owner;
    private int age;
    private float blastPower;
    /** Extra damage to an entity it hits directly (cannon shells), on top of the blast. */
    private float directDamage;

    public EntityRocket(World world) {
        super(world);
        setSize(0.25F, 0.25F);
    }

    public EntityRocket(World world, Entity shooter, @Nullable UUID owner, Vec3d position, Vec3d velocity, Kind kind, float blastPower,
                        float directDamage) {
        this(world);
        this.shooter = shooter;
        this.owner = owner;
        this.blastPower = blastPower;
        this.directDamage = directDamage;
        dataManager.set(KIND, kind.ordinal());
        setPosition(position.x, position.y, position.z);
        motionX = velocity.x;
        motionY = velocity.y;
        motionZ = velocity.z;
        float horizontal = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        rotationYaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        rotationPitch = (float) -Math.toDegrees(Math.atan2(velocity.y, horizontal));
    }

    @Override
    protected void entityInit() {
        dataManager.register(KIND, 0);
    }

    public Kind getKind() {
        return Kind.byIndex(dataManager.get(KIND));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        Vec3d from = new Vec3d(posX, posY, posZ);
        Vec3d to = from.add(motionX, motionY, motionZ);
        if (world.isRemote) {
            if (getKind().hasRocketMotor()) {
                Exhaust.rocketTrail(this);
            } else {
                Exhaust.shellTracer(this);
            }
            setPosition(to.x, to.y, to.z);
            return;
        }
        RayTraceResult hit = Raytrace.firstResult(world, from, to, shooter);
        if (hit != null) {
            if (hit.entityHit != null && directDamage > 0) {
                if (hit.entityHit instanceof EntityLivingBase) {
                    hit.entityHit.hurtResistantTime = 0; // the blast right after must still land
                }
                hit.entityHit.attackEntityFrom(new EntityDamageSourceIndirect("combat_drones.cannon", this, blame()).setProjectile(), directDamage);
            }
            explode(hit.hitVec);
            return;
        }
        setPosition(to.x, to.y, to.z);
        if (++age >= LIFETIME_TICKS) {
            explode(to);
        }
    }

    @Nullable
    private EntityLivingBase blame() {
        return owner != null ? world.getPlayerEntityByUUID(owner) : null;
    }

    private void explode(Vec3d at) {
        setDead();
        DroneExplosion.detonate((WorldServer) world, this, blame(), at.x, at.y, at.z, blastPower);
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setInteger("Age", age);
        compound.setInteger("Kind", getKind().ordinal());
        compound.setFloat("BlastPower", blastPower);
        compound.setFloat("DirectDamage", directDamage);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        age = compound.getInteger("Age");
        dataManager.set(KIND, compound.getInteger("Kind"));
        // Rockets saved before blast power was stored with them were all light ones
        blastPower = compound.hasKey("BlastPower") ? compound.getFloat("BlastPower") : (float) RocketType.LIGHT.getBlastPower();
        directDamage = compound.getFloat("DirectDamage");
    }
}
