package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.physics.MissileGuidance;
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
 *
 * <p>A Stinger missile is the same thing given a target ({@link #home}): it steers after it ({@link MissileGuidance}),
 * goes off when it passes close by, and its direct damage only counts against aircraft.
 */
public class EntityRocket extends Entity {
    private static final int LIFETIME_TICKS = 100;
    /** How close a guided missile has to pass its target to go off, in blocks round the hitbox. */
    private static final double PROXIMITY = 0.75;

    /** What was fired: picks the model and trail. Synced so clients draw the right one. */
    public enum Kind {
        ROCKET("rocket", true),
        HEAVY_ROCKET("rocket_heavy", true),
        CANNON_SHELL("cannon_shell", false),
        HOWITZER_SHELL("howitzer_shell", false),
        STINGER("stinger_missile", true);

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
    private int lifetime = LIFETIME_TICKS;
    /** What a guided missile is after (only known until a reload: it flies straight on afterwards), and how hard it turns. */
    @Nullable
    private Entity target;
    private double turnDegrees;
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

    /** Makes it a guided missile after {@code target} (null: unguided), burning out after {@code lifetimeTicks}. */
    public EntityRocket home(@Nullable Entity target, double turnDegreesPerTick, int lifetimeTicks) {
        this.target = target;
        this.turnDegrees = turnDegreesPerTick;
        this.lifetime = Math.max(1, lifetimeTicks);
        return this;
    }

    /** Server: what this missile is chasing, or null. */
    @Nullable
    public Entity getTarget() {
        return target != null && target.isEntityAlive() && target.world == world ? target : null;
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
        Entity chased = getTarget();
        if (chased != null) {
            if (age % 10 == 0) {
                StingerServer.warnInbound(chased);
            }
            double speed = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
            double[] steered = MissileGuidance.steer(new double[] {motionX, motionY, motionZ},
                    new double[] {chased.posX - posX, chased.posY + chased.height / 2 - posY, chased.posZ - posZ},
                    new double[] {chased.posX - chased.prevPosX, chased.posY - chased.prevPosY, chased.posZ - chased.prevPosZ}, speed, turnDegrees);
            motionX = steered[0];
            motionY = steered[1];
            motionZ = steered[2];
            to = from.add(motionX, motionY, motionZ);
        }
        RayTraceResult hit = Raytrace.firstResult(world, from, to, shooter);
        if (chased != null) {
            // Proximity fuze: passing close to the target is a hit, however small its hitbox
            RayTraceResult near = chased.getEntityBoundingBox().grow(PROXIMITY).calculateIntercept(from, to);
            if (near != null && (hit == null || from.squareDistanceTo(near.hitVec) <= from.squareDistanceTo(hit.hitVec))) {
                hit = new RayTraceResult(chased, near.hitVec);
            }
        }
        if (hit != null) {
            boolean stinger = getKind() == Kind.STINGER;
            // A Stinger's warhead is for airframes: anyone else it runs into only gets the blast
            if (hit.entityHit != null && directDamage > 0 && (!stinger || Emp.isVehicle(hit.entityHit))) {
                if (hit.entityHit instanceof EntityLivingBase) {
                    hit.entityHit.hurtResistantTime = 0; // the blast right after must still land
                }
                hit.entityHit.attackEntityFrom(new EntityDamageSourceIndirect(stinger ? "combat_drones.stinger" : "combat_drones.cannon", this, blame())
                        .setProjectile(), directDamage);
            }
            explode(hit.hitVec);
            return;
        }
        setPosition(to.x, to.y, to.z);
        if (++age >= lifetime) {
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
        compound.setInteger("Lifetime", lifetime);
        compound.setInteger("Kind", getKind().ordinal());
        compound.setFloat("BlastPower", blastPower);
        compound.setFloat("DirectDamage", directDamage);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        age = compound.getInteger("Age");
        lifetime = compound.hasKey("Lifetime") ? compound.getInteger("Lifetime") : LIFETIME_TICKS;
        dataManager.set(KIND, compound.getInteger("Kind"));
        // Rockets saved before blast power was stored with them were all light ones
        blastPower = compound.hasKey("BlastPower") ? compound.getFloat("BlastPower") : (float) RocketType.LIGHT.getBlastPower();
        directDamage = compound.getFloat("DirectDamage");
    }
}
