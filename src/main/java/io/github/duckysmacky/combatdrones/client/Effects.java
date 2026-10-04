package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.network.GunFirePacket;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Client-built effects: explosions (near/far sound with a speed-of-sound delay), catapult launches, gunfire. */
public final class Effects {
    /** Blocks per tick: 343 m/s at 20 ticks a second. */
    private static final double SOUND_SPEED = 17;
    /** Closer than this you hear the sharp blast; further out, the delayed rumble. */
    private static final double NEAR_DISTANCE = 64;
    private static final double PARTICLE_DISTANCE = 192;
    /** Rounds passing closer than this crack overhead. */
    private static final double FLYBY_DISTANCE = 4;

    private static final List<Pending> PENDING = new ArrayList<>();

    private Effects() {
    }

    public static void onEffect(EffectPacket packet) {
        World world = Minecraft.getMinecraft().world;
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (world == null || player == null) {
            return;
        }
        switch (packet.getKind()) {
            case EXPLOSION:
                explosion(world, player, packet.getPosition(), packet.getScale());
                break;
            case LAUNCH:
                launch(world, packet.getPosition(), packet.getDirection());
                break;
        }
    }

    private static void explosion(World world, EntityPlayer player, Vec3d at, float power) {
        double distance = player.getPositionEyes(1).distanceTo(at);
        float pitch = 0.9F + world.rand.nextFloat() * 0.2F - Math.min(0.25F, power / 40);
        if (distance < NEAR_DISTANCE) {
            play(new PositionedSoundRecord(ModSounds.EXPLOSION_NEAR, SoundCategory.BLOCKS, Math.max(2, power), pitch,
                    (float) at.x, (float) at.y, (float) at.z), 0);
        } else {
            // A distant rumble isn't positional to the ear; loudness falls with distance and grows with the blast
            float volume = (float) Math.max(0.12, (1 - distance / EffectPacket.EXPLOSION_RANGE) * Math.min(1.2, power / 5));
            play(new PositionedSoundRecord(ModSounds.EXPLOSION_FAR.getSoundName(), SoundCategory.BLOCKS, volume, pitch, false, 0,
                    ISound.AttenuationType.NONE, 0, 0, 0), (int) (distance / SOUND_SPEED));
        }
        if (distance > PARTICLE_DISTANCE) {
            return;
        }
        Random random = world.rand;
        world.spawnParticle(power >= 5 ? EnumParticleTypes.EXPLOSION_HUGE : EnumParticleTypes.EXPLOSION_LARGE, true, at.x, at.y, at.z, 0, 0, 0);
        double radius = 0.4 + power * 0.25;
        for (int index = 0; index < power * 10; index++) {
            Vec3d out = randomDirection(random);
            double speed = (0.1 + random.nextDouble() * 0.2) * Math.sqrt(power);
            world.spawnParticle(EnumParticleTypes.FLAME, true, at.x + out.x * radius * 0.3, at.y + out.y * radius * 0.3, at.z + out.z * radius * 0.3,
                    out.x * speed * 0.5, out.y * speed * 0.5, out.z * speed * 0.5);
        }
        for (int index = 0; index < power * 8; index++) {
            Vec3d out = randomDirection(random);
            world.spawnParticle(index % 3 == 0 ? EnumParticleTypes.CLOUD : EnumParticleTypes.SMOKE_LARGE, true,
                    at.x + out.x * radius, at.y + Math.abs(out.y) * radius, at.z + out.z * radius,
                    out.x * 0.05, 0.05 + random.nextDouble() * 0.12, out.z * 0.05);
        }
        IBlockState ground = world.getBlockState(new BlockPos(at).down());
        if (ground.getMaterial().isSolid()) {
            int state = Block.getStateId(ground);
            for (int index = 0; index < power * 10; index++) {
                Vec3d out = randomDirection(random);
                world.spawnParticle(EnumParticleTypes.BLOCK_DUST, true, at.x, at.y + 0.2, at.z,
                        out.x * 0.4, 0.3 + random.nextDouble() * 0.5, out.z * 0.4, state);
            }
        }
        for (int index = 0; index < 3 + power; index++) {
            world.spawnParticle(EnumParticleTypes.LAVA, true, at.x, at.y, at.z, 0, 0, 0);
        }
    }

    /** Catapult shot: a burst of compressed air at the back of the rail and a puff along it. */
    private static void launch(World world, Vec3d ramp, Vec3d heading) {
        Random random = world.rand;
        for (int index = 0; index < 16; index++) {
            double along = -1.8 + random.nextDouble() * 3.6;
            Vec3d at = ramp.add(heading.scale(along)).add(0, 0.4 + (along + 1.8) * 0.27, 0);
            world.spawnParticle(index < 6 ? EnumParticleTypes.EXPLOSION_NORMAL : EnumParticleTypes.CLOUD, at.x, at.y, at.z,
                    -heading.x * 0.1 + (random.nextDouble() - 0.5) * 0.1, 0.03, -heading.z * 0.1 + (random.nextDouble() - 0.5) * 0.1);
        }
    }

    /** Muzzle flash, tracer sparks, the impact (dust of whatever was hit + sound) and a crack for anyone it passed. */
    public static void gunFire(GunFirePacket packet) {
        World world = Minecraft.getMinecraft().world;
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (world == null || player == null) {
            return;
        }
        Random random = world.rand;
        Vec3d from = packet.getFrom();
        Vec3d to = packet.getTo();
        Vec3d direction = to.subtract(from).normalize();
        world.spawnParticle(EnumParticleTypes.FLAME, from.x, from.y, from.z, direction.x * 0.1, direction.y * 0.1, direction.z * 0.1);
        world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, from.x, from.y, from.z, direction.x * 0.05, 0.02, direction.z * 0.05);
        double length = from.distanceTo(to);
        for (double along = 2; along < length; along += 2) {
            world.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, from.x + direction.x * along, from.y + direction.y * along,
                    from.z + direction.z * along, 0, 0, 0);
        }
        if (packet.isImpact()) {
            IBlockState state = world.getBlockState(new BlockPos(to.add(direction.scale(0.1))));
            for (int index = 0; index < 6; index++) {
                if (state.getMaterial().isSolid()) {
                    world.spawnParticle(EnumParticleTypes.BLOCK_DUST, to.x, to.y, to.z, (random.nextDouble() - 0.5) * 0.2,
                            0.1 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.2, Block.getStateId(state));
                }
                world.spawnParticle(EnumParticleTypes.CRIT, to.x, to.y, to.z, (random.nextDouble() - 0.5) * 0.4,
                        random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
            }
            world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, to.x, to.y, to.z, 0, 0.05, 0);
            play(new PositionedSoundRecord(ModSounds.BULLET_IMPACT, SoundCategory.NEUTRAL, 1.5F, 0.85F + random.nextFloat() * 0.3F,
                    (float) to.x, (float) to.y, (float) to.z), 0);
        }
        Entity shooter = world.getEntityByID(packet.getShooter());
        if (shooter == null || player.getRidingEntity() != shooter) {
            Vec3d ear = player.getPositionEyes(1);
            double along = Math.max(0, Math.min(length, ear.subtract(from).dotProduct(direction)));
            Vec3d closest = from.add(direction.scale(along));
            if (along > 3 && closest.distanceTo(ear) < FLYBY_DISTANCE) {
                play(new PositionedSoundRecord(ModSounds.BULLET_FLYBY, SoundCategory.NEUTRAL, 1.0F, 0.9F + random.nextFloat() * 0.2F,
                        (float) closest.x, (float) closest.y, (float) closest.z), 0);
            }
        }
    }

    private static Vec3d randomDirection(Random random) {
        Vec3d direction = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        return direction.lengthSquared() < 1.0E-6 ? new Vec3d(0, 1, 0) : direction.normalize();
    }

    private static void play(ISound sound, int delayTicks) {
        if (delayTicks <= 0) {
            Minecraft.getMinecraft().getSoundHandler().playSound(sound);
        } else {
            PENDING.add(new Pending(sound, delayTicks));
        }
    }

    /** Client tick: plays delayed sounds when they're due; drops them on leaving the world. */
    static void tick(boolean inWorld) {
        if (!inWorld) {
            PENDING.clear();
            return;
        }
        for (Iterator<Pending> iterator = PENDING.iterator(); iterator.hasNext(); ) {
            Pending pending = iterator.next();
            if (--pending.ticks <= 0) {
                Minecraft.getMinecraft().getSoundHandler().playSound(pending.sound);
                iterator.remove();
            }
        }
    }

    private static final class Pending {
        final ISound sound;
        int ticks;

        Pending(ISound sound, int ticks) {
            this.sound = sound;
            this.ticks = ticks;
        }
    }
}
